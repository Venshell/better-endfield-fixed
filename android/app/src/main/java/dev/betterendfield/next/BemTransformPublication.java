package dev.betterendfield.next;

/** Commit ordering for immutable BEM transformations, independent of Android and codecs. */
final class BemTransformPublication {
    interface Store {
        void publish() throws Exception;
        void commit() throws Exception;
        boolean retire() throws java.io.IOException;
        void discardUncommitted();
    }

    private BemTransformPublication() {}

    /** A retirement failure does not invalidate a committed replacement. */
    static boolean execute(Store store) throws Exception {
        boolean committed = false;
        try {
            store.publish();
            store.commit();
            committed = true;
            try { return store.retire(); }
            catch (java.io.IOException cleanup) { return false; }
        } finally {
            if (!committed) store.discardUncommitted();
        }
    }
}
