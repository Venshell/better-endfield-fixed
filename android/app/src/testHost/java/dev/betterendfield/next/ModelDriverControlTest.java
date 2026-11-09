package dev.betterendfield.next;

public final class ModelDriverControlTest {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("check " + checks);
    }
    private static String row(String command, int state, int speed) {
        return "[{\"id\":\"tail\",\"command_id\":\"" + command
            + "\",\"name\":\"尾巴\\\"\\n动效\",\"once\":true,\"state\":"
            + state + ",\"speed_millis\":" + speed + "}]";
    }
    private static final class Runtime implements ModelDriverControl.Transport {
        String raw = row("9:0", 3, 1000);
        String sent;
        boolean accept = true;
        int calls;
        public String read() { return raw; }
        public boolean set(String command, int state, int speed) {
            sent = command;
            calls++;
            // Deliberately leave observed state unchanged: accepted delivery is
            // not proof of playback. The controller must read the runtime again.
            return accept;
        }
    }
    public static void main(String[] args) {
        Runtime runtime = new Runtime();
        var snapshot = ModelDriverControl.read(runtime);
        check(snapshot.available());
        var driver = snapshot.drivers().get(0);
        check(driver.state() == 3 && driver.once());
        check(driver.name().equals("尾巴\"\n动效"));
        var replay = ModelDriverControl.apply(runtime, driver, 1, 1000);
        check(replay.error() == ModelDriverControl.Error.NONE);
        check(replay.snapshot().drivers().get(0).state() == 3);
        check(runtime.sent.equals("9:0"));
        runtime.accept = false;
        runtime.raw = row("10:0", 0, 250);
        var stale = ModelDriverControl.apply(runtime, driver, 1, 1000);
        check(stale.error() == ModelDriverControl.Error.REJECTED);
        check(stale.snapshot().drivers().get(0).commandId().equals("10:0"));
        int calls = runtime.calls;
        check(ModelDriverControl.apply(runtime, driver, 3, 1000).error() == ModelDriverControl.Error.INVALID_COMMAND);
        check(ModelDriverControl.apply(runtime, driver, 1, 249).error() == ModelDriverControl.Error.INVALID_COMMAND);
        check(ModelDriverControl.apply(runtime, driver, 1, 4001).error() == ModelDriverControl.Error.INVALID_COMMAND);
        check(runtime.calls == calls);
        check(ModelDriverControl.parse("[]").available());
        check(!ModelDriverControl.parse(null).available());
        check(!ModelDriverControl.parse("x".repeat(ModelDriverControl.MAX_JSON_CHARS + 1)).available());
        check(!ModelDriverControl.parse(row("wrong", 0, 1000)).available());
        check(!ModelDriverControl.parse(row("9:other", 0, 1000)).available());
        check(!ModelDriverControl.parse(row("9:0", 4, 1000)).available());
        check(!ModelDriverControl.parse(row("9:0", 0, 249)).available());
        check(!ModelDriverControl.parse(row("9:0", 0, 4001)).available());
        check(ModelDriverControl.parse(row("9:0", 2, 4000)).available());
        String single = row("9:0", 0, 1000);
        check(!ModelDriverControl.parse(single.substring(0, single.length()-1) + "," + single.substring(1)).available());
        check(!ModelDriverControl.parse("[{\"id\":\"missing\"}]").available());
        check(!ModelDriverControl.parse(single.replace("\"state\":0", "\"state\":0.5")).available());
        check(!ModelDriverControl.parse(single.replace("\"speed_millis\":1000", "\"speed_millis\":\"1000\"")).available());
        check(!ModelDriverControl.parse(single.replace("\"once\":true", "\"once\":\"true\"")).available());
        ModelDriverControl.Transport missing = new ModelDriverControl.Transport() {
            public String read() { throw new UnsatisfiedLinkError(); }
            public boolean set(String id, int state, int speed) { throw new UnsatisfiedLinkError(); }
        };
        check(ModelDriverControl.read(missing).error() == ModelDriverControl.Error.UNAVAILABLE);
        check(ModelDriverControl.apply(missing, driver, 1, 1000).error() == ModelDriverControl.Error.UNAVAILABLE);
        System.out.println("ModelDriverControlTest: " + checks + " checks passed");
    }
}
