package it.simonespadino.smsoflife.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import it.simonespadino.smsoflife.data.DeleteResult;
import it.simonespadino.smsoflife.data.ScanResult;
import it.simonespadino.smsoflife.data.SmsRawRecord;

/** Synchronous coordinator; the ViewModel runs it on its worker. No retries or automatic deletion. */
public final class SmsRecoveryManager {
    public interface Store {
        ScanResult read(long id);
        DeleteResult deleteUnchanged(SmsRawRecord snapshot);
    }
    private final Store store;
    private final BooleanSupplier canDelete;
    public SmsRecoveryManager(Store store, BooleanSupplier canDelete) { this.store = store; this.canDelete = canDelete; }

    public RecoveryReport deleteConfirmed(List<SmsRawRecord> confirmed) {
        List<SmsRawRecord> rows = new ArrayList<>(confirmed);
        Set<Long> ids = new HashSet<>();
        for (SmsRawRecord row : rows) {
            if (row == null || row.id < 0 || !row.isActive() || !ids.add(row.id)) {
                return RecoveryReport.stopped(RecoveryReport.Issue.INVALID_SELECTION, rows.size(), null);
            }
        }
        if (rows.isEmpty()) return RecoveryReport.stopped(RecoveryReport.Issue.INVALID_SELECTION, 0, null);
        if (!canDelete.getAsBoolean()) return RecoveryReport.stopped(RecoveryReport.Issue.PREREQUISITE_LOST, rows.size(), null);
        // Validate the ENTIRE confirmation snapshot before the first mutation.
        for (SmsRawRecord row : rows) {
            ScanResult read = store.read(row.id);
            if (read.status != ScanResult.Status.SUCCESS && read.status != ScanResult.Status.EMPTY) {
                return RecoveryReport.stopped(RecoveryReport.Issue.PRECHECK_FAILED, rows.size(), read.status);
            }
            if (read.records.size() != 1 || !row.sameMetadata(read.records.get(0)) || !read.records.get(0).isActive()) {
                return RecoveryReport.stopped(RecoveryReport.Issue.CHANGED, rows.size(), null);
            }
        }
        int attempted = 0;
        long reported = 0;
        RecoveryReport.Issue issue = RecoveryReport.Issue.NONE;
        DeleteResult.Status deleteError = null;
        for (SmsRawRecord row : rows) {
            if (!canDelete.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                issue = RecoveryReport.Issue.PREREQUISITE_LOST; break;
            }
            // The write's predicate compares all available metadata again inside the provider.
            attempted++;
            DeleteResult result = store.deleteUnchanged(row);
            if (result.status != DeleteResult.Status.RETURNED) {
                issue = RecoveryReport.Issue.DELETE_FAILED; deleteError = result.status; break;
            }
            if (result.count >= 0) reported += result.count;
            if (result.count == 0) { issue = RecoveryReport.Issue.CHANGED; break; }
            if (result.count != 1) { issue = RecoveryReport.Issue.UNEXPECTED_COUNT; break; }
        }
        // Even on partial failure, inspect every selected ID. Never infer success from delete() alone.
        int inactive = 0, active = 0, unknown = 0;
        ScanResult.Status readError = null;
        for (SmsRawRecord row : rows) {
            ScanResult read = store.read(row.id);
            if (read.status == ScanResult.Status.EMPTY) { inactive++; continue; }
            if (read.status == ScanResult.Status.SUCCESS && read.records.size() == 1
                    && row.sameMetadata(read.records.get(0))) {
                SmsRawRecord current = read.records.get(0);
                if (Integer.valueOf(1).equals(current.deleted)) { inactive++; continue; }
                if (current.isActive()) { active++; continue; }
            }
            unknown++;
            readError = read.status == ScanResult.Status.SUCCESS ? ScanResult.Status.INVALID_DATA : read.status;
        }
        if (issue == RecoveryReport.Issue.NONE && !canDelete.getAsBoolean()) {
            // Role/permission loss during verification can change provider visibility.
            issue = RecoveryReport.Issue.PREREQUISITE_LOST;
        }
        if (issue == RecoveryReport.Issue.NONE) {
            if (unknown > 0) issue = RecoveryReport.Issue.VERIFY_FAILED;
            else if (active > 0) issue = RecoveryReport.Issue.STILL_ACTIVE;
        }
        return new RecoveryReport(issue, rows.size(), attempted, reported, inactive, active, unknown, readError, deleteError);
    }
}
