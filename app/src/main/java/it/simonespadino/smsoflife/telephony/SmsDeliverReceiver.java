package it.simonespadino.smsoflife.telephony;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
/** Role eligibility only. Incoming SMS are not decoded or saved.
 * The consent screen warns about possible message loss. No provider writes or forwarding. */
public final class SmsDeliverReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) return;
        // Intentionally no message processing. The role consent describes this limitation.
    }
}
