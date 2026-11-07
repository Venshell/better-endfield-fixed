package dev.betterendfield.next;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.view.WindowManager;

/**
 * Reads the device gyroscope in the <em>game</em> process and turns it into the
 * same screen-space look deltas the panel's look pad already produces.
 *
 * <h3>Why it lives here and not in the settings app</h3>
 * A {@code SensorEvent} is delivered to whichever process registered the
 * listener, and the deltas have to reach the native camera module — which only
 * exists inside the game process. A settings-app listener would need a second
 * cross-process channel for a stream that is useless to the settings app
 * itself. Registering here costs nothing: the relay that carries the panel's
 * drags is already open, and {@link NativeCommandBridge#look} writes exactly the
 * line this needs.
 *
 * <h3>Why it feeds the mouse-look channel instead of a camera controller</h3>
 * This module does not own the first-person camera's orientation: the game
 * produces it and {@code ApplyFirstPersonState} deliberately preserves it. The
 * only way a gyroscope can steer that camera without forking the game's look
 * state — which would desynchronise the reticle, the body-follow and every
 * limit the game applies — is to arrive as the same input a finger produces.
 * The deltas below are in the units the free camera's mouse term expects (screen
 * pixels, x right, y down), so the same path serves both.
 *
 * <h3>Integration uses the sensor clock, not the frame clock</h3>
 * The sensor runs near 200 Hz while the game renders at 60–120 FPS, so a frame
 * sees three or four samples. Integrating against a frame delta would both lose
 * the samples between reads and scale the response with the frame rate. Each
 * sample is therefore integrated against {@link SensorEvent#timestamp}, and only
 * the sum is handed on.
 */
final class GyroscopeController {

    /** rad/s below which a reading never leaves the dead zone. */
    private static final float DEFAULT_DEADZONE = 0.002f;
    /** Low-pass retention: 0 = raw, 0.95 = very heavy. */
    private static final float DEFAULT_SMOOTHING = 0.08f;
    /**
     * Pixels per radian at unit sensitivity. One radian of device rotation is
     * about 57 degrees.
     *
     * The native first-person path now divides these pixels by the live Unity
     * render width/height before handing them to CameraManager::OnInput, whose
     * inputs are *screen-percentage* deltas (the free-look controller names them
     * deltaScreenPercentageX/Y). The anchor is therefore "one radian of rotation
     * ~ one screen width of drag": with a ~1080-1440 px landscape screen that is
     * this constant. A fast finger drag is roughly a quarter screen in a third of
     * a second (about 0.8 percentage/s), and one radian/s at this anchor is
     * ~1.0 percentage/s, so the two land in the same ballpark and the sensitivity
     * slider (0.2-5.0) re-scales it in a sane range. The earlier 30 was far too
     * small once the percentage division was added (a slow turn quantized to +-1
     * pixel, which the controller's speedMinThreshold then snap-quantized into
     * the d-pad-like fixed steps seen on device).
     */
    private static final float PIXELS_PER_RADIAN = 1100.0f;
    /**
     * A single sample is never allowed to integrate over more than this. A
     * resumed-from-suspend timestamp gap would otherwise arrive as one enormous
     * dt and throw the camera across the scene.
     */
    private static final float MAX_SAMPLE_SECONDS = 0.05f;
    /** Sample rate hint: 5000 microseconds, i.e. about 200 Hz. */
    private static final int SAMPLE_PERIOD_US = 5000;

    private final Context applicationContext;
    private final Object lock = new Object();

    /**
     * The settings the listener filters with. Written before {@link #start} arms
     * the sensor, so a live sensitivity edit takes effect on the next sample
     * without re-registering the listener.
     */
    private volatile ModuleSettings.FirstPersonGyro currentSettings
            = ModuleSettings.FirstPersonGyro.disabled();

    private SensorManager manager;
    private Sensor gyroscope;
    private boolean started;

    // Guarded by lock. Reset on every start so a restart cannot inherit the
    // previous session's filter state or timestamp.
    private long lastTimestamp;
    private float filteredYawRate;
    private float filteredPitchRate;
    private boolean filterPrimed;
    private float residualX;
    private float residualY;

    GyroscopeController(Context context) {
        this.applicationContext = context.getApplicationContext();
    }

    /**
     * Whether this device has a gyroscope at all, so a phone without the sensor
     * produces a diagnosis instead of silent nothing.
     */
    boolean available() {
        SensorManager sensors = (SensorManager) applicationContext
                .getSystemService(Context.SENSOR_SERVICE);
        return sensors != null && sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null;
    }

    /** True while the sensor is registered and delivering samples. */
    boolean isRunning() {
        synchronized (lock) {
            return started;
        }
    }

    /**
     * Starts sampling with the given settings. Idempotent, and re-entrant after
     * {@link #stop()}. A disabled configuration unregisters instead of starting,
     * so a session with the gyroscope off never holds the sensor open.
     */
    boolean start(ModuleSettings.FirstPersonGyro settings) {
        if (settings == null) {
            stop();
            return false;
        }
        currentSettings = settings;
        synchronized (lock) {
            if (!settings.enabled()) {
                if (manager != null) manager.unregisterListener(listener);
                resetLocked();
                started = false;
                return false;
            }
            if (started) return true;
            manager = (SensorManager) applicationContext
                    .getSystemService(Context.SENSOR_SERVICE);
            if (manager == null) return false;
            gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if (gyroscope == null) return false;
            resetLocked();
            started = manager.registerListener(listener, gyroscope, SAMPLE_PERIOD_US, 0);
            return started;
        }
    }

