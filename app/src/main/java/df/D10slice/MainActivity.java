package df.D10slice;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.widget.CompoundButton;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
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

    private MaterialCardView launchRootCard;
    private View rootLayout;
    private LinearLayout suManagerRow;
    private TextView suManagerSubtitle;
    private MaterialSwitch switchSuManager;
    private ScrollView outputScroll;
    private TextView outputView;
    private MaterialSwitch switchBootStart;
    private MaterialSwitch switchSoftReboot;
    private MaterialSwitch switchDisableModules;
    private MaterialSwitch switchLaunchRoot;
    private Context mDeCtx;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Executor mExec = Executors.newSingleThreadExecutor();
    private final List<SuManagerEntry> suManagers = new ArrayList<>();
    private String mSelectedSuManagerPackage;
    private SharedPreferences suManagerPrefs;
    private boolean isLaunching;
    private CompoundButton.OnCheckedChangeListener suManagerSwitchListener;
    private PopupWindow suManagerPopup;

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

        rootLayout = findViewById(R.id.rootLayout);
        launchRootCard = findViewById(R.id.launchRootCard);
        suManagerRow = findViewById(R.id.suManagerRow);
        suManagerSubtitle = findViewById(R.id.suManagerSubtitle);
        switchSuManager = findViewById(R.id.switchSuManager);
        outputScroll = findViewById(R.id.outputScroll);
        outputView = findViewById(R.id.outputView);
        switchBootStart = findViewById(R.id.switchBootStart);
        switchSoftReboot = findViewById(R.id.switchSoftReboot);
        switchDisableModules = findViewById(R.id.switchDisableModules);
        switchLaunchRoot = findViewById(R.id.switchLaunchRoot);
        suManagerPrefs = mDeCtx.getSharedPreferences(ExploitRunner.PREFS_NAME, Context.MODE_PRIVATE);
        switchLaunchRoot.setChecked(isRootActive());

        PackageManager pm = getPackageManager();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (!ExploitRunner.hasKsud(ai)) continue;
            suManagers.add(new SuManagerEntry(ai.packageName, pm.getApplicationLabel(ai)));
        }
        suManagers.sort((a, b) -> a.label.toString().compareToIgnoreCase(b.label.toString()));
        suManagerSwitchListener = (button, checked) -> {
            if (isSuManagerLocked()) {
                updateRunButton();
                return;
            }
            if (checked) {
                activateOrChooseSuManager(suManagers, suManagerPrefs);
            } else {
                clearSuManagerSelection(suManagerPrefs);
            }
        };
        switchSuManager.setOnCheckedChangeListener(suManagerSwitchListener);
        refreshSuManagerSelection();

        configureSettings(suManagerPrefs);
        suManagerRow.setOnClickListener(v -> {
            if (isSuManagerLocked()) {
                updateRunButton();
                return;
            }
            activateOrChooseSuManager(suManagers, suManagerPrefs);
        });

        updateRunButton();

        launchRootCard.setOnClickListener(v -> launchRoot());
        switchLaunchRoot.setOnCheckedChangeListener((button, checked) -> {
            if (checked && !isRootActive()) {
                launchRoot();
            } else if (!checked && isRootActive()) {
                button.setChecked(true);
            }
        });
    }

    private void activateOrChooseSuManager(List<SuManagerEntry> entries, SharedPreferences prefs) {
        if (isSuManagerLocked()) {
            updateRunButton();
            return;
        }
        if (entries.isEmpty()) {
            Toast.makeText(this, "No compatible SU managers found", Toast.LENGTH_LONG).show();
            if (switchSuManager.isChecked()) setSuManagerSwitchChecked(false);
        } else if (entries.size() == 1) {
            selectSuManager(entries.get(0), prefs);
        } else {
            if (mSelectedSuManagerPackage == null) setSuManagerSwitchChecked(false);
            showSuManagerPicker(entries, prefs);
        }
    }

    private void showSuManagerPicker(List<SuManagerEntry> entries, SharedPreferences prefs) {
        if (isSuManagerLocked()) return;

        int rootBackgroundColor = getRootBackgroundColor();
        int maxHeight = dp(320);
        ScrollView scroll = new ScrollView(this);
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);

        MaterialCardView surface = new MaterialCardView(this);
        surface.setCardBackgroundColor(rootBackgroundColor);
        surface.setRadius(dp(16));
        surface.setCardElevation(0);
        surface.setStrokeWidth(0);
        surface.addView(scroll, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(options);

        int popupHeight = Math.min(maxHeight, entries.size() * dp(64));
        PopupWindow popup = new PopupWindow(surface, suManagerRow.getWidth(), popupHeight, true);
        popup.setBackgroundDrawable(new ColorDrawable(rootBackgroundColor));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(4));
        popup.setClippingEnabled(true);
        suManagerPopup = popup;
        popup.setOnDismissListener(() -> {
            if (suManagerPopup == popup) suManagerPopup = null;
        });

        for (SuManagerEntry entry : entries) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(android.view.Gravity.CENTER_VERTICAL);
            item.setPadding(dp(16), dp(10), dp(16), dp(10));
            item.setMinimumHeight(dp(64));
            item.setClickable(true);
            item.setFocusable(true);
            TypedValue selectableBackground = new TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground,
                    selectableBackground, true)) {
                item.setBackgroundResource(selectableBackground.resourceId);
            }

            TextView label = new TextView(this);
            label.setText(entry.label);
            label.setTextColor(getColor(R.color.app_foreground));
            label.setTextSize(16);
            label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);

            TextView packageName = new TextView(this);
            packageName.setText(entry.packageName);
            packageName.setTextColor(getColor(R.color.app_secondary_foreground));
            packageName.setTextSize(12);

            item.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            item.addView(packageName, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            item.setOnClickListener(v -> {
                popup.dismiss();
                if (isSuManagerLocked()) {
                    updateRunButton();
                    return;
                }
                selectSuManager(entry, prefs);
            });
            options.addView(item, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        suManagerRow.post(() -> {
            if (!isFinishing() && suManagerRow.isShown() && !isSuManagerLocked()) {
                popup.showAsDropDown(suManagerRow);
            }
        });
    }

    private void selectSuManager(SuManagerEntry entry, SharedPreferences prefs) {
        if (isSuManagerLocked()) return;
        if (!prefs.edit().putString(ExploitRunner.PREF_SU_MANAGER, entry.packageName).commit()) {
            Log.e(TAG, "failed to save selected SU manager");
            Toast.makeText(this, "Could not save SU manager selection", Toast.LENGTH_LONG).show();
            if (mSelectedSuManagerPackage == null) setSuManagerSwitchChecked(false);
            return;
        }
        mSelectedSuManagerPackage = entry.packageName;
        setSuManagerUi(entry);
        updateRunButton();
    }

    private void clearSuManagerSelection(SharedPreferences prefs) {
        if (isSuManagerLocked()) {
            updateRunButton();
            return;
        }
        if (!prefs.edit().remove(ExploitRunner.PREF_SU_MANAGER).commit()) {
            Log.e(TAG, "failed to clear selected SU manager");
            Toast.makeText(this, "Could not clear SU manager selection", Toast.LENGTH_LONG).show();
            setSuManagerSwitchChecked(mSelectedSuManagerPackage != null);
            return;
        }
        mSelectedSuManagerPackage = null;
        suManagerSubtitle.setText("Not selected - required");
        setSuManagerSwitchChecked(false);
        updateRunButton();
    }

    private void setSuManagerUi(SuManagerEntry entry) {
        mSelectedSuManagerPackage = entry.packageName;
        suManagerSubtitle.setText(entry.label + " (" + entry.packageName + ")");
        setSuManagerSwitchChecked(true);
    }

    private void refreshSuManagerSelection() {
        String saved = suManagerPrefs.getString(ExploitRunner.PREF_SU_MANAGER, null);
        for (SuManagerEntry entry : suManagers) {
            if (entry.packageName.equals(saved)) {
                setSuManagerUi(entry);
                return;
            }
        }
        if (saved != null && !suManagerPrefs.edit()
                .remove(ExploitRunner.PREF_SU_MANAGER).commit()) {
            Log.e(TAG, "failed to clear invalid SU manager preference");
        }
        mSelectedSuManagerPackage = null;
        suManagerSubtitle.setText("Not selected - required");
        setSuManagerSwitchChecked(false);
    }

    private void setSuManagerSwitchChecked(boolean checked) {
        switchSuManager.setOnCheckedChangeListener(null);
        switchSuManager.setChecked(checked);
        switchSuManager.setOnCheckedChangeListener(suManagerSwitchListener);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int getRootBackgroundColor() {
        if (rootLayout.getBackground() instanceof ColorDrawable) {
            return ((ColorDrawable) rootLayout.getBackground()).getColor();
        }
        TypedValue value = new TypedValue();
        if (getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, value, true)) {
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
            if (value.resourceId != 0) return getColor(value.resourceId);
        }
        return getColor(R.color.app_background);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSuManagerSelection();
        switchLaunchRoot.setChecked(isRootActive());
        updateRunButton();
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
        boolean hasSuManager = mSelectedSuManagerPackage != null;
        boolean rootAlreadyLaunched = isRootActive();
        boolean suManagerLocked = isLaunching || rootAlreadyLaunched;
        suManagerRow.setClickable(!suManagerLocked);
        suManagerRow.setFocusable(!suManagerLocked);
        switchSuManager.setClickable(!suManagerLocked);
        switchSuManager.setFocusable(!suManagerLocked);
        if (suManagerLocked && suManagerPopup != null) suManagerPopup.dismiss();
        launchRootCard.setEnabled(!isLaunching && hasSuManager && !rootAlreadyLaunched);
        switchLaunchRoot.setEnabled(!isLaunching && hasSuManager
                && (!rootAlreadyLaunched || switchLaunchRoot.isChecked()));
    }

    private boolean isRootActive() {
        return new File("/dev/df").exists();
    }

    private boolean isSuManagerLocked() {
        return isLaunching || isRootActive();
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
                switchLaunchRoot.setChecked(isRootActive());
                updateRunButton();
            });
        }
    }

    private static class SuManagerEntry {
        final String packageName;
        final CharSequence label;

        SuManagerEntry(String pkg, CharSequence label) {
            this.packageName = pkg;
            this.label = label;
        }
    }
}
