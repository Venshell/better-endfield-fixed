package dev.betterendfield.next;

import android.content.Context;
import android.system.Os;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Copies the imported MMD files where the native director can open them.
 *
 * The settings app publishes each slot into the framework's remote file space,
 * which is reachable through the LSPosed service and not as a path - and the
 * native loader takes a path, because {@code LoadVmdCamera} hands it straight to
 * {@code CreateFileW}, which on Android is a plain {@code open()}. So the game
 * process copies each slot once into its own files directory, from where the
 * camera configuration points at it through {@code %files%}.
 *
 * A copy is skipped when the target already has exactly the declared length.
 * That length is stamped by the settings app at import time, so a match means
 * this is already the current file; re-copying on every launch would be pure
 * start-up cost.
 *
 * The directory is shared with the library form of MMD - a work folder under
 * {@code BETTER_ENDFIELD_NEXT_MMD_ROOT} is one level below it - so pruning only ever
 * removes loose files, never a directory.
 */
final class MmdSlotFiles {

    private MmdSlotFiles() {}

    /**
     * @param game the game process's own context, which is what owns the target
     * @param encoded the slot record from the settings snapshot
     * @param source opens a file from the framework's remote space; the game
     *     process supplies it because only the module entry can reach the service
     * @return the absolute path of the directory the configuration's
     *     {@code %files%/betterendfield/mmd} form resolves to, or "" when it could
     *     not be created
     */
    static String prepare(Context game, String encoded, BemInstalledResources.Source source,
            Consumer<String> log) {
        File root = new File(game.getFilesDir(), ModuleSettings.MMD_SLOT_DIRECTORY);
        if (!root.isDirectory() && !root.mkdirs()) {
            log.accept("MMD directory could not be created: " + root);
            return "";
        }
        List<ModuleSettings.MmdSlot> slots = ModuleSettings.mmdSlots(encoded);
        Set<String> referenced = new HashSet<>();
        for (ModuleSettings.MmdSlot slot : slots) {
            String name = ModuleSettings.sanitizeMmdSlotName(slot.name(), slot.id());
            referenced.add(name);
            if (slot.bytes() <= 0 || slot.bytes() > ModuleSettings.mmdSlotMaximumBytes()) {
                log.accept("MMD slot " + slot.id() + " has an unusable declared size: " + slot.bytes());
                continue;
            }
            File target = new File(root, name);
            if (target.isFile() && target.length() == slot.bytes()) continue;
            File temporary = new File(root, name + ".tmp");
            try {
                try (InputStream in = source.open(slot.remote());
                     FileOutputStream out = new FileOutputStream(temporary, false)) {
                    byte[] buffer = new byte[64 * 1024];
                    long length = 0;
                    for (int read = in.read(buffer); read > 0; read = in.read(buffer)) {
                        length += read;
                        // The declared size is what the copy is checked against;
                        // the ceiling is checked as well so a corrupt stamp cannot
                        // make this fill the device.
                        if (length > ModuleSettings.mmdSlotMaximumBytes()) {
                            throw new IOException("MMD payload exceeds the loader ceiling");
                        }
                        out.write(buffer, 0, read);
                    }
                    if (length != slot.bytes()) {
                        throw new IOException("MMD payload is " + length
                                + " bytes, expected " + slot.bytes());
                    }
                    out.getFD().sync();
                }
                Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
                log.accept("MMD " + slot.id() + " ready: " + target
                        + " (" + slot.bytes() + " bytes)");
            } catch (Exception error) {
                log.accept("MMD " + slot.id() + " unavailable: " + error);
            } finally {
                temporary.delete();
            }
        }
        // Anything loose that no slot references is a leftover from an import
        // the user has since replaced or cleared - including a partial copy whose
        // temporary name survived a process death.
        if (!slots.isEmpty()) {
            File[] children = root.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isFile() && !referenced.contains(child.getName()) && child.delete()) {
                        log.accept("Unused MMD file removed: " + child.getName());
                    }
                }
            }
        }
        return root.getAbsolutePath();
    }

    /**
     * Materializes the installed library works beside the loose slots.
     *
     * A work is several files in a folder of its own, so it is copied as a unit
     * and published as a unit: the files go into a staging directory, and the
     * directory is renamed into place only when every file is present at its
     * declared length. The game scans the library for folders with a
     * {@code set.ini}, so a staging name is simply not a work - an interrupted
     * copy is invisible rather than half-playable, which is also why the visible
     * folder is dropped before the rename instead of being merged into.
     *
     * The declared length is checked in both directions. It is what the settings
     * app stamped when it published the file, so a short copy means the remote
     * space lost bytes or the publish never finished - either way the work must
     * not be advertised, because the alternative is the game failing to parse a
     * file for reasons it cannot report.
     *
     * Anything left under a UUID name that the index no longer lists is a work
     * the user removed, and it is deleted here rather than in the settings app:
     * the settings app cannot reach this directory at all, which is the same
     * reason the bytes had to travel through the framework in the first place.
     */
    static void prepareWorks(Context game, String index, BemInstalledResources.Source source,
            Consumer<String> log) {
        File root = new File(game.getFilesDir(), ModuleSettings.MMD_SLOT_DIRECTORY);
        if (!root.isDirectory() && !root.mkdirs()) {
            log.accept("MMD library directory could not be created: " + root);
            return;
        }
        Set<String> advertised = new HashSet<>();
        for (ModuleSettings.MmdWork work : ModuleSettings.mmdWorks(index)) {
            String generation = work.generation();
            File folder = new File(root, generation);
            File stage = new File(root, ".stage-" + generation);
            List<ModuleSettings.MmdWorkFile> files = work.files();
            try {
                // Two files is the floor: a work without a set.ini and at least
                // one referenced VMD has nothing to play. The ceiling keeps a
                // corrupt index from turning startup into an unbounded copy.
                if (files.size() < 2 || files.size() > 32) throw new IOException("作品文件数异常");
                boolean complete = folder.isDirectory();
                boolean set = false;
                Set<String> names = new HashSet<>();
                long total = 0;
                for (ModuleSettings.MmdWorkFile file : files) {
                    String name = file.name();
                    long size = file.bytes();
                    total += size;
                    if (!names.add(name) || !validPlainName(name)
                            || !FrameworkSettings.validMmdRemote(file.remote())) {
                        throw new IOException("作品条目无效：" + name);
                    }
                    if (size <= 0 || size > ModuleSettings.mmdWorkMaximumBytes()
                            || total > MmdLibraryFiles.MAX_TOTAL) {
                        throw new IOException("作品长度超限：" + name);
                    }
                    set |= "set.ini".equals(name);
                    File cached = new File(folder, name);
                    complete &= cached.isFile() && cached.length() == size;
                }
                if (!set) throw new IOException("作品缺少 set.ini");
                if (!complete) {
                    deleteOwned(stage);
                    if (!stage.mkdir()) throw new IOException("无法创建作品暂存目录");
                    for (ModuleSettings.MmdWorkFile file : files) {
                        File target = new File(stage, file.name());
                        long length = 0;
                        try (InputStream in = source.open(file.remote());
                             FileOutputStream out = new FileOutputStream(target)) {
                            byte[] buffer = new byte[65536];
                            for (int read = in.read(buffer); read > 0; read = in.read(buffer)) {
                                length += read;
                                if (length > file.bytes()) {
                                    throw new IOException("作品载荷超过声明长度：" + file.name());
                                }
                                out.write(buffer, 0, read);
                            }
                            if (length != file.bytes()) {
                                throw new IOException("作品载荷不完整：" + file.name());
                            }
                            out.getFD().sync();
                        }
                    }
                    deleteOwned(folder);
                    if (!stage.renameTo(folder)) throw new IOException("无法发布作品目录");
                    log.accept("MMD work ready: " + generation + " (" + work.name() + ")");
                }
                advertised.add(generation);
            } catch (Exception error) {
                log.accept("MMD work unavailable " + generation + ": " + error);
                deleteOwned(folder);
            } finally {
                deleteOwned(stage);
            }
        }
        File[] children = root.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && child.getName().matches("[a-f0-9-]{36}")
                        && !advertised.contains(child.getName())) {
                    deleteOwned(child);
                    log.accept("Unused MMD work removed: " + child.getName());
                }
            }
        }
        log.accept("MMD works prepared: " + advertised.size());
    }

    /** The single-name rule the index's file names have to satisfy to be usable as paths. */
    private static boolean validPlainName(String name) {
        if (name == null || name.isEmpty() || name.length() > 200) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32 || c == 127 || c == '/' || c == '\\' || c == ':') return false;
        }
        return true;
    }

    /** Recursively removes a directory this app created inside the game's own files. */
    private static void deleteOwned(File file) {
        if (java.nio.file.Files.isSymbolicLink(file.toPath())) {
            file.delete();
            return;
        }
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteOwned(child);
        file.delete();
    }
}
