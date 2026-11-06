package it.simonespadino.smsoflife.domain;

import it.simonespadino.smsoflife.data.DeleteResult;
import it.simonespadino.smsoflife.data.ScanResult;

/** Summary only: safe to persist without IDs, senders, dates or message metadata. */
public final class RecoveryReport {
    public enum Issue {
        NONE, INVALID_SELECTION, PREREQUISITE_LOST, PRECHECK_FAILED, CHANGED,
        DELETE_FAILED, UNEXPECTED_COUNT, STILL_ACTIVE, VERIFY_FAILED, STORAGE_FAILED, INTERRUPTED
    }
    public final Issue issue;
    public final int requested, attempted, inactive, active, unknown;
    public final long reportedChanges;
    public final ScanResult.Status readError;
    public final DeleteResult.Status deleteError;
    public RecoveryReport(Issue issue, int requested, int attempted, long reportedChanges,
            int inactive, int active, int unknown, ScanResult.Status readError, DeleteResult.Status deleteError) {
        this.issue = issue; this.requested = requested; this.attempted = attempted;
        this.reportedChanges = reportedChanges; this.inactive = inactive;
        this.active = active; this.unknown = unknown; this.readError = readError; this.deleteError = deleteError;
    }
    public boolean cleanupVerified() {
        return issue == Issue.NONE && requested > 0 && inactive == requested && active == 0 && unknown == 0;
    }
    public static RecoveryReport stopped(Issue issue, int requested, ScanResult.Status readError) {
        return new RecoveryReport(issue, requested, 0, 0, 0, 0, requested, readError, null);
    }
}
