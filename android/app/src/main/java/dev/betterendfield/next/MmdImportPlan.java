package dev.betterendfield.next;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns a directory of MMK material into the works it can offer.
 *
 * This is the step that makes the library form usable on a phone. A work is a
 * folder with a {@code set.ini} naming its files - the format the native loader
 * reads - but a phone user rarely receives material in that shape: they get a
 * zip of VMDs and a music track, sometimes several dancers in one folder, often
 * a {@code set.ini} written for the desktop install with paths that do not
 * survive the trip. So the plan does two things at once:
 *
 *  - every {@code set.ini} found becomes a work, with its slots resolved and
 *    checked against the files that are actually there;
 *  - material that no {@code set.ini} covers is grouped by folder into
 *    proposals, pairing a motion with the face, camera and music sitting beside
 *    it, so the common case needs no hand-written ini at all.
 *
 * Inspection never installs anything and never writes to the material; it
 * produces a proposal the page edits and the installer consumes. The split
 * matters because the user has to be able to see what was recognised - a
 * mispaired slot silently produces a dance with no camera, which looks like a
 * broken feature rather than a wrong choice.
 *
 * A work that cannot be played is a warning, not a failure: the rest of the
 * directory is still offered. Only a directory that yields nothing at all falls
 * back to a single empty proposal, so the page always has something to edit.
 */
final class MmdImportPlan {

    /** The slots a work may fill, in the order a {@code set.ini} writes them. */
    static final String[] SLOTS = {"motion", "face", "camera", "music", "motion2", "face2",
            "motion3", "face3", "motion4", "face4"};

    private static final long MAX_SET_BYTES = 16384;

    /** One file in the cache, with what its contents say it can be used for. */
    static final class Asset {
        final String path;
        final MmdVmdParser.Sections vmd;

        Asset(String path, MmdVmdParser.Sections vmd) {
            this.path = path;
            this.vmd = vmd;
        }

        /** Audio is whatever the parser could not read; everything else is a VMD. */
        boolean supports(String slot) {
            return vmd == null ? "music".equals(slot) : vmd.supports(slot);
        }

        /** The path with the import's own directory prefix stripped off. */
        String display() {
            return path.replaceFirst("^import-[a-f0-9-]{36}/", "");
        }

        String label() {
            return display() + (vmd == null ? " · 音乐" : " · " + vmd.label().trim());
        }
    }

    /** One proposed work: a display name and the slot-to-file mapping to install. */
    static final class Work {
        String name;
        final Map<String, String> slots = new LinkedHashMap<>();
        final Map<String, String> settings = new LinkedHashMap<>();

        Work(String name) {
            this.name = name;
        }
    }

    final File root;
    final List<Asset> assets = new ArrayList<>();
    final List<Work> works = new ArrayList<>();
    final List<String> warnings = new ArrayList<>();

    private MmdImportPlan(File root) {
        this.root = root;
    }

