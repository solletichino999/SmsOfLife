package it.simonespadino.smsoflife;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import it.simonespadino.smsoflife.data.*;
import it.simonespadino.smsoflife.domain.*;
import static org.junit.Assert.*;

/** Synthetic metadata only. The fake store never connects to a device/provider. */
public class RecoveryTest {
    private static SmsRawRecord row(long id, int deleted) { return new SmsRawRecord(id, "synthetic", 134020L, 10L, deleted); }
    private static final class Store implements SmsRecoveryManager.Store {
        final Map<Long, SmsRawRecord> rows = new HashMap<>();
        int writes, reads, count = 1;
        boolean physicallyRemove, ignoreWrites, failVerification, changeAfterDelete;
        DeleteResult.Status failure;
        Store() { rows.put(1L, row(1, 0)); rows.put(2L, row(2, 0)); }
        @Override public ScanResult read(long id) {
            reads++;
            if (failVerification && writes > 0) return ScanResult.state(ScanResult.Status.ACCESS_DENIED);
            SmsRawRecord record = rows.get(id);
            return ScanResult.rows(record == null ? Collections.emptyList() : Collections.singletonList(record), true);
        }
        @Override public DeleteResult deleteUnchanged(SmsRawRecord record) {
            writes++;
            if (failure != null) return DeleteResult.error(failure);
            if (!ignoreWrites) {
                if (physicallyRemove) rows.remove(record.id);
                else rows.put(record.id, changeAfterDelete
                        ? new SmsRawRecord(record.id, "replacement", 80L, 99L, 1) : row(record.id, 1));
            }
            return DeleteResult.returned(count);
        }
    }
    private RecoveryReport execute(Store store) {
        return new SmsRecoveryManager(store, () -> true).deleteConfirmed(Arrays.asList(row(1, 0), row(2, 0)));
    }
    @Test public void verifiesSoftDeletion() {
        Store store = new Store(); RecoveryReport report = execute(store);
        assertTrue(report.cleanupVerified()); assertEquals(2, report.reportedChanges); assertEquals(2, report.inactive);
    }
    @Test public void verifiesPhysicalAbsenceWithoutUsingPermanentDeleteUri() {
        Store store = new Store(); store.physicallyRemove = true;
        assertTrue(execute(store).cleanupVerified());
    }
    @Test public void allRowsAreCheckedBeforeAnyWrite() {
        Store store = new Store(); store.rows.put(2L, new SmsRawRecord(2, "changed", 134020L, 10L, 0));
        assertEquals(RecoveryReport.Issue.CHANGED, execute(store).issue); assertEquals(0, store.writes);
    }
    @Test public void missingAndAlreadyDeletedSelectionsAbort() {
        Store store = new Store(); store.rows.remove(2L);
        assertEquals(RecoveryReport.Issue.CHANGED, execute(store).issue); assertEquals(0, store.writes);
        store.rows.put(2L, row(2, 1));
        assertEquals(RecoveryReport.Issue.CHANGED, execute(store).issue); assertEquals(0, store.writes);
    }
    @Test public void preflightReadDenialPreventsMutation() {
        SmsRecoveryManager.Store store = new SmsRecoveryManager.Store() {
            public ScanResult read(long id) { return ScanResult.state(ScanResult.Status.ACCESS_DENIED); }
            public DeleteResult deleteUnchanged(SmsRawRecord row) { fail("Must not delete"); return null; }
        };
        RecoveryReport report = new SmsRecoveryManager(store, () -> true).deleteConfirmed(Collections.singletonList(row(1, 0)));
        assertEquals(RecoveryReport.Issue.PRECHECK_FAILED, report.issue);
        assertEquals(ScanResult.Status.ACCESS_DENIED, report.readError);
    }
    @Test public void zeroCountStopsFurtherWritesEvenIfRowBecameInactive() {
        Store store = new Store(); store.count = 0;
        RecoveryReport report = execute(store);
        assertEquals(RecoveryReport.Issue.CHANGED, report.issue); assertEquals(1, store.writes);
        assertEquals(1, report.inactive); assertEquals(1, report.active); assertFalse(report.cleanupVerified());
    }
    @Test public void negativeOrMultipleCountsAreNotSuccess() {
        for (int count : new int[]{-1, 2}) {
            Store store = new Store(); store.count = count;
            assertEquals(RecoveryReport.Issue.UNEXPECTED_COUNT, execute(store).issue); assertEquals(1, store.writes);
        }
    }
    @Test public void countOfOneDoesNotProveDeletion() {
        Store store = new Store(); store.ignoreWrites = true;
        RecoveryReport report = execute(store);
        assertEquals(RecoveryReport.Issue.STILL_ACTIVE, report.issue); assertEquals(2, report.active);
    }
    @Test public void verificationDenialIsNeverAnEmptyResult() {
        Store store = new Store(); store.failVerification = true;
        RecoveryReport report = execute(store);
        assertEquals(RecoveryReport.Issue.VERIFY_FAILED, report.issue); assertEquals(2, report.unknown);
    }
    @Test public void reusedIdIsNotReportedAsVerified() {
        Store store = new Store(); store.changeAfterDelete = true;
        assertEquals(RecoveryReport.Issue.VERIFY_FAILED, execute(store).issue);
    }
    @Test public void writeDenialStopsAndStillVerifiesRemainingRows() {
        Store store = new Store(); store.failure = DeleteResult.Status.ACCESS_DENIED;
        RecoveryReport report = execute(store);
        assertEquals(RecoveryReport.Issue.DELETE_FAILED, report.issue); assertEquals(1, store.writes);
        assertEquals(2, report.active); assertEquals(DeleteResult.Status.ACCESS_DENIED, report.deleteError);
    }
    @Test public void roleLossBeforeAndDuringOperationStopsWrites() {
        Store store = new Store();
        assertEquals(RecoveryReport.Issue.PREREQUISITE_LOST,
                new SmsRecoveryManager(store, () -> false).deleteConfirmed(Collections.singletonList(row(1,0))).issue);
        assertEquals(0, store.writes);
        AtomicInteger calls = new AtomicInteger();
        RecoveryReport report = new SmsRecoveryManager(store, () -> calls.incrementAndGet() < 3)
                .deleteConfirmed(Arrays.asList(row(1,0), row(2,0)));
        assertEquals(RecoveryReport.Issue.PREREQUISITE_LOST, report.issue);
        assertEquals(1, store.writes); assertEquals(1, report.inactive); assertEquals(1, report.active);
    }
    @Test public void roleLossDuringFinalVerificationCannotDeclareCompletion() {
        Store store = new Store();
        AtomicInteger checks = new AtomicInteger();
        RecoveryReport report = new SmsRecoveryManager(store, () -> checks.incrementAndGet() <= 3)
                .deleteConfirmed(Arrays.asList(row(1,0),row(2,0)));
        assertEquals(2,store.writes);
        assertEquals(RecoveryReport.Issue.PREREQUISITE_LOST,report.issue);
        assertFalse(report.cleanupVerified());
    }
    @Test public void rejectsDuplicateAndInactiveSelections() {
        Store store = new Store(); SmsRecoveryManager manager = new SmsRecoveryManager(store, () -> true);
        assertEquals(RecoveryReport.Issue.INVALID_SELECTION, manager.deleteConfirmed(Arrays.asList(row(1,0),row(1,0))).issue);
        assertEquals(RecoveryReport.Issue.INVALID_SELECTION, manager.deleteConfirmed(Collections.singletonList(row(1,1))).issue);
        assertEquals(RecoveryReport.Issue.INVALID_SELECTION, manager.deleteConfirmed(Collections.emptyList()).issue);
        assertEquals(0, store.writes);
    }
    @Test public void sqlBindsEveryValueAndHandlesNulls() {
        String address = "' OR 1=1 --";
        RawSelection selection = RawSelection.unchangedActive(new SmsRawRecord(7, address, null, 10L, 0));
        assertEquals("_id = ? AND deleted = ? AND address = ? AND destination_port IS NULL AND date = ?", selection.sql);
        assertArrayEquals(new String[]{"7","0", address,"10"}, selection.args());
        assertFalse(selection.sql.contains(address));
        assertEquals("_id = ?", RawSelection.id(7).sql);
        assertArrayEquals(new String[]{"7"}, RawSelection.id(7).args());
        assertTrue(RawSelection.unchangedActive(new SmsRawRecord(7,null,null,null,0)).sql.contains("address IS NULL"));
    }
    @Test public void confirmationSnapshotIncludesHiddenSelectionsAndCannotChange() {
        SmsRecordSelection selection = new SmsRecordSelection();
        selection.replace(Arrays.asList(row(1,0),new SmsRawRecord(2,"synthetic",80L,10L,0)));
        selection.select(1,true); selection.select(2,true);
        List<SmsRawRecord> snapshot = selection.snapshot();
        selection.visible(true, SmsRecordSelection.Sort.NEWEST); selection.select(2,false);
        assertEquals(2,snapshot.size()); assertEquals(1,selection.count());
    }
    @Test public void completionRequiresVerifiedCleanupAndSystemRestoration() {
        RecoveryReport report = execute(new Store());
        assertFalse(RecoveryCompletion.complete(report,false));
        assertTrue(RecoveryCompletion.complete(report,true));
        assertFalse(RecoveryCompletion.complete(RecoveryReport.stopped(RecoveryReport.Issue.INTERRUPTED,2,null),true));
        assertFalse(RecoveryCompletion.restorationVerified(false,"tool","original"));
        assertFalse(RecoveryCompletion.restorationVerified(false,"tool",null));
        assertFalse(RecoveryCompletion.restorationVerified(true,"tool","tool"));
        assertTrue(RecoveryCompletion.restorationVerified(true,"tool","original"));
        assertTrue(RecoveryCompletion.restorationVerified(true,"tool","another-sms-app"));
    }
    @Test public void releasedRoleCompletesRecoveryEvenWhenDefaultPackageIsUnavailable() {
        RecoveryReport report = execute(new Store());
        assertTrue(RecoveryCompletion.complete(report,
                RecoveryCompletion.restorationVerified(true,"tool",null)));
        assertTrue(RecoveryCompletion.complete(report,
                RecoveryCompletion.restorationVerified(true,"tool","")));
        assertFalse(RecoveryCompletion.complete(report,
                RecoveryCompletion.restorationVerified(false,"tool",null)));
    }
}
