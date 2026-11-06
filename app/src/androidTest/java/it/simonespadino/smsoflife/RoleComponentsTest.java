package it.simonespadino.smsoflife;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import static org.junit.Assert.*;

/** Uses the real package manager, without granting roles or receiving/sending messages. */
@RunWith(AndroidJUnit4.class)
public class RoleComponentsTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final PackageManager pm = context.getPackageManager();
    private Intent own(Intent intent) { return intent.setPackage(context.getPackageName()); }

    @Test public void smsReceiverHasRequiredProtection() {
        List<ResolveInfo> receivers = pm.queryBroadcastReceivers(own(new Intent(Telephony.Sms.Intents.SMS_DELIVER_ACTION)), 0);
        assertEquals(1, receivers.size());
        assertEquals(Manifest.permission.BROADCAST_SMS, receivers.get(0).activityInfo.permission);
        assertTrue(receivers.get(0).activityInfo.exported);
    }
    @Test public void mmsReceiverHasRequiredMimeTypeAndProtection() {
        Intent intent = new Intent(Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION).setType("application/vnd.wap.mms-message");
        List<ResolveInfo> receivers = pm.queryBroadcastReceivers(own(intent), 0);
        assertEquals(1, receivers.size());
        assertEquals(Manifest.permission.BROADCAST_WAP_PUSH, receivers.get(0).activityInfo.permission);
        assertTrue(receivers.get(0).activityInfo.exported);
    }
    @Test public void sendToAndQuickReplyResolveForAllFourSchemes() {
        for (String scheme : new String[]{"sms", "smsto", "mms", "mmsto"}) {
            Intent sendTo = own(new Intent(Intent.ACTION_SENDTO, Uri.parse(scheme + ":synthetic")));
            List<ResolveInfo> activities = pm.queryIntentActivities(sendTo, PackageManager.MATCH_DEFAULT_ONLY);
            assertEquals(1, activities.size());
            assertTrue(activities.get(0).activityInfo.exported);
            Intent respond = own(new Intent(TelephonyManager.ACTION_RESPOND_VIA_MESSAGE, Uri.parse(scheme + ":synthetic")));
            List<ResolveInfo> services = pm.queryIntentServices(respond, 0);
            assertEquals(1, services.size());
            assertTrue(services.get(0).serviceInfo.exported);
            assertEquals(Manifest.permission.SEND_RESPOND_VIA_MESSAGE, services.get(0).serviceInfo.permission);
        }
    }
    @Test public void noNetworkOrSendingPermissionsDeclared() throws Exception {
        String[] requested = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
        assertNotNull(requested);
        for (String permission : requested) {
            assertNotEquals(Manifest.permission.INTERNET, permission);
            assertNotEquals(Manifest.permission.SEND_SMS, permission);
            assertNotEquals(Manifest.permission.READ_CONTACTS, permission);
        }
    }
}
