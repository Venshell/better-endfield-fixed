import dev.betterendfield.next.HeadwearAssetStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/** Run with java -ea after compiling with the pure Java store. */
public final class HeadwearAssetStoreTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    private static String hash(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder s = new StringBuilder();
        for (byte b : digest) s.append(String.format("%02x", b & 255));
        return s.toString();
    }
    private static final class Source implements HeadwearAssetStore.AssetSource {
        final Map<String, byte[]> files = new HashMap<>();
        int assetOpens;
        Source(String content) throws Exception {
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            files.put("headwear-v3/A.behw", data);
            manifest("A.behw\t" + data.length + "\t" + hash(data) + "\n");
        }
        void manifest(String rows) throws Exception {
            String header = "BEHWASSETS\t1\t61\t" + hash(rows.getBytes(StandardCharsets.UTF_8)) + "\n";
            files.put("headwear-v3/manifest.tsv", (header + rows).getBytes(StandardCharsets.UTF_8));
        }
        @Override public InputStream open(String name) throws IOException {
            if (!name.endsWith("manifest.tsv")) assetOpens++;
            byte[] bytes = files.get(name);
            if (bytes == null) throw new IOException("missing " + name);
            return new ByteArrayInputStream(bytes);
        }
    }
    private static void rejected(Path files, Source source, long version, String stage) throws Exception {
        try { HeadwearAssetStore.materialize(files, source, version); throw new AssertionError("accepted invalid source"); }
        catch (HeadwearAssetStore.StoreException error) { check(error.stage.equals(stage)); }
    }
    private static long childCount(Path path) throws Exception {
        try (var entries = Files.list(path)) { return entries.count(); }
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path files = Files.createTempDirectory(root, "headwear-store-test-");
        Path external = files.resolve("headwear-v3"); Files.createDirectory(external);
        Files.writeString(external.resolve("user.behw"), "external untouched");
        Source one = new Source("accepted one");
        Path current = HeadwearAssetStore.materialize(files, one, 61);
        check(Files.readString(current.resolve("A.behw")).equals("accepted one"));
        check(one.assetOpens == 1);
        check(HeadwearAssetStore.materialize(files, one, 61).equals(current));
        check(one.assetOpens == 1); // Cache verifies files without copying APK assets.
        byte[] corruptCache = Files.readAllBytes(current.resolve("A.behw"));
        corruptCache[0] ^= 1;
        Files.write(current.resolve("A.behw"), corruptCache);
        check(HeadwearAssetStore.materialize(files, one, 61).equals(current));
        check(Files.readString(current.resolve("A.behw")).equals("accepted one"));
        Source missing = new Source("new package"); missing.files.remove("headwear-v3/A.behw");
        rejected(files, missing, 61, "copy");
        check(Files.readString(current.resolve("A.behw")).equals("accepted one"));
        check(childCount(current.getParent()) == 1);
        Source corrupt = new Source("new package"); corrupt.files.put("headwear-v3/A.behw", "bad package".getBytes());
        rejected(files, corrupt, 61, "copy");
        check(Files.readString(current.resolve("A.behw")).equals("accepted one"));
        Source missingManifest = new Source("new package"); missingManifest.files.remove("headwear-v3/manifest.tsv");
        rejected(files, missingManifest, 61, "manifest");
        Source path = new Source("test"); path.manifest("../outside.behw\t4\t" + hash("test".getBytes()) + "\n");
        rejected(files, path, 61, "manifest");
        Source bounds = new Source("test"); bounds.manifest("A.behw\t16777217\t" + hash("test".getBytes()) + "\n");
        rejected(files, bounds, 61, "manifest");
        Source total = new Source("test"); StringBuilder oversized = new StringBuilder();
        for (int i = 0; i < 7; i++) oversized.append("A" + i + ".behw\t16777216\t" + hash("test".getBytes()) + "\n");
        total.manifest(oversized.toString()); rejected(files, total, 61, "manifest");
        Source count = new Source("test"); StringBuilder tooMany = new StringBuilder();
        for (int i = 0; i < 899; i++) tooMany.append(String.format("A%04d.behw\t1\t%s\n", i, hash("x".getBytes())));
        count.manifest(tooMany.toString()); rejected(files, count, 61, "manifest");
        Source unsorted = new Source("test"); unsorted.manifest("B.behw\t4\t" + hash("test".getBytes()) + "\nA.behw\t4\t" + hash("test".getBytes()) + "\n");
        rejected(files, unsorted, 61, "manifest");
        Source manifestCorrupt = new Source("test"); manifestCorrupt.files.get("headwear-v3/manifest.tsv")[20] ^= 1;
        rejected(files, manifestCorrupt, 61, "manifest");
        Source manifestVersion = new Source("test"); byte[] meta = manifestVersion.files.get("headwear-v3/manifest.tsv"); meta[13] = '5';
        rejected(files, manifestVersion, 61, "manifest");
        rejected(files, one, 51, "manifest");
        Source hugeManifest = new Source("test"); hugeManifest.files.put("headwear-v3/manifest.tsv", new byte[262145]);
        rejected(files, hugeManifest, 61, "manifest");
        Source truncated = new Source("different"); truncated.files.put("headwear-v3/A.behw", "d".getBytes());
        rejected(files, truncated, 61, "copy");
        Source extended = new Source("different"); extended.files.put("headwear-v3/A.behw", "different plus".getBytes());
        rejected(files, extended, 61, "copy");
        Source two = new Source("accepted two");
        Path updated = HeadwearAssetStore.materialize(files, two, 61);
        check(!updated.equals(current)); check(!Files.exists(current));
        check(childCount(updated.getParent()) == 1);
        check(Files.readString(external.resolve("user.behw")).equals("external untouched"));
        Path outside = files.resolve("outside"); Files.createDirectory(outside); Files.writeString(outside.resolve("keep"), "untouched");
        Path link = updated.getParent().resolve("0".repeat(64));
        boolean symlinks = true;
        try { Files.createSymbolicLink(link, outside); } catch (UnsupportedOperationException | IOException denied) { symlinks = false; }
        if (symlinks) {
            rejected(files, two, 61, "cleanup");
            check(Files.readString(outside.resolve("keep")).equals("untouched"));
            Files.delete(link);
            Files.delete(updated.resolve("A.behw"));
            Files.createSymbolicLink(updated.resolve("A.behw"), outside.resolve("keep"));
            rejected(files, two, 61, "activate");
            check(Files.readString(outside.resolve("keep")).equals("untouched"));
            check(childCount(updated.getParent()) == 1); // Rejected stage is cleaned.
            Files.delete(updated.resolve("A.behw"));
            HeadwearAssetStore.materialize(files, two, 61);
        }
        check(Files.readString(updated.resolve("A.behw")).equals("accepted two"));
        System.out.println("PASS " + checks + " checks; symlink=" + symlinks + "; artifacts=" + files);
    }
}
