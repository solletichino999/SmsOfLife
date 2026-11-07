package it.simonespadino.smsoflife.ui;

import android.app.Application;
import android.Manifest;
import android.content.pm.PackageManager;
import it.simonespadino.smsoflife.R;
import it.simonespadino.smsoflife.data.RecoveryJournal;
import it.simonespadino.smsoflife.data.SmsRawRecord;
import it.simonespadino.smsoflife.domain.RecoveryReport;
import it.simonespadino.smsoflife.domain.SmsRecoveryManager;
import java.util.List;
import java.util.ArrayList;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import it.simonespadino.smsoflife.data.ScanResult;
import it.simonespadino.smsoflife.data.SmsProviderRepository;
import it.simonespadino.smsoflife.domain.SmsRecordSelection;
import it.simonespadino.smsoflife.domain.SmsRoleManager;

public final class MainViewModel extends AndroidViewModel {
    // Prevent a second Activity/ViewModel from starting writes while a prior one finishes its journal.
    // Contains no Activity/context or message data. Process death resets it; the durable journal stays uncertain.
    private static RecoveryReport lastProcessReport;
    private RecoveryReport observedProcessReport;
    private static final MutableLiveData<Boolean> PROCESS_RECOVERY = new MutableLiveData<>(false);
    private final MutableLiveData<ScanResult> result = new MutableLiveData<>(ScanResult.state(ScanResult.Status.IDLE));
    private final MutableLiveData<SmsRoleManager.Preparation> preparation = new MutableLiveData<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService canceller = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SmsProviderRepository repository;
    private final RecoveryJournal journal;
    private final MutableLiveData<RecoveryReport> recovery = new MutableLiveData<>();
    public boolean recovering;
    public boolean showingRecovery;
    private boolean confirmationOpen;
    private List<SmsRawRecord> confirmation;
    public final SmsRoleManager roles;
    public final SmsRecordSelection selection = new SmsRecordSelection();
    public boolean suspiciousOnly;
    public SmsRecordSelection.Sort sort = SmsRecordSelection.Sort.NEWEST;
    public boolean showingResults;
    public boolean permissionAttempted;
    public boolean preparingRole;
    // Saved by the Activity across the system role dialog; contains no SMS metadata.
    public boolean roleRequestedForScan;
    public int notice;
    private CancellationSignal cancellation;
    private volatile boolean cleared;

