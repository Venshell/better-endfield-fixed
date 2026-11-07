package dev.betterendfield.next;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Reads a VMD file's section counts so the importer can tell what a file is
 * good for.
 *
 * The role of a VMD is a property of its contents, not of its name: the same
 * extension covers a body motion, a morph set and a camera track, and a file
 * usually carries more than one of them. Deciding by filename would classify a
 * perfectly usable motion as "not a motion" - so the importer opens the file
 * and counts the sections the native loader will read.
 *
 * This is the header-only walk {@code LoadVmdCamera} does before it commits to a
 * file: two generations of magic, a fixed-length model name, then four counted
 * sections. Sections after the camera block are optional, which is why a short
 * but valid motion is accepted rather than reported as truncated.
 */
final class MmdVmdParser {

    /** The native loader's ceiling, so an import is refused here instead of there. */
    static final long MAX_BYTES = 64L * 1024 * 1024;

    private static final String MAGIC_0002 = "Vocaloid Motion Data 0002";
    private static final String MAGIC_FILE = "Vocaloid Motion Data file";
    private static final String MAGIC_PLAIN = "Vocaloid Motion Data";

    private MmdVmdParser() {}

    /** What a VMD actually holds, counted rather than guessed. */
    static final class Sections {
        final long bones;
        final long morphs;
        final long cameras;

        Sections(long bones, long morphs, long cameras) {
            this.bones = bones;
            this.morphs = morphs;
            this.cameras = cameras;
        }

        /** Whether this file can fill the named slot. */
        boolean supports(String slot) {
            return slot.startsWith("motion") ? bones > 0
                    : slot.startsWith("face") ? morphs > 0
                    : "camera".equals(slot) && cameras > 0;
        }

        /** The short label the import page shows next to a candidate file. */
        String label() {
            return (bones > 0 ? "动作 " : "") + (morphs > 0 ? "表情 " : "")
                    + (cameras > 0 ? "镜头" : "");
        }
    }

    /** @throws IOException when the file is not a VMD, is too large, or is malformed */
    static Sections read(File file) throws IOException {
        if (file.length() > MAX_BYTES) throw new IOException("VMD 超过 64 MiB：" + file.getName());
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[30];
            in.readFully(header);
            // The magic is NUL-padded to a fixed width; compare the text before
            // the first NUL, not the whole field.
            int end = 0;
            while (end < header.length && header[end] != 0) ++end;
            String magic = new String(header, 0, end, StandardCharsets.US_ASCII);
            int modelBytes;
            if (magic.equals(MAGIC_0002)) modelBytes = 20;
            else if (magic.equals(MAGIC_FILE) || magic.equals(MAGIC_PLAIN)) modelBytes = 10;
            else throw new IOException("无效 VMD：" + file.getName());
            skip(in, modelBytes);
            long bones = section(in, 111, false);
            long morphs = section(in, 23, true);
            long cameras = section(in, 61, true);
            section(in, 28, true); // lights
            section(in, 9, true);  // shadows
            // The display/IK block is the only trailing part with a
            // self-describing length; walk it so a file with a torn tail is
            // rejected here rather than handed to the native loader.
            if (in.getFilePointer() < in.length()) {
                long displays = count(in);
                if (displays > (in.length() - in.getFilePointer()) / 9) {
                    throw new IOException("VMD IK 长度异常");
                }
                for (long i = 0; i < displays; i++) {
                    skip(in, 5);
                    long ik = count(in);
                    skip(in, ik * 21);
                }
            }
            if (in.getFilePointer() != in.length()) throw new IOException("VMD 尾部长度异常");
            if (bones == 0 && morphs == 0 && cameras == 0) {
                throw new IOException("VMD 没有动作、表情或镜头");
            }
            return new Sections(bones, morphs, cameras);
        }
    }

    /** One counted section: a big-endian count followed by fixed-size records. */
    private static long section(RandomAccessFile in, int bytes, boolean optional) throws IOException {
        if (optional && in.getFilePointer() == in.length()) return 0;
        long count = count(in);
        skip(in, count * bytes);
        return count;
    }

    private static long count(RandomAccessFile in) throws IOException {
        return Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
    }

    private static void skip(RandomAccessFile in, long bytes) throws IOException {
        if (bytes < 0 || bytes > in.length() - in.getFilePointer()) {
            throw new IOException("VMD 节长度异常");
        }
        in.seek(in.getFilePointer() + bytes);
    }
}
