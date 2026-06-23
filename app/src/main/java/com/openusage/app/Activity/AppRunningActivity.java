package com.openusage.app.Activity;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.health.connect.client.PermissionController;

import android.graphics.Color;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.openusage.app.Alarm.SetAlarm;
import com.openusage.app.activites.HealthConnectStepReader;
import com.openusage.app.activites.StepHistoryCapture;
import com.openusage.app.TextBasedEventData.EventOperationManager;
import com.openusage.app.TextBasedEventData.HashMapPool;
import com.openusage.app.TextBasedEventData.SessionDataUploaderToFireStore;
import com.openusage.app.modulemanager.ModuleCharacteristics;
import com.openusage.app.AppUtils.InAppUpdate;
import com.openusage.app.DatabaseHelper.InterCommunicationPreference;
import com.openusage.app.DatabaseHelper.LogInPreference;
import com.openusage.app.FirebaseSettingsObserver;
import com.openusage.app.PermissionScreens.PermissionChecker;
import com.openusage.app.PermissionScreens.PermissionParentActivity;
import com.openusage.app.Services.CaptureUploadService;
import com.openusage.app.R;
import com.openusage.app.FirebaseSettings.SettingsManager;
import com.openusage.app.Services.ScreenMonitorService;
import com.openusage.app.Utils;
import com.openusage.app.apps.HistoricalUsageCapture;

public class AppRunningActivity extends AppCompatActivity {

    PermissionChecker checker;
    public static int ResultCode;
    public static Intent intent1;
    private SwitchCompat ServiceTurnOnOff;
    private TextView arTitle;
    private TextView arDescription;
    private static final int REQUEST_CODE = 100;
    private MediaProjectionManager mediaProjectionManager;
    private static MediaProjection mediaProjection;

    SetAlarm alarm2;

    private InAppUpdate inAppUpdate;

    private boolean IsUserClickedTurnOnOff = false;

    private InterCommunicationPreference interCommunicationPrefrence;

    // Health Connect step-permission launcher (PASSIVE group). Registered only on API 26+.
    private ActivityResultLauncher<Set<String>> hcStepPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_running);

        ServiceTurnOnOff = findViewById(R.id.ServiceTurnOnOff);
        arTitle = findViewById(R.id.ar_title);
        arDescription = findViewById(R.id.ar_description);


        SetStatusBarColor();

        // Register the Health Connect permission launcher (API 26+ only; HC is unavailable below).
        // Must be registered during onCreate, before the activity is RESUMED.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hcStepPermissionLauncher = registerForActivityResult(
                    PermissionController.createRequestPermissionResultContract(),
                    granted -> {
                        Log.i("AppRunningActivity", "HC step permission result: " + granted);
                        // Proceed with the PASSIVE capture regardless of grant outcome;
                        // if denied, the step read simply returns nothing.
                        String uid = new LogInPreference(AppRunningActivity.this).GetUserSubjId();
                        new Thread(() -> runPassiveCaptureAndUpload(uid)).start();
                    });
        }

        alarm2 = new SetAlarm();
        alarm2.SetAlarmFor7Am(getApplicationContext());
        alarm2.SetAlarmFor7Pm(getApplicationContext());
        interCommunicationPrefrence = new InterCommunicationPreference(AppRunningActivity.this);

        DecideIsServiceRunningOrNot();

        inAppUpdate = new InAppUpdate(AppRunningActivity.this,getWindow().getDecorView().getRootView());

        try {
            inAppUpdate.checkForAppUpdate();
        }catch (Exception ignored){}
        IsUserClickedTurnOnOff = true;

        checker = new PermissionChecker(AppRunningActivity.this);

        interCommunicationPrefrence.WhichActivityCalledStartService(1);

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isMyServiceRunning(CaptureUploadService.class,AppRunningActivity.this)
                        || isMyServiceRunning(ScreenMonitorService.class,AppRunningActivity.this)){
                    setResult(Activity.RESULT_CANCELED);

                    finishAffinity();
                }
            }
        });

        ShowBatteryOptimizationDialog();
        
        // Check and request notification permission
        checkNotificationPermission();

        // Only auto-start services if the user hasn't explicitly turned tracking off
        // and this is not a PASSIVE study group (PASSIVE only does one-shot captures)
        boolean userStoppedTracking = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("user_stopped_tracking", false);
        String currentStudyGroup = new LogInPreference(this).GetStudyGroup();
        boolean isPassiveGroup = "PASSIVE".equalsIgnoreCase(currentStudyGroup);

        if (isPassiveGroup) {
            Log.d("AppRunningActivity", "PASSIVE group — no continuous tracking services");
        } else if (!userStoppedTracking) {
            startScreenMonitorService();
            if (SettingsManager.val("screenshots-enabled") == 1) {
                startCaptureUploadService();
            } else {
                Log.d("AppRunningActivity", "Screenshots disabled — skipping CaptureUploadService");
                ServiceIsOnLabel();
            }
        } else {
            Log.d("AppRunningActivity", "User previously stopped tracking — not auto-starting");
        }


