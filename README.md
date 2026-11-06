# SmsOfLife

SmsOfLife is a temporary, offline Android recovery tool written in Java with XML Views. It inspects pending SMS queue metadata, highlights a known WAP Push port pattern, and lets the user selectively delete queue records before restoring their usual SMS application and uninstalling the tool.

Malformed WAP Push messages left in the pending queue can be associated with repeated crashes of `com.android.phone`, sometimes described as “SMS of Death.” SmsOfLife does not inspect message contents or establish whether a message is malicious. **Removing problematic queue records does not fix the underlying operating-system vulnerability or prove that the crashes have been resolved.** Install relevant system security updates separately.

## Requirements and scope

| Item | Configuration |
| --- | --- |
| Supported Android versions by design | Android 14 and later, including Android 15, 16 and 17 |
| Minimum SDK | API 34 |
| Target SDK | API 37 |
| Compile SDK | API 37, minor API level 2 |
| Application ID | `it.simonespadino.smsoflife` |
| Implementation | Java, XML layouts, AndroidX Views and RecyclerView |
| Java source/target compatibility | Java 11 |
| Installation | Signed APK via sideload |

The installed app requires no root, unlocked bootloader, ADB, network connection, or external service. Interface text is currently English. SMS provider access and the SMS role depend on the device, Android implementation, user profile, and system policies. SDK compatibility alone does not guarantee access to the pending queue.

This is not an SMS/MMS client, antivirus scanner, or permanent background protection service.

## Install and scan

1. Install the APK on the device using Android's package installer. If necessary, allow installation from the particular app used to open the APK.
2. Open **SmsOfLife** and choose **Scan device**.
3. The app first queries `content://sms/raw` with its current access. If read access is missing, choose **Allow SMS reading**, review the explanation, and respond to Android's `READ_SMS` permission request.
4. Review the results. Scanning does not request the default SMS role. If Android or the installer refuses read access, the app reports the problem rather than switching SMS applications to scan.

The query reads only `_id`, `address`, `destination_port`, `date`, and `deleted`. It does not request SMS bodies or PDUs. Queries and deletion work run outside the main UI thread.

Results show the record ID, sender, date, stored and decoded destination port, record state, and diagnostic indicator. Use the sort selector and **Suspicious only** filter to inspect the list.

**Records with `deleted=1` are excluded from the results, visible totals, and suspicious-message filter.** They remain available to internal verification queries. A null `deleted` value is shown as unknown and cannot be selected; missing required columns stop the scan.

A valid empty response, denied access, an unverified empty response, a null cursor, unsupported queries, missing columns, invalid metadata, and database/provider errors have distinct outcomes. An empty list is not a declaration that the device is repaired or free from malicious messages.

## Interpret suspicious records

The **Suspicious only** filter matches the stored value `destination_port = 134020`. Under the AOSP encoding, this is destination port 2948 with the 3GPP format flag (`131072 + 2948`). It is a diagnostic indicator relevant to the reported problem, **not proof of malicious content**. Ordinary WAP Push messages may also match.

Other supported encodings that decode to port 2948 are identified separately and do not match this exact-value filter. The absence of an indicator is not a safety verdict. SmsOfLife never passes suspicious PDUs to framework parsers, constructs malicious messages, or attempts to reproduce an exploit.

Each queue record may represent a message part. Record counts must not be interpreted as counts of complete user-visible SMS/MMS messages.

## Delete selected records

1. Select active records manually using their checkboxes. The app does not automatically select suspicious records. Selections remain selected when hidden by a filter, and a new scan clears all selections.
2. If SmsOfLife does not hold the SMS role, choose **Enable deletion with temporary SMS role**. Review the warning before continuing to Android's role-consent dialog. The existing default SMS package is saved locally before that request.
3. After Android grants the role, review your selection and choose **Delete selected**. Granting the role does not delete anything automatically. The deletion button remains disabled without the required role, read permission, and selection.
4. Review the confirmation dialog: it lists the total number and **all selected IDs, including hidden selected rows**. Confirm only if you accept losing those messages or message parts. The app provides no undo.
5. Keep the app open until the operation reports its outcome, then restore the original SMS application promptly.

The SMS role is requested using `RoleManager`. It is a prerequisite for deletion in this app, not a guarantee that the device permits writes to `sms/raw`.

Before the first write, every selected ID is re-read and compared with the confirmed snapshot. Missing, changed, or inactive records stop the operation. Each `ContentResolver.delete()` call uses `content://sms/raw` with placeholders and bound arguments for the ID, active state, and available metadata. This checks the metadata again in the write predicate rather than trusting the ID alone.

The app expects one affected row per call. Zero, unexpected counts, errors, or lost prerequisites stop further writes. Multi-record deletion is not an atomic transaction: earlier calls may have succeeded when a later call fails. There are no automatic deletion retries.

After writes have been attempted, the app queries the selected IDs again. It accepts both:

- A record absent from a successfully verified query result.
- A record with matching metadata and `deleted=1`.

A positive return count alone is insufficient. Still-active records, changed metadata, inaccessible verification results, or lost role/permission prevent a successful cleanup verdict. The recovery screen distinguishes records selected, calls attempted, provider-reported changes, verified inactive/absent records, still-active records, and unverified records.

