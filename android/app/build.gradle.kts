// Explicit imports: inside a build script `java` resolves to Gradle's java
// extension, so `java.util.zip.ZipFile` has to be imported by name.
import groovy.json.JsonSlurper
import java.io.File
import java.util.Properties
import java.util.zip.ZipFile
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Generated asset directories are registered with AGP's Variant API so the
// producer dependency and generated/read-only ownership remain explicit.
abstract class GeneratedAssetCopy : Copy() {
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
}
abstract class GeneratedAssetSync : Sync() {
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
}
abstract class GeneratedAssetExec : Exec() {
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
}



// testHost 的独立 main 检查不是 JUnit；统一门禁拥有编译、执行和计数。
// 每次执行重新编译，不能把 Gradle NO-SOURCE 或旧 class 当成回归通过。
val verifyPortHost by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs Android port Java host suites and four Python BEM suites."
    workingDir(rootProject.file(".."))
    commandLine(providers.gradleProperty("portHostPython").orElse("python").get(),
        "-B", rootProject.file("../tools/AndroidPort/run_host_gate.py").absolutePath,
        "--java-home", System.getProperty("java.home"),
        "--work-dir", layout.buildDirectory.dir("portHost").get().asFile.absolutePath)
}

tasks.matching { it.name == "check" || it.name.matches(Regex("test.*UnitTest")) }.configureEach {
    dependsOn(verifyPortHost)
}

// Signing identities. The two are deliberately kept at different secrecy.
//
//   debug   -- keystore/bem-debug.keystore, committed together with its
//              (therefore public) password. Pinning it is what lets two
//              consecutive CI debug APKs install over one another; a debug
//              identity protects nothing, so there is no secret to keep.
//   release -- keystore/bem-release.keystore, never committed. The
//              distribution repository is public, and whoever holds this file
//              can sign an APK that Android accepts as an in-place upgrade of
//              the installed app. Locally the credentials come from the
//              gitignored keystore/release.properties; in CI the workflow
//              writes that file from repository secrets.
//
// Both descriptions are read here, at configuration time, but neither may
// fail the build here: a missing release identity must not break
// `assembleDebug`. The checkDebugSigning / checkReleaseSigning tasks below turn
// a missing identity into an instruction at the moment it is actually needed.
val signingDescriptions: Map<String, File> = mapOf(
    "debug" to rootProject.file("keystore/debug.properties"),
    "release" to rootProject.file("keystore/release.properties")
)
val signingProperties: Map<String, Properties> = signingDescriptions.mapValues { (_, file) ->
    Properties().apply { if (file.isFile) file.reader(Charsets.UTF_8).use { load(it) } }
}
// Absent and blank are the same thing to a signing config, so normalise both
// to null and let the checks decide whether that is fatal.
fun signingProperty(buildType: String, key: String): String? =
    signingProperties.getValue(buildType).getProperty(key)?.takeIf { it.isNotBlank() }

// Paths are written relative to the android/ root so the file stays portable.
fun signingStoreFile(buildType: String): File? =
    signingProperty(buildType, "storeFile")?.let { rootProject.file(it) }

@Suppress("UNCHECKED_CAST")
val beWorkspace = rootProject.extra["beWorkspace"] as Map<String, Any?>
@Suppress("UNCHECKED_CAST")
val workspacePaths = beWorkspace["paths"] as Map<String, Any?>
@Suppress("UNCHECKED_CAST")
val workspaceTools = beWorkspace["tools"] as Map<String, Any?>
@Suppress("UNCHECKED_CAST")
val resourceUpdate = beWorkspace["resource_update"] as Map<String, Any?>
@Suppress("UNCHECKED_CAST")
val sharedOutputs = resourceUpdate["outputs"] as Map<String, Any?>
val nativeStaging = File(workspacePaths["build"] as String, "next/android/native/app")
val generatedAssets = layout.buildDirectory.dir("generatedAssets")


