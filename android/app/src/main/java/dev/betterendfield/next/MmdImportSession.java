package dev.betterendfield.next;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Holds one import in progress, owned by the application rather than by a screen.
 *
 * This exists because the work happens in two places that cannot hand a value to
 * each other: the document picker returns a {@link Uri} whose grant is tied to
 * the activity that asked for it, and the analysis runs off the main thread so a
 * large folder does not freeze the page. So the session copies the selection
 * into a private cache directory the moment it is granted, and everything after
 * that reads and writes plain files under an id the session owns. An activity
 * that is recreated finds the same session by id and does not need the original
 * picker result.
 *
 * The grant is released as soon as the copy is done. Holding a persisted
 * permission would let this app read a folder again long after the import - a
 * capability the feature does not need and the user never granted for that
 * purpose.
 */
final class MmdImportSession {

    private static final Map<String, MmdImportSession> SESSIONS = new ConcurrentHashMap<>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    final String id;
    final File directory;

    /** The recognised proposal, or null while the cache is being read. */
    volatile MmdImportPlan plan;
    volatile boolean busy;
    volatile boolean cancelled;
    volatile boolean installed;
    volatile String status = "";

    /** Bumped whenever the plan or the status changes, so the page can poll it. */
    volatile int revision;

    int selected;

    private MmdImportSession(Context app, String id) {
        this.id = id;
        this.directory = new File(new File(app.getCacheDir(), "mmd-import"), id);
    }

    /**
     * Opens the session for an id, or starts a new one.
     *
     * The id comes from saved instance state, so an activity that was recreated
     * during an import resumes the same cache directory instead of starting a
     * second import of the same folder.
     */
    static MmdImportSession open(Context app, String saved) {
        String id = saved != null && saved.matches("[a-f0-9-]{36}") ? saved : UUID.randomUUID().toString();
        MmdImportSession session = SESSIONS.computeIfAbsent(id, key -> new MmdImportSession(app, key));
        if (session.plan == null && !session.busy && new File(session.directory, "ready").isFile()) {
            session.busy = true;
            WORKER.execute(() -> {
                try {
                    MmdImportPlan prepared = MmdImportPlan.inspect(new File(session.directory, "data"));
                    session.restoreEdits(prepared);
                    session.plan = prepared;
                    session.revision++;
                } catch (Exception error) {
                    session.status = "资料缓存失效，请重新选择";
                } finally {
                    session.busy = false;
                    if (session.cancelled) session.dispose();
                }
            });
        }
        return session;
    }

