package dev.betterendfield.next;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Failure injection for publication: no source retirement before durable commit. */
public final class BemTransformPublicationTest {
    private static int checks;
    private static final class Store implements BemTransformPublication.Store {
        final List<String> calls = new ArrayList<>();
        final String failure;
        boolean activeOriginal = true;
        boolean retired;
        Store(String failure) { this.failure = failure; }
        private void step(String stage) throws IOException {
            calls.add(stage);
            if (failure.equals(stage)) throw new IOException(stage);
        }
        public void publish() throws IOException { step("publish"); }
        public void commit() throws IOException { step("commit"); activeOriginal = false; }
        public boolean retire() throws IOException { step("retire"); retired = true; return true; }
        public void discardUncommitted() { calls.add("discard"); }
    }
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("check " + checks);
    }
    public static void main(String[] args) throws Exception {
        Store success = new Store("");
        check(BemTransformPublication.execute(success));
        check(success.calls.equals(List.of("publish", "commit", "retire")));
        check(!success.activeOriginal && success.retired);
        for (String stage : List.of("publish", "commit")) {
            Store failure = new Store(stage);
            try { BemTransformPublication.execute(failure); throw new AssertionError("Accepted " + stage); }
            catch (IOException expected) { check(expected.getMessage().equals(stage)); }
            check(failure.activeOriginal && !failure.retired);
            check(failure.calls.get(failure.calls.size() - 1).equals("discard"));
            check(!failure.calls.contains("retire"));
        }
        Store cleanupFailure = new Store("retire");
        check(!BemTransformPublication.execute(cleanupFailure));
        check(!cleanupFailure.activeOriginal);
        check(!cleanupFailure.calls.contains("discard"));
        System.out.println("BemTransformPublicationTest: " + checks + " checks");
    }
}
