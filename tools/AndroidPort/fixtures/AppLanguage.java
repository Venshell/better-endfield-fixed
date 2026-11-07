package dev.betterendfield.next;

/** Host-only language adapter: execute the production Text table in Chinese.
 * Android preferences, resources and locale selection are outside this gate.
 */
final class AppLanguage {
    private AppLanguage() {}
    static boolean isEnglish() { return false; }
}
