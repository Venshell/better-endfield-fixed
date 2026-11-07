package dev.betterendfield.next;

import java.io.IOException;

/** Orders the durable BEM index and preference snapshot as one recoverable update. */
final class BemIndexCommit {
    interface Store {
        void writeDurable(String value) throws IOException;
        boolean writePreference(String value);
        String readPreference();
    }

    private BemIndexCommit() {}

    static void commit(Store store,String previous,String next,String message) throws IOException {
        store.writeDurable(next);
        IOException failure=null;
        try {
            if(!store.writePreference(next)) failure=new IOException(message);
            else if(!next.equals(store.readPreference())) failure=new IOException("安装索引校验失败");
        } catch(RuntimeException error) {
            failure=new IOException("安装索引偏好写入失败",error);
        }
        if(failure==null) return;
        try {store.writeDurable(previous);} catch(IOException rollback) {failure.addSuppressed(rollback);}
        try {
            if(!store.writePreference(previous) || !previous.equals(store.readPreference()))
                failure.addSuppressed(new IOException("安装索引偏好回滚失败"));
        } catch(RuntimeException rollback) {failure.addSuppressed(rollback);}
        throw failure;
    }
}
