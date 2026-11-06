package it.simonespadino.smsoflife.telephony;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
/** Role eligibility only. Incoming MMS are not decoded, downloaded, or saved.
 * The consent screen warns about possible message loss. No provider writes or forwarding. */
public final class MmsWapPushReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION.equals(intent.getAction())) return;
        // Intentionally no message processing. The role consent describes this limitation.
    }
}
