package dev.betterendfield.next;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The bounds and primitives a work import is built from.
 *
 * An import copies files out of a document tree the user granted access to, and
 * every one of those files is attacker-influenced in the only sense that
 * matters here: it comes from outside this process. So the sizes, the entry
 * count, the nesting depth and the shape of every path are all checked before a
 * byte is written, and the checks live in one place so the tree walk, the loose
 * slot import and the installer cannot drift apart.
 *
 * Archive extraction is owned by MmdImportArchive. The import session feeds
 * its extracted tree into the same planner and installer as a picked directory.
 */
final class MmdLibraryFiles {

    /** One work may not exceed this in total; also the cap on a selection. */
    static final long MAX_TOTAL = 1024L * 1024 * 1024;

    /** Audio is the only part with a separate ceiling - it is the bulky one. */
    static final long MAX_AUDIO = 512L * 1024 * 1024;

    static final int MAX_ENTRIES = 4096;
    static final int MAX_DEPTH = 16;

    private MmdLibraryFiles() {}

    /**
     * Running totals for one import.
     *
     * The duplicate check is case-insensitive because the game's own loader
     * runs on a filesystem where two names differing only in case are one file
     * - accepting both would let a work reference a file that overwrote another.
     */
    static final class Budget {
        int entries;
        long bytes;
        final Set<String> names = new HashSet<>();

        void entry(String path) throws IOException {
            if (++entries > MAX_ENTRIES) throw new IOException("文件数超过 4096");
            if (!names.add(path.toLowerCase(Locale.ROOT))) throw new IOException("重复路径：" + path);
        }
    }

    /**
     * Normalizes a relative path from a document tree, or refuses it.
     *
     * Everything the rest of the import does assumes {@code path} is a plain
     * relative name: it is joined onto the cache root, written into a
     * {@code set.ini}, and later used as a file name inside the game's
     * directory. A separator, a drive letter, a {@code ..} or a control
     * character would break one of those, so they are rejected rather than
     * escaped.
     */
    static String path(String raw) throws IOException {
        if (raw == null) throw new IOException("缺少文件名");
        String path = raw.replace('\\', '/');
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.isEmpty() || path.length() > 1024 || path.startsWith("/") || path.contains(":")) {
            throw new IOException("无效资源路径");
        }
        String[] parts = path.split("/", -1);
        if (parts.length > MAX_DEPTH) throw new IOException("目录层级超过 16");
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IOException("无效资源路径：" + path);
            }
            for (int i = 0; i < part.length(); i++) {
                if (part.charAt(i) < 32 || part.charAt(i) == 127) throw new IOException("无效文件名");
            }
        }
        return path;
    }

    /** Resolves a checked relative path under a root, refusing anything that escapes. */
    static File target(File root, String path) throws IOException {
        File file = new File(root, path).getCanonicalFile();
        if (!file.getPath().startsWith(root.getCanonicalPath() + File.separator)) {
            throw new IOException("资源路径越界");
        }
        return file;
    }

    /** The per-file ceiling for one relative path. */
    static long limit(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".vmd") ? MmdVmdParser.MAX_BYTES
                : lower.endsWith("set.ini") ? 16384 : MAX_AUDIO;
    }

    /**
     * Copies one stream to one file, refusing to exceed either ceiling.
     *
     * {@code expected} is the length the provider declared, or -1 when it
     * declared none: a declared length that does not come true is a truncation
     * or a lie, and both have to fail here rather than produce a work that the
     * game will refuse to load for reasons it cannot explain.
     */
    static void copy(InputStream in, File file, String path, long expected, Budget budget)
            throws IOException {
        if (expected < -1) throw new IOException("资源长度异常：" + path);
        if (expected >= 0 && (expected > limit(path) || expected > MAX_TOTAL - budget.bytes)) {
            throw new IOException("资源长度超限：" + path);
        }
        mkdir(file.getParentFile());
        long size = 0;
        try (FileOutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = in.read(buffer)) != -1) {
                size += count;
                budget.bytes += count;
                if (size > limit(path) || budget.bytes > MAX_TOTAL || (expected >= 0 && size > expected)) {
                    throw new IOException("资源长度超限：" + path);
                }
                out.write(buffer, 0, count);
            }
            if (expected >= 0 && size != expected) throw new IOException("资源长度异常：" + path);
            out.getFD().sync();
        }
    }

    static void mkdir(File file) throws IOException {
        if (!file.isDirectory() && !file.mkdirs()) throw new IOException("无法创建缓存目录");
    }

    /**
     * Recursively deletes a directory this app owns.
     *
     * A symlink is removed rather than followed: the staging directories are
     * private and nothing in this app creates links, but a link placed there by
     * other means must not turn a cleanup into a deletion outside the tree.
     */
    static void delete(File file) {
        if (java.nio.file.Files.isSymbolicLink(file.toPath())) {
            file.delete();
            return;
        }
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
}
