package dev.betterendfield.next;

import android.content.Context;
import android.os.Bundle;
import android.net.Uri;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * In-process runtime journal for the hooked game process. Every milestone of
 * the load pipeline (LSPosed attach, settings schema, module selection, Unity
 * frame trigger, native library load attempts) records here. The ring is
 * sent to the companion app through its journal provider and echoed to logcat.
 * The hooked process's framework preferences are read-only.
 */
public final class RuntimeLog {
    private static final int CAPACITY = 150;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static volatile Context gameContext;
    private static final java.util.concurrent.ExecutorService SENDER =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "BetterEndfield-Journal");
                thread.setDaemon(true);
                return thread;
            });
    private static boolean sending;
    private static String pendingLog;
    private static String pendingStatus;

    private RuntimeLog() {}

    static void bind(Context context) {
        gameContext = context;
        flush();
    }

    /**
     * Sees every journal line as it is recorded. The overlay uses this to read
     * the native module's own playback events - the runtime reports a started
     * or stopped take through the log it already publishes, and nothing else in
     * the process can tell whether a take is still running.
     */
    public interface Observer {
        void onJournalLine(String line);
    }

    private static final java.util.concurrent.CopyOnWriteArrayList<Observer> OBSERVERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Registers an observer; it must be removed before its surface goes away. */
    public static void observe(Observer observer) {
        OBSERVERS.addIfAbsent(observer);
    }

    public static void stopObserving(Observer observer) {
        OBSERVERS.remove(observer);
    }

    public static void record(String message) {
        String line = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date())
                + " " + message;
        synchronized (RuntimeLog.class) {
            if (line.length() > 512) line = line.substring(0, 512);
            if (LINES.size() >= CAPACITY) LINES.pollFirst();
            LINES.addLast(line);
            Log.i("BetterEndfield.Runtime", message);
            flush();
        }
        if (OBSERVERS.isEmpty()) return;
        // Notified outside the lock, and never allowed to take the journal down
        // with it: an observer runs on whichever thread recorded the line.
        for (Observer observer : OBSERVERS) {
            try {
                observer.onJournalLine(line);
            } catch (Throwable ignored) { /* observation must never break logging */ }
        }
    }

    /** Queue the latest bounded snapshot without blocking the game's hook thread. */
    public static synchronized boolean flush() {
        if (gameContext == null || LINES.isEmpty()) return false;
        StringBuilder all = new StringBuilder();
        for (String line : LINES) all.append(line).append('\n');
        pendingLog = all.toString();
        scheduleSend();
        return true;
    }

    private static synchronized void scheduleSend() {
        if (sending || gameContext == null) return;
        sending = true;
        SENDER.execute(() -> {
            while (true) {
                String log;
                String status;
                Context context;
                synchronized (RuntimeLog.class) {
                    log = pendingLog;
                    status = pendingStatus;
                    context = gameContext;
                    pendingLog = null;
                    pendingStatus = null;
                    if (log == null && status == null) {
                        sending = false;
                        return;
                    }
                }
                Bundle payload = new Bundle();
                if (log != null) payload.putString("log", log);
                if (status != null) payload.putString("status", status);
                try {
                    context.getContentResolver().call(
                            Uri.parse("content://dev.betterendfield.next.journal"),
                            "publish", null, payload);
                } catch (RuntimeException error) {
                    Log.w("BetterEndfield.Runtime", "journal publish failed", error);
                }
            }
        });
    }

    /**
     * The newest {@code max} lines, oldest first. Read by the overlay panel —
     * the same process, so this path cannot lose a race with any transport.
     */
    public static synchronized String tail(int max) {
        if (LINES.isEmpty()) return "";
        int skip = Math.max(0, LINES.size() - max);
        java.util.Iterator<String> it = LINES.iterator();
        for (int i = 0; i < skip; i++) it.next();
        StringBuilder out = new StringBuilder();
        while (it.hasNext()) out.append(it.next()).append('\n');
        return out.toString();
    }

    /**
     * Mirrors the native command status through the journal provider.
     */
    public static synchronized boolean setStatus(String status) {
        if (gameContext == null || status == null || status.isEmpty()) return false;
        pendingStatus = status.length() > 512 ? status.substring(0, 512) : status;
        scheduleSend();
        return true;
    }
}
