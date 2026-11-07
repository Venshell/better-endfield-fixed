package dev.betterendfield.next;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/** Revision and draft retention contract owned by the BEM index layer. */
final class BemIndexRevision {
    static String of(String index) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(index.getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder(64);
            for(byte value:digest) result.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
            return result.toString();
        } catch(java.security.NoSuchAlgorithmException impossible) {throw new AssertionError(impossible);}
    }
    static boolean matches(String expected,String current) {return expected!=null && expected.equals(of(current));}

    static final class Draft {
        enum Refresh { UNCHANGED, LOAD_LATEST, KEEP_DRAFT }
        private String revision;
        private Map<String,String> baseline=new HashMap<>();
        private boolean conflict;
        String revision() {return revision;}
        boolean conflict() {return conflict;}
        void accept(String nextRevision,Map<String,String> selections) {
            revision=nextRevision;baseline=new HashMap<>(selections);conflict=false;
        }
        Refresh refresh(String nextRevision,Map<String,String> selections) {
            if(nextRevision.equals(revision)) {conflict=false;return Refresh.UNCHANGED;}
            if(revision!=null && !baseline.equals(selections)) {conflict=true;return Refresh.KEEP_DRAFT;}
            return Refresh.LOAD_LATEST;
        }
    }
}
