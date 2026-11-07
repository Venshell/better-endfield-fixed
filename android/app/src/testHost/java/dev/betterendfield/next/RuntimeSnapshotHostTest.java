package dev.betterendfield.next;

/**
 * Device-free contract check for the runtime status string.
 *
 * <p>{@code RuntimeSnapshot} is pure {@code java.util}: it reads the text
 * {@code native_bridge.cpp} concatenates and turns it into the questions the
 * overlay asks ({@code ready}, {@code cameraAvailable}, {@code summary}). That
 * makes the parser itself testable on a plain JVM, with no Android stubs and no
 * device, against the exact byte shape the producer emits.
 *
 * <p>Run through {@code runtime_snapshot_fixture.py}, next to the source set.
 */
public final class RuntimeSnapshotHostTest {
    private static int failed;

    private static void check(String what, boolean ok) {
        if (!ok) { failed++; System.out.println("FAIL  " + what); }
        else { System.out.println("ok    " + what); }
    }

    public static void main(String[] args) {
        // Exactly the shape native_bridge.cpp concatenates for a running camera
        // module: the startup record, then the camera counters, then the frame
        // clients' state. std::to_string(float) is the "%.6f" below.
        String ready = "BE_RUNTIME_V1\n"
                + "runtime=startup_complete\n"
                + "betterendfield.camera=ready\n"
                + "betterendfield.ui=ready\n"
                + "camera.speed=5.000000\n"
                + "camera.fov=60.000000\n"
                + "camera.global_fov_enabled=0\n"
                + "camera.global_fov=60.000000\n"
                + "camera.capabilities=3\n"
                + "camera.active=1\n"
                + "ui.hud_hidden=1\n";

        RuntimeSnapshot live = RuntimeSnapshot.parse(ready);
        check("connected", live.connected);
        check("camera module ready", live.ready("betterendfieldnext.camera"));
        check("camera capability bit 1", live.cameraAvailable(1));
        check("camera capability bit 3 absent", !live.cameraAvailable(4));
        check("free camera active", live.cameraActive(1));
        check("world pause not active", !live.cameraActive(8));
        check("hud hidden", live.hudHidden());
        check("not failed", !live.failed());

        // Contract note: number() is integer-only, and the producer writes floats
        // with std::to_string, so "5.000000" does NOT parse as an int. Upstream
        // reads those keys with a float parser (finiteNumber) and only ever hands
        // number() the integer-valued ones (camera.capabilities, camera.active).
        check("number() is integer-only", live.number("camera.speed") == 0);
        check("integer keys do parse", live.number("camera.capabilities") == 3);
        check("float keys need a float parser",
                Float.parseFloat(live.values.get("camera.fov")) == 60f);
        check("summary names the free camera",
                live.summary().equals("自由镜头 · 按住移动，划动转向"));

        // Starting up: no per-module record yet.
        RuntimeSnapshot starting =
                RuntimeSnapshot.parse("BE_RUNTIME_V1\nruntime=starting_modules\n");
        check("starting is connected", starting.connected);
        check("starting is not ready", !starting.ready("betterendfieldnext.camera"));
        check("starting summary", starting.summary().equals("模块准备中 · 长按查看"));

        // A module that failed has to surface as such, not as "still starting".
        RuntimeSnapshot broken = RuntimeSnapshot.parse(
                "BE_RUNTIME_V1\nruntime=startup_complete\nbetterendfield.camera=failed_exception\n");
        check("failed detected", broken.failed());
        check("failed summary", broken.summary().equals("部分模块启动失败 · 长按查看"));

        // The degraded paths the overlay relies on. Any of these returning a
        // connected snapshot would put a confident lie on screen.
        check("null is offline", !RuntimeSnapshot.parse(null).connected);
        check("wrong header is offline",
                !RuntimeSnapshot.parse("NOT_BE\nruntime=startup_complete\n").connected);
        check("offline summary", RuntimeSnapshot.offline().summary().equals("等待运行时连接"));
        check("oversized rejected",
                !RuntimeSnapshot.parse("BE_RUNTIME_V1\n" + "a=b\n".repeat(3000)).connected);

        // Malformed lines are skipped, not fatal.
        RuntimeSnapshot ragged = RuntimeSnapshot.parse(
                "BE_RUNTIME_V1\nruntime=startup_complete\n=novalue\nnokey=\nkept=1\n");
        check("ragged keeps good lines", ragged.number("kept") == 1);
        check("ragged ignores empty key", !ragged.values.containsKey(""));

        System.out.println(failed == 0
                ? "RuntimeSnapshotHostTest: all checks passed"
                : "RuntimeSnapshotHostTest: " + failed + " CHECK(S) FAILED");
        if (failed != 0) System.exit(1);
    }
}