//        Intent intent = getIntent();
//        boolean str = intent.getBooleanExtra("start_service",false);
//
//        if (str){
//            Toast.makeText(this, "true", Toast.LENGTH_SHORT).show();
//            ServiceTurnOnOff.setChecked(true);
//        }

        ServiceTurnOnOff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {


                if (!SettingsManager.exists()) {
                    SettingsManager.create(new FirebaseSettingsObserver() {
                        @Override
                        public void onSettingsChanged(List<String> changedSettings) {

                        }
                    });
                }

                SettingsManager.get().load(AppRunningActivity.this, new SettingsManager.DatabaseListener() {
                    @Override
                    public void onSuccess() {
                        // Settings loaded success fully
                    }

                    @Override
                    public void onFailure() {
                        // Failed to load the settings? Too bad!
                    }
                });

                Intent serv_intent = new Intent(AppRunningActivity.this, CaptureUploadService.class);

                if (IsUserClickedTurnOnOff){
                    if (isChecked){
                        // Clear the "user stopped" flag since they're turning it back on
                        getSharedPreferences("app_prefs", MODE_PRIVATE)
                                .edit().putBoolean("user_stopped_tracking", false).apply();
                        logTrackingToggleEvent("enabled");

                        // Check if PASSIVE study group — one-shot historical capture only
                        String studyGroup = new LogInPreference(AppRunningActivity.this).GetStudyGroup();
                        if ("PASSIVE".equalsIgnoreCase(studyGroup)) {
                            handlePassiveToggleOn();
                            return;
                        }

                        PermissionChecker permissionChecker = new PermissionChecker(AppRunningActivity.this);
                        if (permissionChecker.CheckIsAnyPermissionLeftFromBeingAllowed()){
                            startActivity(new Intent(AppRunningActivity.this, PermissionParentActivity.class));
                            finish();
                        }else{
                            if (SettingsManager.val("screenshots-enabled") == 1) {
                                // Request media projection permission for screenshot capture
                                mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                                startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
                            } else {
                                // Text-only mode — just start monitor service, no Media Projection needed
                                startScreenMonitorService();
                                ServiceIsOnLabel();
                            }
                        }



                    }else {

                        AlertDialog.Builder builder = new AlertDialog.Builder(AppRunningActivity.this);
                        builder.setMessage("Do you want to turn off the application service?");
                        builder.setTitle("Confirmation");
                        builder.setCancelable(false);
                        builder.setPositiveButton("Turn Off", (DialogInterface.OnClickListener) (dialog, which) -> {

//                            alarm2.SetAlarmFor30MinToAskUserAgainToStartService(AppRunningActivity.this);
                            stopService(serv_intent);
                            // Also stop CaptureUploadService and ScreenMonitorService
                            Intent captureServiceIntent = new Intent(AppRunningActivity.this, CaptureUploadService.class);
                            stopService(captureServiceIntent);
                            Intent monitorServiceIntent = new Intent(AppRunningActivity.this, ScreenMonitorService.class);
                            stopService(monitorServiceIntent);
                            // Remember that user explicitly stopped tracking (commit synchronously)
                            getSharedPreferences("app_prefs", MODE_PRIVATE)
                                    .edit().putBoolean("user_stopped_tracking", true).commit();
                            logTrackingToggleEvent("disabled");
                            ServiceIsOffLabel();

                            dialog.cancel();

                        });

                        builder.setNegativeButton("No", (DialogInterface.OnClickListener) (dialog, which) -> {
                            DecideIsServiceRunningOrNot();

                            dialog.cancel();
                        });

                        AlertDialog alertDialog = builder.create();
                        alertDialog.show();
                    }
                }
            }
        });

    }


    private void startScreenMonitorService() {

        if (!isMyServiceRunning(ScreenMonitorService.class,AppRunningActivity.this)){
            Intent serviceIntent = new Intent(this, ScreenMonitorService.class);

            ContextCompat.startForegroundService(this, serviceIntent);
        }

    }
    
    private void startCaptureUploadService() {
        Log.d("AppRunningActivity", "=== ATTEMPTING TO START CAPTUREUPLOADSERVICE ===");
        
        boolean isRunning = isMyServiceRunning(CaptureUploadService.class, AppRunningActivity.this);
        Log.d("AppRunningActivity", "CaptureUploadService currently running: " + isRunning);
        
        if (!isRunning) {
            try {
                Intent serviceIntent = new Intent(this, CaptureUploadService.class);
                Log.d("AppRunningActivity", "Created service intent: " + serviceIntent);
                Log.d("AppRunningActivity", "Starting CaptureUploadService as foreground service...");
                
                ContextCompat.startForegroundService(this, serviceIntent);
                
                Log.d("AppRunningActivity", "CaptureUploadService start command sent successfully");
                
                // Wait a moment and check if it actually started
                new android.os.Handler().postDelayed(() -> {
                    boolean nowRunning = isMyServiceRunning(CaptureUploadService.class, AppRunningActivity.this);
                    Log.d("AppRunningActivity", "CaptureUploadService running after start attempt: " + nowRunning);
                    if (!nowRunning) {
                        Log.e("AppRunningActivity", "CaptureUploadService failed to start or was killed immediately!");
                    }
                }, 2000);
                
            } catch (Exception e) {
                Log.e("AppRunningActivity", "Exception starting CaptureUploadService: " + e.getMessage(), e);
            }
        } else {
            Log.d("AppRunningActivity", "CaptureUploadService already running - skipping start");
        }
    }
    
    private void checkNotificationPermission() {
        Log.d("AppRunningActivity", "=== CHECKING NOTIFICATION PERMISSION ===");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                
                Log.w("AppRunningActivity", "POST_NOTIFICATIONS permission not granted!");
                Log.w("AppRunningActivity", "Requesting notification permission...");
                
                // Request notification permission
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            } else {
                Log.d("AppRunningActivity", "POST_NOTIFICATIONS permission already granted");
            }
        } else {
            Log.d("AppRunningActivity", "Android version < 13, no notification permission needed");
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 1001) { // Notification permission request
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d("AppRunningActivity", "Notification permission granted by user");
            } else {
                Log.w("AppRunningActivity", "Notification permission denied by user");
                Log.w("AppRunningActivity", "Service notifications may not be visible");
            }
        }
    }


    private void ShowBatteryOptimizationDialog(){

        int value = interCommunicationPrefrence.GetShowBatteryOptimizationDialog();

            if (value % 5 != 0){
                interCommunicationPrefrence.PutShowBatteryOptimizationDialog(interCommunicationPrefrence.GetShowBatteryOptimizationDialog() + 1);
                return;
            }else {
                interCommunicationPrefrence.PutShowBatteryOptimizationDialog(interCommunicationPrefrence.GetShowBatteryOptimizationDialog() + 1);
            }


        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        String packageName = getPackageName();
        boolean isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName);

        if (!isIgnoring) {

            Log.i("AppRunningActivity","ignoring run");

            AlertDialog.Builder builder = new AlertDialog.Builder(AppRunningActivity.this);
            builder.setMessage("To ensure you receive important updates and notifications, please add our app to your whitelist. This will help us provide you with a better experience.\n" +
                    "Would you like to whitelist the Open Usage app now?");
            builder.setTitle("Whitelist Open Usage App");
            builder.setCancelable(false);
            builder.setPositiveButton("Yes", (DialogInterface.OnClickListener) (dialog, which) -> {

                try {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    dialog.cancel();
                }catch (Exception e){
                    PowerManager powerManager1 = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (powerManager1 != null && !powerManager1.isIgnoringBatteryOptimizations(getPackageName())) {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    }
                }



            });

            builder.setNegativeButton("No", (DialogInterface.OnClickListener) (dialog, which) -> {

                dialog.cancel();
            });

            AlertDialog alertDialog = builder.create();
            alertDialog.show();
            // The app is ignoring battery optimizations.
        }else {
            Log.i("AppRunningActivity","else run");

        }


    }


    private void SetStatusBarColor(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }

        WindowInsetsController controller = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            controller = getWindow().getInsetsController();


            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());

                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

            }

            // Create a fake status bar (Scrim View) to add color
            View statusBarScrim = new View(this);
            statusBarScrim.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, getStatusBarHeight()+ 20));
            statusBarScrim.setBackgroundColor(Color.parseColor("#BFDAE2")); // Open Usage brand color

            // Add the scrim to your root layout
            FrameLayout rootLayout = findViewById(android.R.id.content);
            rootLayout.addView(statusBarScrim);
        }
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

    }

    private void IsAnyPermissionLeftAskForIt(){
        // In text-only mode, CaptureUploadService is not running, but we still
        // need to check other permissions (e.g., accessibility).
        if (isMyServiceRunning(CaptureUploadService.class, AppRunningActivity.this)
                || isMyServiceRunning(ScreenMonitorService.class, AppRunningActivity.this)){
            checker.checkPermissions();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();

        IsAnyPermissionLeftAskForIt();
        DecideIsServiceRunningOrNot();
        IsUserClickedTurnOnOff = true;
        interCommunicationPrefrence.WhichActivityCalledStartService(1);
        inAppUpdate.onResume();



    }

    private void DecideIsServiceRunningOrNot(){
        IsUserClickedTurnOnOff = false;
//        if (isMyServiceRunning(CaptureUploadService.class,AppRunningActivity.this)){
//            ServiceTurnOnOff.setChecked(true);
//            IsUserClickedTurnOnOff = true;
//            arTitle.setText(getText(R.string.ar_prompt_title));
//            arDescription.setText(getText(R.string.ar_prompt_description));
//        }else {
//            ServiceTurnOnOff.setChecked(false);
//            IsUserClickedTurnOnOff = true;
//            arTitle.setText(R.string.service_is_not_running);
//            arDescription.setText(R.string.app_is_not_running_please_turn_on_the_above_button_to_start_the_app);
//
//        }

        boolean screenshotsEnabled = SettingsManager.val("screenshots-enabled") == 1;
        boolean captureRunning = isMyServiceRunning(CaptureUploadService.class, AppRunningActivity.this);
        boolean monitorRunning = isMyServiceRunning(ScreenMonitorService.class, AppRunningActivity.this);
        boolean userStoppedTracking = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("user_stopped_tracking", false);

        // PASSIVE group: always show toggle-OFF with appropriate description
        String decideStudyGroup = new LogInPreference(this).GetStudyGroup();
        if ("PASSIVE".equalsIgnoreCase(decideStudyGroup)) {
            ServiceTurnOnOff.setChecked(false);
            IsUserClickedTurnOnOff = true;
            arTitle.setText("Usage History Mode");
            arDescription.setText("Tap the switch to capture and upload your recent app usage history.");
            return;
        }

        if (userStoppedTracking) {
            // User explicitly turned tracking off — respect their choice
            ServiceTurnOnOff.setChecked(false);
            IsUserClickedTurnOnOff = true;
            arTitle.setText(getText(R.string.service_is_not_running));
            arDescription.setText("Tracking is off. Toggle the switch to resume data collection.");
        }
        else if (!screenshotsEnabled) {
            // Text-only mode: tracking is "on" if AccessibilityService is running
            if (monitorRunning || com.openusage.app.PermissionScreens.PermissionChecker.isAccessibilityServiceEnabled(this, com.openusage.app.Services.ScreenomicsAccessService.class)) {
                ServiceTurnOnOff.setChecked(true);
                IsUserClickedTurnOnOff = true;
                arTitle.setText(getText(R.string.ar_prompt_title));
                arDescription.setText(getText(R.string.ar_prompt_description));
            } else {
                ServiceTurnOnOff.setChecked(false);
                IsUserClickedTurnOnOff = true;
                arTitle.setText(getText(R.string.service_is_not_running));
                arDescription.setText("App is not running. To resume data collection, please reopen the app.");
            }
        }
        else if (!captureRunning && !monitorRunning){
            ServiceTurnOnOff.setChecked(false);
            IsUserClickedTurnOnOff = true;
            arTitle.setText(getText(R.string.service_is_not_running));
            arDescription.setText("App is not running. To resume data collection, please reopen the app.");
        }
        else if (!captureRunning && monitorRunning) {
            ServiceTurnOnOff.setChecked(false);
            IsUserClickedTurnOnOff = true;
            arTitle.setText(getText(R.string.ar_prompt_title));
            arDescription.setText("Screen capture is paused. Please reopen the app to resume.");
        }
        else if (captureRunning && monitorRunning){
            ServiceTurnOnOff.setChecked(true);
            IsUserClickedTurnOnOff = true;
            arTitle.setText(getText(R.string.ar_prompt_title));
            arDescription.setText(getText(R.string.ar_prompt_description));
        }

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

//        if (!checker.IsPermissionDialogShowing()){
//            if (isMyServiceRunning(CaptureUploadService.class,AppRunningActivity.this)){
//                checker.checkPermissions();
//            }
//        }


    }

    private void ServiceIsOffLabel(){
        IsUserClickedTurnOnOff = false;
        ServiceTurnOnOff.setChecked(false);
        IsUserClickedTurnOnOff = true;

        arTitle.setText(R.string.service_is_not_running);
        arDescription.setText("Tracking is off. Toggle the switch to resume data collection.");
    }

    private void logTrackingToggleEvent(String action) {
        HashMap<String, String> map = HashMapPool.getMap();
        map.put("action", action);
        map.put("study_group", SettingsManager.val("screenshots-enabled") == 1 ? "SCREENSHOTS" : "TEXT_ONLY");
        EventOperationManager.getInstance(this).addEvent(
                ModuleCharacteristics.getInstance().getTrackingToggleCharacteristics(), map);
        HashMapPool.releaseMap(map);
        Log.d("AppRunningActivity", "TrackingToggleEvent logged: " + action);
    }

    private void ServiceIsOnLabel(){
        IsUserClickedTurnOnOff = false;
        ServiceTurnOnOff.setChecked(true);
        arTitle.setText(getText(R.string.ar_prompt_title));
        arDescription.setText(getText(R.string.ar_prompt_description));

    }

    /**
     * Handle PASSIVE study group toggle-ON: capture historical usage + Health Connect step
     * history, then auto-toggle OFF. If Health Connect steps are available but not yet
     * permitted, the permission is requested first; the capture resumes in the launcher
     * callback regardless of the grant outcome.
     */
    private void handlePassiveToggleOn() {
        final String userId = new LogInPreference(AppRunningActivity.this).GetUserSubjId();

        // Reset snapshot flag so we can capture fresh data
        HistoricalUsageCapture.resetSnapshotFlag(AppRunningActivity.this);

        // Show status
        Toast.makeText(AppRunningActivity.this, "Capturing history...", Toast.LENGTH_SHORT).show();
        arTitle.setText("Capturing Data");
        arDescription.setText("Please wait while your history is being captured and uploaded...");

        // Decide whether we need to request Health Connect permission first (off main thread,
        // since the availability/permission checks block briefly).
        new Thread(() -> {
            try {
                boolean needsHcPermission =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                && hcStepPermissionLauncher != null
                                && HealthConnectStepReader.isAvailable(AppRunningActivity.this)
                                && !HealthConnectStepReader.hasAllPermissions(AppRunningActivity.this);

                if (needsHcPermission) {
                    Set<String> perms = new HashSet<>();
                    for (String p : HealthConnectStepReader.getRequiredPermissions()) {
                        perms.add(p);
                    }
                    Log.i("AppRunningActivity", "PASSIVE group: requesting Health Connect step permission");
                    runOnUiThread(() -> hcStepPermissionLauncher.launch(perms));
                    // Capture resumes in the permission-result callback.
                } else {
                    runPassiveCaptureAndUpload(userId);
                }
            } catch (Exception e) {
                Log.e("AppRunningActivity", "PASSIVE group: error deciding HC permission flow", e);
                runPassiveCaptureAndUpload(userId);
            }
        }).start();
    }

    /**
     * Runs the actual PASSIVE capture (usage history + Health Connect step history), uploads
     * everything, and auto-toggles tracking OFF. MUST be called on a background thread.
     */
    private void runPassiveCaptureAndUpload(String userId) {
        try {
            Log.i("AppRunningActivity", "PASSIVE group: Starting historical usage capture for " + userId);
            HistoricalUsageCapture.SnapshotResult result =
                    HistoricalUsageCapture.captureHistoricalSnapshot(AppRunningActivity.this, userId);
            Log.i("AppRunningActivity", "PASSIVE group: Usage capture result: " + result);

            // Health Connect step history (best-effort)
            StepHistoryCapture.Result stepResult =
                    StepHistoryCapture.capture(AppRunningActivity.this, userId);
            Log.i("AppRunningActivity", "PASSIVE group: Step capture result: " + stepResult);

            // Trigger immediate upload of all unsynced data
            SessionDataUploaderToFireStore uploader =
                    SessionDataUploaderToFireStore.getInstance(AppRunningActivity.this);
            uploader.uploadUnsyncedSessions(false);
            Log.i("AppRunningActivity", "PASSIVE group: Data upload triggered");

            // Brief delay to allow upload to start, then auto-toggle OFF
            Thread.sleep(1000);

            final boolean suggestHcInstall = stepResult.providerUpdateRequired;
            runOnUiThread(() -> {
                String msg = suggestHcInstall
                        ? "History uploaded. Install Health Connect to enable step history."
                        : "History captured and uploaded!";
                Toast.makeText(AppRunningActivity.this, msg, Toast.LENGTH_LONG).show();
                autoTogglePassiveOff();
            });

        } catch (Exception e) {
            Log.e("AppRunningActivity", "PASSIVE group: Error during capture", e);
            runOnUiThread(() -> {
                Toast.makeText(AppRunningActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                autoTogglePassiveOff();
            });
        }
    }

    /**
     * Auto-toggle the PASSIVE tracking switch OFF and persist the stopped state.
     * Must be called on the UI thread.
     */
    private void autoTogglePassiveOff() {
        IsUserClickedTurnOnOff = false;
        ServiceTurnOnOff.setChecked(false);
        IsUserClickedTurnOnOff = true;
        getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit().putBoolean("user_stopped_tracking", true).commit();
        ServiceIsOffLabel();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        Utils.createOptionsMenu(menu);

        return super.onCreateOptionsMenu(menu);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        Utils.optionsItemSelected(this, item);
        return super.onOptionsItemSelected(item);
    }


    private static boolean isMyServiceRunning(Class<?> serviceClass, Context context) {


        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        // Make sure this is the result of starting media projection.

        super.onActivityResult(requestCode, resultCode, intent);

        inAppUpdate.onActivityResult(requestCode, resultCode);


        if (requestCode == REQUEST_CODE) {

            // If the result is not OK, the user probably didn't give us permission. Abort.
            if (resultCode != Activity.RESULT_OK) {
                setResult(resultCode);
                DecideIsServiceRunningOrNot();
//                alarm2.SetAlarmFor30MinToAskUserAgainToStartService(AppRunningActivity.this);
                Toast.makeText(this, getText(R.string.cp_toast_nostart), Toast.LENGTH_SHORT).show();
                return;
            }else{
                startScreenMonitorService();
            }

            interCommunicationPrefrence.WhichActivityCalledStartService(1);

            // Store resultCode and intent in static variables
            // Service will create MediaProjection AFTER it starts foreground service
            ResultCode = resultCode;
            intent1 = intent;
            
            Log.i("AppRunningActivity", "MediaProjection data stored in static variables");
            Log.i("AppRunningActivity", "   ResultCode: " + resultCode);
            Log.i("AppRunningActivity", "   Intent: " + (intent != null ? "present" : "null"));

            // Start the CaptureUploadService.
            Intent serv_intent = new Intent(AppRunningActivity.this, CaptureUploadService.class);

            ContextCompat.startForegroundService(AppRunningActivity.this, serv_intent);

//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                startForegroundService(serv_intent);
//
//            } else {
//
//                startService(serv_intent);
//
//            }



            alarm2.CancelAlarm(AppRunningActivity.this,5);

            DecideIsServiceRunningOrNot();
            // Close this activity.
            setResult(Activity.RESULT_OK);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        inAppUpdate.onDestroy();
    }

    public static MediaProjection getMediaProjection()
    {
        return mediaProjection;
    }

    public static Intent getIntents()
    {
        return intent1;
    }

    public static int getResultCode()
    {
        return ResultCode;
    }
}
