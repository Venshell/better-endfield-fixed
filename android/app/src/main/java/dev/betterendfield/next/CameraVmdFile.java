package dev.betterendfield.next;

import android.content.Context;
import android.system.Os;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Materializes the imported VMD camera motion where the native module can open
 * it.
 *
 * The settings app publishes the .vmd into the framework's remote file space,
 * which is reachable through the LSPosed service and not as a path - and the
 * native loader takes a path ({@code LoadVmdCamera} hands it straight to
 * {@code CreateFileW}, which on Android is a plain {@code open()}). So the game
 * process copies it once into its own files directory, and the configuration
 * points there through {@link ModuleSettings#VMD_FILE_SLOT}.
 *
 * The copy is skipped when the target already has exactly the published length:
 * that length is stamped by the settings app, so a match means this is already
 * the current file. Re-copying on every launch would be pure start-up cost, and
 * a .vmd is small but not free.
 */
final class CameraVmdFile {
    /** Target inside the game's own files directory; {@code %files%} expands to it. */
    private static final String SLOT = "betterendfield/camera/current.vmd";
    private static final String TEMPORARY = "current.vmd.tmp";

    private CameraVmdFile() {}

    /**
     * @param game the game process's own context, which is what owns the target
     * @param expectedBytes the published length, or 0 when nothing was imported
     * @param source opens a file from the framework's remote space; the game
     *     process supplies it because only the module entry can reach the service
     * @return the absolute path the configuration can use, or "" when the slot
     *     could not be filled
     */
    static String materialize(Context game, long expectedBytes,
            BemInstalledResources.Source source, Consumer<String> log) {
        if (expectedBytes <= 0 || expectedBytes > ModuleSettings.vmdMaximumBytes()) {
            log.accept("no VMD camera motion to publish (declared size " + expectedBytes + ")");
            return "";
        }
        File target = new File(game.getFilesDir(), SLOT);
        if (target.isFile() && target.length() == expectedBytes) {
            return target.getAbsolutePath();
        }
        File directory = target.getParentFile();
        if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) {
            log.accept("VMD directory could not be created: " + directory);
            return "";
        }
        File temporary = new File(directory, TEMPORARY);
        try {
            try (InputStream in = source.open(ModuleSettings.VMD_REMOTE_NAME);
                 FileOutputStream out = new FileOutputStream(temporary, false)) {
                byte[] buffer = new byte[64 * 1024];
                long length = 0;
                for (int read = in.read(buffer); read > 0; read = in.read(buffer)) {
                    length += read;
                    // The declared size is what the copy is checked against; the
                    // ceiling is checked as well so a corrupt stamp cannot make
                    // this allocate a runaway amount of storage.
                    if (length > ModuleSettings.vmdMaximumBytes()) {
                        throw new IOException("VMD payload exceeds 64 MiB");
                    }
                    out.write(buffer, 0, read);
                }
                if (length != expectedBytes) {
                    throw new IOException("VMD payload is " + length
                            + " bytes, expected " + expectedBytes);
                }
                out.getFD().sync();
            }
            Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
            log.accept("VMD camera motion ready: " + target + " (" + expectedBytes + " bytes)");
            return target.getAbsolutePath();
        } catch (Exception error) {
            log.accept("VMD camera motion unavailable: " + error);
            return "";
        } finally {
            temporary.delete();
        }
    }
}
