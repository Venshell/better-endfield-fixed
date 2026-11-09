package dev.betterendfield.next;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Bounded byte-preserving copy for both raw BEM and ZIP-container packages. */
final class BemPackageCopy {
    enum Reason { SIZE_LIMIT, SIZE_MISMATCH }
    static final class Failure extends IOException {
        final Reason reason;
        Failure(Reason reason) { super(reason.name()); this.reason = reason; }
    }
    interface Checkpoint { void check() throws IOException; }
    private BemPackageCopy() {}

    static void copyExact(InputStream input, OutputStream output, long expected, Checkpoint checkpoint)
            throws IOException {
        if (copy(input, output, expected, checkpoint) != expected)
            throw new Failure(Reason.SIZE_MISMATCH);
    }

    static long copy(InputStream input, OutputStream output, long limit, Checkpoint checkpoint)
            throws IOException {
        if (limit < 0) throw new IllegalArgumentException("limit");
        long total = 0;
        byte[] buffer = new byte[65536];
        while (true) {
            checkpoint.check();
            int count = input.read(buffer);
            if (count < 0) break;
            if (count == 0) {
                int value = input.read();
                if (value < 0) break;
                buffer[0] = (byte) value;
                count = 1;
            }
            if (count > limit - total) throw new Failure(Reason.SIZE_LIMIT);
            checkpoint.check();
            output.write(buffer, 0, count);
            total += count;
        }
        checkpoint.check();
        return total;
    }
}