The app never uses a permanent-deletion URI, deletes all WAP Push messages, removes all messages from a sender, resets the provider, accesses its internal database directly, or performs a factory reset.

## Temporary SMS role and incoming-message risk

**While SmsOfLife is the default SMS application, incoming SMS/MMS are not saved by this app and may be lost or not received correctly. Sending and quick replies are unavailable.** Keep this period as short as possible, including when deletion fails or you cancel it.

The manifest contains the minimum role components: a protected `SMS_DELIVER` receiver, a protected `WAP_PUSH_DELIVER` receiver for `application/vnd.wap.mms-message`, a `SENDTO` activity for `sms`, `smsto`, `mms`, and `mmsto`, and a protected `RESPOND_VIA_MESSAGE` service for those schemes. These are deliberately not a complete messaging implementation. They do not parse incoming PDUs, download MMS, send messages, or write incoming messages to the provider.

## Restore the original SMS app and uninstall

1. Open **Restore previous SMS app** or use the recovery screen shown after deletion.
2. Check the recorded previous SMS app and current default package.
3. Choose **Restore original SMS app in Settings**. In Android's Default apps settings, select **SMS app**, then select the recorded previous application. If Android did not report an original package, select a trusted SMS application.
4. Return to SmsOfLife. It rechecks the actual role and default package; **Check restoration** also performs this check. Opening or returning from Settings alone is not proof of restoration.
5. The procedure is declared complete when cleanup was verified and Android confirms that SmsOfLife no longer holds the SMS role. Android does not need to disclose the replacement package, and selecting another SMS app instead of the recorded original also permits completion. The recorded previous app remains a guide for your choice in Settings.
6. Once the completion message appears, uninstall SmsOfLife through Android's normal app-management interface.

An unavailable replacement package name does not block the positive restoration result. This result confirms release of SmsOfLife's SMS role, not the identity of the replacement app. If Android cannot report the role itself, or still identifies SmsOfLife as the default package, completion remains blocked until a fresh check confirms release. If Settings cannot be opened, navigate manually to **Settings → Apps → Default apps → SMS app**; menu names may vary.

Restore the previous app even after a failed or interrupted repair. **Do not assume that uninstalling SmsOfLife automatically restores the previous SMS app.** Leaving the recovery screen does not mark the procedure complete.

## Interruption, privacy, and technical limits

Read records and selections are held only in memory. No SMS contents, addresses, IDs, dates, or PDUs are persistently stored by the app. It saves only the previous default SMS package and a local operation summary containing state, error categories, and counts. Preferences are excluded from backup and device transfer, and application backup is disabled.

If the process dies during deletion, the pending summary is treated as an uncertain result on reopening. The app does not resume deletion automatically. Some writes may already have happened; restore the SMS app and perform a fresh scan before considering a new manual attempt.

There is no `INTERNET` permission, analytics, advertising, telemetry, contacts access, cloud integration, or upload/export feature. The app does not log SMS metadata or raw provider exception messages.

`content://sms/raw` is implementation-specific rather than a portable SDK table contract. A manufacturer or system policy can refuse access, return a different schema, or behave differently despite the SMS role. The app reports those limits and stops the affected operation; it does not bypass Android security through root, reflection, privileged shell commands, or hidden APIs.

The queue can change concurrently. Snapshot checks and guarded predicates reduce the risk of deleting changed records, but there is no cross-call transaction or proof of identity beyond the metadata available to the app. Cleanup verification applies to selected queue IDs at that moment, not to the whole device or the underlying vulnerability.

## Build and test

Open the project in Android Studio and use its configured Gradle JDK with the checked-in wrapper. Java 11 is the app's source compatibility level, not a requirement to run Gradle on JDK 11. The project pins Gradle 9.8.2, SDK 37.2, Build Tools 37.0.0, and NDK 30.0.16248370. Install the configured SDK components and set the local SDK location through Android Studio or `local.properties`; do not change the SDK levels to work around an environment mismatch.

From the project root, or using the equivalent Android Studio Gradle tasks:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

To run instrumented tests with an emulator or test device connected to the development environment:

```sh
./gradlew :app:connectedDebugAndroidTest
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. It is debug-signed and installable for testing. Release signing is not configured in this project; distribution of a release APK requires a release signing configuration and a securely managed signing key. Runtime offline operation does not imply an offline first build: Gradle may need to download SDK components or dependencies.

The automated suite covers classification, port interpretation, filtering out deleted rows, selection, bound SQL predicates, preflight changes, delete counts, partial failures, permission/role loss, post-delete verification, journal recreation, completion conditions, manifest components, and Activity recreation. Android integration tests use a local in-memory provider and synthetic metadata, never the device's SMS queue. They do not grant roles or generate/send malicious SMS.

Validation recorded for this version:

- Debug build successful.
- 30 local Java tests passed.
- 14 instrumented tests passed on an Android 17/API 37 emulator.
- Lint: zero errors; one non-blocking `NotifyDataSetChanged` efficiency warning.
- Scanning with `READ_SMS` was reported working by the project owner on physical phones.
- Deletion against a physical phone's TelephonyProvider was also reported working by the project owner. The restoration check was subsequently updated to accept a confirmed release of the SMS role even when the replacement package is unavailable; this updated feedback still requires physical-device validation. The automated results do not establish compatibility with every Android version or manufacturer.
