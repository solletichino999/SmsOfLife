package it.simonespadino.smsoflife.domain;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Telephony;

public final class SmsRoleManager {
    public enum State { HELD, AVAILABLE, UNAVAILABLE, ERROR }
    public enum Preparation { READY, ALREADY_HELD, UNAVAILABLE, SAVE_FAILED, SYSTEM_ERROR }
    private final Context context;
    private final SharedPreferences preferences;
    private final RoleManager roles;

    public SmsRoleManager(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences("sms_role", Context.MODE_PRIVATE);
        roles = this.context.getSystemService(RoleManager.class);
    }
    public State state() {
        try {
            if (roles == null || !roles.isRoleAvailable(RoleManager.ROLE_SMS)) return State.UNAVAILABLE;
            return roles.isRoleHeld(RoleManager.ROLE_SMS) ? State.HELD : State.AVAILABLE;
        } catch (RuntimeException e) { return State.ERROR; }
    }
    /** Run on a worker: persist before Android's consent dialog, including across process death. */
    public Preparation prepareRequest() {
        State state = state();
        if (state == State.HELD) return Preparation.ALREADY_HELD;
        if (state == State.UNAVAILABLE) return Preparation.UNAVAILABLE;
        if (state == State.ERROR) return Preparation.SYSTEM_ERROR;
        try {
            String original = Telephony.Sms.getDefaultSmsPackage(context);
            if (context.getPackageName().equals(original)) return Preparation.ALREADY_HELD;
            boolean saved = preferences.edit().putBoolean("original_recorded", true)
                    .putString("original_package", original).commit();
            return saved ? Preparation.READY : Preparation.SAVE_FAILED;
        } catch (RuntimeException e) { return Preparation.SYSTEM_ERROR; }
    }
    public String currentPackage() {
        try { return Telephony.Sms.getDefaultSmsPackage(context); }
        catch (RuntimeException e) { return null; }
    }
    public boolean restorationVerified() {
        // UNAVAILABLE/ERROR are not evidence that the role is no longer held.
        return RecoveryCompletion.restorationVerified(state() == State.AVAILABLE,
                context.getPackageName(), currentPackage());
    }
    public Intent requestIntent() { return roles.createRequestRoleIntent(RoleManager.ROLE_SMS); }
    public boolean originalRecorded() { return preferences.getBoolean("original_recorded", false); }
    public String originalPackage() { return preferences.getString("original_package", null); }
    public String originalLabel() {
        String original = originalPackage();
        if (original == null) return null;
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(original, 0)).toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) { return original; }
    }
}
