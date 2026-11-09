package dev.betterendfield.next;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.json.JSONArray;

/** Pins an installed generation in the requesting editor's owned directory. */
final class BemInstalledSource {
    private BemInstalledSource() {}

    static File materialize(Context context, String generation, File directory,
            BemPackageCopy.Checkpoint checkpoint) throws IOException {
        if (generation == null || !generation.matches("[a-f0-9-]{36}"))
            throw new IOException(context.getString(R.string.next_bem_invalid_generation));
        org.json.JSONObject selected = null;
        JSONArray entries = BemInstaller.index(context);
        for (int i = 0; i < entries.length(); i++) {
            org.json.JSONObject entry = entries.optJSONObject(i);
            if (entry == null) throw new IOException(context.getString(R.string.next_bem_source_size_mismatch));
            if (generation.equals(entry.optString("generation"))) selected = entry;
        }
        if (selected == null) throw new IOException(context.getString(R.string.next_bem_generation_changed));
        long expected = selected.optLong("bytes", -1);
        if (expected <= 0 || expected > BemImportStream.MAX_BYTES)
            throw new IOException(context.getString(R.string.next_bem_source_size_mismatch));
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException(context.getString(R.string.next_bem_stage_failed));
        File pinned = new File(directory, generation + ".bem");
        if (pinned.isFile() && pinned.length() == expected) return pinned;
        if (pinned.exists() && !pinned.delete())
            throw new IOException(context.getString(R.string.next_bem_source_size_mismatch));
        File local = new File(new File(new File(context.getFilesDir(), "bem-installed"), generation), "installed.bem");
        boolean localReady = local.isFile() && local.length() == expected;
        File temporary = new File(directory, generation + ".tmp");
        try {
            if (!localReady && !FrameworkSettings.awaitConnection(checkpoint::check))
                throw new IOException(context.getString(R.string.next_bem_framework_unavailable));
            try (InputStream input = localReady ? new FileInputStream(local)
                    : FrameworkSettings.openBem("bem-" + generation + ".bem");
                    FileOutputStream output = new FileOutputStream(temporary)) {
                BemPackageCopy.copyExact(input, output, expected, checkpoint);
                output.getFD().sync();
            }
            checkpoint.check();
            if (!temporary.renameTo(pinned)) throw new IOException(context.getString(R.string.next_bem_stage_failed));
            return pinned;
        } catch (BemPackageCopy.Failure oversized) {
            throw new IOException(context.getString(R.string.next_bem_source_size_mismatch), oversized);
        } finally {
            if (temporary.exists() && !temporary.delete())
                android.util.Log.w("BetterEndfieldNext.Install", "Could not remove source staging file");
        }
    }
}
