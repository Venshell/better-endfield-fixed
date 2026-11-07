package dev.betterendfield.next;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/** Owns only the game's private, content-addressed packaged headwear directory. */
public final class HeadwearAssetStore {
    public interface AssetSource { InputStream open(String name) throws IOException; }
    public static final class StoreException extends IOException {
        private static final long serialVersionUID = 1L;
        public final String stage;
        StoreException(String stage, String reason, Throwable cause) {
            super(reason, cause); this.stage = stage;
        }
    }
    private record Entry(String name, long size, String hash) {}
    private record Manifest(String id, List<Entry> entries) {}
    private static final long MAX_FILE = 16L * 1024 * 1024, MAX_TOTAL = 96L * 1024 * 1024;
    private static final int MAX_MANIFEST = 256 * 1024, MAX_ENTRIES = 898;
    private static final LinkOption[] NOFOLLOW = {LinkOption.NOFOLLOW_LINKS};
    private static final OutputStream DISCARD = new OutputStream() {
        @Override public void write(int value) {}
        @Override public void write(byte[] bytes, int offset, int length) {}
    };
    private HeadwearAssetStore() {}

    public static synchronized Path materialize(Path files, AssetSource source, long gameVersion)
            throws StoreException {
        String step = "manifest";
        Path stage = null, backup = null;
        Path owned = files.toAbsolutePath().normalize().resolve("betterendfield/headwear-assets");
        Path target = null;
        try {
            if (gameVersion != 61) throw new IOException("game version differs from packaged assets");
            Manifest manifest = parse(source);
            step = "owned-directory";
            ensureDirectory(files.toAbsolutePath().normalize());
            ensureDirectory(owned.getParent());
            ensureDirectory(owned);
            target = owned.resolve(manifest.id);
            step = "cache";
            if (valid(target, manifest)) {
                step = "cleanup"; cleanup(owned, target); return target;
            }
            step = "copy";
            stage = owned.resolve(".stage-" + UUID.randomUUID());
            Files.createDirectory(stage);
            for (Entry entry : manifest.entries) {
                try (InputStream input = source.open("headwear-v3/" + entry.name);
                     OutputStream output = Files.newOutputStream(stage.resolve(entry.name))) {
                    String actual = transfer(input, output, entry.size);
                    if (!actual.equals(entry.hash)) throw new IOException("asset digest differs: " + entry.name);
                }
            }
            if (!valid(stage, manifest)) throw new IOException("staged asset verification failed");
            step = "activate";
            if (Files.exists(target, NOFOLLOW)) {
                checkTree(owned, target);
                backup = owned.resolve(".backup-" + UUID.randomUUID());
                Files.move(target, backup, StandardCopyOption.ATOMIC_MOVE);
            }
            Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE);
            stage = null;
            step = "cleanup";
            cleanup(owned, target);
            backup = null;
            return target;
        } catch (IOException | RuntimeException failure) {
            try {
                if (backup != null && !Files.exists(target, NOFOLLOW))
                    Files.move(backup, target, StandardCopyOption.ATOMIC_MOVE);
                if (stage != null && Files.exists(stage, NOFOLLOW)) removeTree(owned, stage);
            } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw new StoreException(step, failure.getMessage(), failure);
        }
    }

    private static Manifest parse(AssetSource source) throws IOException {
        byte[] bytes;
        try (InputStream input = source.open("headwear-v3/manifest.tsv")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count == 0) throw new IOException("manifest stream made no progress");
                if (output.size() + count > MAX_MANIFEST) throw new IOException("manifest exceeds bound");
                output.write(buffer, 0, count);
            }
            bytes = output.toByteArray();
        }
        if (bytes.length > MAX_MANIFEST) throw new IOException("manifest exceeds bound");
        for (byte b : bytes) if (b < 0 || b == '\r' || b == 0) throw new IOException("manifest encoding invalid");
        String text = new String(bytes, StandardCharsets.UTF_8);
        int end = text.indexOf('\n');
        if (end < 0 || !text.endsWith("\n")) throw new IOException("manifest newline contract differs");
        String[] header = text.substring(0, end).split("\t", -1);
        if (header.length != 4 || !header[0].equals("BEHWASSETS") ||
                !header[1].equals("1") || !header[2].equals("61") || !hex(header[3]))
            throw new IOException("manifest version/header differs");
        byte[] rows = text.substring(end + 1).getBytes(StandardCharsets.UTF_8);
        if (!digest(rows).equals(header[3])) throw new IOException("manifest row digest differs");
        String[] lines = text.substring(end + 1).split("\n", -1);
        if (lines.length < 2 || lines.length - 1 > MAX_ENTRIES)
            throw new IOException("manifest entry count exceeds bound");
        List<Entry> entries = new ArrayList<>();
        long total = 0;
        String previous = "";
        for (int i = 0; i < lines.length - 1; i++) {
            String[] fields = lines[i].split("\t", -1);
            if (fields.length != 3 || !fields[0].matches("[A-Za-z0-9_]{1,160}\\.behw") ||
                    fields[0].compareTo(previous) <= 0 || !fields[1].matches("[1-9][0-9]{0,8}") || !hex(fields[2]))
                throw new IOException("manifest row identity/order invalid");
            long size = Long.parseLong(fields[1]);
            total += size;
            if (size > MAX_FILE || total > MAX_TOTAL) throw new IOException("manifest size exceeds bound");
            entries.add(new Entry(fields[0], size, fields[2]));
            previous = fields[0];
        }
        return new Manifest(header[3], entries);
    }

    private static boolean valid(Path directory, Manifest manifest) throws IOException {
        if (!Files.isDirectory(directory, NOFOLLOW)) return false;
        try (Stream<Path> list = Files.list(directory)) {
            if (list.limit(MAX_ENTRIES + 1L).count() != manifest.entries.size()) return false;
        }
        for (Entry entry : manifest.entries) {
            Path file = directory.resolve(entry.name);
            if (!Files.isRegularFile(file, NOFOLLOW) || Files.size(file) != entry.size) return false;
            try (InputStream input = Files.newInputStream(file)) {
                if (!transfer(input, DISCARD, entry.size).equals(entry.hash)) return false;
            }
        }
        return true;
    }

    private static String transfer(InputStream input, OutputStream output, long expected) throws IOException {
        MessageDigest hash = sha();
        byte[] buffer = new byte[65536];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count == 0) throw new IOException("asset stream made no progress");
            total += count;
            if (total > expected) throw new IOException("asset exceeds declared length");
            hash.update(buffer, 0, count); output.write(buffer, 0, count);
        }
        if (total != expected) throw new IOException("asset length differs");
        return format(hash.digest());
    }
    private static void ensureDirectory(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("owned directory is symlink");
        Files.createDirectories(path);
        if (!Files.isDirectory(path, NOFOLLOW)) throw new IOException("owned directory unavailable");
    }
    private static List<Path> checkTree(Path owned, Path target) throws IOException {
        if (!target.toAbsolutePath().normalize().startsWith(owned) || target.equals(owned))
            throw new IOException("cleanup path outside owned root");
        try (Stream<Path> tree = Files.walk(target)) {
            List<Path> paths = tree.limit(10001).collect(Collectors.toList());
            if (paths.size() > 10000) throw new IOException("cleanup tree exceeds bound");
            for (Path path : paths) if (Files.isSymbolicLink(path)) throw new IOException("cleanup symlink rejected");
            return paths;
        }
    }
    private static void removeTree(Path owned, Path target) throws IOException {
        List<Path> paths = new ArrayList<>(checkTree(owned, target));
        paths.sort(Comparator.reverseOrder());
        for (Path path : paths) Files.delete(path);
    }
    private static void cleanup(Path owned, Path current) throws IOException {
        List<Path> children;
        try (Stream<Path> list = Files.list(owned)) {
            children = list.limit(1025).collect(Collectors.toList());
        }
        if (children.size() > 1024) throw new IOException("owned cache count exceeds bound");
        for (Path child : children) {
            String name = child.getFileName().toString();
            if (!child.equals(current) && (hex(name) || name.matches("\\.(stage|backup)-[0-9a-f-]{36}")))
                removeTree(owned, child);
        }
    }
    private static boolean hex(String value) { return value.matches("[0-9a-f]{64}"); }
    private static MessageDigest sha() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String digest(byte[] bytes) { return format(sha().digest(bytes)); }
    private static String format(byte[] bytes) {
        StringBuilder result = new StringBuilder(64);
        for (byte b : bytes) result.append(Character.forDigit((b >>> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return result.toString();
    }
}
