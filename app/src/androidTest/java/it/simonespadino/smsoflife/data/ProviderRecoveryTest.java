package it.simonespadino.smsoflife.data;

import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.CancellationSignal;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.Collections;
import it.simonespadino.smsoflife.domain.*;
import static org.junit.Assert.*;

/** Local, in-memory test provider. NEVER connects to or mutates Android's TelephonyProvider. */
@RunWith(AndroidJUnit4.class)
public class ProviderRecoveryTest {
    private LocalProvider provider;
    private SmsProviderRepository repository;
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Before public void setUp() {
        provider = new LocalProvider();
        ProviderInfo info = new ProviderInfo(); info.authority = "synthetic.test.only";
        provider.attachInfo(context, info);
        repository = new SmsProviderRepository(ContentResolver.wrap(provider), () -> true);
    }
    @After public void tearDown() { provider.db.close(); }
    private SmsRawRecord row(String address, Long port, Long date) { return new SmsRawRecord(1,address,port,date,0); }

    @Test public void realResolverDeleteMarksOnlyTheUnchangedSelectedRow() {
        provider.add(1,"synthetic",134020L,10L);
        provider.add(2,"other",80L,20L);
        RecoveryReport report = new SmsRecoveryManager(repository, () -> true)
                .deleteConfirmed(Collections.singletonList(row("synthetic",134020L,10L)));
        assertTrue(report.cleanupVerified());
        assertEquals(Integer.valueOf(1), repository.read(1).records.get(0).deleted);
        assertTrue(repository.read(2).records.get(0).isActive());
    }
    @Test public void guardedPredicateRejectsChangesBetweenReadAndWrite() {
        provider.add(1,"new-sender",134020L,10L);
        DeleteResult result = repository.deleteUnchanged(row("old-sender",134020L,10L));
        assertEquals(0,result.count);
        assertTrue(repository.read(1).records.get(0).isActive());
    }
    @Test public void boundInjectionTextDoesNotBroadenDeletion() {
        provider.add(1,"' OR 1=1 --",null,null);
        provider.add(2,"other",null,null);
        assertEquals(1,repository.deleteUnchanged(row("' OR 1=1 --",null,null)).count);
        assertTrue(repository.read(2).records.get(0).isActive());
    }
    @Test public void cursorAndSchemaFailuresAreNotEmptyScans() {
        provider.mode = 1; assertEquals(ScanResult.Status.NULL_CURSOR,repository.scan(new CancellationSignal()).status);
        provider.mode = 2; assertEquals(ScanResult.Status.MISSING_COLUMNS,repository.scan(new CancellationSignal()).status);
        provider.mode = 3; assertEquals(ScanResult.Status.ACCESS_DENIED,repository.scan(new CancellationSignal()).status);
        provider.mode = 4; assertEquals(ScanResult.Status.DATABASE_ERROR,repository.scan(new CancellationSignal()).status);
        provider.mode = 5; assertEquals(ScanResult.Status.UNSUPPORTED,repository.scan(new CancellationSignal()).status);
    }
    @Test public void missingReadAccessPreventsTreatingAbsenceAsVerified() {
        SmsProviderRepository restricted = new SmsProviderRepository(ContentResolver.wrap(provider), () -> false);
        assertEquals(ScanResult.Status.EMPTY_UNVERIFIED, restricted.read(1).status);
    }
    @Test public void writeSecurityExceptionIsTyped() {
        provider.denyWrite = true;
        assertEquals(DeleteResult.Status.ACCESS_DENIED, repository.deleteUnchanged(row(null,null,null)).status);
    }
    @Test public void journalSurvivesRecreationWithoutSavingSmsMetadata() {
        Context isolated = new ContextWrapper(context) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return context.getSharedPreferences("synthetic_recovery_test",mode);
            }
        };
        SharedPreferences prefs = isolated.getSharedPreferences("ignored",0);
        prefs.edit().clear().commit();
        try {
            assertTrue(new RecoveryJournal(isolated).begin(2));
            assertEquals(RecoveryReport.Issue.INTERRUPTED,new RecoveryJournal(isolated).load().issue);
            RecoveryReport report = new RecoveryReport(RecoveryReport.Issue.NONE,2,2,2,2,0,0,null,null);
            assertTrue(new RecoveryJournal(isolated).finish(report));
            assertTrue(new RecoveryJournal(isolated).load().cleanupVerified());
            assertFalse(prefs.contains("address")); assertFalse(prefs.contains("ids"));
            assertFalse(RecoveryCompletion.complete(new RecoveryJournal(isolated).load(),false));
        } finally { prefs.edit().clear().commit(); }
    }

    public static final class LocalProvider extends ContentProvider {
        SQLiteDatabase db;
        int mode;
        boolean denyWrite;
        @Override public boolean onCreate() {
            db = SQLiteDatabase.create(null);
            db.execSQL("CREATE TABLE metadata (_id INTEGER PRIMARY KEY, address TEXT, destination_port INTEGER, date INTEGER, deleted INTEGER)");
            return true;
        }
        void add(long id,String address,Long port,Long date) {
            ContentValues values = new ContentValues(); values.put("_id",id); values.put("address",address);
            values.put("destination_port",port); values.put("date",date); values.put("deleted",0);
            db.insertOrThrow("metadata",null,values);
        }
        @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
            assertEquals("content://sms/raw",uri.toString());
            assertArrayEquals(new String[]{"_id","address","destination_port","date","deleted"},projection);
            switch (mode) {
                case 1: return null;
                case 2: return new MatrixCursor(new String[]{"_id"});
                case 3: throw new SecurityException("synthetic denial");
                case 4: throw new SQLiteException("synthetic database failure");
                case 5: throw new UnsupportedOperationException("synthetic URI refusal");
                default: return db.query("metadata",projection,selection,args,null,null,sort);
            }
        }
        @Override public int delete(Uri uri,String selection,String[] args) {
            assertEquals("content://sms/raw",uri.toString());
            assertTrue(selection.startsWith("_id = ? AND deleted = ?"));
            if (denyWrite) throw new SecurityException("synthetic denial");
            ContentValues values = new ContentValues(); values.put("deleted",1);
            return db.update("metadata",values,selection,args);
        }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException(); }
        @Override public int update(Uri uri,ContentValues values,String selection,String[] args) { throw new UnsupportedOperationException(); }
    }
}
