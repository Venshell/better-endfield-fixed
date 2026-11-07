package dev.betterendfield.next;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import java.util.concurrent.atomic.AtomicReference;

/** Preview opens and closes in the settings process, where libxposed is absent. */
final class OverlayPreviewTest {
    static void run(Instrumentation instrumentation) {
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = instrumentation.startActivitySync(intent);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            instrumentation.runOnMainSync(() -> {
                try {
                    GameOverlay preview = new GameOverlay(activity, true);
                    preview.remove();
                } catch (Throwable error) {
                    failure.set(error);
                }
            });
            instrumentation.waitForIdleSync();
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
        if (failure.get() != null) {
            throw new AssertionError("overlay preview requires framework classes", failure.get());
        }
    }
}
