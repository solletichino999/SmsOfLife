package it.simonespadino.smsoflife.domain;

/** Public-system observations must confirm restoration; a settings result code is insufficient. */
public final class RecoveryCompletion {
    private RecoveryCompletion() { }
    public static boolean restorationVerified(boolean roleKnownNotHeld, String own, String current) {
        // Releasing our role is sufficient. Package visibility must not block completion.
        // A contradictory package observation still requires restoration/rechecking.
        return roleKnownNotHeld && !own.equals(current);
    }
    public static boolean complete(RecoveryReport report, boolean restored) {
        return report != null && report.cleanupVerified() && restored;
    }
}
