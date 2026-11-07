package dev.betterendfield.next;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Map;
import java.util.Set;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.os.ParcelFileDescriptor;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Local UI preferences remain authoritative; framework service publishes snapshots. */
final class FrameworkSettings {
    private static XposedService service;
    private static Context appContext;
    private static SharedPreferences local;
    private static XposedService remoteService;
    // SharedPreferences keeps weak references; retain this for the application lifetime.
    private static final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> publish();

    static void initialize(Context context) {
        appContext = context.getApplicationContext();
        local = open(context);
        try { OverlayWriteAuthorization.initialize(context); }
        catch (java.io.IOException unavailable) { Log.e("BetterEndfieldNext.Settings", "overlay authorization unavailable", unavailable); }
        local.registerOnSharedPreferenceChangeListener(listener);
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override public void onServiceBind(XposedService connected) {
                synchronized (FrameworkSettings.class) {
                    service = connected; remoteService = connected;
                    FrameworkSettings.class.notifyAll();
                    publish();
                    OverlaySettingsDiagnostics.record(context, "framework=connected authorization_ready="
                            + OverlayWritePolicy.validToken(OverlayWriteAuthorization.ownerToken()));
                }
            }
            @Override public void onServiceDied(XposedService disconnected) {
                synchronized (FrameworkSettings.class) { if (service == disconnected) { service = null; remoteService = null; } }
            }
        });
    }

    static synchronized boolean writeRemoteCommand(String payload) {
        if (remoteService == null || payload == null) return false;
        try (ParcelFileDescriptor descriptor = remoteService.openRemoteFile("command.next");
                FileOutputStream stream = new FileOutputStream(descriptor.getFileDescriptor())) {
            // The remote file is opened for read/write, not truncated, so a
            // shorter payload would leave the tail of the previous one behind.
            // That tail matters: a camera configuration that follows a longer
            // one would be parsed together with the stale key=value lines, and
            // the old values would win by coming last.
            java.nio.channels.FileChannel channel = stream.getChannel();
            channel.truncate(0);
            channel.position(0);
            stream.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            stream.flush();
            return true;
        } catch (RuntimeException | java.io.IOException error) {
            return false;
        }
    }

    static synchronized boolean writeRemoteStatus(String payload) {
        if (remoteService == null || payload == null) return false;
        try (ParcelFileDescriptor descriptor = remoteService.openRemoteFile("command.status");
                FileOutputStream stream = new FileOutputStream(descriptor.getFileDescriptor())) {
            stream.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            stream.flush();
            return true;
        } catch (RuntimeException | java.io.IOException error) {
            return false;
        }
    }

    static synchronized String readRemoteStatus() {
        return appContext == null ? "" : RuntimeJournalProvider.readStatus(appContext);
    }

    /** Reads a file from the module's remote (LSPosed service) file space; "" when unavailable. */
    static synchronized String readRemoteFile(String name) {
        if (remoteService == null) return "";
        try (ParcelFileDescriptor descriptor = remoteService.openRemoteFile(name);
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        new ParcelFileDescriptor.AutoCloseInputStream(descriptor),
                        java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder value = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null) value.append(line).append('\n');
            return value.toString();
        } catch (RuntimeException | java.io.IOException error) { return ""; }
    }

    /** Reads the latest game-process snapshot received by the journal provider. */
    static synchronized String readRemoteLog() {
        return appContext == null ? "" : RuntimeJournalProvider.readLog(appContext);
    }

    static SharedPreferences open(Context context) {
        return context.getSharedPreferences("module_settings", Context.MODE_PRIVATE);
    }

    static synchronized void publishThirdParty(java.io.File file, String name)
            throws java.io.IOException {
        if (remoteService == null || file == null || !file.isFile()
                || !name.matches("tpm-[a-f0-9-]{36}\\.zip")) {
            throw new java.io.IOException("框架服务未连接或第三方模块 ZIP 无效");
        }
        try (ParcelFileDescriptor descriptor = remoteService.openRemoteFile(name);
                java.io.FileInputStream input = new java.io.FileInputStream(file);
                FileOutputStream output = new FileOutputStream(descriptor.getFileDescriptor())) {
            output.getChannel().truncate(0);
            byte[] buffer = new byte[65536];
            long copied = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                copied += count;
                if (copied > ThirdPartyModulePackage.LIMIT) {
                    throw new java.io.IOException("第三方模块 ZIP 超过容量限制");
                }
                output.write(buffer, 0, count);
            }
            output.getFD().sync();
        } catch (RuntimeException error) {
            throw new java.io.IOException("第三方模块 ZIP 发布失败", error);
        }
    }

    static synchronized boolean removeThirdParty(String name) {
        if (remoteService == null || name == null
                || !name.matches("tpm-[a-f0-9-]{36}\\.zip")) return false;
        try {
            return remoteService.deleteRemoteFile(name)
                    || !java.util.Arrays.asList(remoteService.listRemoteFiles()).contains(name);
        } catch (RuntimeException error) {
            Log.e("BetterEndfield.ThirdParty", "Removing shared ZIP failed", error);
            return false;
        }
    }

    /**
     * Whether the framework service is reachable at all. The settings screen asks
     * before offering an import: a .vmd that cannot be published has to be
     * refused here, not handed to the game as a slot that stays empty.
     */
    static synchronized boolean remoteAvailable() {
        return remoteService != null;
    }

    static boolean awaitConnection(FrameworkServiceWait.Checkpoint checkpoint) throws java.io.IOException {
        return FrameworkServiceWait.await(FrameworkSettings.class,
                () -> remoteService != null, 10_000, checkpoint);
    }

    /**
     * Publishes the imported .vmd into the framework's remote file space, under
     * the name the game process opens.
     *
     * This space is the only channel that carries a file between the two
     * processes: the module app's own files directory belongs to the module's UID
     * and the game process cannot read it, while assets inside the APK are fixed
     * at build time. The space is what the BEM package manager already uses.
     */
    static synchronized boolean publishVmd(java.io.File file) {
        if (remoteService == null || file == null || !file.isFile()) return false;
        try (ParcelFileDescriptor descriptor = remoteService.openRemoteFile(ModuleSettings.VMD_REMOTE_NAME);
             java.io.FileInputStream in = new java.io.FileInputStream(file);
             FileOutputStream out = new FileOutputStream(descriptor.getFileDescriptor())) {
            out.getChannel().truncate(0);
            byte[] buffer = new byte[65536];
            long total = 0;
            for (int read = in.read(buffer); read > 0; read = in.read(buffer)) {
                out.write(buffer, 0, read);
                total += read;
            }
            out.getFD().sync();
            if (total <= 0) return false;
            // The game side sizes its copy from the value stamped into the
            // settings snapshot; an empty or failed publish must not leave that
            // stamp claiming a payload that is not there.
            return true;
        } catch (Exception error) {
            Log.e("BetterEndfield.Camera", "VMD publish failed", error);
            return false;
        }
    }

    /** Drops the published slot so a cleared import cannot be picked up again. */
    static synchronized boolean removeVmd() {
        if (remoteService == null) return false;
        try {
            if (remoteService.deleteRemoteFile(ModuleSettings.VMD_REMOTE_NAME)) return true;
            return !java.util.Arrays.asList(remoteService.listRemoteFiles())
                    .contains(ModuleSettings.VMD_REMOTE_NAME);
        } catch (RuntimeException error) {
            Log.e("BetterEndfield.Camera", "VMD slot removal failed", error);
            return false;
        }
    }

    /**
     * Publishes one MMD slot into the framework's remote file space.
     *
     * The remote space is the only channel between the two processes, and the
     * native director takes a path, so the game process copies the slot out of
     * here into its own files directory on startup - the same arrangement the VMD
     * camera slot already uses. One live file per slot, so the name is fixed
     * rather than generated.
     *
     * A refused or short write returns false and the slot stays unreferenced: the
     * import is recorded only after this returns true, and a path in the
     * configuration that points at a file which is not there is worse than no
     * path at all.
     */
    static synchronized boolean publishMmdSlot(java.io.File file, String remote) {
        if (remoteService == null || file == null || !file.isFile()
                || remote == null || !remote.matches("mmd\\.[a-z]{3,16}")) return false;
        try (ParcelFileDescriptor descriptor = remoteService.openRemoteFile(remote);
             java.io.FileInputStream in = new java.io.FileInputStream(file);
             FileOutputStream out = new FileOutputStream(descriptor.getFileDescriptor())) {
            out.getChannel().truncate(0);
            byte[] buffer = new byte[65536];
            long total = 0;
            for (int read = in.read(buffer); read > 0; read = in.read(buffer)) {
                total += read;
                if (total > ModuleSettings.mmdSlotMaximumBytes()) {
                    Log.e("BetterEndfield.Mmd", "MMD slot exceeds the loader ceiling: " + remote);
                    return false;
                }
                out.write(buffer, 0, read);
            }
            out.getFD().sync();
            if (total <= 0) return false;
            return true;
        } catch (Exception error) {
            Log.e("BetterEndfield.Mmd", "MMD slot publish failed: " + remote, error);
            return false;
        }
    }

    /** Drops a published MMD slot. */
    static synchronized boolean removeMmdSlot(String remote) {
        if (remoteService == null || remote == null || !remote.matches("mmd\\.[a-z]{3,16}")) {
            return false;
        }
        try {
            if (remoteService.deleteRemoteFile(remote)) return true;
            return !java.util.Arrays.asList(remoteService.listRemoteFiles()).contains(remote);
        } catch (RuntimeException error) {
            Log.e("BetterEndfield.Mmd", "MMD slot removal failed: " + remote, error);
            return false;
        }
    }

    /**
     * Whether a name is one this app publishes a work file under.
     *
     * A work file's remote name is {@code mmd-<generation>-<file>}, and the
     * generation is exactly 36 characters wide because that is what a UUID
     * stringifies to. Splitting on the first hyphen after the prefix would
     * instead cut inside the UUID, which contains four of them - so the
     * generation is taken as a fixed-width field and the separator position is
     * checked rather than searched for.
     *
     * This is the one place the shape is defined: the publisher, the remover and
     * the game-side reader all go through it, so a work whose name was mangled
     * on the way in is refused instead of being written under a name nothing
     * will ever look up.
     */


    /**
     * Publishes one file of a library work into the framework's remote space.
     *
     * Identical in mechanism to the loose slot beside it - same channel, same
     * one-file-per-name rule - but the payload is a file inside a work folder
     * rather than a whole playback, so the ceiling and the name shape differ.
     * The declared length is what the game side sizes its copy from, so a short
     * write has to fail here rather than leave a stamp claiming bytes that were
     * never sent.
     */
    static synchronized boolean publishMmdWork(java.io.File file, String remote) {
        if (remoteService == null || file == null || !file.isFile() || !validMmdRemote(remote)) return false;
        try (ParcelFileDescriptor descriptor = remoteService.openRemoteFile(remote);
             java.io.FileInputStream in = new java.io.FileInputStream(file);
             FileOutputStream out = new FileOutputStream(descriptor.getFileDescriptor())) {
            out.getChannel().truncate(0);
            byte[] buffer = new byte[65536];
            long total = 0;
            for (int read = in.read(buffer); read > 0; read = in.read(buffer)) {
                total += read;
                if (total > ModuleSettings.mmdWorkMaximumBytes()) {
                    Log.e("BetterEndfield.Mmd", "MMD work file exceeds the ceiling: " + remote);
                    return false;
                }
                out.write(buffer, 0, read);
            }
            out.getFD().sync();
            return total > 0;
        } catch (Exception error) {
            Log.e("BetterEndfield.Mmd", "MMD work publish failed: " + remote, error);
            return false;
        }
    }

    /** Drops one published work file, so a removed work cannot be rebuilt from stale bytes. */
    static synchronized boolean removeMmdWork(String remote) {
        if (remoteService == null || !validMmdRemote(remote)) return false;
        try {
            if (remoteService.deleteRemoteFile(remote)) return true;
            return !java.util.Arrays.asList(remoteService.listRemoteFiles()).contains(remote);
        } catch (RuntimeException error) {
            Log.e("BetterEndfield.Mmd", "MMD work removal failed: " + remote, error);
            return false;
        }
    }

    static synchronized boolean isConnected() { return remoteService != null; }

    static void awaitConnection() throws java.io.IOException {
        if (!FrameworkServiceWait.await(FrameworkSettings.class, () -> remoteService != null,
                10_000, BemInstaller::checkpoint))
            throw new java.io.IOException(Text.of("框架服务未连接；请确认已在框架中启用模块后重试"));
    }

    static synchronized boolean removeBem(String name) {
        if(remoteService==null || !name.matches("bem-[a-f0-9-]{36}\\.bem")) return false;
        try {
            if(remoteService.deleteRemoteFile(name)) return true;
            return !java.util.Arrays.asList(remoteService.listRemoteFiles()).contains(name);
        } catch(RuntimeException error) {Log.e("BetterEndfieldNext.Install","Removing shared package failed",error);return false;}
    }
    /** A failed listing must not be mistaken for an empty remote store during cleanup. */
    static synchronized String[] listBem() throws java.io.IOException {
        return checkedBemNames(remoteService!=null,()->remoteService.listRemoteFiles());
    }
    static String[] checkedBemNames(boolean connected,java.util.function.Supplier<String[]> source) throws java.io.IOException {
        if(!connected) throw new java.io.IOException("框架服务未连接");
        try {
            String[] listed=source.get();
            if(listed==null) throw new java.io.IOException("框架模型文件列表不可用");
            java.util.ArrayList<String> names=new java.util.ArrayList<>();
            for(String name:listed)
                if(name!=null && name.matches("bem-[a-f0-9-]{36}\\.bem")) names.add(name);
            return names.toArray(new String[0]);
        } catch(RuntimeException error) {
            Log.e("BetterEndfield.Install","Listing shared packages failed",error);
            throw new java.io.IOException("框架模型文件列举失败",error);
        }
    }
    /** A missing package is -1; opening a remote name directly may create an empty file. */
    static synchronized long bemSize(String name) {
        if(remoteService==null || !name.matches("bem-[a-f0-9-]{36}\\.bem")) return -1;
        try {
            if(!java.util.Arrays.asList(remoteService.listRemoteFiles()).contains(name)) return -1;
            try(ParcelFileDescriptor descriptor=remoteService.openRemoteFile(name)) {return descriptor.getStatSize();}
        } catch(RuntimeException | java.io.IOException error) {return -1;}
    }
    static synchronized java.io.InputStream openBem(String name) throws java.io.IOException {
        if(bemSize(name)<=0) throw new java.io.IOException(Text.of("框架中缺少模型包文件，请重新导入"));
        try {return new ParcelFileDescriptor.AutoCloseInputStream(remoteService.openRemoteFile(name));}
        catch(RuntimeException error) {throw new java.io.IOException(Text.of("模型包读取失败：")+error.getMessage(),error);}
    }
    static synchronized void publishBem(java.io.File file,String name) throws java.io.IOException {
        if(remoteService==null) throw new java.io.IOException(Text.of("框架服务已断开，请重试"));
        if(!name.matches("bem-[a-f0-9-]+\\.bem") || !file.isFile())
            throw new java.io.IOException(Text.of("无效的模型包发布文件"));
        try(ParcelFileDescriptor descriptor=remoteService.openRemoteFile(name);
            java.io.FileInputStream in=new java.io.FileInputStream(file);
            FileOutputStream out=new FileOutputStream(descriptor.getFileDescriptor())) {
            out.getChannel().truncate(0);
            BemInstaller.copy(in,out,2L*1024*1024*1024);out.getFD().sync();
        } catch(java.io.IOException | RuntimeException error) {
            Log.e("BetterEndfieldNext.Install","Publishing failed",error);
            throw new java.io.IOException(Text.of("模型包发布失败：")+error.getMessage(),error);
        }
    }

    static boolean validMmdRemote(String name) {
        return name != null && name.length() <= 200
                && name.matches("mmd-[a-f0-9-]{36}-[A-Za-z0-9_.-]+");
    }
    static void publishMedia(java.io.File file, String name) throws java.io.IOException {
        if (!MediaScreenFiles.valid(name) || !file.isFile()) throw new java.io.IOException(Text.of("无效的媒体文件"));
        ParcelFileDescriptor descriptor;
        synchronized (FrameworkSettings.class) {
            if (remoteService == null) throw new java.io.IOException(Text.of("框架服务未连接"));
            descriptor = remoteService.openRemoteFile(name);
        }
        try (descriptor; java.io.FileInputStream input = new java.io.FileInputStream(file);
                FileOutputStream output = new FileOutputStream(descriptor.getFileDescriptor())) {
            output.getChannel().truncate(0); byte[] buffer = new byte[65536]; long total = 0; int count;
            while ((count = input.read(buffer)) != -1) { total += count; if (total > 512L * 1024 * 1024) throw new java.io.IOException(Text.of("媒体文件过大")); output.write(buffer, 0, count); }
            output.getFD().sync();
        }
    }
    static synchronized void removeMedia(String name) {
        if (remoteService != null && MediaScreenFiles.valid(name)) {
            try { remoteService.deleteRemoteFile(name); } catch (RuntimeException ignored) {}
        }
    }
    static synchronized boolean publishMmd(java.io.File file, String name) {
        if (remoteService == null || !validMmdRemote(name) || !file.isFile()) return false;
        try (ParcelFileDescriptor descriptor = remoteService.openRemoteFile(name);
             java.io.FileInputStream in = new java.io.FileInputStream(file);
             FileOutputStream out = new FileOutputStream(descriptor.getFileDescriptor())) {
            out.getChannel().truncate(0);
            byte[] buffer = new byte[65536]; long total = 0; int count;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > 512L * 1024 * 1024) throw new java.io.IOException(Text.of("作品文件过大"));
                out.write(buffer, 0, count);
            }
            out.getFD().sync(); return true;
        } catch (Exception error) {
            Log.e("BetterEndfieldNext.Mmd", "Publishing work failed", error); return false;
        }
    }
    static synchronized boolean removeMmd(String name) {
        if (remoteService == null || !validMmdRemote(name)) return false;
        try {
            return remoteService.deleteRemoteFile(name)
                    || !java.util.Arrays.asList(remoteService.listRemoteFiles()).contains(name);
        } catch (RuntimeException error) { return false; }
    }

    @SuppressWarnings("unchecked")
    private static synchronized void publish() {
        if (service == null || local == null) return;
        try {
            SharedPreferences remote = service.getRemotePreferences("module_settings");
            SharedPreferences.Editor edit = remote.edit().clear();
            for (Map.Entry<String, ?> entry : local.getAll().entrySet()) {
                String key = entry.getKey(); Object value = entry.getValue();
                if (value instanceof String) edit.putString(key, (String) value);
                else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) edit.putInt(key, (Integer) value);
                else if (value instanceof Long) edit.putLong(key, (Long) value);
                else if (value instanceof Float) edit.putFloat(key, (Float) value);
                else if (value instanceof Set<?>) edit.putStringSet(key, (Set<String>) value);
            }
            // The write token is the one value that must not travel in the UI
            // preference map: the overlay's settings channel admits a caller
            // outside this package only when it presents it, so publishing it
            // through the framework-protected snapshot is what keeps the channel
            // closed to every other app on the device.
            String authorization = OverlayWriteAuthorization.ownerToken();
            if (OverlayWritePolicy.validToken(authorization)) edit.putString(OverlayWriteAuthorization.PREFERENCE, authorization);
            else edit.remove(OverlayWriteAuthorization.PREFERENCE);
            edit.putInt("schemaVersion", 1);
            edit.putLong("generation", remote.getLong("generation", 0) + 1);
            if (!edit.commit()) Log.e("BetterEndfieldNext.Settings", "framework snapshot commit failed");
        } catch (RuntimeException error) {
            Log.e("BetterEndfieldNext.Settings", "framework snapshot unavailable; local settings retained", error);
        }
    }
}