    /** Walks the cache and proposes works; never throws for bad material, only for a bad cache. */
    static MmdImportPlan inspect(File root) throws IOException {
        MmdImportPlan plan = new MmdImportPlan(root);
        List<String> paths = new ArrayList<>();
        scan(root, root, paths, 0, new MmdLibraryFiles.Budget());
        Collections.sort(paths);
        List<String> sets = new ArrayList<>();
        for (String path : paths) {
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".vmd")) {
                try {
                    plan.assets.add(new Asset(path, MmdVmdParser.read(MmdLibraryFiles.target(root, path))));
                } catch (IOException invalid) {
                    plan.warnings.add(path + "：" + invalid.getMessage());
                }
            } else if (lower.matches(".*\\.(mp3|wav|ogg|m4a|aac|flac|opus)")) {
                plan.assets.add(new Asset(path, null));
            } else if (base(path).equalsIgnoreCase("set.ini")) {
                sets.add(path);
            }
        }
        Set<String> covered = new HashSet<>();
        for (String set : sets) {
            try {
                String directory = parent(set);
                Map<String, String> settings = parseSet(decode(readSmall(MmdLibraryFiles.target(root, set))));
                Work work = new Work(settings.getOrDefault("name",
                        directory.isEmpty() ? "MMD" : base(directory)));
                work.settings.putAll(settings);
                for (String slot : SLOTS) {
                    String ref = settings.getOrDefault(slot, "");
                    if (ref.isEmpty()) continue;
                    String path = MmdLibraryFiles.path(ref);
                    path = directory.isEmpty() ? path : directory + "/" + path;
                    Asset asset = plan.asset(path);
                    if (asset == null || !asset.supports(slot)) {
                        throw new IOException(slot + " 引用缺失或类型不符：" + ref);
                    }
                    work.slots.put(slot, asset.path);
                }
                requirePlayable(work.slots);
                plan.works.add(work);
                covered.add(directory);
            } catch (IOException invalid) {
                plan.warnings.add(set + "：" + invalid.getMessage());
            }
        }
        List<Asset> unassigned = new ArrayList<>();
        for (Asset asset : plan.assets) {
            boolean inside = false;
            for (String directory : covered) {
                if (directory.isEmpty() || asset.path.startsWith(directory + "/")) inside = true;
            }
            if (!inside) unassigned.add(asset);
        }
        List<Asset> motions = new ArrayList<>();
        for (Asset asset : unassigned) if (asset.supports("motion")) motions.add(asset);
        if (motions.isEmpty()) {
            // No motion at all: a lone camera track is still a playback, so each
            // one becomes its own proposal rather than being dropped.
            for (Asset asset : unassigned) {
                if (!asset.supports("camera")) continue;
                Work work = new Work(stem(asset.path));
                work.slots.put("camera", asset.path);
                plan.unique(work, unassigned, "music", parent(asset.path));
                plan.works.add(work);
            }
        } else {
            for (Asset motion : motions) {
                Work work = new Work(stem(motion.path));
                work.slots.put("motion", motion.path);
                // Pairing only makes sense when the material sits in one folder:
                // with a single motion there is no ambiguity to resolve, so the
                // whole cache is in scope.
                String scope = motions.size() == 1 ? null : parent(motion.path);
                int peers = 0;
                for (Asset other : motions) if (parent(other.path).equals(parent(motion.path))) peers++;
                // A combined bone+morph file is also a valid face source.
                if (motion.supports("face")) work.slots.put("face", motion.path);
                else if (peers == 1) plan.unique(work, unassigned, "face", scope);
                if (peers == 1) {
                    plan.unique(work, unassigned, "camera", scope);
                    plan.unique(work, unassigned, "music", scope);
                }
                plan.works.add(work);
            }
        }
        if (plan.works.isEmpty()) plan.works.add(new Work("MMD"));
        if (plan.works.size() > 512) throw new IOException("识别作品超过 512 个");
        return plan;
    }

    /** Fills a slot only when exactly one unused file can serve it. */
    private void unique(Work work, List<Asset> assets, String slot, String scope) {
        Asset only = null;
        for (Asset asset : assets) {
            if (!asset.supports(slot) || (scope != null && !parent(asset.path).equals(scope))) continue;
            if (only != null) return;
            only = asset;
        }
        if (only != null) work.slots.put(slot, only.path);
    }

    Asset asset(String path) {
        for (Asset asset : assets) if (asset.path.equals(path)) return asset;
        return null;
    }

    /** Checks a proposal before it is installed, so a bad one costs an error message. */
    void validate(String name, Map<String, String> slots) throws IOException {
        safeValue(name);
        if (name.trim().isEmpty() || name.length() > 120) throw new IOException("请输入作品名（最多 120 字）");
        requirePlayable(slots);
        long total = 0;
        Set<String> used = new HashSet<>();
        for (String slot : SLOTS) {
            String path = slots.get(slot);
            if (path == null || path.isEmpty()) continue;
            Asset asset = asset(path);
            if (asset == null || !asset.supports(slot)) throw new IOException("资源类型不符：" + slot);
            File file = MmdLibraryFiles.target(root, path);
            if (!file.isFile() || file.length() <= 0 || file.length() > MmdLibraryFiles.limit(path)) {
                throw new IOException("资源缺失或长度异常：" + path);
            }
            // The same file may fill two slots (a motion that also carries the
            // face keys); it only counts once against the total.
            if (used.add(path)) total += file.length();
        }
        if (total > MmdLibraryFiles.MAX_TOTAL - MAX_SET_BYTES) throw new IOException("作品超过 1 GiB");
    }

    /**
     * Writes the {@code set.ini} the native loader will read.
     *
     * Resource paths are replaced by the installer's own flat file names, and
     * playback options carried over from a supplied ini are kept - only the
     * name and the slots are rewritten. That is what makes importing an existing
     * desktop work preserve its camera mode and audio offset instead of
     * resetting them.
     */
    byte[] generate(String name, Map<String, String> slots, Map<String, String> extra,
            Map<String, String> filenames) throws IOException {
        validate(name, slots);
        StringBuilder ini = new StringBuilder("name=").append(name.trim()).append('\n');
        for (String slot : SLOTS) {
            String path = slots.get(slot);
            if (path != null && !path.isEmpty()) {
                ini.append(slot).append('=').append(filenames.get(path)).append('\n');
            }
        }
        for (Map.Entry<String, String> option : extra.entrySet()) {
            if (option.getKey().equals("name") || Arrays.asList(SLOTS).contains(option.getKey())) continue;
            safeValue(option.getKey());
            safeValue(option.getValue());
            ini.append(option.getKey()).append('=').append(option.getValue()).append('\n');
        }
        byte[] bytes = ini.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SET_BYTES) throw new IOException("set.ini 过大");
        return bytes;
    }

    /**
     * Parses a {@code set.ini} the way the native loader does, plus the checks
     * the loader has no room for: a repeat of a key is refused rather than
     * letting the last write win, and a slot number outside 1-4 is refused here
     * because the loader would silently ignore it and the work would look
     * incomplete for no visible reason.
     */
    static Map<String, String> parseSet(String text) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String raw : text.replace("\uFEFF", "").split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#") || line.startsWith("[")) continue;
            int equals = line.indexOf('=');
            if (equals < 0) continue;
            String key = line.substring(0, equals).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(equals + 1).trim();
            if (key.matches("motion[0-9]+|face[0-9]+") && !key.matches("motion[2-4]|face[2-4]")) {
                throw new IOException("仅支持动作/表情 1–4");
            }
            safeValue(key);
            safeValue(value);
            if (values.put(key, value) != null) throw new IOException("重复字段：" + key);
        }
        return values;
    }

    private static void requirePlayable(Map<String, String> slots) throws IOException {
        if (slots.getOrDefault("motion", "").isEmpty() && slots.getOrDefault("camera", "").isEmpty()) {
            throw new IOException("请选择动作或镜头");
        }
    }

    /** A value has to survive a line in a UTF-8 ini; control characters would not. */
    private static void safeValue(String value) throws IOException {
        if (value == null) throw new IOException("缺少配置值");
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < 32 || value.charAt(i) == 127) throw new IOException("配置值包含控制字符");
        }
    }

    private static void scan(File root, File directory, List<String> paths, int depth,
            MmdLibraryFiles.Budget budget) throws IOException {
        if (depth > MmdLibraryFiles.MAX_DEPTH) throw new IOException("目录层级过深");
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("无法读取缓存资料");
        for (File file : files) {
            if (java.nio.file.Files.isSymbolicLink(file.toPath())) throw new IOException("不支持符号链接");
            String path = root.toPath().relativize(file.toPath()).toString().replace('\\', '/');
            MmdLibraryFiles.path(path);
            budget.entry(path);
            if (file.isDirectory()) {
                scan(root, file, paths, depth + 1, budget);
            } else {
                budget.bytes += file.length();
                if (budget.bytes > MmdLibraryFiles.MAX_TOTAL || file.length() > MmdLibraryFiles.limit(path)) {
                    throw new IOException("资料长度超限：" + path);
                }
                paths.add(path);
            }
        }
    }

    static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    static String base(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    static String stem(String path) {
        String name = base(path);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static byte[] readSmall(File file) throws IOException {
        if (file.length() > MAX_SET_BYTES) throw new IOException("set.ini 过大");
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > MAX_SET_BYTES) throw new IOException("set.ini 过大");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    /**
     * Decodes an ini, falling back to GBK.
     *
     * Material circulated among Chinese users is routinely still GBK, and a
     * strict UTF-8 failure would reject an otherwise perfect work over its
     * display name.
     */
    private static String decode(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalid) {
            return Charset.forName("GBK").newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        }
    }
}