android {
    namespace = "dev.betterendfield.next"
    compileSdk = 37
    ndkVersion = "27.2.12479018"

    buildFeatures {
        buildConfig = true
        aidl = true
        // The settings app and BEM manager use Compose. This experimental branch
        // also composes the Activity-scoped game panel; device acceptance is pending.
        compose = true
    }

    defaultConfig {
        applicationId = "dev.betterendfield.next"
        // The libxposed API 102 service is the only framework entry point, and it
        // needs Android 10. The legacy API 82 build was dropped in 3.3.0.
        minSdk = 29
        targetSdk = 35
        versionCode = 40109
        versionName = "4.1.9"
        testInstrumentationRunner = "dev.betterendfield.next.BemInstallerTest"

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20")
                arguments += listOf("-DANDROID_STL=c++_static",
                    "-DDOBBY_ROOT=${workspaceTools["android_dobby"]}")
            }
        }
    }

    signingConfigs {
        // Overriding AGP's built-in `debug` config instead of adding a second
        // one: every variant -- and any future test-only build type -- that
        // names the debug config picks this keystore up without further wiring.
        getByName("debug") {
            storeType = "PKCS12"
            storeFile = signingStoreFile("debug")
            storePassword = signingProperty("debug", "storePassword")
            keyAlias = signingProperty("debug", "keyAlias")
            // PKCS12 cannot hold a key password distinct from the store
            // password, and keytool silently ignores -keypass for it.
            keyPassword = storePassword
        }
        // Named after the project rather than "release" to keep it visually
        // distinct from the AGP defaults it deliberately does not reuse.
        create("bemRelease") {
            storeType = "PKCS12"
            storeFile = signingStoreFile("release")
            storePassword = signingProperty("release", "storePassword")
            keyAlias = signingProperty("release", "keyAlias")
            keyPassword = storePassword
        }
    }

    buildTypes {
        debug {
            isJniDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // Compose's synthetic classes and kotlin.Metadata cost ~21 MB of
            // dex when they survive into the APK; R8 shrink+obfuscate brings the
            // release build back down (8.60 MB here). The optimiser stage also
            // stays off: its class merging folds game-process classes into
            // holders whose <clinit> instantiates Compose, which crashes the
            // hooked game process - see proguard-rules.pro. The module's names
            // that are read from outside the type system -- the libxposed entry
            // point in META-INF/xposed/java_init.list, and the JNI symbols of
            // libbetterendfieldnext_installer.so -- are pinned in proguard-rules.pro
            // and asserted by verifyReleaseEntryPoints.
            isMinifyEnabled = true
            // The release identity, not AGP's debug one: release APKs are what
            // users install as updates, so they have to keep one stable
            // signature across builds. See the signing note at the top.
            signingConfig = signingConfigs.getByName("bemRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    androidResources {
        // The sustained-dash bone-pose banks are copied out to the game's files
        // directory once and then skipped on a size match. Storing them
        // uncompressed is what makes the asset's reported length the real one.
        noCompress += "bin"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

// A signing identity that is described but unusable has to fail where it is
// used, and say what to do about it. Left to AGP it surfaces as "Keystore file
// not set for signing config bemRelease" from inside the packaging step, which
// never mentions this project's conventions. One task per build type, wired
// into pre*Build below, keeps that failure cheap and legible.
fun signingCheckTask(name: String, buildType: String) = tasks.register(name) {
    val descriptionFile = signingDescriptions.getValue(buildType)
    group = "verification"
    description = "Asserts the $buildType signing identity is complete and present."
    doLast {
        val storeFile = signingStoreFile(buildType)
        val missing = buildList {
            if (signingProperty(buildType, "storeFile") == null) add("storeFile")
            if (storeFile != null && !storeFile.isFile) add("the keystore file $storeFile")
            if (signingProperty(buildType, "storePassword") == null) add("storePassword")
            if (signingProperty(buildType, "keyAlias") == null) add("keyAlias")
        }
        check(missing.isEmpty()) {
            val remedy = if (buildType == "release") {
                "No copy of it is tracked: create $descriptionFile together with the " +
                    "keystore it names, or let CI write both from the " +
                    "ANDROID_RELEASE_KEYSTORE_* secrets -- see android/README.md, " +
                    "section \"Signing\"."
            } else {
                "$descriptionFile and the keystore it names are tracked files: " +
                    "restore them, or regenerate both as documented in " +
                    "android/README.md, section \"Signing\"."
            }
            "the $buildType signing identity is unusable, missing " +
                "${missing.joinToString(", ")}. $remedy"
        }
    }
}

val checkDebugSigning = signingCheckTask("checkDebugSigning", "debug")
val checkReleaseSigning = signingCheckTask("checkReleaseSigning", "release")

tasks.matching { it.name == "preDebugBuild" }.configureEach {
    dependsOn(checkDebugSigning)
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(checkReleaseSigning)
}

val prepareAndroidResourceAssets by tasks.registering(GeneratedAssetCopy::class) {
    outputDirectory.set(layout.buildDirectory.dir("generated/androidResourceAssets"))
    from(rootProject.file("resources/voice-catalog-index.json"))
    from(rootProject.file("resources/character-names.json"))
    from(rootProject.file("resources/character-presets.json"))
    // Same layout the desktop module reads from beside its DLL, so one set of
    // bone-pose banks serves both platforms.
    from(rootProject.file("../native/modules/actions/assets")) {
        include("pose_*.bin")
        into("actions")
    }
    into(outputDirectory)
}

// Every APK carries the complete verified catalog. Missing input is a build
// failure, never an APK that silently depends on an earlier external directory.
val androidCoreSource = JsonSlurper().parse(rootProject.file("resources/resource-source.json")) as Map<*, *>
val androidCoreGame = (androidCoreSource["provenance"] as Map<*, *>)["gameVersion"] as String
val resourcePackRegistry = JsonSlurper().parse(rootProject.file("../config/resource-packs.json")) as Map<*, *>
val androidCoreRevisions = (((resourcePackRegistry["revisions"] as Map<*, *>)["core"] as Map<*, *>)["android"] as Map<*, *>)
val bundledAndroidCoreArchive = File(workspacePaths["build"] as String,
    "resource-pack-archives/BE-Core-Android-$androidCoreGame.${androidCoreRevisions[androidCoreGame] ?: 1}.zip")

val prepareBundledAndroidCoreAssets by tasks.registering(GeneratedAssetSync::class) {
    outputDirectory.set(generatedAssets)
    dependsOn("stageAndroidCoreResources")
    // The baseline pack is data, extracted into the same external resource store.
    // Optional preview geometry/textures are still downloaded separately.
    from(bundledAndroidCoreArchive) { into("bundled-resources"); rename { "core.zip" } }
    from(rootProject.file("../resources/bem-preview")) { exclude("*.zip"); into("bem-preview") }
    into(outputDirectory)
}

// Build the external companion and the identical offline baseline for the APK.
val stageAndroidCoreResources by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir.parentFile)
    commandLine(workspaceTools["python"] as String, "scripts/BuildResourcePacks.py",
        "--platform", "android", "--type", "core",
        "--output", File(workspacePaths["build"] as String, "resource-packs").absolutePath,
        "--archives", File(workspacePaths["build"] as String, "resource-pack-archives").absolutePath)
}


val prepareHeadwearAssets by tasks.registering(GeneratedAssetExec::class) {
    outputDirectory.set(layout.buildDirectory.dir("generated/headwearAssets"))
    val catalog = providers.gradleProperty("headwearCatalogDir")
    val script = rootProject.file("../tools/Camera/package_android_headwear_assets.py")
    inputs.file(script)
    inputs.file(rootProject.file("../tools/Camera/build_android_headwear_fixture.py"))
    inputs.file(rootProject.file("../tools/Camera/android_headwear_asset_profiles.json"))
    catalog.orNull?.let { inputs.dir(file(it)) }
    doFirst {
        check(catalog.isPresent) {
            "Generate the v3 headwear catalog, then pass -PheadwearCatalogDir=<catalog directory>."
        }
        commandLine(providers.gradleProperty("headwearPython").orElse("python").get(),
            script.absolutePath, "--catalog", file(catalog.get()).absolutePath,
            "--output", outputDirectory.get().asFile.absolutePath)
    }
}

val verifyDesktopModelHookParity by tasks.registering {
    val modelSource = rootProject.file("../native/modules/model/module.cpp")
    inputs.file(modelSource)
    doLast {
        val source = modelSource.readText()
        val expected = linkedMapOf(
            "login_bind" to "LoginBindHook",
            "init_main_hash" to "InitMainHashHook",
            "init_initial_hash" to "InitInitialHashHook",
            "anim_tick" to "AnimationTickHook",
            "anim_release" to "AnimationReleaseHook",
            "anim_change_state" to "AnimationChangeStateHook",
            "anim_reset_a1" to "AnimationResetA1Hook",
            "anim_play_special" to "AnimationSpecialHook",
            "anim_play_transition" to "AnimationTransitionHook",
            "clone_with_parent" to "CloneWithParentHook",
            "login_decorate_tick" to "LoginDecorateTickHook",
            "login_decorate_release" to "LoginDecorateReleaseHook",
            "login_enter_value_changed" to "LoginEnterGamePanelValueChangedHook",
            "login_material_animation_late_tick" to "LoginMaterialAnimationLateTickHook",
            "canvas_update_perform" to "CanvasUpdatePerformHook"
        )
        val missing = expected.filter { (field, detour) ->
            !Regex(
                "Hook\\s*\\(\\s*g_methods\\.${Regex.escape(field)}\\s*," +
                    "\\s*reinterpret_cast<void\\*>\\s*\\(&${Regex.escape(detour)}\\)",
                setOf(RegexOption.DOT_MATCHES_ALL)
            ).containsMatchIn(source)
        }
        check(missing.isEmpty()) {
            "Android model Hook parity failed; missing desktop entries: " +
                missing.entries.joinToString { "${it.key}->${it.value}" }
        }
        logger.lifecycle(
            "Verified Android model parity against ${expected.size} desktop Hook entries")
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareAndroidResourceAssets)
    dependsOn(prepareBundledAndroidCoreAssets)
    dependsOn(prepareHeadwearAssets)
    dependsOn(verifyDesktopModelHookParity)
}

// The module's own Java sources, scanned by the verification task below for
// `native` declarations. Declared here because the task's receiver is a Task,
// where `fileTree` is not resolvable.
val moduleJavaSources = fileTree("src/main/java") { include("**/*.java") }

// R8 can delete a class that is only reachable through a name held in a
// resource or in a native symbol, and nothing in the build would notice: the
// APK still installs, still launches, and simply never hooks anything. Three
// kinds of name are resolved from outside the Java type system:
//
//   * META-INF/xposed/java_init.list -> the libxposed entry class, instantiated
//     reflectively by the framework;
//   * the Java_dev_betterendfieldnext_android_* exports of the .so files this APK
//     ships, whose export name *is* the class and method name, plus the one
//     method JNI looks up with GetStaticMethodID;
//   * the merged manifest's components, which AGP turns into keep rules.
//
// proguard-rules.pro answers all three, and this task refuses to trust it: it
// reads the packaged APK and R8's own mapping.txt and fails the build when a
// name that the framework or the native code resolves as a string is gone or
// was renamed. mapping.txt is the authority here, because a class name can
// survive in the dex string table (as an unrelated literal, or in a synthesized
// member's signature) while the class itself was renamed.
val verifyReleaseEntryPoints by tasks.registering {
    description = "Asserts the R8-processed release APK still carries the framework and JNI entry points."
    val apkDirectory = layout.buildDirectory.dir("outputs/apk/release")
    val mappingOutput = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")
    inputs.dir(apkDirectory)
    inputs.files(moduleJavaSources)
    inputs.file(mappingOutput).withPropertyName("r8Mapping").optional()
    doLast {
        val apks: List<File> = apkDirectory.get().asFile
            .listFiles { file -> file.extension == "apk" && !file.name.contains("unsigned") }
            ?.sortedBy { it.name }
            .orEmpty()
        check(apks.isNotEmpty()) { "no release APK to verify in ${apkDirectory.get().asFile}" }

        // Classes named in META-INF/xposed/java_init.list.
        val frameworkEntryClasses = listOf("dev.betterendfield.next.XposedEntry")
        // Manifest-declared components: AGP generates these keep rules, this
        // asserts they were actually generated for the merged manifest.
        val manifestComponents = listOf(
            "Ldev/betterendfield/next/ModuleApplication;",
            "Ldev/betterendfield/next/MainActivity;",
            "Ldev/betterendfield/next/BemInstallActivity;",
            // The overlay's settings channel. The framework instantiates this
            // one by name from the game process' call(), so a renamed provider
            // is not a shrinking win but a ClassNotFoundException at the point
            // the panel tries to read the model list.
            "Ldev/betterendfield/next/OverlaySettingsProvider;",
            "Lio/github/libxposed/service/XposedProvider;"
        )
        // Methods the native code reaches by name without being an export. Both
        // owners declare natives, so the default member-name rule already keeps
        // the *class*; what it does not keep is the name of a plain method, and
        // these four are plain: the MMD player behind them is Java
        // (MmdAudio.java) and the native side only ever holds their names.
        //   * conversionProgress - GetStaticMethodID(owner, "conversionProgress",
        //     "(Ljava/lang/String;IIIIF)V") in src/main/cpp/installer/install_jni.cpp.
        //     A renamed method returns null and the conversion aborts with no
        //     progress at all.
        //   * audioOpen/audioControl/audioStatus/audioError - resolved by
        //     src/main/cpp/core/local_music_android.cpp. Renamed, the interface
        //     is refused and MMD works play silently without their music track.
        val jniCallbacks = mapOf(
            "dev.betterendfield.next.BemInstaller" to listOf("conversionProgress"),
            "dev.betterendfield.next.NativeCommandBridge" to listOf(
                "audioOpen", "audioControl", "audioStatus", "audioError",
                "driverAudioStart", "driverAudioStop", "driverAudioStopAll", "driverAudioStatus")
        )
        // Exports with no Java declaration behind them any more. The panel-to-
        // runtime channel moved from JNI to plain files (see the
        // NativeCommandBridge header comment), and the native bridge still
        // carries the old entry points, and Java never resolves those exports.
        val obsoleteJniExports = setOf(
            "dev.betterendfield.next.NativeCommandBridge.key",
            "dev.betterendfield.next.NativeCommandBridge.look",
            "dev.betterendfield.next.NativeCommandBridge.releaseKeys",
            "dev.betterendfield.next.NativeCommandBridge.status",
            "dev.betterendfield.next.NativeCommandBridge.submit",
            "dev.betterendfield.next.NativeCommandBridge.probeIl2Cpp"
        )

        // Every `native` method in the module's own Java sources, qualified with
        // the class declaring it. A JNI export name is the mangled form of
        // exactly that pair, so this is what the shipped libraries may resolve.
        // Comments are stripped, and `native` must follow a run of member
        // modifiers at the start of a line: the word also appears in prose and,
        // unstripped, inside literals such as "native load attempt " -- a looser
        // scan reads that literal plus the following ATTEMPTS.get() as a native
        // method named get(). Unmodified `native void f();` declarations would
        // not match, which the pinned set below turns into a visible drift.
        val declaredNatives: Set<String> = moduleJavaSources.files
            .filter { it.isFile }
            .flatMap { source: File ->
                val text = source.readText()
                    .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
                    .replace(Regex("""//[^\n]*"""), " ")
                val packageName = Regex("""^\s*package\s+([\w.]+)\s*;""", RegexOption.MULTILINE)
                    .find(text)?.groupValues?.get(1) ?: return@flatMap emptyList()
                val className = Regex("""\b(?:class|interface)\s+(\w+)""")
                    .find(text)?.groupValues?.get(1) ?: return@flatMap emptyList()
                Regex(
                    """^[ \t]*(?:(?:public|protected|private|static|final|synchronized|abstract|strictfp)[ \t]+)*native\b[^;(){}]*?\b(\w+)\s*\(""",
                    RegexOption.MULTILINE
                ).findAll(text)
                    .map { match -> "$packageName.$className.${match.groupValues[1]}" }
                    .toList()
            }
            .toSet()
        // A regex that stops matching would silently disable the checks below,
        // so pin the set that is known to exist today.
        val expectedNatives = setOf(
            "dev.betterendfield.next.BemInstaller.inspectNativeWithOptions",
            "dev.betterendfield.next.BemInstaller.convertNative",
            "dev.betterendfield.next.BemInstaller.cancelNative"
        )
        check(declaredNatives.containsAll(expectedNatives)) {
            "native-method scan drifted: found ${declaredNatives.sorted()}, " +
                "expected at least ${expectedNatives.sorted()}"
        }
        check(declaredNatives.intersect(obsoleteJniExports).isEmpty()) {
            "the obsolete-JNI list and the Java sources disagree about " +
                declaredNatives.intersect(obsoleteJniExports).sorted()
        }

        val mappingFile = mappingOutput.get().asFile
        val mappingLines: List<String> = if (mappingFile.isFile) mappingFile.readLines() else emptyList()
        if (mappingLines.isEmpty()) {
            logger.warn(
                "no R8 mapping at $mappingFile: the renamed-class checks are " +
                    "downgraded to the dex string table for this run")
        }
        // mapping.txt prints "<original> -> <obfuscated>:" for a class and
        // indents its members, so an identity mapping means "R8 kept this name".
        fun mappingHeader(owner: String): String? =
            mappingLines.firstOrNull { it.startsWith("$owner -> ") }
        fun keptNames(owner: String, references: MutableList<String>) {
            if (mappingLines.isEmpty()) return
            val header = mappingHeader(owner)
            if (header == null) {
                references.add("$owner is absent from mapping.txt")
            } else if (header != "$owner -> $owner:") {
                references.add("$owner was renamed (${header.substringAfter("-> ")})")
            }
        }

        // The classes the hooked game process actually executes. They must stay
        // their own classes rather than being folded into a shared holder with
        // the library - see the merged-<clinit> evidence in proguard-rules.pro.
        val gameProcessClasses = listOf(
            "dev.betterendfield.next.XposedEntry",         // libxposed entry
            "dev.betterendfield.next.RuntimeBootstrap",    // module attach
            "dev.betterendfield.next.HeadwearAssets",      // bundled resource adapter
            "dev.betterendfield.next.HeadwearAssetStore",  // verified catalog owner
            "dev.betterendfield.next.RuntimeLog",          // journal
            "dev.betterendfield.next.GameOverlay",         // the in-game panel
            "dev.betterendfield.next.OverlaySurface",      // Compose view owner
            "dev.betterendfield.next.OverlayViewOwners",   // owner tag bridge
            "dev.betterendfield.next.OverlayFeatures",
            "dev.betterendfield.next.NativeCommandBridge", // file relay
            "dev.betterendfield.next.ModuleConfigurations",
            "dev.betterendfield.next.ModuleSettings",
            "dev.betterendfield.next.Hotkeys",
            "dev.betterendfield.next.FrameworkSettings",
        )

        apks.forEach { apk: File ->
            val archive = ZipFile(apk)
            try {
                // DEX strings are NUL-terminated MUTF-8, so a name survives as
                // "<name>\u0000" in the string table. Decoding the dex as
                // ISO-8859-1 keeps every byte addressable as one character.
                val dex = StringBuilder()
                val nativeLibraries = sortedMapOf<String, String>()
                var entryList = ""
                val entries = archive.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    when {
                        entry.name.startsWith("classes") && entry.name.endsWith(".dex") ->
                            dex.append(archive.getInputStream(entry).readBytes()
                                .toString(Charsets.ISO_8859_1))
                        entry.name.endsWith(".so") ->
                            nativeLibraries[entry.name.substringAfterLast('/')] =
                                archive.getInputStream(entry).readBytes()
                                    .toString(Charsets.ISO_8859_1)
                        entry.name == "META-INF/xposed/java_init.list" ->
                            entryList = archive.getInputStream(entry).readBytes()
                                .toString(Charsets.UTF_8)
                    }
                }

                // 1. Names that must appear in the dex at all.
                val missing = (frameworkEntryClasses.map { "L$it;".replace('.', '/') }
                    + manifestComponents)
                    .filterNot { dex.contains("$it\u0000") }
                check(missing.isEmpty()) {
                    "${apk.name} lost names that are resolved at runtime: " +
                        missing.joinToString { it.trim('L', ';') }
                }
                check(entryList.contains("dev.betterendfield.next.XposedEntry")) {
                    "${apk.name} does not declare the libxposed entry point"
                }

                // 2. Every JNI export shipped in every native library must be
                //    backed by a Java declaration, and that declaration must
                //    still carry its original name.
                val exportPattern = Regex(
                    """Java_dev_betterendfield_next_([A-Za-z0-9]+)_([A-Za-z0-9]+)\x00"""
                )
                val exports = nativeLibraries.values.flatMap { bytes ->
                    exportPattern.findAll(bytes).map { match ->
                        val (owner, method) = match.destructured
                        "dev.betterendfield.next.$owner.$method"
                    }.toList()
                }.toSet()
                val unaccounted = exports - declaredNatives - obsoleteJniExports
                check(unaccounted.isEmpty()) {
                    "${apk.name} ships JNI exports without Java declarations: ${unaccounted.sorted()}"
                }
                val missingExports = declaredNatives - exports
                check(missingExports.isEmpty()) {
                    "${apk.name} is missing JNI exports for: ${missingExports.sorted()}"
                }

                // 3. Class names. mapping.txt is the authority: R8 prints an
                //    identity line for every class it retains.
                val renames = mutableListOf<String>()
                (frameworkEntryClasses
                    + declaredNatives.map { it.substringBeforeLast('.') }
                    + jniCallbacks.keys)
                    .distinct()
                    .forEach { keptNames(it, renames) }
                check(renames.isEmpty()) {
                    "${apk.name} lets R8 rewrite a class name that is resolved " +
                        "from outside the JVM: ${renames.sorted()}"
                }

                // 4. Method names. mapping.txt is deliberately NOT the witness
                //    here: R8 can omit unchanged native method names from mapping.txt.
                //    The dex string table is used: if
                //    a keep rule stops applying, R8 renames declaration and call
                //    sites together and the original name leaves the dex.
                val lostMethods = (declaredNatives.map { it.substringAfterLast('.') }
                    + jniCallbacks.values.flatten())
                    .distinct()
                    .filterNot { dex.contains("$it\u0000") }
                check(lostMethods.isEmpty()) {
                    "${apk.name} lost method names that the native code resolves " +
                        "by string: ${lostMethods.sorted()}"
                }

                // 5. Nothing on the game path was folded into a shared holder.
                //    R8's optimiser merges classes that are never instantiated
                //    into one holder, and the holders pool <clinit> bodies with
                //    whatever else was folded in. That is how NativeCommandBridge
                //    shipped inside a class whose initialiser instantiates
                //    androidx.compose.ui.BiasAbsoluteAlignment: the panel runs in
                //    the hooked game process, which must never load Compose.
                //
                //    mapping.txt distinguishes the two ways a class can vanish.
                //    Folded: its members are mapped but it has no class header,
                //    because the members now live in someone else's class.
                //    Shrunk: it has no trace at all, because javac inlined its
                //    constants and nothing referenced it (Hotkeys is the example).
                //    Only the first is a failure - a shrunk class has no code on
                //    the game path. Measured with the optimiser on: Hotkeys had 0
                //    mapping lines, while NativeCommandBridge had 48 and
                //    ModuleSettings 304.
                if (mappingLines.isNotEmpty()) {
                    val mappedOwners: Set<String> = mappingLines
                        .filter { it.isNotEmpty() && !it[0].isWhitespace() && !it.startsWith("#") }
                        .mapNotNull { line ->
                            val arrow = line.indexOf(" -> ")
                            if (arrow > 0 && line.endsWith(":")) line.substring(0, arrow) else null
                        }
                        .toSet()
                    val folded = gameProcessClasses.filter { owner ->
                        owner !in mappedOwners && mappingLines.any { it.contains("$owner.") }
                    }
                    check(folded.isEmpty()) {
                        "${apk.name} folded classes that run inside the hooked game " +
                            "process into shared holders: ${folded.sorted()}. Those " +
                            "holders pool <clinit> bodies with the rest of the " +
                            "library, which is how Compose initialisation reaches " +
                            "the game process. The optimiser has to stay off - see " +
                            "proguard-rules.pro."
                    }
                }

                logger.lifecycle(
                    "Verified ${apk.name}: framework entry, ${manifestComponents.size} " +
                        "manifest components, ${declaredNatives.size} JNI exports across " +
                        "${nativeLibraries.size} libraries, class names unrenamed in " +
                        "mapping.txt, ${jniCallbacks.values.sumOf { it.size }} JNI " +
                        "callback names intact and ${gameProcessClasses.size} " +
                        "game-process classes unmerged")
            } finally {
                archive.close()
            }
        }
    }
}

tasks.matching { it.name == "packageRelease" }.configureEach {
    finalizedBy(verifyReleaseEntryPoints)
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.10")
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    // The existing Compose dependencies also serve the experimental game panel.
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.savedstate:savedstate-compose:1.5.0")
    implementation("androidx.core:core-ktx:1.19.1")
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.apply {
            addGeneratedSourceDirectory(prepareAndroidResourceAssets) { it.outputDirectory }
            addGeneratedSourceDirectory(prepareBundledAndroidCoreAssets) { it.outputDirectory }
            addGeneratedSourceDirectory(prepareHeadwearAssets) { it.outputDirectory }
        }
    }
}
