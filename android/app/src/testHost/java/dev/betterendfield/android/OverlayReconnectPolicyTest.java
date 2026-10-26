package dev.betterendfield.android;

/** Pure policy checks; no Android device or provider required. */
public final class OverlayReconnectPolicyTest {
    private static int checks;
    public static void main(String[] args) {
        OverlayReconnectPolicy policy=new OverlayReconnectPolicy();
        check(!policy.begin("edit_models","transport_dead",true,1000),"write replay allowed");
        check(!policy.begin("read_models","provider_IOException",true,1000),"provider error woke owner");
        check(!policy.begin("read_models","caller_rejected",false,1000),"background read woke owner");
        check(policy.begin("read_models","caller_rejected",true,1000),"foreground read did not wake owner");
        check(!policy.begin("read_fov","transport_dead",true,1200),"wake cooldown ignored");
        check(policy.begin("read_fov","transport_dead",true,31000),"wake cooldown never ended");
        check(!policy.begin("read_fov","transport_dead",true,100),"clock reset bypassed cooldown");
        System.out.println("OverlayReconnectPolicyTest: " + checks + " checks passed");
    }
    private static void check(boolean valid,String reason) {checks++;if(!valid) throw new AssertionError(reason);}
}
