package dev.betterendfield.next;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.function.Consumer;

/** Copy framework-published immutable generations into the game's own private directory. */
final class BemInstalledResources {
    interface Source {InputStream open(String name) throws Exception;}
    private static final class Prepared {
        final String generation,remote,packageId,appearance,selection,parameters;
        final long bytes;
        Prepared(JSONObject entry) throws Exception {
            boolean composable=entry.optInt("bem_minor",0)>=1;
            appearance=composable?"":BemOptions.appearance(entry,entry.optString("selected_appearance",entry.getString("default_appearance")));
            selection=composable?BemOptions.encode(BemOptions.parse(entry,entry.optString("selected_options",entry.getString("default_options")))):"";
            parameters=BemParameters.encode(BemParameters.parse(entry,
                entry.optString("selected_parameters",entry.optString("default_parameters",""))));
            generation=entry.getString("generation");remote=entry.getString("remote");
            packageId=entry.getString("package_id");bytes=entry.getLong("bytes");
            if(!generation.matches("[a-f0-9-]{36}")||!remote.equals("bem-"+generation+".bem"))
                throw new IOException("Invalid installed generation");
            if(bytes<=0||bytes>2L*1024*1024*1024) throw new IOException("Invalid installed size");
        }
    }
    static volatile String configuration="";
    static String prepare(Context context,String index,Source source,Consumer<String> log) throws Exception {
        return prepare(context,index,source,log,false);
    }
    static String prepare(Context context,String index,Source source,Consumer<String> log,boolean skipValidation) throws Exception {
        return prepare(context,index,source,log,skipValidation,false,false);
    }
    static String prepare(Context context,String index,Source source,Consumer<String> log,
            boolean skipValidation,boolean hotSwitch,boolean fastLoading) throws Exception {
        return prepare(context,index,source,log,skipValidation,hotSwitch,fastLoading,false);
    }
    /** pruneUnused is only safe before the runtime has opened any package path. */
    static String prepare(Context context,String index,Source source,Consumer<String> log,
            boolean skipValidation,boolean hotSwitch,boolean fastLoading,boolean pruneUnused) throws Exception {
        return prepare(context,index,source,log,skipValidation,hotSwitch,fastLoading,pruneUnused,false);
    }
    static String prepare(Context context,String index,Source source,Consumer<String> log,
            boolean skipValidation,boolean hotSwitch,boolean fastLoading,boolean pruneUnused,boolean cloneSupport) throws Exception {
        return prepare(context,index,source,log,skipValidation,hotSwitch,fastLoading,pruneUnused,cloneSupport,false);
    }
    static String prepare(Context context,String index,Source source,Consumer<String> log,
            boolean skipValidation,boolean hotSwitch,boolean fastLoading,boolean pruneUnused,boolean cloneSupport,boolean releaseRetiredResources) throws Exception {
        java.util.Set<String> used=new java.util.HashSet<>();
        JSONArray entries=BemOptions.exclusive(new JSONArray(BemConfigurationLimit.index(index)));
        StringBuilder paths=new StringBuilder(),appearances=new StringBuilder(),options=new StringBuilder(),parameters=new StringBuilder();
        java.util.ArrayList<Prepared> ready=new java.util.ArrayList<>();
        for(int i=0;i<entries.length();++i) {
            JSONObject entry=entries.getJSONObject(i);
            if(entry.optBoolean("enabled",true)) ready.add(new Prepared(entry));
        }
        File root=new File(context.getFilesDir(),"betterendfieldnext/installed-models");
        for(Prepared item:ready) {
            if(paths.length()>0) {paths.append(',');appearances.append(',');options.append(',');parameters.append(',');}
            paths.append(new File(root,item.generation+".bem").getAbsolutePath());
            appearances.append(item.appearance);options.append(item.selection);parameters.append(item.parameters);
            used.add(item.generation+".bem");
        }
        String configuration=BemConfigurationLimit.configuration(paths.length()==0&&!hotSwitch?"":
            "resource=auto;replace=1;lod_pipeline=1;lod_npc=1;packages="+paths+
            ";appearances="+appearances+";options="+options+";parameters="+parameters
            +";skip_validation="+(skipValidation?"1":"0")+";hot_switch="+(hotSwitch?"1":"0")
            +";fast_loading="+(fastLoading?"1":"0")+";clone_support="+(cloneSupport?"1":"0")
            +";release_retired_resources="+(releaseRetiredResources?"1":"0"));
        if(!root.isDirectory()&&!root.mkdirs()) throw new IOException("Cannot create game model directory");
        for(Prepared item:ready) {
            File output=new File(root,item.generation+".bem"),temp=new File(root,item.generation+".tmp");
            if(!output.isFile()||output.length()!=item.bytes) {
                try {
                    try(InputStream in=source.open(item.remote);FileOutputStream out=new FileOutputStream(temp)) {
                        byte[] buffer=new byte[65536];long length=0;int n;
                        while((n=in.read(buffer))!=-1) {length+=n;if(length>item.bytes) throw new IOException("Installed payload exceeds declared size");out.write(buffer,0,n);}
                        if(length!=item.bytes) throw new IOException("Truncated installed payload");out.getFD().sync();
                    }
                    android.system.Os.rename(temp.getAbsolutePath(),output.getAbsolutePath());
                } finally {temp.delete();}
            }
            log.accept("Installed BEM ready: "+item.packageId+" selection="+(item.selection.isEmpty()?item.appearance:item.selection));
        }
        File[] stale=pruneUnused?root.listFiles():null;
        if(stale!=null) for(File file:stale) {
            String name=file.getName();
            if(file.isFile() && name.matches("[a-f0-9-]{36}\\.(bem|tmp)") && !used.contains(name) && file.delete())
                log.accept("Unused installed BEM removed: "+name);
        }
        return configuration;
    }
}
