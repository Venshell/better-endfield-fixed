package dev.betterendfield.android;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;

/** Accept exactly one granted document URI from a file manager. */
final class BemImportRequest {
    private BemImportRequest() {}
    static final class Rejection extends IllegalArgumentException {
        final int messageId;
        Rejection(int messageId) { this.messageId=messageId; }
    }

    static Uri fromIntent(Intent intent) {
        if (intent == null || intent.getAction() == null || Intent.ACTION_MAIN.equals(intent.getAction())) return null;
        String action = intent.getAction();
        if (!Intent.ACTION_VIEW.equals(action) && !Intent.ACTION_SEND.equals(action))
            throw new Rejection(R.string.bem_import_request_action);
        try {
            Uri candidate = intent.getData();
            if (Intent.ACTION_SEND.equals(action) && intent.hasExtra(Intent.EXTRA_STREAM)) {
                Parcelable stream = streamExtra(intent);
                if (!(stream instanceof Uri))
                    throw new Rejection(R.string.bem_import_request_not_file);
                candidate = merge(candidate, (Uri) stream);
            }
            ClipData clip = intent.getClipData();
            if (clip != null) {
                if (clip.getItemCount() != 1 || clip.getItemAt(0).getUri() == null)
                    throw new Rejection(R.string.bem_import_request_multiple);
                candidate = merge(candidate, clip.getItemAt(0).getUri());
            }
            return requireContentUri(candidate);
        } catch (Rejection error) {
            throw error;
        } catch (RuntimeException malformed) {
            throw new Rejection(R.string.bem_import_request_malformed);
        }
    }

    static Uri requireContentUri(Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme()) ||
                uri.getAuthority() == null || uri.getAuthority().isEmpty())
            throw new Rejection(R.string.bem_import_request_uri);
        return uri;
    }

    private static Uri merge(Uri first, Uri second) {
        if (first != null && !first.equals(second))
            throw new Rejection(R.string.bem_import_request_conflict);
        return second;
    }

    @SuppressWarnings("deprecation")
    private static Parcelable streamExtra(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        return intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }
}
