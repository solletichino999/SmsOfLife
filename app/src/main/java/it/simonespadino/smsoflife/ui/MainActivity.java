package it.simonespadino.smsoflife.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ScrollView;
import androidx.appcompat.app.AlertDialog;
import java.util.List;
import it.simonespadino.smsoflife.data.SmsRawRecord;
import it.simonespadino.smsoflife.domain.RecoveryReport;
import it.simonespadino.smsoflife.domain.RecoveryCompletion;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import it.simonespadino.smsoflife.R;
import it.simonespadino.smsoflife.data.ScanResult;
import it.simonespadino.smsoflife.domain.SmsRecordSelection;
import it.simonespadino.smsoflife.domain.SmsRoleManager;

public final class MainActivity extends AppCompatActivity {
    private MainViewModel model;
    private SmsRecordAdapter adapter;
    private AlertDialog deletionDialog;
    private final ActivityResultLauncher<String> permissionRequest = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                model.permissionAttempted = true;
                if (granted) model.scan();
                else { model.notice = R.string.permission_denied; render(); }
            });
    private final ActivityResultLauncher<Intent> roleRequest = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                // A result code alone does not establish that the role is held.
                if (model.roles.state() == SmsRoleManager.State.HELD) {
                    model.notice = R.string.role_granted_for_deletion;
                    render();
                }
                else { model.notice = R.string.role_declined; render(); }
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        model = new ViewModelProvider(this).get(MainViewModel.class);
        if (state != null) model.permissionAttempted |= state.getBoolean("permission_attempted");
        adapter = new SmsRecordAdapter(model.selection, this::renderSelection);
        RecyclerView list = findViewById(R.id.records);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        list.setSaveEnabled(false);
        findViewById(R.id.scan).setOnClickListener(v -> model.scan());
        findViewById(R.id.rescan).setOnClickListener(v -> model.scan());
        findViewById(R.id.cancel_scan).setOnClickListener(v -> {
            model.cancelScan(); model.notice = R.string.cancelling; render();
        });
        findViewById(R.id.home).setOnClickListener(v -> { model.showingResults = false; render(); });
        findViewById(R.id.delete_selected).setOnClickListener(v -> confirmDeletion());
        findViewById(R.id.restore_settings).setOnClickListener(v -> openDefaultSettings());
        findViewById(R.id.check_restoration).setOnClickListener(v -> render());
        findViewById(R.id.recovery_scan).setOnClickListener(v -> model.scan());
        findViewById(R.id.recovery_home).setOnClickListener(v -> {
            model.showingRecovery = false; model.showingResults = false; render();
        });
        findViewById(R.id.read_permission).setOnClickListener(v -> explainPermission());
        findViewById(R.id.request_role).setOnClickListener(v -> explainRole());
        findViewById(R.id.default_settings).setOnClickListener(v -> { model.showingRecovery = true; render(); });
        CheckBox filter = findViewById(R.id.suspicious_only);
        filter.setChecked(model.suspiciousOnly);
        filter.setOnCheckedChangeListener((button, checked) -> { model.suspiciousOnly = checked; renderRows(); });
        Spinner sort = findViewById(R.id.sort);
        ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(this, R.array.sort_options,
                android.R.layout.simple_spinner_item);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sort.setAdapter(sortAdapter);
        sort.setSelection(model.sort.ordinal());
        sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                model.sort = SmsRecordSelection.Sort.values()[position]; renderRows();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (model.busy()) return;
                if (!model.showingRecovery && model.roles.state() == SmsRoleManager.State.HELD) {
                    model.showingRecovery = true; render();
                } else if (model.showingRecovery) {
                    // Leaving does not mark recovery complete. On reopening, a held role shows this screen again.
                    if (model.roles.state() == SmsRoleManager.State.HELD) finish();
                    else { model.showingRecovery = false; model.showingResults = false; render(); }
                } else if (model.showingResults) { model.showingResults = false; render(); }
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
            }
        });
        model.result().observe(this, result -> render());
        model.recovery().observe(this, report -> render());
        model.processRecovery().observe(this, running -> { model.refreshSummary(); render(); });
        model.preparation().observe(this, prepared -> {
            if (prepared == null) return;
            model.consumePreparation();
            if (prepared == SmsRoleManager.Preparation.READY) {
                try { roleRequest.launch(model.roles.requestIntent()); }
                catch (ActivityNotFoundException | SecurityException | IllegalArgumentException e) {
                    model.notice = R.string.role_request_failed;
                }
            } else if (prepared == SmsRoleManager.Preparation.ALREADY_HELD) {
                model.notice = R.string.role_granted_for_deletion;
            } else {
                model.notice = prepared == SmsRoleManager.Preparation.SAVE_FAILED
                        ? R.string.role_save_failed : R.string.role_request_failed;
            }
            render();
        });
    }
    @Override protected void onResume() { super.onResume(); if (model != null) render(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("permission_attempted", model.permissionAttempted);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (deletionDialog != null) deletionDialog.dismiss();
        super.onDestroy();
    }
    private void confirmDeletion() {
        List<SmsRawRecord> snapshot = model.openConfirmation();
        if (snapshot == null) { render(); return; }
        StringBuilder ids = new StringBuilder();
        for (SmsRawRecord row : snapshot) {
            if (ids.length() > 0) ids.append(", ");
            ids.append(row.id);
        }
        // All IDs remain accessible even for long selections. Nothing is saved to disk.
        TextView message = new TextView(this);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        message.setPadding(padding, padding / 2, padding, padding / 2);
        message.setText(getString(R.string.delete_confirmation, snapshot.size(), ids.toString()));
        message.setSaveEnabled(false);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(message);
        deletionDialog = new MaterialAlertDialogBuilder(this).setTitle(R.string.delete_title)
                .setView(scroll).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm_delete, (dialog, which) -> { model.deleteConfirmed(); render(); })
                .create();
        deletionDialog.setOnDismissListener(dialog -> { model.dismissConfirmation(); deletionDialog = null; });
        deletionDialog.show();
    }
    private boolean hasReadPermission() {
        return checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }
    private void explainPermission() {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.permission_title)
                .setMessage(R.string.permission_explanation).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.continue_label, (dialog, which) -> {
                    model.permissionAttempted = true;
                    permissionRequest.launch(Manifest.permission.READ_SMS);
                }).show();
    }
    private void explainRole() {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.role_title)
                .setMessage(R.string.role_warning).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.continue_label, (dialog, which) -> { model.prepareRole(); render(); })
                .show();
    }
    private void openDefaultSettings() {
        try { startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)); }
        catch (ActivityNotFoundException | SecurityException e) {
            model.notice = R.string.settings_unavailable; render();
        }
    }
    private void render() {
        ScanResult result = model.current();
        SmsRoleManager.State role = model.roles.state();
        boolean held = role == SmsRoleManager.State.HELD;
        boolean busy = model.busy();
        text(R.id.device_status, getString(R.string.device_status, Build.VERSION.RELEASE, Build.VERSION.SDK_INT));
        text(R.id.permission_status, getString(hasReadPermission() ? R.string.permission_granted : R.string.permission_missing));
        int roleText = held ? R.string.role_held : role == SmsRoleManager.State.AVAILABLE ? R.string.role_available
                : role == SmsRoleManager.State.UNAVAILABLE ? R.string.role_unavailable : R.string.role_error;
        text(R.id.role_status, getString(roleText));
        String original = model.roles.originalPackage();
        text(R.id.original_app, !model.roles.originalRecorded() ? getString(R.string.original_not_recorded)
                : original == null ? getString(R.string.original_unknown)
                : getString(R.string.original_app, model.roles.originalLabel(), original));
        visible(R.id.role_banner, held);
        visible(R.id.home_panel, !model.showingResults && !model.showingRecovery);
        visible(R.id.results_panel, model.showingResults && !model.showingRecovery);
        visible(R.id.recovery_panel, model.showingRecovery);
        visible(R.id.scan_status, !model.showingRecovery);
        visible(R.id.progress, busy);
        visible(R.id.cancel_scan, model.scanning());
        findViewById(R.id.scan).setEnabled(!busy);
        findViewById(R.id.rescan).setEnabled(!busy);
        boolean accessNeeded = result.needsAccess() && !busy;
        visible(R.id.read_permission, accessNeeded && !hasReadPermission());
        findViewById(R.id.home).setEnabled(!busy);
        findViewById(R.id.default_settings).setEnabled(!busy);
        findViewById(R.id.restore_settings).setEnabled(!busy);
        findViewById(R.id.recovery_scan).setEnabled(!busy);
        findViewById(R.id.recovery_home).setEnabled(!busy);
        String status = getString(statusText(result.status));
        if (result.status == ScanResult.Status.MISSING_COLUMNS && !result.missingColumns.isEmpty()) {
            status += " " + String.join(", ", result.missingColumns);
        }
        if (result.needsAccess() && held) status += " " + getString(R.string.access_blocked_with_role);
        text(R.id.scan_status, status);
        visible(R.id.notice, model.notice != 0);
        if (model.notice != 0) text(R.id.notice, getString(model.notice));
        renderRecovery(role);
        renderRows();
    }
    private void renderRows() {
        if (adapter == null) return;
        adapter.submit(model.selection.visible(model.suspiciousOnly, model.sort));
        text(R.id.result_count, getString(R.string.result_count, adapter.getItemCount(), model.selection.totalCount()));
        text(R.id.no_matches, getString(model.selection.totalCount() == 0
                ? R.string.no_undeleted_records : R.string.no_matches));
        visible(R.id.no_matches, model.current().status == ScanResult.Status.SUCCESS && adapter.getItemCount() == 0);
        renderSelection();
    }
    private void renderSelection() {
        text(R.id.selection_count, getString(R.string.selection_count, model.selection.count()));
        boolean selected = model.selection.count() > 0 && model.current().status == ScanResult.Status.SUCCESS;
        findViewById(R.id.delete_selected).setEnabled(selected && !model.busy() && model.canDelete());
        visible(R.id.request_role, selected && model.roles.state() == SmsRoleManager.State.AVAILABLE);
        findViewById(R.id.request_role).setEnabled(!model.busy() && hasReadPermission());
        adapter.setSelectionEnabled(!model.busy());
    }
    private void renderRecovery(SmsRoleManager.State role) {
        RecoveryReport report = model.report();
        int outcome = model.recoveryRunning() ? R.string.recovery_running : report == null ? R.string.recovery_no_operation
                : recoveryText(report.issue);
        String details = getString(outcome);
        if (report != null && !model.recoveryRunning()) {
            if (report.readError != null) details += "\n" + getString(statusText(report.readError));
            if (report.deleteError != null) {
                int error;
                switch (report.deleteError) {
                    case ACCESS_DENIED: error = R.string.delete_access_denied; break;
                    case UNSUPPORTED: error = R.string.delete_unsupported; break;
                    case DATABASE_ERROR: error = R.string.delete_database_error; break;
                    default: error = R.string.delete_provider_error;
                }
                details += "\n" + getString(error);
            }
        }
        text(R.id.recovery_outcome, details);
        boolean showCounts = report != null && !model.recoveryRunning() && report.issue != RecoveryReport.Issue.INTERRUPTED;
        visible(R.id.recovery_counts, showCounts);
        if (showCounts) text(R.id.recovery_counts, getString(R.string.recovery_counts, report.requested,
                report.attempted, report.reportedChanges, report.inactive, report.active, report.unknown));
        String original = model.roles.originalPackage();
        text(R.id.restore_original, original == null ? getString(R.string.original_unknown)
                : getString(R.string.original_app, model.roles.originalLabel(), original));
        String current = model.roles.currentPackage();
        text(R.id.restore_current, getString(R.string.current_app, current == null ? getString(R.string.unknown) : current));
        int roleText = role == SmsRoleManager.State.HELD ? R.string.role_held
                : role == SmsRoleManager.State.AVAILABLE ? R.string.role_available
                : role == SmsRoleManager.State.UNAVAILABLE ? R.string.role_unavailable : R.string.role_error;
        text(R.id.restore_role, getString(roleText));
        boolean restored = RecoveryCompletion.restorationVerified(role == SmsRoleManager.State.AVAILABLE,
                getPackageName(), current);
        text(R.id.restore_status, getString(restored ? R.string.restore_verified
                : role == SmsRoleManager.State.HELD || getPackageName().equals(current)
                ? R.string.restore_required : R.string.restore_unknown));
        boolean complete = !model.recoveryRunning() && RecoveryCompletion.complete(report, restored);
        visible(R.id.recovery_complete, complete);
    }
    private int recoveryText(RecoveryReport.Issue issue) {
        switch (issue) {
            case NONE: return R.string.recovery_verified;
            case INVALID_SELECTION: return R.string.recovery_invalid;
            case PREREQUISITE_LOST: return R.string.delete_prerequisite;
            case PRECHECK_FAILED: return R.string.recovery_precheck;
            case CHANGED: return R.string.recovery_changed;
            case DELETE_FAILED: return R.string.recovery_delete_failed;
            case UNEXPECTED_COUNT: return R.string.recovery_count_anomaly;
            case STILL_ACTIVE: return R.string.recovery_still_active;
            case VERIFY_FAILED: return R.string.recovery_unverified;
            case STORAGE_FAILED: return R.string.recovery_storage;
            default: return R.string.recovery_interrupted;
        }
    }
    private void text(int id, String value) { ((TextView) findViewById(id)).setText(value); }
    private void visible(int id, boolean show) { findViewById(id).setVisibility(show ? View.VISIBLE : View.GONE); }
    private int statusText(ScanResult.Status status) {
        switch (status) {
            case SCANNING: return R.string.scan_running;
            case SUCCESS: return R.string.scan_success;
            case EMPTY: return R.string.scan_empty;
            case ACCESS_DENIED: return R.string.scan_denied;
            case EMPTY_UNVERIFIED: return R.string.scan_empty_unverified;
            case NULL_CURSOR: return R.string.scan_null;
            case UNSUPPORTED: return R.string.scan_unsupported;
            case MISSING_COLUMNS: return R.string.scan_columns;
            case DATABASE_ERROR: return R.string.scan_database;
            case INVALID_DATA: return R.string.scan_invalid;
            case PROVIDER_ERROR: return R.string.scan_provider;
            case CANCELLED: return R.string.scan_cancelled;
            default: return R.string.scan_idle;
        }
    }
}
