package dev.betterendfield.next;

/** Process-local projection of the native media session; submitting is not playback success. */
final class MediaScreenControl {
    interface Backend {
        String status();
        boolean command(int action, double value);
    }
    enum Phase { UNAVAILABLE, IDLE, LOADING, PLAYING, PAUSED, ENDED, ERROR }
    record Snapshot(Phase phase, String detail) {}
    record Submission(boolean accepted, Snapshot snapshot) {}
    private final Backend backend;

    MediaScreenControl(Backend backend) { this.backend = backend; }

    static MediaScreenControl nativeControl() {
        return new MediaScreenControl(new Backend() {
            public String status() { return NativeCommandBridge.mediaScreenStatus(); }
            public boolean command(int action, double value) { return NativeCommandBridge.mediaScreen(action, value); }
        });
    }

    Snapshot read() {
        try {
            String raw = backend.status();
            if (raw == null || raw.isEmpty() || raw.length() > 512) return new Snapshot(Phase.UNAVAILABLE, "");
            int colon = raw.indexOf(':');
            String phase = colon < 0 ? raw : raw.substring(0, colon);
            String detail = colon < 0 ? "" : raw.substring(colon + 1);
            return switch (phase) {
                case "idle" -> new Snapshot(Phase.IDLE, detail);
                case "loading" -> new Snapshot(Phase.LOADING, detail);
                case "playing" -> new Snapshot(Phase.PLAYING, detail);
                case "paused" -> new Snapshot(Phase.PAUSED, detail);
                case "ended" -> new Snapshot(Phase.ENDED, detail);
                case "error" -> new Snapshot(Phase.ERROR, detail);
                default -> new Snapshot(Phase.UNAVAILABLE, raw);
            };
        } catch (RuntimeException | LinkageError unavailable) {
            return new Snapshot(Phase.UNAVAILABLE, unavailable.getClass().getSimpleName());
        }
    }

    Submission submit(int action, double value) {
        if (action < 0 || action > 3 || !Double.isFinite(value)) return new Submission(false, read());
        try { return new Submission(backend.command(action, value), read()); }
        catch (RuntimeException | LinkageError unavailable) { return new Submission(false, read()); }
    }
}
