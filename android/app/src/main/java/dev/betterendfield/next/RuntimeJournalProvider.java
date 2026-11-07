package dev.betterendfield.next;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/** Receives bounded runtime snapshots from the hooked game process. */
public final class RuntimeJournalProvider extends ContentProvider {
    private static final String STORE = "runtime_journal";
    private static final int MAX_LOG_CHARS = 80_000;

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!"publish".equals(method) || extras == null || !allowedCaller()) {
            throw new SecurityException("journal publisher is not an Endfield process");
        }
        String log = extras.getString("log");
        String status = extras.getString("status");
        if ((log == null && status == null) || (log != null && log.length() > MAX_LOG_CHARS)
                || (status != null && status.length() > 512)) {
            throw new IllegalArgumentException("journal payload exceeds its bound");
        }
        SharedPreferences.Editor edit = getContext().getSharedPreferences(STORE, 0).edit();
        if (log != null) edit.putString("log", log);
        if (status != null) edit.putString("status", status);
        if (!edit.commit()) throw new IllegalStateException("journal store failed");
        return Bundle.EMPTY;
    }

    private boolean allowedCaller() {
        int uid = Binder.getCallingUid();
        if (uid == android.os.Process.myUid()) return true;
        String caller = getCallingPackage();
        if (isGamePackage(caller)) return true;
        String[] packages = getContext().getPackageManager().getPackagesForUid(uid);
        if (packages == null) return false;
        for (String name : packages) {
            if (isGamePackage(name)) return true;
        }
        return false;
    }

    private static boolean isGamePackage(String name) {
        return name != null && (name.equals("com.hypergryph.endfield")
                || name.startsWith("com.hypergryph.endfield.")
                || name.equals("com.gryphline.endfield.gp")
                || name.startsWith("com.gryphline.endfield."));
    }

    static String readLog(android.content.Context context) {
        return context.getSharedPreferences(STORE, 0).getString("log", "");
    }

    static String readStatus(android.content.Context context) {
        return context.getSharedPreferences(STORE, 0).getString("status", "");
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException();
    }
    @Override public String getType(Uri uri) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
