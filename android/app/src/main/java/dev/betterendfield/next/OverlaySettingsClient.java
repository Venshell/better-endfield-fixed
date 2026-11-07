package dev.betterendfield.next;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Binder and disk operations never run on the game UI/render thread. */
final class OverlaySettingsClient {
    private static volatile Supplier<SharedPreferences> frameworkPreferences;
    /** Installed exclusively by the scoped libxposed entry; never open preferences with the game Context. */
    static void initialize(Supplier<SharedPreferences> preferences) { frameworkPreferences = preferences; }
    static InputStream openCore(Context context, String path) throws IOException {
        Supplier<SharedPreferences> source = frameworkPreferences;
        if (source == null) throw new IOException("Resource bridge authorization unavailable");
        String authorization = source.get().getString(OverlayWriteAuthorization.PREFERENCE, "");
        if (!OverlayWritePolicy.validToken(authorization))
            throw new IOException("Resource bridge authorization unavailable");
        Bundle request = new Bundle();
        request.putString(OverlayWriteAuthorization.REQUEST, authorization);
        request.putString("path", path);
        try {
            Bundle reply = context.getContentResolver().call(
                    Uri.parse("content://" + OverlaySettingsProvider.AUTHORITY), "read_resource", null, request);
            if (reply == null || !reply.getBoolean("success"))
                throw new IOException(reply == null ? "Resource bridge unavailable" : reply.getString("error", "Resource bridge unavailable"));
            ParcelFileDescriptor file = reply.getParcelable("file", ParcelFileDescriptor.class);
            if (file == null) throw new IOException("Resource bridge returned no file");
            return new GameResourceStore.VersionedInput(new ParcelFileDescriptor.AutoCloseInputStream(file),
                    reply.getString("version", ""), reply.getLong("length", -1));
        } catch (RuntimeException error) {
            throw new IOException("Resource bridge failed", error);
        }
    }
    private static final OverlayReconnectPolicy reconnect=new OverlayReconnectPolicy();
    private static final java.util.concurrent.ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "BetterEndfield-OverlaySettings"); thread.setDaemon(true); return thread;
    });
    static void call(Context context, String method, String revision, String patch, Consumer<Bundle> callback) {
        Context app = context.getApplicationContext();
        Activity activity=context instanceof Activity?(Activity)context:null;
        WORKER.execute(() -> {
            Bundle reply=invoke(app,method,revision,patch);
            if(!reply.getBoolean("ok") && frameworkPreferences!=null &&
                    !RuntimeBootstrap.MODULE_PACKAGE.equals(app.getPackageName()) &&
                    reconnect.begin(method,reply.getString("error_code"),foreground(activity),
                            SystemClock.elapsedRealtime()) && bootstrap(app,activity)) {
                scheduleRead(app,activity,method,callback,1);
            } else deliver(callback,reply);
        });
    }

    private static Bundle invoke(Context app,String method,String revision,String patch) {
        try {
            Bundle request=new Bundle();
            Supplier<SharedPreferences> source=frameworkPreferences;
            if(source!=null) {
                String authorization=source.get().getString(OverlayWriteAuthorization.PREFERENCE,"");
                if(OverlayWritePolicy.validToken(authorization))
                    request.putString(OverlayWriteAuthorization.REQUEST,authorization);
            }
            if(revision!=null) request.putString("expected",revision);
            if(patch!=null) request.putString("patch",patch);
            Bundle reply=app.getContentResolver().call(
                    Uri.parse("content://"+OverlaySettingsProvider.AUTHORITY),method,null,request);
            if(reply==null) throw new IllegalStateException("设置桥不可用");
            return reply;
        } catch(RuntimeException error) {
            Bundle reply=new Bundle();reply.putBoolean("ok",false);reply.putString("error","设置桥不可用");
            reply.putString("error_code",error instanceof SecurityException
                    ?"caller_rejected":"transport_"+error.getClass().getSimpleName());
            android.util.Log.e("BetterEndfield.Overlay","settings bridge failed category="+
                    reply.getString("error_code"));
            return reply;
        }
    }

    private static void scheduleRead(Context app,Activity activity,String method,
            Consumer<Bundle> callback,int attempt) {
        WORKER.schedule(()->{
            if(!usable(activity)) {
                Bundle stopped=new Bundle();stopped.putBoolean("ok",false);
                stopped.putString("error","设置桥不可用");
                deliver(callback,stopped);return;
            }
            Bundle reply=invoke(app,method,null,null);
            if(!reply.getBoolean("ok") && attempt<OverlayReconnectPolicy.READ_RETRIES)
                scheduleRead(app,activity,method,callback,attempt+1);
            else deliver(callback,reply);
        },OverlayReconnectPolicy.RETRY_DELAY_MS,TimeUnit.MILLISECONDS);
    }

    private static boolean usable(Activity activity) {
        return activity!=null && !activity.isFinishing() && !activity.isDestroyed();
    }
    private static boolean foreground(Activity activity) {return usable(activity) && activity.hasWindowFocus();}
    private static boolean bootstrap(Context app,Activity activity) {
        if(!foreground(activity)) return false;
        try {
            Intent intent=new Intent().setClassName(RuntimeBootstrap.MODULE_PACKAGE,
                    RuntimeBootstrap.MODULE_PACKAGE+".FrameworkBootstrapActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION);
            app.startActivity(intent);
            return true;
        } catch(RuntimeException unavailable) {
            android.util.Log.w("BetterEndfield.Overlay","framework bootstrap unavailable");
            return false;
        }
    }
    private static void deliver(Consumer<Bundle> callback,Bundle reply) {
        new Handler(Looper.getMainLooper()).post(()->callback.accept(reply));
    }
}
