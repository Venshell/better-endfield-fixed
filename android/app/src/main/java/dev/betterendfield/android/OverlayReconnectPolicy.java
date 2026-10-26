package dev.betterendfield.android;

/** Only failed foreground reads may wake the settings owner. */
final class OverlayReconnectPolicy {
    static final int READ_RETRIES=4, RETRY_DELAY_MS=250;
    static final long MIN_WAKE_INTERVAL_MS=30_000;
    private boolean attempted;
    private long lastAttempt;

    boolean begin(String method,String failure,boolean foreground,long now) {
        if(!foreground || !("read_models".equals(method) || "read_fov".equals(method))) return false;
        if(failure==null || !("caller_rejected".equals(failure) || failure.startsWith("transport_"))) return false;
        if(attempted && (now<lastAttempt || now-lastAttempt<MIN_WAKE_INTERVAL_MS)) return false;
        attempted=true;lastAttempt=now;
        return true;
    }
}
