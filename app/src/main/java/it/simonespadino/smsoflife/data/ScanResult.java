package it.simonespadino.smsoflife.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScanResult {
    public enum Status {
        IDLE, SCANNING, SUCCESS, EMPTY, ACCESS_DENIED, EMPTY_UNVERIFIED,
        NULL_CURSOR, UNSUPPORTED, MISSING_COLUMNS, DATABASE_ERROR, INVALID_DATA,
        PROVIDER_ERROR, CANCELLED
    }
    public final Status status;
    public final List<SmsRawRecord> records;
    public final List<String> missingColumns;

    private ScanResult(Status status, List<SmsRawRecord> records, List<String> missingColumns) {
        this.status = status;
        this.records = Collections.unmodifiableList(new ArrayList<>(records));
        this.missingColumns = Collections.unmodifiableList(new ArrayList<>(missingColumns));
    }
    public static ScanResult state(Status status) {
        return new ScanResult(status, Collections.emptyList(), Collections.emptyList());
    }
    public static ScanResult rows(List<SmsRawRecord> rows, boolean readAccessConfirmed) {
        return new ScanResult(rows.isEmpty()
                ? (readAccessConfirmed ? Status.EMPTY : Status.EMPTY_UNVERIFIED)
                : Status.SUCCESS, rows, Collections.emptyList());
    }
    public static ScanResult missing(List<String> columns) {
        return new ScanResult(Status.MISSING_COLUMNS, Collections.emptyList(), columns);
    }
    public boolean needsAccess() {
        return status == Status.ACCESS_DENIED || status == Status.EMPTY_UNVERIFIED;
    }
}
