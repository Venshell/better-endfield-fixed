package dev.betterendfield.next;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Installs an edited proposal as a work the game can play.
 *
 * A work is installed as an immutable generation, never edited in place. Every
 * file goes into a staging directory first, the directory is renamed into its
 * final name only once all of it is there, and the bytes are published to the
 * framework's remote space before the index names the work. Each step is the
 * ordering that makes an interrupted install harmless: the game scans for
 * directories, so a half-written one under a staging name is invisible, and an
 * index entry is only written once every file it lists can actually be fetched.
 *
 * The generation is a UUID and it is also the folder name. That is what makes a
 * re-import of the same material a replacement rather than an accumulation: the
 * old generation is withdrawn as a unit, and nothing that referenced it can
 * silently pick up a partial new one.
 *
 * Work is done on one background thread, and the class exposes the current step
 * as a string because the import can take minutes on a large work - a progress
 * line is the difference between "slow" and "hung".
 */
final class MmdLibraryInstaller {

    /** True while an install or removal is running; the page disables its buttons. */
    static volatile boolean busy;

    /** What the install is doing right now, for the page's status line. */
    static volatile String status = "";

    private static final long MAX_WORK_BYTES = MmdLibraryFiles.MAX_TOTAL;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private MmdLibraryInstaller() {}

    /**
     * Installs a proposal, publishing its files and then naming it in the index.
     *
     * @return false when another operation is already running or the proposal is
     *     no longer valid; the caller is expected to look at {@link #status} for
     *     the reason
     */
    static synchronized boolean start(Context context, MmdImportSession session, String name,
            Map<String, String> slots, Map<String, String> settings) {
        if (busy || session.busy || session.plan == null || session.cancelled) return false;
        Context app = context.getApplicationContext();
        MmdImportPlan plan = session.plan;
        Map<String, String> selected = new LinkedHashMap<>(slots);
        Map<String, String> options = new LinkedHashMap<>(settings);
        session.busy = true;
        try {
            return run(() -> {
                try {
                    install(app, session, plan, name, selected, options);
                    session.installed = true;
                } catch (Exception error) {
                    session.status = "导入失败：" + message(error);
                    throw error;
                } finally {
                    session.busy = false;
                    if (session.cancelled) session.dispose();
                }
            });
        } catch (RuntimeException error) {
            session.busy = false;
            throw error;
        }
    }

    /**
     * Removes a work: index first, bytes second.
     *
     * Withdrawing the index before deleting anything is what keeps a failed
     * cleanup from leaving the app claiming a work whose files are already
     * gone - a dangling entry would make the library show a work that cannot
     * play. The local copy and the published bytes are both dropped; leaving
     * either behind would let the work reappear on the next launch.
     */
    static synchronized boolean remove(Context context, String generation) {
        Context app = context.getApplicationContext();
        return run(() -> {
            List<ModuleSettings.MmdWork> previous = ModuleSettings.getMmdWorks(app);
            ModuleSettings.MmdWork removed = null;
            List<ModuleSettings.MmdWork> next = new ArrayList<>();
            for (ModuleSettings.MmdWork work : previous) {
                if (generation.equals(work.generation())) removed = work;
                else next.add(work);
            }
            if (removed == null) throw new IOException("作品已被移除，请刷新后重试");
            ModuleSettings.setMmdWorks(app, next);
            if (generation.equals(ModuleSettings.getMmdWork(app))) {
                ModuleSettings.setMmdWork(app, "");
            }
            boolean complete = true;
            for (ModuleSettings.MmdWorkFile file : removed.files()) {
                complete &= FrameworkSettings.removeMmdWork(file.remote());
            }
            complete &= deleteOwned(ownedFolder(app, generation));
            status = "作品已移除" + (complete ? "" : "；部分文件暂未清理，但该作品已停止发布。");
        });
    }

