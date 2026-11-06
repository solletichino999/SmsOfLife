package it.simonespadino.smsoflife;

import android.view.View;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import it.simonespadino.smsoflife.ui.MainActivity;
import it.simonespadino.smsoflife.ui.MainViewModel;
import static org.junit.Assert.*;

/** UI/lifecycle checks only: no role grants and no SMS reads or writes. */
@RunWith(AndroidJUnit4.class)
public class RecoveryUiTest {
    @Test public void noRoleEscalationIsOfferedWithoutADeletionSelection() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.request_role).getVisibility());
                assertFalse(activity.findViewById(R.id.delete_selected).isEnabled());
            });
        }
    }
    @Test public void restorationScreenSurvivesActivityRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                MainViewModel model = new ViewModelProvider(activity).get(MainViewModel.class);
                model.showingRecovery = true;
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.recovery_panel).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.results_panel).getVisibility());
                assertNotNull(activity.findViewById(R.id.restore_settings));
                assertNotNull(activity.findViewById(R.id.check_restoration));
                MainViewModel model = new ViewModelProvider(activity).get(MainViewModel.class);
                if (model.report() == null) {
                    assertEquals(View.GONE, activity.findViewById(R.id.recovery_complete).getVisibility());
                }
            });
        }
    }
}
