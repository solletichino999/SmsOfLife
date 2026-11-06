package it.simonespadino.smsoflife.data;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.os.Process;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** sms/raw is implementation-specific: never assume a role grants access to it. */
public final class SmsProviderRepository implements it.simonespadino.smsoflife.domain.SmsRecoveryManager.Store {
    public static final Uri RAW_URI = Uri.parse("content://sms/raw");
    private static final String[] PROJECTION = {"_id", "address", "destination_port", "date", "deleted"};
    private final Context context;
    private final android.content.ContentResolver resolver;
    private final java.util.function.BooleanSupplier readAccess;

    public SmsProviderRepository(Context context) {
        this.context = context.getApplicationContext();
        resolver = this.context.getContentResolver();
        readAccess = this::hasReadAccess;
    }
    // Package-private injection for isolated provider tests; production always uses the device resolver.
    SmsProviderRepository(android.content.ContentResolver resolver, java.util.function.BooleanSupplier readAccess) {
        context = null;
        this.resolver = resolver;
        this.readAccess = readAccess;
    }

    public ScanResult scan(CancellationSignal cancellation) {
        return query(null, null, cancellation);
    }

    @Override public ScanResult read(long id) {
        RawSelection selection = RawSelection.id(id);
        return query(selection.sql, selection.args(), new CancellationSignal());
    }

    @Override public DeleteResult deleteUnchanged(SmsRawRecord snapshot) {
        RawSelection selection = RawSelection.unchangedActive(snapshot);
        try {
            return DeleteResult.returned(resolver.delete(RAW_URI, selection.sql, selection.args()));
        } catch (SecurityException e) {
            return DeleteResult.error(DeleteResult.Status.ACCESS_DENIED);
        } catch (SQLiteException e) {
            return DeleteResult.error(DeleteResult.Status.DATABASE_ERROR);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            return DeleteResult.error(DeleteResult.Status.UNSUPPORTED);
        } catch (RuntimeException e) {
            return DeleteResult.error(DeleteResult.Status.PROVIDER_ERROR);
        }
    }

    private ScanResult query(String selection, String[] args, CancellationSignal cancellation) {
        // Metadata only. Never query PDUs or access the provider's internal database.
        try (Cursor cursor = resolver.query(
                RAW_URI, PROJECTION.clone(), selection, args, null, cancellation)) {
            if (cursor == null) return ScanResult.state(ScanResult.Status.NULL_CURSOR);
            List<String> missing = new ArrayList<>();
            int[] columns = new int[PROJECTION.length];
            for (int i = 0; i < PROJECTION.length; i++) {
                columns[i] = cursor.getColumnIndex(PROJECTION[i]);
                if (columns[i] < 0) missing.add(PROJECTION[i]);
            }
            if (!missing.isEmpty()) return ScanResult.missing(missing);
            List<SmsRawRecord> rows = new ArrayList<>();
            Set<Long> ids = new HashSet<>();
            while (cursor.moveToNext()) {
                cancellation.throwIfCanceled();
                Long id = nullableLong(cursor, columns[0]);
                Long deleted = nullableLong(cursor, columns[4]);
                if (id == null || id < 0 || !ids.add(id)
                        || (deleted != null && deleted != 0 && deleted != 1)) {
                    return ScanResult.state(ScanResult.Status.INVALID_DATA);
                }
                rows.add(new SmsRawRecord(id, cursor.isNull(columns[1]) ? null : cursor.getString(columns[1]),
                        nullableLong(cursor, columns[2]), nullableLong(cursor, columns[3]),
                        deleted == null ? null : deleted.intValue()));
            }
            cancellation.throwIfCanceled();
            // Some providers/AppOps return an empty cursor instead of throwing on denial.
            // An empty response without confirmed read permission is NOT a clean-device verdict.
            return ScanResult.rows(rows, readAccess.getAsBoolean());
        } catch (SecurityException e) {
            return ScanResult.state(ScanResult.Status.ACCESS_DENIED);
        } catch (OperationCanceledException e) {
            return ScanResult.state(ScanResult.Status.CANCELLED);
        } catch (SQLiteException e) {
            return ScanResult.state(isMissingColumn(e) ? ScanResult.Status.MISSING_COLUMNS
                    : ScanResult.Status.DATABASE_ERROR);
        } catch (NumberFormatException e) {
            return ScanResult.state(ScanResult.Status.INVALID_DATA);
        } catch (IllegalArgumentException e) {
            return ScanResult.state(isMissingColumn(e) ? ScanResult.Status.MISSING_COLUMNS
                    : ScanResult.Status.UNSUPPORTED);
        } catch (UnsupportedOperationException e) {
            return ScanResult.state(ScanResult.Status.UNSUPPORTED);
        } catch (RuntimeException e) {
            // Provider errors can contain SMS metadata. Never log or display exception messages.
            return ScanResult.state(ScanResult.Status.PROVIDER_ERROR);
        }
    }

    private boolean hasReadAccess() {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        if (ops == null) return false;
        int mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_READ_SMS, Process.myUid(), context.getPackageName());
        // A foreground-only raw mode is not proof that a background query was permitted.
        return mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT;
    }

    private static Long nullableLong(Cursor cursor, int column) {
        if (cursor.isNull(column)) return null;
        // getLong can silently coerce unexpected strings to zero in SQLite cursors.
        return Long.valueOf(cursor.getString(column));
    }
    private static boolean isMissingColumn(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("no such column") || lower.contains("unknown column")
                || lower.contains("invalid column");
    }
}
