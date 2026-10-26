package dev.betterendfield.android;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

/** Real page-state + installer interleaving, with isolated preferences and durable files. */
final class BemIndexRevisionDeviceTest {
    private static void check(boolean value) {if(!value) throw new AssertionError("index revision invariant");}
    private static final class Fixture implements AutoCloseable {
        final File directory;
        final Context app;
        final SharedPreferences preferences;
        Fixture(Context target) throws Exception {
            directory=new File(target.getCacheDir(),"index-revision-"+System.nanoTime());
            check(directory.mkdir());
            app=new ContextWrapper(target) {
                @Override public File getFilesDir() {return directory;}
                @Override public SharedPreferences getSharedPreferences(String name,int mode) {
                    return super.getSharedPreferences(directory.getName()+"-"+name,mode);
                }
            };
            preferences=FrameworkSettings.open(app);
            set(new JSONArray().put(entry("one","11111111-1111-1111-1111-111111111111")));
        }
        void set(JSONArray entries) {check(preferences.edit().putString(BemInstaller.INDEX,entries.toString()).commit());}
        String current() {return BemInstaller.snapshot(app).index;}
        String durable() throws Exception {return new String(Files.readAllBytes(new File(directory,"bem-index.json").toPath()),StandardCharsets.UTF_8);}
        BemInstallState page() {BemInstallState page=new BemInstallState(app);page.refresh();return page;}
        void patch(JSONObject patch) throws Exception {
            BemInstaller.saveChanges(app,BemInstaller.snapshot(app).revision,new JSONArray().put(patch));
        }
        @Override public void close() {preferences.edit().clear().commit();for(File file:directory.listFiles()) check(file.delete());check(directory.delete());}
    }
    private static JSONObject entry(String id,String generation) throws Exception {
        JSONObject option=new JSONObject().put("id","base").put("name","Base").put("default","on")
                .put("choices",new JSONArray().put(new JSONObject().put("id","on").put("name","On"))
                .put(new JSONObject().put("id","off").put("name","Off")));
        JSONObject parameter=new JSONObject().put("id","width").put("name","Width")
                .put("min",0).put("max",1000).put("step",100).put("neutral",0).put("default",0);
        return new JSONObject().put("generation",generation).put("package_id",id).put("character_id","test_character")
                .put("name",id).put("bem_minor",3).put("enabled",true)
                .put("option_groups",new JSONArray().put(option)).put("selection_constraints",new JSONArray())
                .put("default_options","base:on").put("selected_options","base:on")
                .put("parameters",new JSONArray().put(parameter)).put("default_parameters","width:0")
                .put("selected_parameters","width:0");
    }
    static void stale(Context target) throws Exception {
        try(Fixture f=new Fixture(target)) {
            BemInstallState page=f.page();BemPackage pkg=page.getPackages().get(0);
            pkg.setOptions("base:off");pkg.setParameters("width:700");
            BemInstaller.IndexSnapshot original=BemInstaller.snapshot(f.app);
            f.patch(new JSONObject().put("generation",pkg.getGeneration()).put("enabled",false).put("parameters","width:300"));
            String latest=f.current(),mirror=f.durable();
            check(!page.saveAll());check(page.getIndexConflict());
            check(pkg.getParameters().equals("width:700"));check(latest.equals(f.current()));check(mirror.equals(f.durable()));
            try {BemInstaller.saveChanges(f.app,original.revision,new JSONArray().put(new JSONObject()
                    .put("generation",pkg.getGeneration()).put("enabled",true)));throw new AssertionError("stale overlay accepted");}
            catch(BemInstaller.IndexConflict expected) {}
            try {BemInstaller.disableAll(f.app,original.revision);throw new AssertionError("stale disable accepted");}
            catch(BemInstaller.IndexConflict expected) {}
            check(latest.equals(f.current()));check(mirror.equals(f.durable()));
        }
    }
    static void refresh(Context target) throws Exception {
        try(Fixture f=new Fixture(target)) {
            BemInstallState page=f.page();BemPackage pkg=page.getPackages().get(0);
            pkg.setParameters("width:700");
            f.patch(new JSONObject().put("generation",pkg.getGeneration()).put("parameters","width:300").put("enabled",false));
            String latest=f.current();
            page.refresh();page.refresh();check(page.getIndexConflict());check(page.getPackages().get(0)==pkg);
            check(pkg.getParameters().equals("width:700"));check(!page.saveAll());check(latest.equals(f.current()));
            page.discardDraftAndReload();check(!page.getIndexConflict());
            check(!page.getPackages().get(0).getEnabled());check(page.getPackages().get(0).getParameters().equals("width:300"));
            check(page.saveAll());page.refresh();check(!page.getIndexConflict());
        }
    }
    static void cleanAndImport(Context target) throws Exception {
        try(Fixture f=new Fixture(target)) {
            BemInstallState page=f.page();String generation=page.getPackages().get(0).getGeneration();
            f.patch(new JSONObject().put("generation",generation).put("options","base:off").put("parameters","width:500").put("enabled",false));
            page.refresh();check(!page.getIndexConflict());BemPackage pkg=page.getPackages().get(0);
            check(!pkg.getEnabled());check(pkg.getOptions().equals("base:off"));check(pkg.getParameters().equals("width:500"));
            f.patch(new JSONObject().put("generation",generation).put("enabled",true));page.refresh();
            JSONObject imported=entry("two","22222222-2222-2222-2222-222222222222");
            f.set(BemInstaller.mergePublishedIndex(BemInstaller.index(f.app),imported));
            page.refresh();check(!page.getIndexConflict());check(page.getPackages().size()==2);
            check(!page.getPackages().get(0).getEnabled());check(page.getPackages().get(1).getEnabled());
        }
    }
    static void normalAndDisable(Context target) throws Exception {
        try(Fixture f=new Fixture(target)) {
            BemInstallState page=f.page();BemPackage pkg=page.getPackages().get(0);
            pkg.setOptions("base:off");pkg.setParameters("width:700");check(page.saveAll());
            check(f.current().equals(f.durable()));page.refresh();check(!page.getIndexConflict());
            pkg=page.getPackages().get(0);pkg.setParameters("width:900");check(page.disableAll());
            check(!page.getPackages().get(0).getEnabled());check(page.getPackages().get(0).getParameters().equals("width:900"));
            check(BemInstaller.index(f.app).getJSONObject(0).getString("selected_parameters").equals("width:700"));
            check(page.saveAll());check(BemInstaller.index(f.app).getJSONObject(0).getString("selected_parameters").equals("width:900"));
            String previous=f.current();page.getPackages().get(0).setParameters("width:750");check(!page.saveAll());
            check(previous.equals(f.current()));check(previous.equals(f.durable()));
        }
    }
    static void removed(Context target) throws Exception {
        try(Fixture f=new Fixture(target)) {
            BemInstallState page=f.page();page.getPackages().get(0).setOptions("base:off");
            f.set(new JSONArray());page.refresh();check(page.getIndexConflict());check(page.getPackages().size()==1);
            check(!page.saveAll());check(f.current().equals("[]"));page.discardDraftAndReload();check(page.getPackages().isEmpty());
        }
    }
    static void reverted(Context target) throws Exception {
        try(Fixture f=new Fixture(target)) {
            BemInstallState page=f.page();BemPackage pkg=page.getPackages().get(0);
            pkg.setParameters("width:700");pkg.setParameters("width:0");
            f.patch(new JSONObject().put("generation",pkg.getGeneration()).put("parameters","width:300"));
            page.refresh();check(!page.getIndexConflict());check(page.getPackages().get(0).getParameters().equals("width:300"));
            check(page.disableAll());
            f.patch(new JSONObject().put("generation",pkg.getGeneration()).put("enabled",true).put("parameters","width:500"));
            page.refresh();check(!page.getIndexConflict());check(page.getPackages().get(0).getEnabled());
            check(page.getPackages().get(0).getParameters().equals("width:500"));
        }
    }
}
