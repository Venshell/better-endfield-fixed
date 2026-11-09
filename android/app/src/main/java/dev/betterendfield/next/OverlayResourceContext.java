package dev.betterendfield.next;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;
import java.util.Locale;

/** Module resources with host identity, storage and resolver retained for game-process UI. */
final class OverlayResourceContext extends ContextWrapper {
    private final Context resourceOwner;

    OverlayResourceContext(Context host) throws PackageManager.NameNotFoundException {
        super(host);
        Context module = host.createPackageContext(RuntimeBootstrap.MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
        Configuration configuration = new Configuration(module.getResources().getConfiguration());
        Locale locale = Locale.forLanguageTag(AppLanguage.tag());
        configuration.setLocales(new LocaleList(locale));
        configuration.setLayoutDirection(locale);
        resourceOwner = module.createConfigurationContext(configuration);
    }

    @Override public Resources getResources() { return resourceOwner.getResources(); }
    @Override public AssetManager getAssets() { return resourceOwner.getAssets(); }
    @Override public Resources.Theme getTheme() { return resourceOwner.getTheme(); }
}
