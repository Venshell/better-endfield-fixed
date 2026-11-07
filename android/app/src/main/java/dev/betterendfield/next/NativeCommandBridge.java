package dev.betterendfield.next;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Panel-to-runtime channel. This used to be a JNI bridge, but the runtime
 * library registers under the game's classloader while these classes belong to
 * the LSPosed module classloader — and Android scopes JNI symbol lookup (and
 * .so openings) per classloader, so every call threw UnsatisfiedLinkError while
 * the native modules themselves ran fine. Plain files in the game's own files
 * directory have no such boundary: the native side (input_relay.cpp) tails the
 * input file and publishes the status file.
 *
 * Protocol, one event per line:
 *   "&lt;vk&gt; &lt;action&gt;\n"  latch a virtual key
 *   "c &lt;payload&gt;\n"       submit a runtime command
 *   "r\n"               release every latched key
 *   "m &lt;dx&gt; &lt;dy&gt;\n"     accumulate a mouse-look delta
 *   "p &lt;dx&gt; &lt;dy&gt;\n"     accumulate a PC-layout relative mouse delta
 *   "P &lt;0|1&gt;\n"         report platform pointer capture for the PC layout
 */
final class NativeCommandBridge {
    /** Matches betterendfieldnext::VirtualKeyAction in native/shared/android_compat. */
    static final int KEY_RELEASE = 0;
    static final int KEY_PRESS = 1;
    static final int KEY_PULSE = 2;

    /**
     * Stands in for a newline inside a command payload.
     *
     * <p>The relay frames the input stream by newlines - one event per line -
     * but a runtime command is itself multi-line ({@code BE_COMMAND_V1} on its
     * own line, then the generation, then the verb, then the value). Writing
     * those newlines raw ends the event after its first field, and the relay
     * hands the pump the 13-byte fragment {@code "BE_COMMAND_V1"}, which it
     * rejects outright. Folding them into U+001F keeps the whole command on one
     * line; the native relay unfolds it. A control character, not a printable
     * one, so no verb or value can collide with it.
     */
    private static final char LINE_SEPARATOR = '\u001f';

    private static final Object LOCK = new Object();
    private static volatile File inputFile;
    private static volatile File statusFile;
    private static volatile File nativeLogFile;

    private NativeCommandBridge() {}

    /** Called from the game process before the native runtime loads. */
    static void configure(File input, File status, File nativeLog) {
        inputFile = input;
        statusFile = status;
        nativeLogFile = nativeLog;
        // Start every session with an empty stream so the native tail cannot
        // replay events from a previous run of the game.
        try (FileOutputStream fresh = new FileOutputStream(input)) {
            fresh.getChannel().force(false);
        } catch (Throwable ignored) {
            // The panel reports "unable to send" until the directory exists.
        }
        try (FileOutputStream fresh = new FileOutputStream(nativeLog)) {
            fresh.getChannel().force(false);
        } catch (Throwable ignored) {
            // The journal simply stays empty until the runtime creates it.
        }
    }

    /**
     * Submits a runtime command. The payload is multi-line; it travels folded
     * into a single relay line and is unfolded by the native relay. Note the
     * absence of a trim: the payload's own trailing newline is part of the
     * shape the pump validates, and folding it preserves that terminator.
     */
    static boolean submit(String payload) {
        if (payload == null || payload.isEmpty()) return false;
        String flat = payload.replace('\r', ' ').replace('\n', LINE_SEPARATOR);
        return append("c " + flat + "\n");
    }

    /**
     * Presses a Windows virtual-key code in the latch the ported desktop
     * modules poll through GetAsyncKeyState. Returns false while the channel
     * is not configured (no native runtime expected yet).
     */
    static boolean key(int virtualKey, int action) {
        if (action < KEY_RELEASE || action > KEY_PULSE) return false;
        return append(virtualKey + " " + action + "\n");
    }

    static boolean releaseKeys() {
        return append("r\n");
    }

    /**
     * Adds a mouse-look delta, in the screen pixels and the axis directions the
     * free camera's mouse term already expects (x right, y down). Deliberately
     * not a "press and release" pair like {@link #key}: looking is a stream, not
     * a state, and the native side sums whatever arrives instead of queueing it,
     * so a dropped line costs a few pixels rather than a movement.
     */
    static boolean look(int dx, int dy) {
        if (dx == 0 && dy == 0) return false;
        return append("m " + dx + " " + dy + "\n");
    }

