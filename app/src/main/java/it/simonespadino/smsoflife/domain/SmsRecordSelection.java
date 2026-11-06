package it.simonespadino.smsoflife.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import it.simonespadino.smsoflife.data.SmsRawRecord;

/** In-memory only. A fresh scan clears selection, even when IDs happen to be reused. */
public final class SmsRecordSelection {
    public enum Sort { NEWEST, OLDEST, ID }
    private List<SmsRawRecord> records = Collections.emptyList();
    private final Set<Long> selected = new HashSet<>();
    public void replace(List<SmsRawRecord> records) {
        this.records = new ArrayList<>();
        for (SmsRawRecord record : records) {
            // Hide soft-deleted rows only in presentation; repository reads still include them for verification.
            if (!Integer.valueOf(1).equals(record.deleted)) this.records.add(record);
        }
        selected.clear();
    }
    public void select(long id, boolean checked) {
        if (!checked) { selected.remove(id); return; }
        for (SmsRawRecord record : records) {
            if (record.id == id && record.isActive()) { selected.add(id); return; }
        }
    }
    public List<SmsRawRecord> snapshot() {
        List<SmsRawRecord> snapshot = new ArrayList<>();
        for (SmsRawRecord record : records) if (selected.contains(record.id)) snapshot.add(record);
        snapshot.sort(Comparator.comparingLong(r -> r.id));
        return Collections.unmodifiableList(snapshot);
    }
    public boolean isSelected(long id) { return selected.contains(id); }
    public int count() { return selected.size(); }
    public int totalCount() { return records.size(); }
    public List<SmsRawRecord> visible(boolean suspiciousOnly, Sort sort) {
        List<SmsRawRecord> visible = new ArrayList<>();
        for (SmsRawRecord record : records) {
            if (!suspiciousOnly || SmsRiskAnalyzer.isSuspicious(record)) visible.add(record);
        }
        Comparator<Long> dates = sort == Sort.OLDEST ? Comparator.naturalOrder() : Comparator.reverseOrder();
        Comparator<SmsRawRecord> comparator = sort == Sort.ID ? Comparator.comparingLong(r -> r.id)
                : Comparator.comparing((SmsRawRecord r) -> r.date, Comparator.nullsLast(dates))
                    .thenComparingLong(r -> r.id);
        visible.sort(comparator);
        return visible;
    }
}
