package it.simonespadino.smsoflife.telephony;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;
import it.simonespadino.smsoflife.R;

public final class RespondViaMessageService extends Service {
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, R.string.sending_unavailable, Toast.LENGTH_LONG).show();
        stopSelf(startId);
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
