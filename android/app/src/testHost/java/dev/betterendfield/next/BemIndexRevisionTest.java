package dev.betterendfield.next;

import java.util.HashMap;
import java.util.Map;

/** Actual draft baseline must survive interleaved changes, not borrow a new revision. */
final class BemIndexRevisionTest {
    private static int checks;
    private static void check(boolean value) {checks++;if(!value) throw new AssertionError("check "+checks);}
    public static void main(String[] args) {
        String a=BemIndexRevision.of("[]"),b=BemIndexRevision.of("[1]");
        check(BemIndexRevision.matches(a,"[]"));
        check(!BemIndexRevision.matches(a,"[1]"));
        check(!BemIndexRevision.matches(null,"[]"));
        BemIndexRevision.Draft draft=new BemIndexRevision.Draft();
        Map<String,String> original=new HashMap<>();original.put("one","enabled;on;width:0");
        check(draft.refresh(a,Map.of())==BemIndexRevision.Draft.Refresh.LOAD_LATEST);
        draft.accept(a,original);
        original.put("one","changed after accept");
        Map<String,String> clean=Map.of("one","enabled;on;width:0");
        Map<String,String> edited=Map.of("one","disabled;off;width:700");
        check(draft.refresh(a,edited)==BemIndexRevision.Draft.Refresh.UNCHANGED);
        check(draft.refresh(b,clean)==BemIndexRevision.Draft.Refresh.LOAD_LATEST);
        check(draft.refresh(b,edited)==BemIndexRevision.Draft.Refresh.KEEP_DRAFT);
        check(draft.conflict());check(draft.revision().equals(a));
        check(draft.refresh(b,edited)==BemIndexRevision.Draft.Refresh.KEEP_DRAFT);
        check(draft.revision().equals(a));
        check(draft.refresh(b,clean)==BemIndexRevision.Draft.Refresh.LOAD_LATEST);
        draft.accept(b,edited);
        check(!draft.conflict());check(draft.revision().equals(b));
        check(draft.refresh(a,edited)==BemIndexRevision.Draft.Refresh.LOAD_LATEST);
        check(draft.refresh(a,Map.of())==BemIndexRevision.Draft.Refresh.KEEP_DRAFT);
        System.out.println("BemIndexRevisionTest: "+checks+" checks passed");
    }
}
