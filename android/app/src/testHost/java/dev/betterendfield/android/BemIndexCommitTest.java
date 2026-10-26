package dev.betterendfield.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Failure injection for the production BEM index commit order. */
final class BemIndexCommitTest {
    private static int checks;
    private static void check(boolean value,String message) {
        checks++;if(!value) throw new AssertionError(message);
    }
    private static final class Store implements BemIndexCommit.Store {
        String durable="old",preference="old";
        final List<String> order=new ArrayList<>();
        boolean failDurable,failPreference,wrongRead,throwPreference,failDurableRollback;
        @Override public void writeDurable(String value) throws IOException {
            order.add("durable:"+value);
            if((failDurable && value.equals("new")) || (failDurableRollback && value.equals("old")))
                throw new IOException("fixture disk failure");
            durable=value;
        }
        @Override public boolean writePreference(String value) {
            order.add("preference:"+value);
            preference=value; // Android can update memory even when commit reports a disk failure.
            if(throwPreference && value.equals("new")) throw new IllegalStateException("fixture preference failure");
            return !failPreference || value.equals("old");
        }
        @Override public String readPreference() {
            return wrongRead && preference.equals("new") ? "stale" : preference;
        }
    }
    private static IOException rejects(Store store) {
        try {BemIndexCommit.commit(store,"old","new","save failed");}
        catch(IOException expected) {checks++;return expected;}
        throw new AssertionError("Unexpected successful commit");
    }
    public static void main(String[] args) throws Exception {
        Store success=new Store();BemIndexCommit.commit(success,"old","new","save failed");
        check(success.durable.equals("new") && success.preference.equals("new"),"successful commit missing");
        check(success.order.equals(List.of("durable:new","preference:new")),"durable write must happen first");

        Store disk=new Store();disk.failDurable=true;rejects(disk);
        check(disk.preference.equals("old") && disk.durable.equals("old"),"disk failure changed active index");
        check(disk.order.equals(List.of("durable:new")),"disk failure reached preferences");

        Store preference=new Store();preference.failPreference=true;rejects(preference);
        check(preference.durable.equals("old") && preference.preference.equals("old"),"failed preference commit was not rolled back");
        check(preference.order.equals(List.of("durable:new","preference:new","durable:old","preference:old")),"rollback order changed");

        Store mismatch=new Store();mismatch.wrongRead=true;rejects(mismatch);
        check(mismatch.durable.equals("old") && mismatch.preference.equals("old"),"mismatched read was not rolled back");

        Store thrown=new Store();thrown.throwPreference=true;rejects(thrown);
        check(thrown.durable.equals("old") && thrown.preference.equals("old"),"preference exception was not rolled back");

        Store rollback=new Store();rollback.failPreference=true;rollback.failDurableRollback=true;
        IOException failure=rejects(rollback);
        check(rollback.preference.equals("old"),"preference rollback skipped after durable rollback failure");
        check(failure.getSuppressed().length==1,"durable rollback failure was hidden");
        System.out.println("PASS BEM index commit: "+checks+" checks; ordering, disk, preference, readback and rollback");
    }
}
