package dev.betterendfield.android;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Starts ModuleApplication's existing framework listener without opening settings. */
public final class FrameworkBootstrapActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean resumed;
    private long startedAt;
    private final Runnable waitForFramework=new Runnable() {
        @Override public void run() {
            if(!resumed || isFinishing() || isDestroyed()) return;
            long elapsed=SystemClock.uptimeMillis()-startedAt;
            if(elapsed>=500 && (FrameworkSettings.remoteAvailable() || elapsed>=1_500)) {
                finishBootstrap();return;
            }
            handler.postDelayed(this,100);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {super.onCreate(savedInstanceState);}
    @Override protected void onResume() {
        super.onResume();
        if(isFinishing() || isDestroyed()) return;
        resumed=true;startedAt=SystemClock.uptimeMillis();
        handler.removeCallbacks(waitForFramework);
        handler.postDelayed(waitForFramework,100);
    }
    @Override protected void onPause() {resumed=false;finishBootstrap();super.onPause();}
    @Override protected void onDestroy() {
        resumed=false;handler.removeCallbacks(waitForFramework);super.onDestroy();
    }
    private void finishBootstrap() {
        handler.removeCallbacks(waitForFramework);
        if(!isFinishing()) finish();
    }
}