    /**
     * Re-publishes an installed work's bytes without touching the index.
     *
     * The local copy exists exactly for this: the framework service can be
     * disconnected or restarted, and the published files can be lost while the
     * work is still installed. Re-publishing from the local copy avoids asking
     * the user to select the source folder again for a work that never changed.
     */
    static synchronized boolean republish(Context context, String generation) {
        Context app = context.getApplicationContext();
        return run(() -> {
            ModuleSettings.MmdWork entry = null;
            for (ModuleSettings.MmdWork work : ModuleSettings.getMmdWorks(app)) {
                if (generation.equals(work.generation())) entry = work;
            }
            if (entry == null) throw new IOException("作品已被移除，请刷新后重试");
            File folder = ownedFolder(app, generation);
            List<ModuleSettings.MmdWorkFile> files = entry.files();
            for (int i = 0; i < files.size(); i++) {
                ModuleSettings.MmdWorkFile file = files.get(i);
                requirePlainName(file.name());
                File source = new File(folder, file.name());
                if (!source.isFile() || source.length() != file.bytes()) {
                    throw new IOException("本地作品文件缺失或已变化：" + file.name());
                }
                status = "正在发布 " + (i + 1) + "/" + files.size() + "：" + file.name();
                if (!FrameworkSettings.publishMmdWork(source, file.remote())) {
                    throw new IOException("框架服务未连接或发布失败，请启用模块后重试");
                }
            }
            status = "作品已重新发布";
        });
    }

    @FunctionalInterface
    private interface Operation {
        void run() throws Exception;
    }

    /**
     * Runs one operation on the shared worker, reporting why it could not start.
     *
     * The rejection path restores {@code busy} synchronously: the caller that
     * gets {@code false} has to see a usable state immediately, not after a
     * queued task that may never run.
     */
    private static boolean run(Operation operation) {
        if (busy) return false;
        busy = true;
        status = "正在准备作品文件…";
        try {
            WORKER.execute(() -> {
                try {
                    operation.run();
                } catch (Exception error) {
                    status = "操作未完成：" + message(error);
                    android.util.Log.e("BetterEndfield.Mmd", status, error);
                } finally {
                    busy = false;
                }
            });
            return true;
        } catch (RuntimeException rejected) {
            busy = false;
            throw rejected;
        }
    }

    private static void install(Context app, MmdImportSession session, MmdImportPlan plan,
            String name, Map<String, String> slots, Map<String, String> settings) throws Exception {
        if (ModuleSettings.getMmdWorks(app).size() >= 512) throw new IOException("作品库最多 512 个作品");
        plan.validate(name, slots);
        // One flat file name per source file, shared by every slot that uses it -
        // a motion that also supplies the face keys must not be copied twice, and
        // the name is generated rather than kept because the source name can
        // carry characters the game's loader will not open.
        Map<String, String> filenames = new LinkedHashMap<>();
        for (String slot : MmdImportPlan.SLOTS) {
            String path = slots.get(slot);
            if (path != null && !path.isEmpty() && !filenames.containsKey(path)) {
                String extension = "music".equals(slot) ? extension(path) : ".vmd";
                filenames.put(path, "f" + filenames.size() + extension);
            }
        }
        byte[] setBytes = plan.generate(name, slots, settings, filenames);
        String generation = UUID.randomUUID().toString();
        File root = new File(app.getFilesDir(), ModuleSettings.MMD_LIBRARY_DIRECTORY);
        MmdLibraryFiles.mkdir(root);
        File stage = new File(root, ".stage-" + generation);
        if (!stage.mkdir()) throw new IOException("无法创建临时作品目录");
        List<ModuleSettings.MmdWorkFile> files = new ArrayList<>();
        boolean advertised = false;
        try {
            long total = setBytes.length;
            try (FileOutputStream output = new FileOutputStream(new File(stage, "set.ini"))) {
                output.write(setBytes);
                output.getFD().sync();
            }
            files.add(new ModuleSettings.MmdWorkFile(
                    ModuleSettings.mmdWorkRemote(generation, "set.ini"), "set.ini", setBytes.length));
            for (Map.Entry<String, String> item : filenames.entrySet()) {
                String path = item.getKey();
                String filename = item.getValue();
                session.check("复制：" + path);
                status = session.status;
                File source = MmdLibraryFiles.target(plan.root, path);
                File destination = new File(stage, filename);
                long bytes;
                try (InputStream input = new FileInputStream(source);
                     FileOutputStream output = new FileOutputStream(destination)) {
                    bytes = copy(input, output, Math.min(MmdLibraryFiles.limit(path), MAX_WORK_BYTES - total),
                            session);
                    if (bytes != source.length() || bytes == 0) throw new IOException("资料长度异常：" + path);
                    output.getFD().sync();
                }
                // Re-read what was written, not what was proposed: the copy is
                // the only artifact the game will ever see, so a VMD that fails
                // to parse after the copy has to fail the install.
                if (filename.endsWith(".vmd")) {
                    MmdVmdParser.Sections sections = MmdVmdParser.read(destination);
                    for (Map.Entry<String, String> slot : slots.entrySet()) {
                        if (path.equals(slot.getValue()) && !sections.supports(slot.getKey())) {
                            throw new IOException("VMD 类型不符");
                        }
                    }
                }
                total += bytes;
                files.add(new ModuleSettings.MmdWorkFile(
                        ModuleSettings.mmdWorkRemote(generation, filename), filename, bytes));
            }
            session.check("正在发布…");
            File installed = ownedFolder(app, generation);
            if (!stage.renameTo(installed)) throw new IOException("无法发布本地作品目录");
            stage = installed;
            for (int i = 0; i < files.size(); i++) {
                ModuleSettings.MmdWorkFile file = files.get(i);
                session.check("发布：" + (i + 1) + "/" + files.size());
                status = session.status;
                if (!FrameworkSettings.publishMmdWork(new File(installed, file.name()), file.remote())) {
                    throw new IOException("框架服务未连接或发布失败");
                }
            }
            List<ModuleSettings.MmdWork> next = ModuleSettings.getMmdWorks(app);
            next.add(new ModuleSettings.MmdWork(generation, name.trim(), files));
            synchronized (session) {
                session.check("正在完成…");
                ModuleSettings.setMmdWorks(app, next);
                advertised = true;
                session.installed = true;
            }
            session.status = status = "已导入：" + name.trim();
        } finally {
            // A work that never made it into the index has to leave nothing
            // behind: the published bytes would be unreachable garbage and the
            // local copy would be a folder the game might still scan.
            if (!advertised) {
                for (ModuleSettings.MmdWorkFile file : files) {
                    FrameworkSettings.removeMmdWork(file.remote());
                }
                deleteOwned(stage);
            }
        }
    }

