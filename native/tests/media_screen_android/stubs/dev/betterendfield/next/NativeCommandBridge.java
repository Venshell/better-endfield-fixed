package dev.betterendfield.next;

/** Native loading is unavailable in a host JVM; each controller test supplies its backend. */
final class NativeCommandBridge {
    static String mediaScreenStatus() { throw new UnsatisfiedLinkError("host JVM"); }
    static boolean mediaScreen(int action, double value) { throw new UnsatisfiedLinkError("host JVM"); }
}