    public MainViewModel(@NonNull Application application) {
        super(application);
        repository = new SmsProviderRepository(application);
        roles = new SmsRoleManager(application);
        journal = new RecoveryJournal(application);
        recovery.setValue(journal.load());
        showingRecovery = recovery.getValue() != null || roles.state() == SmsRoleManager.State.HELD;
    }
    public LiveData<RecoveryReport> recovery() { return recovery; }
    public RecoveryReport report() { return recovery.getValue(); }
    public LiveData<Boolean> processRecovery() { return PROCESS_RECOVERY; }
    public boolean recoveryRunning() { return Boolean.TRUE.equals(PROCESS_RECOVERY.getValue()); }
    public void refreshSummary() {
        if (!recoveryRunning() && !recovering && lastProcessReport != null
                && lastProcessReport != observedProcessReport) {
            observedProcessReport = lastProcessReport;
            recovery.setValue(lastProcessReport);
        }
    }
    public boolean busy() { return scanning() || preparingRole || recoveryRunning(); }
    public boolean canDelete() {
        return roles.state() == SmsRoleManager.State.HELD
                && hasReadPermission();
    }
    public boolean hasReadPermission() {
        return getApplication().checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }
    public List<SmsRawRecord> openConfirmation() {
        if (busy() || confirmationOpen || !canDelete() || current().status != ScanResult.Status.SUCCESS) return null;
        confirmation = new ArrayList<>(selection.snapshot());
        if (confirmation.isEmpty()) { confirmation = null; return null; }
        confirmationOpen = true;
        return java.util.Collections.unmodifiableList(confirmation);
    }
    public void dismissConfirmation() { confirmationOpen = false; confirmation = null; }
    public void deleteConfirmed() {
        if (!confirmationOpen || confirmation == null || busy()) return;
        List<SmsRawRecord> snapshot = new ArrayList<>(confirmation);
        dismissConfirmation();
        if (!canDelete()) { notice = R.string.delete_prerequisite; return; }
        recovering = true;
        PROCESS_RECOVERY.setValue(true);
        showingRecovery = true;
        notice = 0;
        selection.replace(java.util.Collections.emptyList());
        worker.execute(() -> {
            RecoveryReport report;
            if (!journal.begin(snapshot.size())) {
                report = RecoveryReport.stopped(RecoveryReport.Issue.STORAGE_FAILED, snapshot.size(), null);
            } else {
                try { report = new SmsRecoveryManager(repository, () -> !cleared && canDelete()).deleteConfirmed(snapshot); }
                catch (RuntimeException e) {
                    // An exception after a provider call can have an unknown write outcome. Never retry.
                    report = RecoveryReport.stopped(RecoveryReport.Issue.INTERRUPTED, snapshot.size(), null);
                }
                if (!journal.finish(report)) {
                    report = new RecoveryReport(RecoveryReport.Issue.STORAGE_FAILED, report.requested,
                            report.attempted, report.reportedChanges, report.inactive, report.active, report.unknown,
                            report.readError, report.deleteError);
                }
            }
            RecoveryReport completed = report;
            main.post(() -> {
                recovering = false;
                lastProcessReport = completed;
                observedProcessReport = completed;
                if (!cleared) {
                    showingRecovery = true;
                    result.setValue(ScanResult.state(ScanResult.Status.IDLE));
                    recovery.setValue(completed);
                }
                PROCESS_RECOVERY.setValue(false);
            });
        });
    }
    public LiveData<ScanResult> result() { return result; }
    public ScanResult current() { return result.getValue(); }
    public LiveData<SmsRoleManager.Preparation> preparation() { return preparation; }
    public boolean scanning() { return current().status == ScanResult.Status.SCANNING; }
    public void scan() {
        if (busy() || confirmationOpen) return;
        notice = 0;
        showingRecovery = false;
        selection.replace(java.util.Collections.emptyList());
        CancellationSignal signal = new CancellationSignal();
        cancellation = signal;
        result.setValue(ScanResult.state(ScanResult.Status.SCANNING));
        worker.execute(() -> {
            ScanResult scanned = repository.scan(signal);
            main.post(() -> {
                if (cleared) return;
                ScanResult finalResult = signal.isCanceled()
                        ? ScanResult.state(ScanResult.Status.CANCELLED) : scanned;
                cancellation = null;
                selection.replace(finalResult.records);
                if (finalResult.status == ScanResult.Status.SUCCESS || finalResult.status == ScanResult.Status.EMPTY) {
                    showingResults = true;
                }
                result.setValue(finalResult);
            });
        });
    }
    public void cancelScan() {
        CancellationSignal signal = cancellation;
        if (signal != null) canceller.execute(signal::cancel);
    }
    public void prepareRole(boolean forScan) {
        if (busy() || !hasReadPermission()) return;
        if (forScan ? !current().canRequestReadRole(hasReadPermission())
                : selection.count() == 0 || current().status != ScanResult.Status.SUCCESS) return;
        roleRequestedForScan = forScan;
        preparingRole = true;
        worker.execute(() -> {
            SmsRoleManager.Preparation prepared = roles.prepareRequest();
            main.post(() -> {
                if (cleared) return;
                preparingRole = false;
                preparation.setValue(prepared);
            });
        });
    }
    public void onRoleRequestResult() {
        boolean retryScan = roleRequestedForScan;
        roleRequestedForScan = false;
        if (roles.state() != SmsRoleManager.State.HELD) {
            notice = R.string.role_declined;
        } else if (retryScan) {
            // Retry only reading, and only after a real grant with READ_SMS still present.
            if (hasReadPermission()) scan();
            else notice = R.string.permission_denied;
        } else {
            notice = R.string.role_granted_for_deletion;
        }
    }
    public void consumePreparation() { preparation.setValue(null); }
    @Override protected void onCleared() {
        cleared = true;
        cancelScan();
        worker.shutdown();
        canceller.shutdown();
    }
}
