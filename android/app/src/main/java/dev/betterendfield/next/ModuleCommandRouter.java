package dev.betterendfield.next;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Atomic, versioned command drop for the game process. Native consumes it on its Unity pump. */
final class ModuleCommandRouter {
    /**
     * The native pump rejects a payload over 4096 bytes ({@code
     * SubmitRuntimeCommand}), measured on the wire as UTF-8. The header this
     * class prepends is "BE_COMMAND_V1\n<generation>\n<command>\n" plus a
     * trailing newline, which is under 70 bytes, so the body is checked against
     * the whole payload rather than against a limit of its own: a camera
     * configuration is roughly 2 KB and has to fit, while a rule of thumb such
     * as "512 characters" would have refused it.
     */
    private static final int MAXIMUM_PAYLOAD_BYTES = 4096;

    private ModuleCommandRouter() {}

    static boolean issue(Context context, String command, String value) {
        if (command == null || !command.matches("[a-z][a-z0-9_]{1,31}") || value == null
                || value.length() > 512) return false;
        File directory = new File(context.getFilesDir(), "betterendfieldnext/commands");
        if (!directory.isDirectory() && !directory.mkdirs()) return false;
        // Persist the generation across service restarts.  The native pump
        // rejects stale generations, and an in-memory counter would reset to
        // zero whenever Android recreates the overlay service.
        long generation = ModuleSettings.nextCommandGeneration(context);
        String payload = "BE_COMMAND_V1\n" + generation + "\n" + command + "\n" + value + "\n";
        if (payload.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_PAYLOAD_BYTES) return false;
        if (FrameworkSettings.writeRemoteCommand(payload)) return true;
        File temporary = new File(directory, "next.tmp");
        File target = new File(directory, "next.command");
        try (FileOutputStream stream = new FileOutputStream(temporary, false)) {
            stream.write(payload.getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        } catch (Exception error) {
            // The overlay must never crash the host app when its command path is unavailable.
            return false;
        }
        return temporary.renameTo(target);
    }

    static String readStatus() { return FrameworkSettings.readRemoteStatus(); }
}
