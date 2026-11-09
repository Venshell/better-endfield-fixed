package dev.betterendfield.next;

import android.os.RemoteException;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Narrow Shizuku UserService; its package allowlist is enforced in this process too. */
public final class GameControlUserService extends IGameControlService.Stub {
    private static final long COMMAND_TIMEOUT_SECONDS = 8;

    @Override public int forceStop(String packageName) throws RemoteException {
        if (!isSupportedPackage(packageName)) return -2;
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/am", "force-stop", packageName)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.to(new File("/dev/null")))
                    .start();
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(250, TimeUnit.MILLISECONDS)) process.destroyForcibly();
                return -3;
            }
            return process.exitValue();
        } catch (IOException error) {
            return -4;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            return -5;
        } catch (RuntimeException error) {
            if (process != null) process.destroyForcibly();
            return -4;
        }
    }

    private static boolean isSupportedPackage(String packageName) {
        return "com.hypergryph.endfield".equals(packageName)
                || "com.gryphline.endfield.gp".equals(packageName);
    }

    @Override public void destroy() {
        System.exit(0);
    }
}
