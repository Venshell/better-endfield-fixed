package dev.betterendfield.next;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Tests the untrusted archive boundary without loading third-party code. */
final class ThirdPartyModulePackageTest {
    static void run(BemInstallerTest test) throws Exception {
        File root = new File(test.getTargetContext().getCacheDir(),
                "third-party-package-test-" + System.nanoTime());
        if (!root.mkdirs()) throw new IOException("Cannot create test directory");
        try {
            Map<String, String> valid = new LinkedHashMap<>();
            valid.put("module.json", "{\"format\":1,\"abi\":1,\"id\":\"sample.echo\","
                    + "\"name\":\"Echo\",\"author\":\"Test\",\"version\":\"1\","
                    + "\"libraries\":{},\"ui\":\"ui/index.html\"}");
            valid.put("ui/index.html", "<!doctype html><title>Echo</title>");
            File validZip = zip(root, "valid.zip", valid);
            JSONObject manifest = ThirdPartyModulePackage.extract(validZip, new File(root, "valid"));
            require("sample.echo".equals(manifest.getString("id")), "valid module ID changed");
            require(ThirdPartyModulePackage.supported(manifest), "Android UI module rejected");

            Map<String, String> traversal = new LinkedHashMap<>(valid);
            traversal.put("../escape.txt", "outside");
            expectRejected(zip(root, "traversal.zip", traversal), new File(root, "traversal"));
            require(!new File(root, "escape.txt").exists(), "ZIP escaped its extraction root");

            Map<String, String> duplicate = new LinkedHashMap<>(valid);
            duplicate.put("ui/INDEX.HTML", "duplicate on case-insensitive storage");
            expectRejected(zip(root, "duplicate.zip", duplicate), new File(root, "duplicate"));

            Map<String, String> unsupported = new LinkedHashMap<>();
            unsupported.put("module.json", "{\"format\":1,\"abi\":1,\"id\":\"sample.windows\","
                    + "\"name\":\"Windows\",\"author\":\"Test\",\"version\":\"1\","
                    + "\"libraries\":{\"windows-x64\":\"native/sample.dll\"}}");
            unsupported.put("native/sample.dll", "placeholder");
            JSONObject windows = ThirdPartyModulePackage.extract(
                    zip(root, "windows.zip", unsupported), new File(root, "windows"));
            require(!ThirdPartyModulePackage.supported(windows), "Windows-only module accepted on Android");
        } finally {
            remove(root);
        }
    }

    private static File zip(File root, String name, Map<String, String> entries) throws IOException {
        File file = new File(root, name);
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return file;
    }

    private static void expectRejected(File archive, File directory) throws Exception {
        try {
            ThirdPartyModulePackage.extract(archive, directory);
            throw new AssertionError("Unsafe package was accepted: " + archive.getName());
        } catch (IOException expected) {
            // Rejection is the contract being tested.
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