    /**
     * Stops sampling and drops the accumulated rotation. Clearing is what makes
     * the next start behave: a residual left in {@code residualX/Y} would be
     * delivered as a jump the moment the feature comes back on.
     */
    void stop() {
        synchronized (lock) {
            if (manager != null) manager.unregisterListener(listener);
            resetLocked();
            started = false;
        }
    }

    private void resetLocked() {
        lastTimestamp = 0;
        filteredYawRate = 0.0f;
        filteredPitchRate = 0.0f;
        filterPrimed = false;
        residualX = 0.0f;
        residualY = 0.0f;
    }

    /**
     * Screen rotation, so a landscape game reads the axis the player actually
     * turns about.
     *
     * The signs are the ones the sensor axes imply, not a guess: a phone held in
     * landscape turns about the axis that runs down the screen, and the two
     * landscape rotations place that axis on opposite sensor axes. The
     * unrotated case keeps the sensor's own axes. They stay configurable through
     * the invert switches precisely because a device can disagree.
     */
    private int screenRotation() {
        WindowManager windows =
                (WindowManager) applicationContext.getSystemService(Context.WINDOW_SERVICE);
        if (windows == null || windows.getDefaultDisplay() == null) return Surface.ROTATION_0;
        return windows.getDefaultDisplay().getRotation();
    }

    private final SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event == null || event.values == null || event.values.length < 3) return;
            synchronized (lock) {
                if (!started) return;
                final long timestamp = event.timestamp;
                if (lastTimestamp == 0) {
                    // The first sample has no interval to integrate over; it only
                    // establishes the clock.
                    lastTimestamp = timestamp;
                    return;
                }
                float dt = (timestamp - lastTimestamp) * 1e-9f;
                lastTimestamp = timestamp;
                if (!Float.isFinite(dt) || dt <= 0.0f) return;
                if (dt > MAX_SAMPLE_SECONDS) dt = MAX_SAMPLE_SECONDS;

                final float gx = event.values[0];
                final float gy = event.values[1];
                if (!Float.isFinite(gx) || !Float.isFinite(gy)) return;

                final float yawRate;
                final float pitchRate;
                switch (screenRotation()) {
                    case Surface.ROTATION_90:
                        yawRate = gx;
                        pitchRate = -gy;
                        break;
                    case Surface.ROTATION_270:
                        yawRate = -gx;
                        pitchRate = gy;
                        break;
                    default:
                        // ROTATION_0 / ROTATION_180: the sensor's own axes.
                        yawRate = gy;
                        pitchRate = gx;
                        break;
                }
                addSampleLocked(yawRate, pitchRate, dt, currentSettings);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // The phone gyroscope is used uncalibrated here; nothing in this path
            // consumes the accuracy rating.
        }
    };

    private void addSampleLocked(float yawRate, float pitchRate, float dt,
            ModuleSettings.FirstPersonGyro settings) {
        if (settings == null) return;

        final float deadzone = (float) settings.deadzone();
        yawRate = applyDeadzone(yawRate, deadzone);
        pitchRate = applyDeadzone(pitchRate, deadzone);

        // A one-pole low-pass on the rate, not on the accumulated angle: the
        // angle still integrates every sample, so smoothing costs a little
        // crispness instead of costing input.
        final float smoothing = (float) settings.smoothing();
        if (!filterPrimed) {
            filteredYawRate = yawRate;
            filteredPitchRate = pitchRate;
            filterPrimed = true;
        } else {
            filteredYawRate = filteredYawRate * smoothing + yawRate * (1.0f - smoothing);
            filteredPitchRate = filteredPitchRate * smoothing + pitchRate * (1.0f - smoothing);
        }

        final float horizontal = (float) settings.horizontalSensitivity();
        final float vertical = (float) settings.verticalSensitivity();
        final float scale = dt * PIXELS_PER_RADIAN;
        // Sign conventions the camera module already expects: a positive delta x
        // looks right, a positive delta y looks down. The invert switches flip
        // the axis at the source so the same convention reaches both consumers.
        final float horizontalSign = settings.invertHorizontal() ? -1.0f : 1.0f;
        final float verticalSign = settings.invertVertical() ? -1.0f : 1.0f;

        // Accumulated in a float pixel field that is deliberately not truncated
        // per sample: at 200 Hz a slow turn moves well under one pixel per
        // sample, so rounding each one would mute the input entirely. Only whole
        // pixels leave, and the fraction is carried forward.
        residualX += filteredYawRate * scale * horizontal * horizontalSign;
        residualY += filteredPitchRate * scale * vertical * verticalSign;

        final int dx = (int) residualX;
        final int dy = (int) residualY;
        if (dx == 0 && dy == 0) return;
        residualX -= dx;
        residualY -= dy;
        NativeCommandBridge.look(dx, dy);
    }

    private static float applyDeadzone(float value, float threshold) {
        return Math.abs(value) < threshold ? 0.0f : value;
    }

    /** The bounds {@link #start} accepts, exposed for the settings screen. */
    static float defaultDeadzone() {
        return DEFAULT_DEADZONE;
    }

    static float defaultSmoothing() {
        return DEFAULT_SMOOTHING;
    }
}