    private static String extension(String path) {
        String name = MmdImportPlan.base(path);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? ".audio" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    /**
     * A file name that is written into a {@code set.ini} and used as a path
     * inside the game's directory must be a single plain name: a separator or a
     * colon would escape the folder the work owns.
     */
    static void requirePlainName(String name) throws IOException {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                || name.getBytes(StandardCharsets.UTF_8).length > 200) {
            throw new IOException("资源必须是作品目录内的单层文件名");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32 || c == 127 || c == '/' || c == '\\' || c == ':') {
                throw new IOException("不支持子目录或绝对路径：" + name);
            }
        }
    }

    private static long copy(InputStream input, java.io.OutputStream output, long limit,
            MmdImportSession session) throws IOException {
        byte[] buffer = new byte[65536];
        long bytes = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            session.check(session.status);
            bytes += count;
            if (bytes > limit) throw new IOException("作品文件超过大小限制");
            output.write(buffer, 0, count);
        }
        return bytes;
    }

    /**
     * The work's own folder, proven to be inside this app's library directory.
     *
     * The generation reaches here from the index, which is stored text; a value
     * that is not a UUID is refused rather than resolved, so a corrupt index
     * cannot point a deletion at a path outside the library.
     */
    private static File ownedFolder(Context app, String generation) throws IOException {
        if (!generation.matches("[a-f0-9-]{36}")) throw new IOException("无效的作品目录编号");
        File root = new File(app.getFilesDir(), ModuleSettings.MMD_LIBRARY_DIRECTORY).getCanonicalFile();
        File result = new File(root, generation).getCanonicalFile();
        if (!root.equals(result.getParentFile())) throw new IOException("作品目录超出应用存储范围");
        return result;
    }

    /**
     * Deletes a work folder this app owns, one level deep.
     *
     * A work folder holds only plain files - the installer never creates a
     * subdirectory inside it - so this refuses to recurse: a surprise directory
     * would mean something else put it there, and deleting it blindly is how a
     * cleanup turns into a deletion of data nobody meant to touch.
     */
    private static boolean deleteOwned(File folder) {
        boolean complete = true;
        File[] children = folder.listFiles();
        if (children != null) {
            for (File file : children) complete &= file.delete();
        }
        return folder.delete() && complete;
    }

    private static String message(Exception error) {
        if (error instanceof SecurityException) return "目录读取授权失效，请重新选择。";
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
