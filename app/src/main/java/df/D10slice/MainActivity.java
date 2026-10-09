package df.D10slice;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements IReporter {

    private static final String TAG = "dfroot";
    private static final String PREF_LAUNCH_ROOT_SWITCH = "launch_root_switch";

    private MaterialCardView launchRootCard;
    private ScrollView outputScroll;
    private TextView outputView;
    private Spinner spinnerSuManager;
    private MaterialSwitch switchBootStart;
    private MaterialSwitch switchSoftReboot;
    private MaterialSwitch switchDisableModules;
    private MaterialSwitch switchLaunchRoot;
    private Context mDeCtx;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Executor mExec = Executors.newSingleThreadExecutor();
    private int mValidSuManagerPos = 0;
    private boolean isLaunching;
    private boolean launchSwitchLatched;

    @Override
    public void report(String msg) {
        Log.i(TAG, msg.trim());
        mMain.post(() -> {
            outputView.append(msg);
            outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        mDeCtx = createDeviceProtectedStorageContext();
        setContentView(R.layout.activity_main);

        launchRootCard = findViewById(R.id.launchRootCard);
        outputScroll = findViewById(R.id.outputScroll);
        outputView = findViewById(R.id.outputView);
        spinnerSuManager = findViewById(R.id.spinnerSuManager);
        switchBootStart = findViewById(R.id.switchBootStart);
        switchSoftReboot = findViewById(R.id.switchSoftReboot);
        switchDisableModules = findViewById(R.id.switchDisableModules);
        switchLaunchRoot = findViewById(R.id.switchLaunchRoot);
        SharedPreferences prefs = mDeCtx.getSharedPreferences(ExploitRunner.PREFS_NAME, Context.MODE_PRIVATE);
        launchSwitchLatched = prefs.getBoolean(PREF_LAUNCH_ROOT_SWITCH, false);
        switchLaunchRoot.setChecked(launchSwitchLatched);

        PackageManager pm = getPackageManager();
        List<SuManagerEntry> entries = new ArrayList<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (!ExploitRunner.hasKsud(ai)) continue;
            entries.add(new SuManagerEntry(ai.packageName, pm.getApplicationLabel(ai), pm.getApplicationIcon(ai)));
        }
        entries.sort((a, b) -> a.label.toString().compareToIgnoreCase(b.label.toString()));
        entries.add(0, new SuManagerEntry(null, "Select a SU Manager", null));

        spinnerSuManager.setAdapter(new SuManagerAdapter(this, entries));

        String saved = prefs.getString(ExploitRunner.PREF_SU_MANAGER, null);
        boolean savedFound = false;
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i).packageName.equals(saved)) {
                spinnerSuManager.setSelection(i);
                mValidSuManagerPos = i;
                savedFound = true;
                break;
            }
        }
        if (!savedFound && saved != null) { // Saved manager was uninstalled
            prefs.edit().remove(ExploitRunner.PREF_SU_MANAGER).apply();
        }

        spinnerSuManager.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                SuManagerEntry e = entries.get(pos);
                if (e.packageName == null) return;
                mValidSuManagerPos = pos;
                prefs.edit().putString(ExploitRunner.PREF_SU_MANAGER, e.packageName).apply();
                configureSettings(prefs);
                updateRunButton();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        updateRunButton();

        launchRootCard.setOnClickListener(v -> launchRoot());
        switchLaunchRoot.setOnCheckedChangeListener((button, checked) -> {
            if (checked && !launchSwitchLatched) {
                launchSwitchLatched = true;
                prefs.edit().putBoolean(PREF_LAUNCH_ROOT_SWITCH, true).apply();
                launchRoot();
            } else if (launchSwitchLatched) {
                button.setChecked(true);
            }
        });
    }

    private void configureSettings(SharedPreferences prefs) {
        ComponentName bootReceiver = new ComponentName(this, BootReceiver.class);
        switchBootStart.setChecked(getPackageManager().getComponentEnabledSetting(bootReceiver)
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        switchBootStart.setOnCheckedChangeListener((button, enabled) ->
                getPackageManager().setComponentEnabledSetting(bootReceiver,
                        enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP));

        switchSoftReboot.setChecked(prefs.getBoolean(ExploitRunner.PREF_SOFT_REBOOT, false));
        switchSoftReboot.setOnCheckedChangeListener((button, enabled) ->
                prefs.edit().putBoolean(ExploitRunner.PREF_SOFT_REBOOT, enabled).apply());

        switchDisableModules.setChecked(prefs.getBoolean("disable_modules", false));
        switchDisableModules.setOnCheckedChangeListener((button, enabled) ->
                prefs.edit().putBoolean("disable_modules", enabled).apply());
    }

    private void updateRunButton() {
        boolean hasSuManager = mValidSuManagerPos >= 1;
        boolean rootAlreadyLaunched = new File("/dev/df").exists();
        launchRootCard.setEnabled(!isLaunching && hasSuManager && !rootAlreadyLaunched);
        switchLaunchRoot.setEnabled(!isLaunching && hasSuManager
                && (!rootAlreadyLaunched || switchLaunchRoot.isChecked()));
    }

    private void launchRoot() {
        if (!launchRootCard.isEnabled()) return;
        isLaunching = true;
        updateRunButton();
        outputView.setText("");
        mExec.execute(this::runExploit);
    }

    private void runExploit() {
        try {
            int rc = ExploitRunner.run(mDeCtx, this);
            String msg = rc == 0 ? "DFRoot: SUCCESS"
                       : rc == 1 ? "DFRoot: Error - ksud nonzero exit"
                       : rc == 2 ? "DFRoot: Error - check logcat & dmesg"
                       : "DFRoot: Error - failed to patch files";
            mMain.post(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            Log.e(TAG, "exploit exception", e);
            report("\nexception: " + e + "\n");
        } finally {
            mMain.post(() -> {
                isLaunching = false;
                updateRunButton();
            });
        }
    }

    private static class SuManagerEntry {
        final String packageName;
        final CharSequence label;
        final Drawable icon;

        SuManagerEntry(String pkg, CharSequence label, Drawable icon) {
            this.packageName = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    private static class SuManagerAdapter extends ArrayAdapter<SuManagerEntry> {
        SuManagerAdapter(Context ctx, List<SuManagerEntry> items) {
            super(ctx, R.layout.item_su_manager, items);
        }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null || v.getTag() != Boolean.FALSE)
                v = LayoutInflater.from(getContext()).inflate(R.layout.item_su_manager_closed, parent, false);
            v.setTag(Boolean.FALSE);
            return bindClosedView(pos, v);
        }

        @Override
        public View getDropDownView(int pos, View v, ViewGroup parent) {
            if (v == null || v.getTag() != Boolean.TRUE)
                v = LayoutInflater.from(getContext()).inflate(R.layout.item_su_manager, parent, false);
            v.setTag(Boolean.TRUE);
            return bindView(pos, v);
        }

        @Override
        public boolean isEnabled(int pos) {
            return getItem(pos).packageName != null;
        }

        private View bindClosedView(int pos, View v) {
            SuManagerEntry e = getItem(pos);
            ImageView icon = v.findViewById(R.id.iconApp);
            TextView label = v.findViewById(R.id.labelApp);
            if (e.packageName == null) {
                icon.setVisibility(View.GONE);
                label.setVisibility(View.VISIBLE);
                label.setText(e.label);
            } else {
                icon.setVisibility(View.VISIBLE);
                label.setVisibility(View.GONE);
                icon.setImageDrawable(e.icon);
            }
            return v;
        }

        private View bindView(int pos, View v) {
            SuManagerEntry e = getItem(pos);
            ((ImageView) v.findViewById(R.id.iconApp)).setImageDrawable(e.icon);
            ((TextView)  v.findViewById(R.id.labelApp)).setText(e.label);
            return v;
        }
    }
}
