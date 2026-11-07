package dev.betterendfield.next;

import android.content.Context;
import java.util.function.Consumer;

/** Android mapping; call from the existing asset preparation worker. */
final class HeadwearAssets {
    private HeadwearAssets() {}
    static String materialize(Context game, Context module, Consumer<String> log) {
        try {
            long version = game.getPackageManager().getPackageInfo(game.getPackageName(), 0).getLongVersionCode();
            String directory = HeadwearAssetStore.materialize(game.getFilesDir().toPath(),
                    name -> module.getAssets().open(name), version).toString();
            log.accept("headwear packaged assets ready: " + directory);
            return directory;
        } catch (HeadwearAssetStore.StoreException failure) {
            log.accept("headwear packaged assets unavailable stage=" + failure.stage + " reason=" + failure.getMessage());
        } catch (Exception failure) {
            log.accept("headwear packaged assets unavailable stage=android reason=" + failure);
        }
        return "";
    }
}