    /**
     * The PC layout's three transport calls, and the only members of this class
     * served over JNI rather than the file relay. Upstream serves them this way
     * and the binding now works: {@code JNI_OnLoad} registers them with
     * {@code BindContextLoaderNatives}, which resolves this class from the
     * calling thread's context classloader instead of the classloader the
     * library was opened under. That is what the rest of this class cannot use
     * -- the runtime has to live in the game's namespace and the relay exists
     * precisely because name-based lookup fails across that boundary.
     *
     * <p>They are declared here, after the relay members, so the one place that
     * is not a relay call is visible at a glance.
     */
    static native boolean pcMouseCaptureRequested();
    static native void pcMouseCaptured(boolean captured);
    static native void pcMouseMotion(float dx, float dy);

    /**
     * One frame, the way the engine hands it to the scriptable render pipeline.
     * The native frame clients advance only here -- the PC layout's relative
     * mouse reads its motion snapshot once per frame -- so this has to be called
     * for every rendered frame, and the hook that drives it has to stay
     * installed for as long as the runtime is.
     */
    static native void frame();

    /**
     * The runtime status string: the module startup sequence the native side
     * recorded, plus the camera module's live counters, one {@code id=state}
     * per line behind a {@code BE_RUNTIME_V1} header. Served over JNI rather
     * than the relay, like the PC mouse calls, because the reader is this
     * class's own caller -- the panel, one classloader away.
     */
    static native String runtimeStatus();

    /**
     * The runtime command status the native relay publishes. Empty until the
     * native runtime has loaded — the same "not ready yet" signal the JNI
     * version used to give through exceptions.
     */
    static String status() {
        File file = statusFile;
        if (file == null) return "";
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] bytes = stream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (IOException notYet) {
            return "";
        }
    }

    /**
     * Appends native log lines written since the previous call to {@code into}
     * (complete lines only; a trailing partial line waits for its newline).
     * {@code offset[0]} is the read cursor, owned by the caller. Empty when the
     * runtime has not produced anything (or has not loaded).
     */
    static void tailNativeLog(StringBuilder into, long[] offset) {
        File file = nativeLogFile;
        if (file == null) return;
        try (FileInputStream stream = new FileInputStream(file)) {
            long size = stream.getChannel().size();
            if (size < offset[0]) offset[0] = 0;  // the relay truncated the file
            if (size == offset[0]) return;
            stream.getChannel().position(offset[0]);
            byte[] chunk = new byte[(int) Math.min(size - offset[0], 65536)];
            int got = stream.read(chunk);
            if (got <= 0) return;
            offset[0] += got;
            into.append(new String(chunk, 0, got, StandardCharsets.UTF_8));
        } catch (IOException notYet) {
            // The relay creates the file when the runtime loads.
        }
    }

    private static boolean append(String line) {
        File file = inputFile;
        if (file == null) return false;
        synchronized (LOCK) {
            try (FileOutputStream stream = new FileOutputStream(file, true)) {
                stream.write(line.getBytes(StandardCharsets.UTF_8));
                stream.flush();
                return true;
            } catch (IOException broken) {
                return false;
            }
        }
    }
    static native int protocolVersion();
    /** Queues a prepared BEM snapshot; Unity applies it on a later frame. */
    static native boolean updateCustomModelConfig(String configuration);
    static native boolean setCustomModelDriver(String id, int state, int speedMillis);
    static native String customModelDriverStatus();
    static native boolean mediaScreen(int action, double value);
    static native String mediaScreenStatus();
    static native void foreground(boolean visible);
    static native int pcMouseCursorMode();
    static native void pcMouseAbsolute(float x, float y);
    static native void pcMouseDirectTouch(boolean active);
    static native void cameraValues(float speed, float fov);
    static native boolean globalFov(boolean enabled, float fov);
    static native String mmdStatus();
    private static native boolean mmd(int type, int argument, double value, String text);
    static boolean mmdCommand(String command) {
        try {
            org.json.JSONObject value = new org.json.JSONObject(command);
            return mmd(value.getInt("type"), value.optInt("argument", -1),
                    value.optDouble("value", 0), value.optString("text", ""));
        } catch (org.json.JSONException invalid) { return false; }
    }

    // Section 13 driver sound effects: independent MediaPlayer voices, mixed by
    // the OS alongside the game's own audio.
    static int driverAudioStart(String key, String path, float gain, boolean loop) {
        return DriverAudio.start(key, path, gain, loop);
    }
    static void driverAudioStop(String key) { DriverAudio.stop(key); }
    static void driverAudioStopAll() { DriverAudio.stopAll(); }
    static String driverAudioStatus() { return DriverAudio.status(); }

    // Called through the explicitly bound module ClassLoader from native.
    static long audioOpen(String path) { return MmdAudio.open(path); }
    static int audioControl(long token, int operation, double value) { return MmdAudio.control(token, operation, value); }
    static double[] audioStatus(long token) { return MmdAudio.status(token); }
    static String audioError(long token) { return MmdAudio.error(token); }
}
