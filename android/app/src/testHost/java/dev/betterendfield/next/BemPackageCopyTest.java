package dev.betterendfield.next;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** Adversarial provider reads: preserve container bytes, bound output, and allow cancellation. */
public final class BemPackageCopyTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("check " + checks);
    }
    public static void main(String[] args) throws Exception {
        byte[] zip = {'P', 'K', 3, 4, 0, 1, 2, 3, 4, 5};
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        InputStream shortReads = new ByteArrayInputStream(zip) {
            boolean zero = true;
            @Override public synchronized int read(byte[] buffer, int start, int length) {
                zero = !zero;
                return zero ? 0 : super.read(buffer, start, Math.min(2, length));
            }
        };
        check(BemPackageCopy.copy(shortReads, output, zip.length, () -> {}) == zip.length);
        check(Arrays.equals(zip, output.toByteArray()));
        output.reset();
        try {
            BemPackageCopy.copy(new ByteArrayInputStream(zip), output, zip.length - 1, () -> {});
            throw new AssertionError("Accepted oversized payload");
        } catch (BemPackageCopy.Failure expected) {
            check(expected.reason == BemPackageCopy.Reason.SIZE_LIMIT);
            check(output.size() <= zip.length - 1);
        }
        output.reset();
        int[] checkpoints = {0};
        try {
            BemPackageCopy.copy(new ByteArrayInputStream(zip), output, zip.length, () -> {
                if (++checkpoints[0] == 2) throw new IOException("cancelled");
            });
            throw new AssertionError("Ignored cancellation");
        } catch (IOException expected) {
            check(expected.getMessage().equals("cancelled"));
            check(output.size() == 0);
        }
        check(BemPackageCopy.copy(new ByteArrayInputStream(new byte[0]), output, 0, () -> {}) == 0);
        output.reset();
        try {
            BemPackageCopy.copyExact(new ByteArrayInputStream(zip), output, zip.length + 1, () -> {});
            throw new AssertionError("Accepted truncated installed source");
        } catch (BemPackageCopy.Failure expected) {
            check(expected.reason == BemPackageCopy.Reason.SIZE_MISMATCH);
        }
        output.reset();
        BemPackageCopy.copyExact(new ByteArrayInputStream(zip), output, zip.length, () -> {});
        check(Arrays.equals(zip, output.toByteArray()));
        System.out.println("BemPackageCopyTest: " + checks + " checks");
    }
}
