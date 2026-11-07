package it.simonespadino.smsoflife;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import it.simonespadino.smsoflife.data.ScanResult;
import it.simonespadino.smsoflife.data.SmsRawRecord;
import it.simonespadino.smsoflife.domain.SmsRecordSelection;
import it.simonespadino.smsoflife.domain.SmsRiskAnalyzer;
import static org.junit.Assert.*;

/** Synthetic metadata only. No messages, PDU decoding, or provider mutations. */
public class ScanLogicTest {
    private SmsRawRecord row(long id, Long port, Long date, Integer deleted) {
        return new SmsRawRecord(id, "synthetic-sender", port, date, deleted);
    }
    @Test public void documentedPatternIsAnIndicatorNotAContentVerdict() {
        SmsRawRecord record = row(1, 134020L, 1L, 0);
        assertEquals(Integer.valueOf(2948), SmsRiskAnalyzer.decodedPort(record.destinationPort));
        assertEquals(SmsRiskAnalyzer.Indicator.DOCUMENTED_PORT_PATTERN, SmsRiskAnalyzer.classify(record));
    }
    @Test public void otherWapEncodingsAreNotTheDocumentedPattern() {
        assertEquals(SmsRiskAnalyzer.Indicator.WAP_PUSH_PORT, SmsRiskAnalyzer.classify(row(1, 2948L, 1L, 0)));
        assertFalse(SmsRiskAnalyzer.isSuspicious(row(2, 265092L, 1L, 0)));
    }
    @Test public void noPortFlagAndInvalidValuesDoNotDecodeToWap() {
        assertNull(SmsRiskAnalyzer.decodedPort(199556L));
        assertNull(SmsRiskAnalyzer.decodedPort(-1L));
        assertNull(SmsRiskAnalyzer.decodedPort(null));
        assertNull(SmsRiskAnalyzer.decodedPort(Long.MAX_VALUE));
    }
    @Test public void unknownPortStaysUnknown() {
        assertEquals(SmsRiskAnalyzer.Indicator.UNKNOWN, SmsRiskAnalyzer.classify(row(1, null, null, null)));
        assertFalse(row(1, null, null, null).isActive());
    }
    @Test public void deletedIndicatorIsHiddenAndCannotBeSelected() {
        SmsRawRecord record = row(1, 134020L, 1L, 1);
        assertTrue(SmsRiskAnalyzer.isSuspicious(record));
        SmsRecordSelection selection = new SmsRecordSelection();
        selection.replace(Collections.singletonList(record));
        selection.select(1, true);
        assertEquals(0, selection.count());
        assertEquals(0, selection.totalCount());
        for (SmsRecordSelection.Sort sort : SmsRecordSelection.Sort.values()) {
            assertTrue(selection.visible(false, sort).isEmpty());
            assertTrue(selection.visible(true, sort).isEmpty());
        }
        // Unknown deleted state must not be silently treated as deleted.
        selection.replace(Arrays.asList(record, row(2, 134020L, 2L, 0), row(3, null, null, null)));
        assertEquals(2, selection.totalCount());
        assertEquals(2, selection.visible(false, SmsRecordSelection.Sort.ID).size());
        assertEquals(1, selection.visible(true, SmsRecordSelection.Sort.ID).size());
    }
    @Test public void filteringDoesNotSilentlySelectOrLoseHiddenSelections() {
        SmsRecordSelection selection = new SmsRecordSelection();
        selection.replace(Arrays.asList(row(1, 134020L, 1L, 0), row(2, 80L, 2L, 0)));
        selection.select(2, true);
        assertEquals(1, selection.visible(true, SmsRecordSelection.Sort.ID).size());
        assertTrue(selection.isSelected(2));
        assertFalse(selection.isSelected(1));
    }
    @Test public void rescanClearsSelectionEvenWhenIdsAreReused() {
        SmsRecordSelection selection = new SmsRecordSelection();
        selection.replace(Collections.singletonList(row(1, 134020L, 1L, 0)));
        selection.select(1, true);
        selection.replace(Collections.singletonList(row(1, 134020L, 2L, 0)));
        assertEquals(0, selection.count());
    }
    @Test public void nonexistentIdsCannotBeSelected() {
        SmsRecordSelection selection = new SmsRecordSelection();
        selection.select(100, true);
        assertEquals(0, selection.count());
    }
    @Test public void sortingPlacesUnknownDatesLastAndUsesStableIds() {
        SmsRecordSelection selection = new SmsRecordSelection();
        selection.replace(Arrays.asList(row(4, null, null, 0), row(2, null, 20L, 0), row(1, null, 10L, 0)));
        assertEquals(2, selection.visible(false, SmsRecordSelection.Sort.NEWEST).get(0).id);
        assertEquals(1, selection.visible(false, SmsRecordSelection.Sort.OLDEST).get(0).id);
        assertEquals(4, selection.visible(false, SmsRecordSelection.Sort.OLDEST).get(2).id);
        assertEquals(1, selection.visible(false, SmsRecordSelection.Sort.ID).get(0).id);
    }
    @Test public void emptyResponseWithoutPermissionIsNotAnEmptyScan() {
        ScanResult result = ScanResult.rows(Collections.emptyList(), false);
        assertEquals(ScanResult.Status.EMPTY_UNVERIFIED, result.status);
        assertTrue(result.needsAccess());
    }
    @Test public void emptyResponseWithAccessIsDistinctFromDenial() {
        assertEquals(ScanResult.Status.EMPTY, ScanResult.rows(Collections.emptyList(), true).status);
        assertTrue(ScanResult.state(ScanResult.Status.ACCESS_DENIED).needsAccess());
        assertFalse(ScanResult.state(ScanResult.Status.UNSUPPORTED).needsAccess());
        assertTrue(ScanResult.state(ScanResult.Status.NULL_CURSOR).needsAccess());
    }
    @Test public void readingRoleIsOfferedOnlyForNullCursorWithReadPermission() {
        for (ScanResult.Status status : ScanResult.Status.values()) {
            ScanResult result = ScanResult.state(status);
            assertFalse(status.name(), result.canRequestReadRole(false));
            assertEquals(status.name(), status == ScanResult.Status.NULL_CURSOR,
                    result.canRequestReadRole(true));
        }
    }
    @Test public void validEmptyQueueAndActualRowsNeverNeedReadingRole() {
        assertFalse(ScanResult.rows(Collections.emptyList(), true).canRequestReadRole(true));
        assertFalse(ScanResult.rows(Collections.emptyList(), false).canRequestReadRole(true));
        assertFalse(ScanResult.rows(Collections.singletonList(row(1, 134020L, 1L, 0)), true)
                .canRequestReadRole(true));
    }
    @Test public void actualRowsDoNotTriggerUnnecessaryPermissionEscalation() {
        ScanResult result = ScanResult.rows(Collections.singletonList(row(1, 80L, 1L, 0)), false);
        assertEquals(ScanResult.Status.SUCCESS, result.status);
        assertFalse(result.needsAccess());
    }
}
