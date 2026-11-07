package dev.betterendfield.next;

import android.content.Context;
import android.content.ContextWrapper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ThirdPartyRuntimeMaterializerTest {
    static void run(Context base) throws Exception {
        File root = new File(base.getCacheDir(), "third-party-runtime-test-" + System.nanoTime());
        if (!root.mkdirs()) throw new IOException("Cannot create runtime test root");
        Context isolated = new ContextWrapper(base) {
            @Override public File getFilesDir() { return root; }
        };
        try {
            byte[] valid = archive("sample.echo", null);
            JSONObject input = index("sample.echo", valid.length);
            int[] opens = {0};
            String path = ThirdPartyRuntimeMaterializer.prepare(isolated, input.toString(), name -> {
                opens[0]++;
                return new ByteArrayInputStream(valid);
            }, message -> {});
            JSONObject record = read(path).getJSONArray("modules").getJSONObject(0);
            require(record.getBoolean("enabled"), "valid module disabled");
            require(new File(record.getString("directory"), "native/example.so").isFile(),
                    "private native generation missing");
            require("first".equals(record.getJSONObject("configuration").getString("label")),
                    "opaque configuration changed");
            ThirdPartyRuntimeMaterializer.prepare(isolated, input.toString(), name -> {
                opens[0]++;
                throw new IOException("immutable generation downloaded twice");
            }, message -> {});
            require(opens[0] == 1, "immutable generation downloaded twice");

            byte[] traversal = archive("sample.echo", "../escape.txt");
            JSONObject bad = read(ThirdPartyRuntimeMaterializer.prepare(isolated,
                    index("sample.echo", traversal.length).toString(),
                    name -> new ByteArrayInputStream(traversal), message -> {}));
            JSONObject failed = bad.getJSONArray("modules").getJSONObject(0);
            require(!failed.getBoolean("enabled") && failed.has("preparation_error"),
                    "traversal generation accepted");
            require(!new File(root, "escape.txt").exists(), "ZIP escaped private root");

            JSONObject mismatch = read(ThirdPartyRuntimeMaterializer.prepare(isolated,
                    index("other.echo", valid.length).toString(),
                    name -> new ByteArrayInputStream(valid), message -> {}));
            require(!mismatch.getJSONArray("modules").getJSONObject(0).getBoolean("enabled"),
                    "manifest identity mismatch accepted");
        } finally {
            remove(root);
        }
    }

    private static JSONObject index(String id, int archiveBytes) throws Exception {
        String generation = UUID.randomUUID().toString();
        JSONObject record = new JSONObject().put("id", id).put("generation", generation)
                .put("android_remote", "tpm-" + generation + ".zip")
                .put("archive_bytes", archiveBytes).put("enabled", true)
                .put("configuration", new JSONObject().put("label", "first"));
        return new JSONObject().put("schema", 1).put("port", 41021)
                .put("token", "test_local_only_private_token_12345678")
                .put("modules", new JSONArray().put(record));
    }

    private static byte[] archive(String id, String extra) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(zip, "module.json", new JSONObject().put("format", 1).put("abi", 1)
                    .put("id", id).put("libraries", new JSONObject()
                            .put("android-arm64", "native/example.so")).toString().getBytes(StandardCharsets.UTF_8));
            entry(zip, "native/example.so", new byte[]{1, 2, 3});
            if (extra != null) entry(zip, extra, new byte[]{4});
        }
        return bytes.toByteArray();
    }

    private static void entry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private static JSONObject read(String path) throws Exception {
        try (FileInputStream input = new FileInputStream(path);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void remove(File path) {
        File[] children = path.listFiles();
        if (children != null) for (File child : children) remove(child);
        path.delete();
    }
}