    /** The cancellation check every long step calls; the last one wins as the status. */
    void check(String text) throws IOException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("已取消");
        status = text;
    }

    /**
     * Copies a selection into the private cache and inspects it.
     *
     * @param tree a document tree (a folder) rather than a single document
     * @param slot when set, the file is a loose slot addition to the current
     *     proposal rather than a whole new selection
     */
    synchronized void prepare(Context context, Uri uri, int flags, boolean tree, String slot) {
        if (busy || cancelled) return;
        if (uri == null || !"content".equals(uri.getScheme()) || (tree && !DocumentsContract.isTreeUri(uri))) {
            status = "请选择文件或目录";
            return;
        }
        Context app = context.getApplicationContext();
        busy = true;
        status = "正在准备…";
        boolean owned = false;
        if ((flags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
            try {
                boolean existing = false;
                for (android.content.UriPermission permission : app.getContentResolver().getPersistedUriPermissions()) {
                    if (permission.getUri().equals(uri) && permission.isReadPermission()) existing = true;
                }
                if (!existing) {
                    app.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    owned = true;
                }
            } catch (SecurityException ignored) {
                // Not persistable is the normal case for a one-shot picker
                // result; the copy below still works with the granted read.
            }
        }
        final boolean release = owned;
        WORKER.execute(() -> {
            File incoming = new File(directory, ".incoming-" + UUID.randomUUID());
            try {
                MmdLibraryFiles.mkdir(incoming);
                MmdLibraryFiles.Budget budget = new MmdLibraryFiles.Budget();
                String filename = displayName(app, uri, tree);
                if (tree) {
                    Set<String> visited = new HashSet<>();
                    copyTree(app, uri, DocumentsContract.getTreeDocumentId(uri), incoming, "", budget,
                            visited, 0);
                } else {
                    String path = MmdLibraryFiles.path(filename);
                    if (path.contains("/")) throw new IOException("无效文件名");
                    if (slot == null) {
                        // Whole-file imports are archives. Keep the compressed input
                        // outside the inspected tree and use the existing bounded
                        // ZIP/7z owner, including its CRC and path checks.
                        File archive = new File(directory, ".archive-" + UUID.randomUUID());
                        try {
                            try (InputStream in = input(app, uri)) {
                                MmdImportArchive.copy(in, archive, "archive", declaredSize(app, uri),
                                        new MmdImportArchive.Budget(), this::check, null);
                            }
                            MmdImportArchive.extract(archive, incoming, this::check);
                        } finally {
                            archive.delete();
                        }
                    } else {
                        try (InputStream in = input(app, uri)) {
                            MmdLibraryFiles.copy(in, new File(incoming, path), path, declaredSize(app, uri), budget);
                        }
                    }
                }
                check("正在识别…");
                MmdImportPlan prepared = MmdImportPlan.inspect(incoming);
                if (slot != null) {
                    if (prepared.assets.size() != 1 || !prepared.assets.get(0).supports(slot)) {
                        throw new IOException("所选文件不含该槽需要的数据");
                    }
                    File data = new File(directory, "data");
                    MmdLibraryFiles.mkdir(data);
                    File added = new File(data, "import-" + UUID.randomUUID());
                    if (!incoming.renameTo(added)) throw new IOException("无法保存资料缓存");
                    try {
                        MmdImportPlan updated = MmdImportPlan.inspect(data);
                        if (plan != null) {
                            updated.works.clear();
                            updated.works.addAll(plan.works);
                        }
                        if (selected >= updated.works.size()) selected = 0;
                        updated.works.get(selected).slots.put(slot, added.getName() + "/" + prepared.assets.get(0).path);
                        plan = updated;
                    } catch (Exception error) {
                        MmdLibraryFiles.delete(added);
                        throw error;
                    }
                } else {
                    File data = new File(directory, "data");
                    File previous = new File(directory, ".previous-" + UUID.randomUUID());
                    if (data.exists() && !data.renameTo(previous)) throw new IOException("无法替换资料缓存");
                    if (!incoming.renameTo(data)) {
                        previous.renameTo(data);
                        throw new IOException("无法保存资料缓存");
                    }
                    MmdLibraryFiles.delete(previous);
                    plan = MmdImportPlan.inspect(data);
                    selected = 0;
                }
                try (FileOutputStream marker = new FileOutputStream(new File(directory, "ready"))) {
                    marker.write(1);
                }
                persistEdits(snapshot());
                status = "";
                revision++;
            } catch (Exception error) {
                status = cancelled ? "已取消" : "准备失败：" + (error instanceof SecurityException
                        ? "读取授权失效，请重新选择" : error.getMessage());
            } finally {
                MmdLibraryFiles.delete(incoming);
                if (release) {
                    try {
                        app.getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException ignored) {
                    }
                }
                busy = false;
                if (cancelled) dispose();
            }
        });
    }

    /**
     * Walks a document tree into the cache.
     *
     * A document provider is allowed to hand back the same document id twice, so
     * visited ids are tracked: without that, a provider with a cycle would make
     * this recurse until the depth limit and report a misleading "too deep"
     * rather than the real problem. The provider's declared size is treated as a
     * hint - it is used as an upper bound during the copy and as an equality
     * check at the end, so a provider that lies short fails the import instead of
     * producing a truncated file.
     */
    private void copyTree(Context app, Uri tree, String documentId, File root, String prefix,
            MmdLibraryFiles.Budget budget, Set<String> visited, int depth) throws IOException {
        if (depth > MmdLibraryFiles.MAX_DEPTH || !visited.add(documentId)) {
            throw new IOException("目录层级异常");
        }
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId);
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE};
        try (Cursor cursor = app.getContentResolver().query(children, columns, null, null, null)) {
            if (cursor == null) throw new IOException("无法列出目录");
            while (cursor.moveToNext()) {
                check("正在读取目录…");
                String name = MmdLibraryFiles.path(cursor.getString(1));
                if (name.contains("/")) throw new IOException("无效目录文件名");
                String path = MmdLibraryFiles.path(prefix + name);
                budget.entry(path);
                File file = MmdLibraryFiles.target(root, path);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))) {
                    MmdLibraryFiles.mkdir(file);
                    copyTree(app, tree, cursor.getString(0), root, path + "/", budget, visited, depth + 1);
                } else {
                    long expected = cursor.isNull(3) ? -1 : cursor.getLong(3);
                    if (expected < -1) throw new IOException("资源长度异常：" + path);
                    Uri uri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0));
                    try (InputStream in = input(app, uri)) {
                        MmdLibraryFiles.copy(in, file, path, expected, budget);
                    }
                }
            }
        }
    }

    private static InputStream input(Context app, Uri uri) throws IOException {
        InputStream in = app.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("无法读取文件");
        return in;
    }

    /** The provider's declared length, or -1 when it declines to say. */
    private static long declaredSize(Context app, Uri uri) throws IOException {
        try (Cursor cursor = app.getContentResolver().query(uri,
                new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst() || cursor.isNull(0)) return -1;
            long size = cursor.getLong(0);
            if (size < 0) throw new IOException("资源长度异常");
            return size;
        }
    }

    private static String displayName(Context app, Uri uri, boolean tree) {
        Uri document = tree
                ? DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                : uri;
        try (Cursor cursor = app.getContentResolver().query(document,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0);
        }
        return "resource";
    }

    /** Persists the user's edits, if there is a plan and it is still current. */
    void saveEdits() {
        if (plan == null || cancelled || installed) return;
        MmdImportPlan source = plan;
        final JSONObject edits;
        try {
            edits = snapshot();
        } catch (Exception invalid) {
            return;
        }
        WORKER.execute(() -> {
            if (cancelled || installed || plan != source) return;
            try {
                persistEdits(edits);
            } catch (Exception error) {
                status = "选择缓存写入失败";
            }
        });
    }

    private JSONObject snapshot() throws Exception {
        JSONArray works = new JSONArray();
        for (MmdImportPlan.Work work : plan.works) {
            works.put(new JSONObject().put("name", work.name)
                    .put("slots", new JSONObject(work.slots))
                    .put("settings", new JSONObject(work.settings)));
        }
        return new JSONObject().put("selected", selected).put("works", works);
    }

    private void persistEdits(JSONObject edits) throws Exception {
        check(status);
        File temporary = new File(directory, ".choices");
        try (FileOutputStream out = new FileOutputStream(temporary)) {
            out.write(edits.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!temporary.renameTo(new File(directory, "choices.json"))) {
            throw new IOException("无法写入选择缓存");
        }
    }

    /**
     * Re-applies saved edits to a freshly inspected plan.
     *
     * The plan is rebuilt from the files every time, so an asset that was
     * deleted between sessions silently drops out of the restored slots instead
     * of being referenced by an edit that no longer matches anything.
     */
    private void restoreEdits(MmdImportPlan prepared) throws Exception {
        File file = new File(directory, "choices.json");
        if (!file.isFile()) return;
        if (file.length() > 8 * 1024 * 1024) throw new IOException("选择缓存过大");
        JSONObject edits = new JSONObject(new String(
                java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        JSONArray works = edits.getJSONArray("works");
        if (works.length() < 1 || works.length() > 512) throw new IOException("选择缓存异常");
        List<MmdImportPlan.Work> restored = new ArrayList<>();
        for (int i = 0; i < works.length(); i++) {
            JSONObject edit = works.getJSONObject(i);
            MmdImportPlan.Work work = new MmdImportPlan.Work(edit.getString("name"));
            JSONObject slots = edit.getJSONObject("slots");
            JSONObject settings = edit.getJSONObject("settings");
            for (String slot : MmdImportPlan.SLOTS) {
                String path = slots.optString(slot, "");
                MmdImportPlan.Asset asset = prepared.asset(path);
                if (asset != null && asset.supports(slot)) work.slots.put(slot, path);
            }
            Iterator<String> keys = settings.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                work.settings.put(key, settings.getString(key));
            }
            restored.add(work);
        }
        prepared.works.clear();
        prepared.works.addAll(restored);
        selected = Math.max(0, Math.min(edits.optInt("selected", 0), restored.size() - 1));
    }

    synchronized void cancel() {
        if (installed) return;
        cancelled = true;
        if (!busy) dispose();
    }

    /**
     * Drops the cache, deferred while a worker still holds it.
     *
     * Deleting a directory a running copy is reading from would turn a cancel
     * into an import failure with an unrelated error; the flag makes the worker's
     * own {@code finally} do the deletion instead.
     */
    synchronized void dispose() {
        if (busy) {
            cancelled = true;
            return;
        }
        SESSIONS.remove(id, this);
        MmdLibraryFiles.delete(directory);
    }
}
