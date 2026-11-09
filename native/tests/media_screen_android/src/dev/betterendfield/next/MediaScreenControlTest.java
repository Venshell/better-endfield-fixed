package dev.betterendfield.next;

public final class MediaScreenControlTest {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static final class Backend implements MediaScreenControl.Backend {
        String status = "idle";
        boolean accepted = true;
        int calls, action = -1;
        double value;
        public String status() { return status; }
        public boolean command(int action, double value) { calls++; this.action = action; this.value = value; return accepted; }
    }
    public static void main(String[] args) {
        Backend backend = new Backend();
        MediaScreenControl control = new MediaScreenControl(backend);
        check(control.read().phase() == MediaScreenControl.Phase.IDLE, "manual playback starts idle");
        var result = control.submit(0, 0);
        check(result.accepted() && backend.calls == 1 && backend.action == 0, "start reaches actual command seam");
        check(result.snapshot().phase() == MediaScreenControl.Phase.IDLE, "queue acceptance must not pretend playback started");
        backend.status = "error:Target not found";
        check(control.read().phase() == MediaScreenControl.Phase.ERROR && control.read().detail().equals("Target not found"), "renderer error reaches UI");
        backend.status = "playing:video";
        check(control.read().phase() == MediaScreenControl.Phase.PLAYING, "native playback state");
        control.submit(3, -10);
        check(backend.action == 3 && backend.value == -10, "signed seek reaches native");
        backend.accepted = false;
        check(!control.submit(1, 0).accepted(), "rejected stop must not report success");
        int before = backend.calls;
        check(!control.submit(4, 0).accepted() && !control.submit(3, Double.NaN).accepted() && backend.calls == before, "invalid command rejected before native");
        for (String raw : new String[]{"", "unknown", "x".repeat(513)}) {
            backend.status = raw;
            check(control.read().phase() == MediaScreenControl.Phase.UNAVAILABLE, "invalid status must not look healthy");
        }
        backend.status = "idle:screen_released";
        check(control.read().phase() == MediaScreenControl.Phase.IDLE && control.read().detail().equals("screen_released"), "screen release reason retained");
        check(MediaScreenControl.nativeControl().read().phase() == MediaScreenControl.Phase.UNAVAILABLE, "missing native library explicit");
        System.out.println("PASS MediaScreenControl: " + checks + " checks");
    }
}
