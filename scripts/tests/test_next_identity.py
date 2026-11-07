"""Next installation/ABI identity and archive boundary regressions."""
from pathlib import Path
import hashlib,json,re,unittest,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]

class NextIdentityTests(unittest.TestCase):
    def test_archive_preserves_source_and_is_not_linked_to_production(self):
        archive=ROOT/'legacy/retired-before-next'
        manifest=json.loads((archive/'manifest.json').read_text(encoding='utf-8'))
        for item in manifest['files']:
            with self.subTest(source=item['source']):
                content=(archive/item['archive']).read_bytes()
                self.assertEqual(hashlib.sha256(content).hexdigest(),item['sha256'])
        for relative in ['native/CMakeLists.txt','android/app/src/main/cpp/CMakeLists.txt','ui/BetterEndfieldNext.UI/BetterEndfieldNext.UI.csproj']:
            self.assertNotIn('retired-before-next',(ROOT/relative).read_text(encoding='utf-8'))
        # 本分支保留 Android 第一人称；上游归档仅约束快照字节与链接边界。
        self.assertTrue((ROOT/'native/modules/camera/first_person_runtime.inc').is_file())
        camera=(ROOT/'native/modules/camera/module.cpp').read_text(encoding='utf-8')
        self.assertIn('#include "first_person_runtime.inc"',camera)

    def test_android_package_and_jni_bindings_match(self):
        gradle=(ROOT/'android/app/build.gradle.kts').read_text(encoding='utf-8')
        package=re.search(r'applicationId = "([^"]+)"',gradle)[1]
        self.assertNotEqual(package,'dev.betterendfield.android')
        source=ROOT/'android/app/src/main/java'/package.replace('.','/')
        self.assertTrue((source/'XposedEntry.java').exists())
        init=(ROOT/'android/app/src/main/resources/META-INF/xposed/java_init.list').read_text().strip()
        self.assertEqual(init,package+'.XposedEntry')
        bridge=(ROOT/'android/app/src/main/cpp/native_bridge.cpp').read_text(encoding='utf-8')
        self.assertIn('"'+package+'.NativeCommandBridge"',bridge)
        installer=(ROOT/'android/app/src/main/cpp/installer/install_jni.cpp').read_text(encoding='utf-8')
        exports=re.findall(r'Java_(\w+)_BemInstaller_\w+',installer)
        self.assertTrue(exports)
        self.assertEqual(set(exports),{package.replace('.','_')})
        manifest=ET.parse(ROOT/'android/app/src/main/AndroidManifest.xml')
        ns='{http://schemas.android.com/apk/res/android}'
        providers=manifest.findall('application/provider')
        overlay=[node for node in providers if node.get(ns+'name')=='.OverlaySettingsProvider']
        self.assertEqual(len(overlay),1)
        self.assertEqual(overlay[0].get(ns+'authorities'),package+'.overlay.settings')
        authorities=[node.get(ns+'authorities') for node in providers]
        self.assertEqual(len(authorities),len(set(authorities)))

    def test_windows_module_descriptors_match_new_exports(self):
        cmake=(ROOT/'native/CMakeLists.txt').read_text(encoding='utf-8')
        for descriptor in (ROOT/'native/modules').glob('*/*.module.ini'):
            fields=dict(line.split('=',1) for line in descriptor.read_text(encoding='utf-8').splitlines() if '=' in line)
            with self.subTest(module=fields['id']):
                self.assertTrue(fields['id'].startswith('betterendfieldnext.'))
                self.assertEqual(fields['api'],'BetterEndfieldNext_GetModuleApiV1')
                self.assertTrue(fields['library'].startswith('BetterEndfieldNext.'))
                self.assertIn(fields['library'].removesuffix('.dll'),cmake)

    def test_android_third_party_runtime_and_desktop_workshop_are_wired(self):
        source=ROOT/'android/app/src/main/java/dev/betterendfield/next'
        self.assertTrue((source/'MainActivity.kt').is_file())
        self.assertFalse((source/'MainActivity.java').exists())
        ui=ET.parse(ROOT/'ui/BetterEndfieldNext.UI/MainWindow.xaml')
        tag='{http://schemas.microsoft.com/winfx/2006/xaml}Name'
        self.assertTrue(any(node.get(tag)=='WorkshopNavigationItem' for node in ui.iter()))
        host=(ROOT/'native/shared/host/host_runtime.cpp').read_text(encoding='utf-8')
        self.assertIn('std::make_unique<HookBroker>',host)
        self.assertIn('std::make_unique<ModuleManager>',host)
        self.assertTrue((ROOT/'native/shared/third_party_modules/third_party_host.cpp').is_file())
        self.assertTrue((ROOT/'native/shared/hooks/hook_chain.h').is_file())
        bridge=(ROOT/'android/app/src/main/cpp/native_bridge.cpp').read_text(encoding='utf-8')
        self.assertIn('ThirdPartyHost',bridge)
        self.assertIn('BETTER_ENDFIELD_NEXT_THIRD_PARTY_INDEX',bridge)
        cmake=(ROOT/'android/app/src/main/cpp/CMakeLists.txt').read_text(encoding='utf-8')
        self.assertIn('third_party_modules/third_party_host.cpp',cmake)
        configs=(source/'ModuleConfigurations.java').read_text(encoding='utf-8')
        self.assertIn('ThirdPartyModuleStore.selectedRuntimeIndex(settings)',configs)
        manifest=ET.parse(ROOT/'android/app/src/main/AndroidManifest.xml')
        ns='{http://schemas.android.com/apk/res/android}'
        activities={node.get(ns+'name') for node in manifest.iter('activity')}
        self.assertIn('.ThirdPartyModulesActivity',activities)
        self.assertIn('.ThirdPartyModuleActivity',activities)

    def test_android_signing_uses_explicit_fixed_identity_descriptions(self):
        gradle=(ROOT/'android/app/build.gradle.kts').read_text(encoding='utf-8')
        for build_type in ('debug','release'):
            self.assertIn(f'"{build_type}" to rootProject.file("keystore/{build_type}.properties")',gradle)
        self.assertIn('checkDebugSigning',gradle)
        self.assertIn('checkReleaseSigning',gradle)
        self.assertIn('signingStoreFile("debug")',gradle)
        self.assertIn('signingStoreFile("release")',gradle)

if __name__=='__main__':unittest.main()
