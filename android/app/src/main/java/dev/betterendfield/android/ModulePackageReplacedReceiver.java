package dev.betterendfield.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.IOException;

/** Keeps the update-started process alive briefly for the framework callback. */
public final class ModulePackageReplacedReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        // ModuleApplication has already registered the framework listener.
        if(FrameworkSettings.remoteAvailable()) return;
        PendingResult pending=goAsync();
        boolean started=false;
        try {
            new Thread(()->{
                try {
                    boolean connected=FrameworkServiceWait.await(FrameworkSettings.class,
                            FrameworkSettings::remoteAvailable,8_000,()->{});
                    Log.i("BetterEndfield.Settings",connected
                            ?"Package replacement framework connected"
                            :"Package replacement framework wait timed out");
                } catch(IOException | RuntimeException error) {
                    Log.w("BetterEndfield.Settings","Package replacement framework wait failed",error);
                } finally {pending.finish();}
            },"BE-update-framework").start();
            started=true;
        } catch(RuntimeException error) {
            Log.w("BetterEndfield.Settings","Package replacement worker unavailable",error);
        } finally {
            if(!started) pending.finish();
        }
    }
}
