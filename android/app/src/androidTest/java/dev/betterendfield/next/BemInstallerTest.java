package dev.betterendfield.next;

import android.app.Instrumentation;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.file.Files;

public final class BemInstallerTest extends Instrumentation {
    public void testOverlayResourcesOnForeignHost() throws Exception {OverlayResourceContextDeviceTest.run(getTargetContext());}
    public void testIndexRevisionRejectsStalePageAndOverlay() throws Exception {BemIndexRevisionDeviceTest.stale(getTargetContext());}
    public void testIndexDraftRefreshRetainsAndBlocks() throws Exception {BemIndexRevisionDeviceTest.refresh(getTargetContext());}
    public void testIndexCleanRefreshAndImportConflict() throws Exception {BemIndexRevisionDeviceTest.cleanAndImport(getTargetContext());}
    public void testIndexNormalSaveAndDisableRetainsDraft() throws Exception {BemIndexRevisionDeviceTest.normalAndDisable(getTargetContext());}
    public void testIndexRemovedGenerationRetainsDraft() throws Exception {BemIndexRevisionDeviceTest.removed(getTargetContext());}
    public void testIndexRevertedDraftLoadsLatest() throws Exception {BemIndexRevisionDeviceTest.reverted(getTargetContext());}
    public void testExternalBemImportRequest() {
        Uri first=Uri.parse("content://provider.example/first.bem");
        Uri second=Uri.parse("content://provider.example/second.bem");
        assertTrue(first.equals(BemImportRequest.fromIntent(new Intent(Intent.ACTION_VIEW,first))));
        Intent shared=new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM,first);
        shared.setClipData(ClipData.newRawUri("file",first));
        assertTrue(first.equals(BemImportRequest.fromIntent(shared)));
        shared.setClipData(ClipData.newRawUri("file",second));
        try {BemImportRequest.fromIntent(shared);fail("Accepted conflicting document URIs");}
        catch(IllegalArgumentException expected) {}
        Intent multiple=new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM,first);
        ClipData clip=ClipData.newRawUri("file",first);
        clip.addItem(new ClipData.Item(second));multiple.setClipData(clip);
        try {BemImportRequest.fromIntent(multiple);fail("Accepted multiple shared files");}
        catch(IllegalArgumentException expected) {}
        try {BemImportRequest.requireContentUri(Uri.parse("file:///sdcard/first.bem"));fail("Accepted filesystem path");}
        catch(IllegalArgumentException expected) {}
    }
    public void testMultiResourceIndexConflicts() throws Exception {
        JSONObject body=new JSONObject().put("generation","body-old").put("package_id","body.pack")
            .put("character_id","chr_synthetic").put("target_kind","character").put("target_id","chr_synthetic")
            .put("bem_minor",4).put("resource_keys",new JSONArray().put("android-arm64:body"))
            .put("enabled",true);
        JSONObject weapon=new JSONObject().put("generation","weapon-new").put("package_id","weapon.pack")
            .put("character_id","chr_synthetic").put("target_kind","character").put("target_id","chr_synthetic")
            .put("bem_minor",4).put("resource_keys",new JSONArray().put("android-arm64:weapon"))
            .put("enabled",true);
        JSONArray installed=BemInstaller.mergePublishedIndex(new JSONArray().put(body),weapon);
        assertEquals(2,installed.length());
        assertTrue(installed.getJSONObject(0).getBoolean("enabled"));
        JSONObject replacement=new JSONObject(weapon.toString()).put("generation","body-new")
            .put("resource_keys",new JSONArray().put("android-arm64:body"));
        JSONArray conflicting=BemInstaller.mergePublishedIndex(new JSONArray().put(body),replacement);
        assertEquals(2,conflicting.length());
        assertFalse(conflicting.getJSONObject(0).getBoolean("enabled"));
        JSONArray overlay=OverlayWritePolicy.apply(installed,new JSONArray().put(
            new JSONObject().put("generation","weapon-new").put("enabled",true)));
        assertTrue(overlay.getJSONObject(0).getBoolean("enabled"));
        JSONArray overlapping=new JSONArray().put(new JSONObject(body.toString())).put(new JSONObject(replacement.toString()));
        JSONArray switched=OverlayWritePolicy.apply(overlapping,new JSONArray().put(
            new JSONObject().put("generation","body-new").put("enabled",true)));
        assertFalse(switched.getJSONObject(0).getBoolean("enabled"));
        assertTrue(switched.getJSONObject(1).getBoolean("enabled"));
        JSONObject legacy=new JSONObject().put("generation","legacy-old").put("package_id","legacy.pack")
            .put("character_id","chr_synthetic").put("bem_minor",0).put("enabled",true);
        JSONArray mixed=BemInstaller.mergePublishedIndex(new JSONArray().put(legacy),weapon);
        assertFalse(mixed.getJSONObject(0).getBoolean("enabled"));
        JSONObject otherTarget=new JSONObject(legacy.toString()).put("character_id","chr_other");
        JSONArray independent=BemInstaller.mergePublishedIndex(new JSONArray().put(otherTarget),weapon);
        assertTrue(independent.getJSONObject(0).getBoolean("enabled"));
        JSONObject samePackage=new JSONObject(weapon.toString()).put("generation","weapon-updated")
            .put("resource_keys",new JSONArray().put("android-arm64:body"));
        JSONArray updated=BemInstaller.mergePublishedIndex(new JSONArray().put(weapon),samePackage);
        assertEquals(1,updated.length());
        assertTrue("weapon-updated".equals(updated.getJSONObject(0).getString("generation")));
        JSONObject windows=new JSONObject(weapon.toString()).put("resource_keys",new JSONArray().put("windows-x64:weapon"));
        try {BemInstaller.mergePublishedIndex(new JSONArray(),windows);fail("Accepted Windows-only BEM 1.4");}
        catch(IOException expected) {}
    }
    public void testZipSequentialIndexPolicy() throws Exception {
        JSONObject old=new JSONObject().put("generation","old").put("package_id","old.pack")
            .put("target_kind","character").put("target_id","chr_zip")
            .put("bem_minor",4).put("resource_keys",new JSONArray().put("android-arm64:body"))
            .put("enabled",true);
        JSONObject first=new JSONObject(old.toString()).put("generation","first").put("package_id","first.pack");
        JSONObject disjoint=new JSONObject(old.toString()).put("generation","weapon").put("package_id","weapon.pack")
            .put("resource_keys",new JSONArray().put("android-arm64:weapon"));
        JSONObject last=new JSONObject(old.toString()).put("generation","last").put("package_id","last.pack");
        JSONArray afterFirst=BemInstaller.mergePublishedIndex(new JSONArray().put(old),first);
        assertFalse(afterFirst.getJSONObject(0).getBoolean("enabled"));
        assertTrue(afterFirst.getJSONObject(1).getBoolean("enabled"));
        JSONObject invalid=new JSONObject(old.toString()).put("generation","invalid")
            .put("resource_keys",new JSONArray().put("windows-x64:body"));
        try {BemInstaller.mergePublishedIndex(afterFirst,invalid);fail("Accepted invalid ZIP item");}
        catch(IOException expected) {}
        assertTrue(afterFirst.getJSONObject(1).getBoolean("enabled"));
        JSONArray afterDisjoint=BemInstaller.mergePublishedIndex(afterFirst,disjoint);
        assertTrue(afterDisjoint.getJSONObject(1).getBoolean("enabled"));
        assertTrue(afterDisjoint.getJSONObject(2).getBoolean("enabled"));
        JSONArray afterLast=BemInstaller.mergePublishedIndex(afterDisjoint,last);
        assertFalse(afterLast.getJSONObject(1).getBoolean("enabled"));
        assertTrue(afterLast.getJSONObject(2).getBoolean("enabled"));
        assertTrue(afterLast.getJSONObject(3).getBoolean("enabled"));
    }
    public void testBemIndexDurableMirror() throws Exception {
        File directory=new File(getTargetContext().getCacheDir(),"bem-index-test-"+System.nanoTime());
        assertTrue(directory.mkdir());
        android.content.Context isolated=new android.content.ContextWrapper(getTargetContext()) {
            @Override public File getFilesDir() {return directory;}
            @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode) {
                return super.getSharedPreferences("bem-index-test-"+directory.getName(),mode);
            }
        };
        try {
            JSONObject entry=new JSONObject().put("generation","11111111-1111-1111-1111-111111111111")
                .put("enabled",true).put("selected_appearance","default");
            FrameworkSettings.open(isolated).edit().putString(BemInstaller.INDEX,new JSONArray().put(entry).toString()).commit();
            BemInstaller.disableAll(isolated, BemInstaller.snapshot(isolated).revision);
            String preferences=FrameworkSettings.open(isolated).getString(BemInstaller.INDEX,"[]");
            String durable=new String(Files.readAllBytes(new File(directory,"bem-index.json").toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(preferences.equals(durable));
            assertFalse(new JSONArray(durable).getJSONObject(0).getBoolean("enabled"));
        } finally {
            FrameworkSettings.open(isolated).edit().clear().commit();
            new File(directory,"bem-index.json").delete();directory.delete();
        }
    }
    public void testModelPageRecoversFromBrokenIndexToEmptyList() {
        android.content.Context isolated=new android.content.ContextWrapper(getTargetContext()) {
            @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode) {
                return super.getSharedPreferences("bem-page-recovery-test-"+name,mode);
            }
        };
        android.content.SharedPreferences preferences=FrameworkSettings.open(isolated);
        try {
            preferences.edit().putString(BemInstaller.INDEX,"{broken").commit();
            BemInstallState state=new BemInstallState(isolated);
            state.refresh();
            assertTrue(state.getIndexBroken()!=null);
            preferences.edit().putString(BemInstaller.INDEX,"[]").commit();
            state.refresh();
            assertTrue(state.getIndexBroken()==null);
            assertTrue(state.getPackages().isEmpty());
        } finally {
            preferences.edit().clear().commit();
        }
    }
    public void testThirdPartyModulePackageBoundary() throws Exception {
        ThirdPartyModulePackageTest.run(this);
    }

    public void testThirdPartyRuntimeMaterialization() throws Exception {
        ThirdPartyRuntimeMaterializerTest.run(getTargetContext());
    }

    public void testCameraSettingsRoundTrip() {
        CameraSettingsTest.run(this);
    }
    public void testOverlayAppearance() {
        OverlaySettingsTest.run(this);
    }
    public void testOverlayPreviewLifecycleWithoutFramework() {
        OverlayPreviewTest.run(this);
    }
    public void testDisableAllModelsPreservesSelections() throws Exception {
        android.content.Context isolated = new android.content.ContextWrapper(getTargetContext()) {
            @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("bem-disable-all-test-" + name, mode);
            }
        };
        android.content.SharedPreferences preferences = FrameworkSettings.open(isolated);
        try {
            JSONObject first = new JSONObject().put("generation", "11111111-1111-1111-1111-111111111111")
                    .put("enabled", true).put("selected_appearance", "alternate");
            JSONObject second = new JSONObject().put("generation", "22222222-2222-2222-2222-222222222222")
                    .put("enabled", true).put("selected_options", "shirt:blue");
            preferences.edit().putString(BemInstaller.INDEX, new JSONArray().put(first).put(second).toString()).commit();
            BemInstaller.disableAll(isolated, BemInstaller.snapshot(isolated).revision);
            JSONArray stored = BemInstaller.index(isolated);
            assertFalse(stored.getJSONObject(0).getBoolean("enabled"));
            assertFalse(stored.getJSONObject(1).getBoolean("enabled"));
            assertTrue("alternate".equals(stored.getJSONObject(0).getString("selected_appearance")));
            assertTrue("shirt:blue".equals(stored.getJSONObject(1).getString("selected_options")));
        } finally {
            preferences.edit().clear().commit();
        }
    }
    public void testRuntimeJournalTransport() {
        android.content.Context context = getTargetContext();
        android.net.Uri uri = android.net.Uri.parse("content://dev.betterendfield.next.journal");
        android.os.Bundle payload = new android.os.Bundle();
        String oldLog = RuntimeJournalProvider.readLog(context);
        String marker = "journal-transport-test-" + System.nanoTime();
        try {
            payload.putString("log", marker);
            context.getContentResolver().call(uri, "publish", null, payload);
            assertTrue(RuntimeJournalProvider.readLog(context).contains(marker));
            char[] oversized = new char[80_001];
            java.util.Arrays.fill(oversized, 'x');
            payload.putString("log", new String(oversized));
            try {
                context.getContentResolver().call(uri, "publish", null, payload);
                fail("oversized journal accepted");
            } catch (IllegalArgumentException expected) { }
            assertTrue(RuntimeJournalProvider.readLog(context).contains(marker));
        } finally {
            payload.putString("log", oldLog);
            context.getContentResolver().call(uri, "publish", null, payload);
        }
    }
    private File directory;
    private String method;
    private String realInput;
    public void testOverlayModelCatalogState() throws Exception {
        OverlayModelCatalogStateTest.run();
    }
    @Override public void onCreate(android.os.Bundle arguments) {method=arguments.getString("method","");realInput=arguments.getString("input","installer-real-input.bem");start();}
    @Override public void onStart() {
        android.os.Bundle result=new android.os.Bundle();
        try {
            setUp();
            for(java.lang.reflect.Method test:getClass().getMethods()) if(test.getName().startsWith("test") && (method.isEmpty() || method.equals(test.getName()))) {
                test.invoke(this);android.util.Log.i("BetterEndfieldNext.InstallTest","PASS "+test.getName());
            }
            result.putString("stream","PASS BEM installation tests\n");finish(-1,result);
        } catch(Throwable error) {result.putString("stream","FAIL "+android.util.Log.getStackTraceString(error));finish(0,result);}
    }
    private Instrumentation getInstrumentation(){return this;}
    private static void assertTrue(boolean value){if(!value) throw new AssertionError("Expected true");}
    private static void assertFalse(boolean value){assertTrue(!value);}
    private static void assertEquals(int a,int b){if(a!=b) throw new AssertionError(a+" != "+b);}
    private static void fail(String text){throw new AssertionError(text);}
    private static void writeText(File file,String text) throws IOException {
        try(FileOutputStream out=new FileOutputStream(file)) {
            out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    private void setUp() throws Exception {
        directory=new File(getInstrumentation().getTargetContext().getCacheDir(),"installer-tests");directory.mkdirs();
        BemInstaller.loadCodec();
    }
    private File fixture() throws Exception {
        File file=new File(directory,"input.bem");
        try(InputStream in=getInstrumentation().getContext().getAssets().open("installer-fixture.bem");FileOutputStream out=new FileOutputStream(file)) {
            byte[] buffer=new byte[8192];int count;
            while((count=in.read(buffer))!=-1) out.write(buffer,0,count);
        }
        return file;
    }
    public void testAndroidComposer() throws Exception {
        JSONObject group=new JSONObject("{id:'part0',default:'s0',segments:[{id:'a',source_id:'s0',default:'on',count:6,body_draw:'s0.m0.d0',body_region:'neck',triangles:[0,1]},{id:'b',source_id:'s0',default:'on',count:0,body_draw:'s0.m0.d0',body_region:'head',triangles:[]},{id:'c',source_id:'s1',default:'off',count:3}]}" );
        JSONObject choices=new JSONObject();BemComposeState state=new BemComposeState(new JSONObject().put("slots",new JSONArray().put(group)),choices);
        JSONObject a=group.getJSONArray("segments").getJSONObject(0),b=group.getJSONArray("segments").getJSONObject(1),c=group.getJSONArray("segments").getJSONObject(2);
        assertTrue(state.enabled(group,a));assertFalse(state.enabled(group,c));
        state.setEnabled(group,java.util.List.of(c),true);assertTrue(state.enabled(group,a));assertTrue(state.enabled(group,c));
        state.setEnabled(group,java.util.List.of(a),false);assertFalse(state.enabled(group,a));assertTrue(state.enabled(group,c));
        JSONObject before=new JSONObject(choices.toString());JSONObject inspection=state.inspection(group,java.util.List.of(a));
        assertTrue(inspection.getString("a").equals("on"));assertTrue(inspection.getString("c").equals("off"));assertTrue(before.toString().equals(choices.toString()));
        choices.put("body_edits",new JSONObject().put("s0.m0.d0",new JSONObject().put("0","head")));
        assertEquals(1,state.triangles(a));assertEquals(1,state.triangles(b));
        android.content.Context app=getTargetContext();BemComposeSession session=BemComposeSession.create(app);
        try {
            File first=fixture(),second=new File(directory,"compose-second.bem");Files.copy(first.toPath(),second.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            JSONObject request=new JSONObject().put("schema",1).put("platform","android-arm64").put("project_id","test.android.compose").put("name","Android composition test").put("author","Test").put("version","auto")
                .put("sources",new JSONArray().put(new JSONObject().put("path",first.getAbsolutePath()).put("selection","")).put(new JSONObject().put("path",second.getAbsolutePath()).put("selection","")));
            session.write("request.json",request);session.analyze();assertTrue(session.read("plan.json").getString("platform").equals("android-arm64"));
            assertFalse(session.file("preview").exists());
            JSONObject draft=session.run("draft",session.file("preview"),session.file("cancel"));assertTrue(draft.getBoolean("preview_only"));
            JSONObject built=session.run("build",session.file("built"),session.file("cancel"));assertFalse(built.getBoolean("preview_only"));
            JSONObject inspected=new JSONObject(BemInstaller.inspectNative(built.getJSONArray("outputs").getString(0)));assertTrue(inspected.getString("name").contains("Android composition"));
            session.file("cancel").createNewFile();try{session.run("draft",session.file("cancelled"),session.file("cancel"));fail("Ignored composition cancellation");}catch(IOException expected){}
            assertFalse(session.file("cancelled").exists());
        } finally {BemComposeSession.remove(session.directory);}
    }
    public void testAstcConversionAndOverwriteRejection() throws Exception {
        File input=fixture(),output=new File(directory,"converted.bem");output.delete();
        JSONObject report=new JSONObject(BemInstaller.convertNative(input.getPath(),output.getPath(),"{}",true));
        assertEquals(100,BemInstaller.progressPercent);
        assertTrue(BemInstaller.status.contains("mip"));
        assertEquals(48,report.getJSONArray("textures").getJSONObject(0).getInt("format"));
        assertEquals(50,report.getJSONArray("textures").getJSONObject(1).getInt("format"));
        assertEquals(2,report.getJSONArray("appearances").length());
        byte[] saved=Files.readAllBytes(output.toPath());
        try {BemInstaller.convertNative(input.getPath(),output.getPath(),"{}",true);fail("Overwrote output");}catch(IOException expected){}
        assertTrue(java.util.Arrays.equals(saved,Files.readAllBytes(output.toPath())));
    }
    public void testOriginalImportInspection() throws Exception {
        File input=fixture();
        byte[] original=Files.readAllBytes(input.toPath());
        JSONObject report=new JSONObject(BemInstaller.inspectNative(input.getPath()));
        assertEquals(2,report.getJSONArray("appearances").length());
        assertTrue(report.getLong("bytes")==original.length);
        assertTrue(java.util.Arrays.equals(original,Files.readAllBytes(input.toPath())));
        File bad=new File(directory,"inspect-bad.bem");
        Files.write(bad.toPath(),new byte[40]);
        try {BemInstaller.inspectNative(bad.getPath());fail("Accepted corrupt import");}catch(IOException expected){}
    }
    public void testMultiResourceImportAndConversion() throws Exception {
        File input=new File(directory,"multi-resource-valid.bem");
        try(InputStream in=getInstrumentation().getContext().getAssets().open("multi-resource-valid.bem");
            FileOutputStream out=new FileOutputStream(input)) {
            byte[] bytes=new byte[8192];int count;
            while((count=in.read(bytes))!=-1) out.write(bytes,0,count);
        }
        JSONObject original=new JSONObject(BemInstaller.inspectNative(input.getPath()));
        assertEquals(4,original.getInt("bem_minor"));
        assertEquals(5,original.getJSONArray("resource_keys").length());
        assertTrue("chr_synthetic".equals(original.getString("target_id")));
        File output=new File(directory,"multi-resource-converted.bem");output.delete();
        JSONObject converted=new JSONObject(BemInstaller.convertNative(input.getPath(),output.getPath(),"{}",false));
        assertEquals(4,converted.getInt("bem_minor"));
        assertTrue(output.isFile());
        JSONObject checked=new JSONObject(BemInstaller.inspectNative(output.getPath()));
        assertEquals(original.getJSONArray("resource_keys").length(),checked.getJSONArray("resource_keys").length());
    }
    public void testRgbaFallbackAndCorruptPackage() throws Exception {
        File input=fixture(),output=new File(directory,"fallback.bem");output.delete();
        JSONObject report=new JSONObject(BemInstaller.convertNative(input.getPath(),output.getPath(),"{}",false));
        assertEquals(4,report.getJSONArray("textures").getJSONObject(0).getInt("format"));
        File bad=new File(directory,"bad.bem");try(FileOutputStream out=new FileOutputStream(bad)){out.write(new byte[40]);}
        File rejected=new File(directory,"rejected.bem");rejected.delete();
        try {BemInstaller.convertNative(bad.getPath(),rejected.getPath(),"{}",true);fail("Accepted bad header");}catch(IOException expected){}
        assertFalse(rejected.exists());assertFalse(new File(rejected+".payloads").exists());
    }
    public void testGamePrivateMaterialization() throws Exception {
        File input=fixture();String generation="00000000-0000-0000-0000-000000000001";
        JSONObject entry=new JSONObject().put("generation",generation).put("remote","bem-"+generation+".bem")
            .put("bytes",input.length()).put("package_id","test.package").put("character_id","target")
            .put("default_appearance","hidden").put("appearances",new JSONArray().put("hidden"));
        String config=BemInstalledResources.prepare(getInstrumentation().getTargetContext(),new JSONArray().put(entry).toString(),name->new FileInputStream(input),x->{});
        assertTrue(config.contains("appearances=hidden"));assertTrue(config.contains("replace=1"));
        entry.put("generation","../../bad");
        try {BemInstalledResources.prepare(getInstrumentation().getTargetContext(),new JSONArray().put(entry).toString(),name->new FileInputStream(input),x->{});fail("Accepted path traversal");}catch(IOException expected){}
    }
    public void testRuntimeIndexConflictNormalization() throws Exception {
        JSONObject older=new JSONObject().put("generation","00000000-0000-0000-0000-000000000031")
            .put("package_id","old.package").put("character_id","same_character").put("enabled",true);
        JSONObject newer=new JSONObject().put("generation","00000000-0000-0000-0000-000000000032")
            .put("package_id","new.package").put("character_id","same_character").put("enabled",true);
        JSONArray original=new JSONArray().put(older).put(newer);
        JSONArray normalized=BemOptions.exclusive(original);
        assertFalse(normalized.getJSONObject(0).getBoolean("enabled"));
        assertTrue(normalized.getJSONObject(1).getBoolean("enabled"));
        assertTrue(original.getJSONObject(0).getBoolean("enabled"));
        newer.put("generation",older.getString("generation"));
        try {BemOptions.exclusive(original);fail("Accepted duplicate generation");}catch(IOException expected){}
        newer.put("generation","00000000-0000-0000-0000-000000000032").put("enabled","true");
        try {BemOptions.exclusive(original);fail("Accepted string enabled state");}catch(IOException expected){}
    }
    public void testRuntimePreflightRejectsLaterInvalidEntryBeforeCopy() throws Exception {
        android.content.Context base=getTargetContext();
        File isolated=new File(base.getCacheDir(),"preflight-"+java.util.UUID.randomUUID());
        android.content.Context game=new android.content.ContextWrapper(base) {
            @Override public File getFilesDir(){return isolated;}
        };
        File source=fixture();
        String first="00000000-0000-0000-0000-000000000041";
        String second="00000000-0000-0000-0000-000000000042";
        JSONObject valid=new JSONObject().put("generation",first).put("remote","bem-"+first+".bem")
            .put("bytes",source.length()).put("package_id","first.package").put("character_id","first_character")
            .put("appearances",new JSONArray().put("hidden")).put("default_appearance","hidden");
        JSONObject invalid=new JSONObject().put("generation",second).put("remote","bem-"+second+".bem")
            .put("bytes",source.length()).put("package_id","second.package").put("character_id","second_character")
            .put("appearances",new JSONArray().put("default")).put("default_appearance","missing");
        java.util.concurrent.atomic.AtomicInteger opens=new java.util.concurrent.atomic.AtomicInteger();
        try {
            BemInstalledResources.prepare(game,new JSONArray().put(valid).put(invalid).toString(),name->{
                opens.incrementAndGet();return new FileInputStream(source);
            },value->{});
            fail("Accepted invalid later appearance");
        } catch(IOException expected) {
            assertEquals(0,opens.get());
            assertFalse(new File(isolated,"betterendfield/installed-models").exists());
        } finally {deleteTree(isolated);}
    }
    public void testRuntimeConfigurationLimitRejectsBeforeCopy() throws Exception {
        android.content.Context base=getTargetContext();
        File oversized=new File(base.getCacheDir(),"x".repeat(1024*1024));
        android.content.Context game=new android.content.ContextWrapper(base) {
            @Override public File getFilesDir(){return oversized;}
        };
        String generation="00000000-0000-0000-0000-000000000043";
        JSONObject entry=new JSONObject().put("generation",generation).put("remote","bem-"+generation+".bem")
            .put("bytes",1).put("package_id","limit.package").put("character_id","limit_character")
            .put("appearances",new JSONArray().put("default")).put("default_appearance","default");
        java.util.concurrent.atomic.AtomicInteger opens=new java.util.concurrent.atomic.AtomicInteger();
        try {
            BemInstalledResources.prepare(game,new JSONArray().put(entry).toString(),name->{
                opens.incrementAndGet();return new java.io.ByteArrayInputStream(new byte[]{1});
            },value->{});
            fail("Accepted native configuration above 1 MiB");
        } catch(IOException expected) {
            assertEquals(0,opens.get());
            assertFalse(oversized.exists());
        }
    }
    public void testBem13ParameterSelectionPersistsAndRejectsInvalidTick() throws Exception {
        android.content.Context isolated = new android.content.ContextWrapper(getTargetContext()) {
            @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("bem-parameters-test-" + name, mode);
            }
        };
        android.content.SharedPreferences preferences = FrameworkSettings.open(isolated);
        String generation = "33333333-3333-3333-3333-333333333333";
        JSONObject group = new JSONObject().put("id", "width").put("name", "Width")
                .put("min", 0).put("max", 1000).put("step", 100)
                .put("neutral", 0).put("default", 0);
        JSONObject choice = new JSONObject().put("id", "on").put("name", "On");
        JSONObject options = new JSONObject().put("id", "base").put("default", "on")
                .put("choices", new JSONArray().put(choice));
        JSONObject entry = new JSONObject().put("generation", generation).put("bem_minor", 3)
                .put("enabled", true).put("option_groups", new JSONArray().put(options))
                .put("selection_constraints", new JSONArray())
                .put("default_options", "base:on")
                .put("parameters", new JSONArray().put(group))
                .put("default_parameters", "width:0");
        try {
            preferences.edit().putString(BemInstaller.INDEX, new JSONArray().put(entry).toString()).commit();
            JSONObject change = new JSONObject().put("generation", generation).put("enabled", true)
                    .put("options", "base:on").put("parameters", "width:700");
            BemInstaller.saveAll(isolated, BemInstaller.snapshot(isolated).revision, new JSONArray().put(change));
            JSONObject saved = BemInstaller.index(isolated).getJSONObject(0);
            assertTrue("width:700".equals(saved.getString("selected_parameters")));
            assertTrue("width:700".equals(saved.getString("remembered_parameters")));
            File payload = fixture();
            saved.put("remote", "bem-" + generation + ".bem").put("bytes", payload.length())
                    .put("package_id", "test.parameters").put("character_id", "test_character");
            String runtime = BemInstalledResources.prepare(getTargetContext(),
                    new JSONArray().put(saved).toString(), name -> new FileInputStream(payload), value -> {});
            assertTrue(runtime.contains("parameters=width:700"));
            change.put("parameters", "width:750");
            try { BemInstaller.saveAll(isolated, BemInstaller.snapshot(isolated).revision, new JSONArray().put(change)); fail("Accepted off-step tick"); }
            catch (IOException expected) {}
            assertTrue("width:700".equals(BemInstaller.index(isolated).getJSONObject(0).getString("selected_parameters")));
        } finally {
            preferences.edit().clear().commit();
        }
    }
    public void testGameStartupPrunesOnlyUnusedGenerations() throws Exception {
        android.content.Context base=getTargetContext();
        File isolated=new File(base.getCacheDir(),"game-prune-"+java.util.UUID.randomUUID());isolated.mkdirs();
        android.content.Context game=new android.content.ContextWrapper(base) {
            @Override public File getFilesDir(){return isolated;}
        };
        String active="00000000-0000-0000-0000-000000000011",stale="00000000-0000-0000-0000-000000000012";
        File root=new File(isolated,"betterendfield/installed-models");root.mkdirs();
        writeText(new File(root,stale+".bem"),"old");
        writeText(new File(root,"keep.txt"),"other data");
        File source=fixture();
        JSONObject entry=new JSONObject().put("generation",active).put("remote","bem-"+active+".bem")
                .put("bytes",source.length()).put("package_id","test.package").put("character_id","test_character")
                .put("appearances",new JSONArray().put("hidden")).put("default_appearance","hidden");
        BemInstalledResources.prepare(game,new JSONArray().put(entry).toString(),name->new FileInputStream(source),x->{},true);
        assertTrue(new File(root,active+".bem").isFile());
        assertFalse(new File(root,stale+".bem").exists());
        assertTrue(new File(root,"keep.txt").isFile());
        deleteTree(isolated);
    }
    public void testCleanupRetainsIndexedGenerationsAndUnknownFiles() throws Exception {
        File isolated=new File(getTargetContext().getCacheDir(),"cleanup-"+java.util.UUID.randomUUID());isolated.mkdirs();
        String active="00000000-0000-0000-0000-000000000021",stale="00000000-0000-0000-0000-000000000022";
        File keep=new File(isolated,active),remove=new File(isolated,stale),stage=new File(isolated,"stage-00000000-0000-0000-0000-000000000023");
        keep.mkdir();remove.mkdir();stage.mkdir();
        writeText(new File(keep,"installed.bem"),"keep");
        writeText(new File(remove,"installed.bem"),"remove");
        writeText(new File(stage,"source.bem"),"stage");
        writeText(new File(isolated,"unrelated.txt"),"leave");
        java.util.Set<String> referenced=BemInstaller.referencedGenerations(new JSONArray().put(new JSONObject().put("generation",active)));
        assertTrue(BemInstaller.cleanLocalUnused(isolated,referenced)>0);
        assertTrue(keep.isDirectory());assertFalse(remove.exists());assertFalse(stage.exists());
        assertTrue(new File(isolated,"unrelated.txt").isFile());
        try {BemInstaller.referencedGenerations(new JSONArray().put(new JSONObject().put("generation","../../bad")));fail("Accepted malformed index");}
        catch(IOException expected) { }
        deleteTree(isolated);
    }
    public void testCleanupReportsDeletionFailure() throws Exception {
        File isolated=new File(getTargetContext().getCacheDir(),"cleanup-failure-"+java.util.UUID.randomUUID());isolated.mkdirs();
        File stale=new File(isolated,"00000000-0000-0000-0000-000000000024");stale.mkdir();
        writeText(new File(stale,"installed.bem"),"old");
        try {
            BemInstaller.cleanLocalUnused(isolated,java.util.Collections.emptySet(),file->{});
            fail("Deletion failure was reported as success");
        } catch(IOException expected) {
            assertTrue(expected.getMessage().contains("删除失败"));
            assertTrue(stale.isDirectory());
        } finally {deleteTree(isolated);}
    }
    public void testRemoteListingFailureIsNotEmptyList() throws Exception {
        try {
            FrameworkSettings.checkedBemNames(false,()->new String[0]);
            fail("Disconnected remote was reported as empty");
        } catch(IOException expected) {assertTrue(expected.getMessage().contains("未连接"));}
        try {
            FrameworkSettings.checkedBemNames(true,()->{throw new IllegalStateException("remote failed");});
            fail("Failed remote listing was reported as empty");
        } catch(IOException expected) {assertTrue(expected.getMessage().contains("列举失败"));}
        assertEquals(0,FrameworkSettings.checkedBemNames(true,()->new String[0]).length);
    }
    private static void deleteTree(File file) {
        File[] children=file.listFiles();if(children!=null) for(File child:children) deleteTree(child);
        file.delete();
    }
    public void testRealPackageConversionWhenProvided() throws Exception {
        File input=new File(getInstrumentation().getTargetContext().getFilesDir(),realInput);
        if(!input.isFile()) {if(method.equals("testRealPackageConversionWhenProvided")) fail("Missing real package");return;}
        File output=new File(directory,"real-converted.bem");output.delete();String rules;
        try(InputStream in=GameResourceStore.openCore(getInstrumentation().getTargetContext(),"model/android-normal-rules.json")){rules=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        long start=System.currentTimeMillis();
        String report=BemInstaller.convertNative(input.getPath(),output.getPath(),rules,true);
        Files.writeString(new File(directory,"real-report.json").toPath(),report);
        android.util.Log.i("BetterEndfieldNext.InstallTest","real conversion ms="+(System.currentTimeMillis()-start)+" report="+report);
        assertTrue(output.isFile());
    }
    public void testRemovalPreservesOtherPackages() throws Exception {
        android.content.Context base=getTargetContext();
        String run=java.util.UUID.randomUUID().toString();
        File isolated=new File(base.getCacheDir(),"remove-test-"+run);isolated.mkdirs();
        android.content.Context app=new android.content.ContextWrapper(base) {
            @Override public android.content.Context getApplicationContext(){return this;}
            @Override public File getFilesDir(){return isolated;}
            @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){return base.getSharedPreferences("remove-test-"+run,mode);}
        };
        String target=java.util.UUID.randomUUID().toString(),other=java.util.UUID.randomUUID().toString(),old=java.util.UUID.randomUUID().toString();
        File root=new File(isolated,"bem-installed");root.mkdirs();
        for(String id:new String[]{target,other,old}) {
            File folder=new File(root,id);folder.mkdir();
            writeText(new File(folder,"installed.bem"),"test bytes");
            writeText(new File(folder,"report.json"),"{\"character_id\":\""+(id.equals(other)?"other":"target")+"\"}");
        }
        JSONObject keep=new JSONObject().put("generation",other).put("character_id","target").put("enabled",true)
                .put("default_appearance","alternate").put("appearances",new JSONArray().put("alternate")).put("selected_appearance","alternate");
        JSONArray entries=new JSONArray().put(new JSONObject().put("generation",target).put("character_id","target").put("enabled",false)
                .put("name","Removal fixture").put("default_appearance","default").put("appearances",new JSONArray().put("default"))).put(keep);
        FrameworkSettings.open(app).edit().putString(BemInstaller.INDEX,entries.toString()).commit();
        try {BemInstaller.remove(app,"../../bad");fail("Accepted unsafe generation");}catch(IOException expected){}
        assertEquals(2,BemInstaller.index(app).length());
        BemInstaller.remove(app,target);
        long deadline=System.currentTimeMillis()+10000;
        while(BemInstaller.busy && System.currentTimeMillis()<deadline) Thread.sleep(50);
        assertFalse(BemInstaller.busy);
        JSONArray remaining=BemInstaller.index(app);
        assertEquals(1,remaining.length());assertTrue(keep.toString().equals(remaining.getJSONObject(0).toString()));
        assertFalse(new File(root,target).exists());assertTrue(new File(root,old).exists());
        assertTrue(new File(root,other+"/installed.bem").isFile());
    }
    public void testLegacyMigrationAndImmediateExclusiveSelection() throws Exception {
        android.content.Context base=getTargetContext();String run=java.util.UUID.randomUUID().toString();
        File isolated=new File(base.getCacheDir(),"selection-test-"+run);isolated.mkdirs();
        android.content.Context app=new android.content.ContextWrapper(base) {
            @Override public android.content.Context getApplicationContext(){return this;}
            @Override public File getFilesDir(){return isolated;}
            @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){return base.getSharedPreferences("selection-test-"+run,mode);}
        };
        JSONArray entries=new JSONArray();
        for(int i=0;i<3;i++) entries.put(new JSONObject().put("generation",java.util.UUID.randomUUID().toString())
                .put("character_id",i<2?"same":"other").put("name","Selection fixture")
                .put("default_appearance","default").put("appearances",new JSONArray().put("default").put("alternate")));
        assertTrue(FrameworkSettings.open(app).edit().putString(BemInstaller.INDEX,entries.toString()).commit());
        JSONArray migrated=BemInstaller.index(app);
        assertEquals(3,migrated.length());assertFalse(migrated.getJSONObject(0).getBoolean("enabled"));
        assertTrue(migrated.getJSONObject(1).getBoolean("enabled"));assertTrue(migrated.getJSONObject(2).getBoolean("enabled"));
        String first=migrated.getJSONObject(0).getString("generation"),second=migrated.getJSONObject(1).getString("generation");
        BemInstaller.select(app,first,"alternate",true);
        JSONArray saved=BemInstaller.index(app);
        assertTrue(saved.getJSONObject(0).getBoolean("enabled"));assertFalse(saved.getJSONObject(1).getBoolean("enabled"));
        assertTrue(saved.getJSONObject(0).getString("selected_appearance").equals("alternate"));
        assertTrue(saved.getJSONObject(2).toString().equals(migrated.getJSONObject(2).toString()));
        String durable=Files.readString(new File(isolated,"bem-index.json").toPath());
        assertTrue(durable.equals(saved.toString()));
        try {BemInstaller.select(app,second,"removed-choice",true);fail("Saved invalid appearance");}catch(IOException expected){}
        assertTrue(saved.toString().equals(BemInstaller.index(app).toString()));
        JSONObject converted=new JSONObject(saved.getJSONObject(0).toString()).put("generation",java.util.UUID.randomUUID().toString());
        JSONArray next=BemInstaller.installedIndex(saved,converted,first);
        assertEquals(3,next.length());assertTrue(next.getJSONObject(2).getBoolean("enabled"));
        assertTrue(next.getJSONObject(2).getString("selected_appearance").equals("alternate"));
        assertTrue(next.getJSONObject(0).getString("generation").equals(second));
    }
    /** Android framework tests: compile locally, execute with the installed test APK. */
    public void testIncomingBemOpenAndShareRequests() throws Exception {
        android.net.Uri uri=android.net.Uri.parse("content://bem.test.provider/document/123");
        android.content.Intent view=new android.content.Intent(android.content.Intent.ACTION_VIEW,uri);
        assertTrue(uri.equals(BemImportRequest.fromIntent(view)));
        android.content.Intent share=new android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType("application/octet-stream").putExtra(android.content.Intent.EXTRA_STREAM,uri);
        assertTrue(uri.equals(BemImportRequest.fromIntent(share)));
        share.setClipData(android.content.ClipData.newRawUri("opaque",uri));
        assertTrue(uri.equals(BemImportRequest.fromIntent(share)));
        android.content.Intent clipOnly=new android.content.Intent(android.content.Intent.ACTION_SEND);
        clipOnly.setClipData(android.content.ClipData.newRawUri("fixture.BEM",uri));
        assertTrue(uri.equals(BemImportRequest.fromIntent(clipOnly)));
        assertTrue(BemImportRequest.fromIntent(new android.content.Intent())==null);
        assertTrue(BemImportRequest.fromIntent(new android.content.Intent(android.content.Intent.ACTION_MAIN))==null);
        for(String raw:new String[]{"file:///sdcard/model.bem","https://example.com/model.bem","content:/missing-authority"}) {
            try {BemImportRequest.fromIntent(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(raw)));fail("Accepted unsafe URI");}
            catch(IllegalArgumentException expected) {}
        }
        android.content.Intent text=new android.content.Intent(android.content.Intent.ACTION_SEND)
                .putExtra(android.content.Intent.EXTRA_TEXT,uri.toString());
        try {BemImportRequest.fromIntent(text);fail("Accepted text instead of a grant-bearing URI");}
        catch(IllegalArgumentException expected) {}
        android.content.Intent wrongType=new android.content.Intent(android.content.Intent.ACTION_SEND)
                .putExtra(android.content.Intent.EXTRA_STREAM,"not a Uri");
        try {BemImportRequest.fromIntent(wrongType);fail("Accepted malformed stream extra");}
        catch(IllegalArgumentException expected) {}
        android.content.ClipData multiple=android.content.ClipData.newRawUri("first",uri);
        multiple.addItem(new android.content.ClipData.Item(android.net.Uri.parse("content://bem.test.provider/second")));
        clipOnly.setClipData(multiple);
        try {BemImportRequest.fromIntent(clipOnly);fail("Silently selected from multiple documents");}
        catch(IllegalArgumentException expected) {}
        share.setClipData(android.content.ClipData.newRawUri("conflict",android.net.Uri.parse("content://bem.test.provider/other")));
        try {BemImportRequest.fromIntent(share);fail("Accepted conflicting URI fields");}
        catch(IllegalArgumentException expected) {}
    }
    public void testBemImportBusyResult() throws Exception {
        // Do not touch storage/native parsing; verify that busy is observable and
        // the running operation's status is not overwritten by another request.
        synchronized(BemInstaller.class) {
            if(BemInstaller.busy) fail("Run installer tests with no active import");
            String previous=BemInstaller.status;
            BemInstaller.busy=true;
            try {
                assertFalse(BemInstaller.start(getTargetContext(),android.net.Uri.parse("content://bem.test.provider/document/123")));
                assertTrue(previous.equals(BemInstaller.status));
            } finally {BemInstaller.busy=false;}
        }
    }
    public void testBemOpenWithManifestResolution() throws Exception {
        android.content.Context context=getTargetContext();
        android.content.pm.PackageManager pm=context.getPackageManager();
        android.content.ComponentName component=new android.content.ComponentName(context,BemInstallActivity.class);
        android.content.pm.ActivityInfo info=pm.getActivityInfo(component,0);
        assertTrue(info.exported);
        assertEquals(android.content.pm.ActivityInfo.LAUNCH_SINGLE_TOP,info.launchMode);
        android.net.Uri content=android.net.Uri.parse("content://bem.test.provider/document/123");
        for(String mime:new String[]{"application/x-bem","application/vnd.betterendfield.bem","application/octet-stream","application/x-binary",
                "application/zip","application/x-zip-compressed","application/x-zip"}) {
            android.content.Intent view=new android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setDataAndType(content,mime).setPackage(context.getPackageName());
            assertTrue(resolvesBem(pm,view));
            android.content.Intent send=new android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType(mime).putExtra(android.content.Intent.EXTRA_STREAM,content).setPackage(context.getPackageName());
            assertTrue(resolvesBem(pm,send));
        }
        for(String mime:new String[]{"image/png","application/pdf","text/plain"}) {
            android.content.Intent unrelated=new android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setDataAndType(content,mime).setPackage(context.getPackageName());
            assertFalse(resolvesBem(pm,unrelated));
        }
        android.content.Intent path=new android.content.Intent(android.content.Intent.ACTION_VIEW)
                .setDataAndType(android.net.Uri.parse("file:///sdcard/model.bem"),"application/octet-stream")
                .setPackage(context.getPackageName());
        assertFalse(resolvesBem(pm,path));
    }
    private static boolean resolvesBem(android.content.pm.PackageManager pm,android.content.Intent intent) {
        for(android.content.pm.ResolveInfo info:pm.queryIntentActivities(intent,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY))
            if(BemInstallActivity.class.getName().equals(info.activityInfo.name)) return true;
        return false;
    }

}
