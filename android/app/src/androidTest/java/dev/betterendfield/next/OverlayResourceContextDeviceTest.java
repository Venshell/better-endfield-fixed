package dev.betterendfield.next;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;

/** Reproduces a foreign host's inability to resolve module IDs, then exercises the resource boundary. */
final class OverlayResourceContextDeviceTest {
    static void run(Context module) throws Exception {
        Context foreignHost = new ContextWrapper(module) {
            @Override public Resources getResources() { return Resources.getSystem(); }
            @Override public String getPackageName() { return "android"; }
        };
        try {
            foreignHost.getString(R.string.media_runtime_title);
            throw new AssertionError("Foreign resource table unexpectedly resolved a module ID");
        } catch (Resources.NotFoundException reproduced) {
            // This is the same failure seen at MediaScreenControls during game startup.
        }
        Context wrapped = new OverlayResourceContext(foreignHost);
        for (int id : new int[]{R.string.media_runtime_title, R.string.media_runtime_start,
                R.string.model_driver_title, R.string.bem_all_targets}) {
            if (!module.getString(id).equals(wrapped.getString(id)))
                throw new AssertionError("Module string was not resolved by its resource owner");
        }
        if (!wrapped.getPackageName().equals(foreignHost.getPackageName())
                || wrapped.getApplicationContext() != foreignHost.getApplicationContext()
                || wrapped.getContentResolver() != foreignHost.getContentResolver()
                || !wrapped.getFilesDir().equals(foreignHost.getFilesDir()))
            throw new AssertionError("Resource projection changed host identity, resolver or storage");
    }
}
