package it.simonespadino.smsoflife.telephony;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import it.simonespadino.smsoflife.R;

/** Never reads recipients, bodies, or attachments from the incoming intent. */
public final class SmsSendToActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.app_name)
                .setMessage(R.string.sending_unavailable)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish()).show();
    }
}
