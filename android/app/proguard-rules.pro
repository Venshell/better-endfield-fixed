# Better Endfield - Android module, release build (R8).
#
# The upstream file here kept a single class name from the legacy Xposed API
# (assets/xposed_init era) and had never been exercised: the release build ran
# with minification off since 2.1.1. Turning R8 on without the rules below is
# not a "smaller APK" change, it is a change that fails silently in the field:
# the module still installs and still launches, but the framework cannot
# instantiate it, or the texture installer never reports progress. Each block
# names the code that reads the name it protects.

# --- libxposed entry point --------------------------------------------------
# The framework reads the entry class name from META-INF/xposed/java_init.list
# and instantiates it reflectively (libxposed API 101+ replaced the legacy
# assets/xposed_init with that list). No root in this APK references the type,
# so without this rule R8 deletes it entirely rather than renaming it.
-keep class dev.betterendfield.next.XposedEntry { *; }

# --- JNI --------------------------------------------------------------------
# src/main/cpp/installer/install_jni.cpp exports
#   Java_dev_betterendfield_next_BemInstaller_{inspect,convert,cancel}Native
# and looks its progress callback up by name and signature:
#   GetStaticMethodID(owner, "conversionProgress", "(Ljava/lang/String;IIIIF)V")
# The exported symbol name *is* the class name plus the method name, and the
# callback has no Java-side caller, so both would otherwise be renamed or
# inlined: the first makes inspect/convert/cancel throw UnsatisfiedLinkError,
# the second makes GetStaticMethodID return null and the conversion abort with
# no progress. Keeping the class whole answers both.
#
# The second shipped library, libbetterendfieldnext_android.so, also exports
# Java_dev_betterendfield_next_NativeCommandBridge_* symbols. Nothing declares
# them any more -- the panel-to-runtime channel is a file relay, not JNI (see the
# NativeCommandBridge header) -- so they are dead weight in a prebuilt library and
# R8 is free to merge that class away. verifyReleaseEntryPoints in
# build.gradle.kts records them explicitly instead of ignoring them, so a new
# native method without a rule here fails the build rather than disappearing.
-keep class dev.betterendfield.next.BemInstaller { *; }

# --- prevent R8 from merging unrelated game-process and Compose classes ------
# R8's optimiser merges classes: anything never instantiated is folded into a
# shared static holder, and lambda classes are pooled the same way. That is a
# normal, desirable optimisation for an ordinary app and a hazard here, because
# this module's panel is loaded into the *game* process.
#
# Measured on the shipped dex with the optimiser on (android/app 3.3.21):
#   * NativeCommandBridge - static fields, static methods, no instances, exactly
#     the shape R8 folds - was merged into class 'Lr4;'. That class declares
#     NativeCommandBridge's own members (Q = status(), R = tailNativeLog, and the
#     inputFile/statusFile/nativeLogFile fields XposedEntry writes) *and* a
#     <clinit> that instantiates androidx.compose.ui.BiasAbsoluteAlignment,
#     androidx.compose.ui.BiasAbsoluteAlignment$Horizontal and
#     androidx.compose.foundation.layout.Arrangement$Absolute$Left$1.
#   * Walking the class-reference graph from the libxposed entry point
#     XposedEntry reached 2463 of the dex's 2537 classes, and 364 of those have a
#     <clinit> that constructs an androidx.compose object.
#
# The remote-command poller in the game process calls status() on a timer, and an
# active use of any of those holders runs a <clinit> that builds Compose objects
# inside Unity. The experimental panel now loads Compose intentionally, but
# unrelated command and hook classes still must not gain Compose initialisers.
#
# With -dontoptimize the same walk reaches 91 classes: the module's own code, the
# Android/JDK platform and the libxposed client API. The experiment changes this
# closure for the overlay path only; the historical measurement remains useful.
# Shrinking and obfuscation still run, which is where the size win actually comes
# from (28.0 MB unminified -> 8.6 MB); dropping the optimiser costs ~1.5 MB on
# top of that and buys a game-process class graph identical to the source's.
#
# verifyReleaseEntryPoints in build.gradle.kts fails the build if a class on the
# game path is folded again, so this does not silently regress.
-dontoptimize

# --- field diagnostics ------------------------------------------------------
# The in-game journal is the only debugging channel on a device we do not own;
# without these attributes a stack trace written there is unreadable.
-keepattributes SourceFile,LineNumberTable

# --- MMD audio transport ----------------------------------------------------
# src/main/cpp/core/local_music_android.cpp resolves four statics on the bridge
# class by name and signature:
#   GetStaticMethodID(owner, "audioOpen",    "(Ljava/lang/String;)J")
#   GetStaticMethodID(owner, "audioControl", "(JID)I")
#   GetStaticMethodID(owner, "audioStatus",  "(J)[D")
#   GetStaticMethodID(owner, "audioError",   "(J)Ljava/lang/String;")
# The player behind them is Java, so these are plain methods -- the default
# `-keepclasseswithmembernames class * { native <methods>; }` that keeps the
# class name does not keep their names. Renamed, every lookup returns null,
# InitializeAndroidMusic refuses the interface, and MMD works play silently
# without their music track. verifyReleaseEntryPoints asserts the names survive
# in the dex, so a lost rule fails the build instead of the feature.
-keepclassmembers class dev.betterendfield.next.NativeCommandBridge {
    static long audioOpen(java.lang.String);
    static int audioControl(long, int, double);
    static double[] audioStatus(long);
    static java.lang.String audioError(long);
}
