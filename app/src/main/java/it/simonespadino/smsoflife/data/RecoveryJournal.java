package it.simonespadino.smsoflife.data;

import android.content.Context;
import android.content.SharedPreferences;
import it.simonespadino.smsoflife.domain.RecoveryReport;

/** Persists only operation state/counts. Never persists selections, IDs, addresses or SMS data. */
public final class RecoveryJournal {
    private final SharedPreferences preferences;
    public RecoveryJournal(Context context) {
        preferences = context.getSharedPreferences("recovery_state", Context.MODE_PRIVATE);
    }
    public boolean begin(int requested) {
        return preferences.edit().clear().putBoolean("pending", true).putInt("requested", requested).commit();
    }
    public boolean finish(RecoveryReport report) {
        return preferences.edit().clear().putBoolean("pending", false)
                .putString("issue", report.issue.name()).putInt("requested", report.requested)
                .putInt("attempted", report.attempted).putLong("reported", report.reportedChanges)
                .putInt("inactive", report.inactive).putInt("active", report.active).putInt("unknown", report.unknown)
                .putString("read_error", report.readError == null ? null : report.readError.name())
                .putString("delete_error", report.deleteError == null ? null : report.deleteError.name()).commit();
    }
    public RecoveryReport load() {
        if (!preferences.contains("requested")) return null;
        int requested = preferences.getInt("requested", 0);
        if (preferences.getBoolean("pending", false)) {
            return RecoveryReport.stopped(RecoveryReport.Issue.INTERRUPTED, requested, null);
        }
        try {
            String read = preferences.getString("read_error", null);
            String delete = preferences.getString("delete_error", null);
            return new RecoveryReport(RecoveryReport.Issue.valueOf(preferences.getString("issue", "INTERRUPTED")),
                    requested, preferences.getInt("attempted", 0), preferences.getLong("reported", 0),
                    preferences.getInt("inactive", 0), preferences.getInt("active", 0), preferences.getInt("unknown", requested),
                    read == null ? null : ScanResult.Status.valueOf(read),
                    delete == null ? null : DeleteResult.Status.valueOf(delete));
        } catch (IllegalArgumentException e) {
            return RecoveryReport.stopped(RecoveryReport.Issue.INTERRUPTED, requested, null);
        }
    }
}
