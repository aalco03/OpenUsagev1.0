package com.openusage.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.openusage.app.Activity.LoginActivity;
import com.openusage.app.Alarm.SetAlarm;
import com.openusage.app.DatabaseHelper.LogInPreference;
import com.openusage.app.Services.ScreenMonitorService;
import com.openusage.app.TextBasedEventData.EventDatabaseHelper;
import com.openusage.app.TextBasedEventData.EventMapBuilder;
import com.openusage.app.TextBasedEventData.NetworkUtils;
import com.openusage.app.TextBasedEventData.EventUploaderToFireStore;
import com.openusage.app.TextBasedEventData.SessionDataUploaderToFireStore;


public class Utils {

    public static String getSubjectId(Context context)
    {
        LogInPreference sharedPref = new LogInPreference(context);
        return sharedPref.GetUserSubjId();
//        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
//        return sharedPref.getString("user_subj_id", "");
    }

    public static String getGroupCode(Context context)
    {
        LogInPreference sharedPref = new LogInPreference(context);
        return sharedPref.GetUserCode();

    }

    public static String getDataSubjectId(Context context)
    {
        String subject_id = getSubjectId(context);
        return getDataSubjectId(subject_id);
    }

    public static String getDataSubjectId(String subjectId)
    {
        return subjectId.replaceAll("[^a-zA-Z0-9]", "");
    }

    public static void createOptionsMenu(Menu menu)
    {
//        menu.add(Menu.NONE, 2, Menu.NONE, R.string.menu_dumplog);
        menu.add(Menu.NONE, 1, Menu.NONE, R.string.menu_zlogout);
    }


    public static Map<String, String> createPhoneSpecMap()
    {
        HashMap<String, String> map = new HashMap<>();
        map.put("fingerprint", Build.FINGERPRINT);
        map.put("manufacturer", Build.MANUFACTURER);
        map.put("brand", Build.BRAND);
        map.put("model", Build.MODEL);
        map.put("product", Build.PRODUCT);
        map.put("display-id", Build.DISPLAY);
        return map;
    }

    public static boolean isInstallCodeSet(Context context)
    {
        return !TextUtils.isEmpty(getInstallCode(context));
    }

    public static String getInstallCode(Context context)
    {
        LogInPreference sharedPref = new LogInPreference(context);
        return sharedPref.GetInstallCode();
    }

    public static String setRandomInstallCode(Context context)
    {
        LogInPreference pref = new LogInPreference(context);

        String installcode = UUID.randomUUID().toString();
        pref.AddInstallCode(installcode);
//        sharedPref.edit().putString("install_code", installcode).apply();
        return installcode;
    }

    public static String getCrashLogDirectory(Context context) {
        File ext = context.getExternalFilesDir(null);
        if (ext != null) return ext.getAbsolutePath() + "/logs/";
        return getBackupCrashLogDirectory();
    }

    public static String getBackupCrashLogDirectory() {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/screenomics_logs/";
    }

    public static int getBatteryPercentage(Context context) {
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            float percent = (level * 100.f) / scale;
            return (int) percent;
        }

        return -1;
    }

    public static void optionsItemSelected(Activity activity, MenuItem item)
    {
        switch (item.getItemId())
        {
            case 1:
                // Do logout.


                int waitTime = estimateLogoutWaitTime(activity);

                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setMessage("Are you sure ?");
                builder.setTitle("Confirmation");
                builder.setCancelable(false);
                builder.setPositiveButton("Logout", (DialogInterface.OnClickListener) (dialog, which) -> {

                    if (NetworkUtils.isInternetAvailable(activity)){
                        LoginActivity.IsUserWantLoginOrRegister = false;

                        EventUploaderToFireStore uploaderToFireStore = EventUploaderToFireStore.getInstance(activity);

                        uploaderToFireStore.uploadSingleEvent("LogInOutEvent", EventMapBuilder.buildCompleteMap(null, "log-out"), activity.getApplicationContext());

                        uploaderToFireStore.uploadSingleEvent("ScreenshotPauseEvent", EventMapBuilder.buildCompleteMap(null, "Paused"), activity.getApplicationContext());

                        uploaderToFireStore.startUploadOfflineToOnlineEvents(true);

                        SessionDataUploaderToFireStore.getInstance(activity).uploadUnsyncedSessions(true);

                        Toast.makeText(activity, "Please Wait.....", Toast.LENGTH_SHORT).show();

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {

                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        LoginActivity.userLogOut(activity,true);
                                        LoginActivity.IsUserWantLoginOrRegister = false;
                                        Toast.makeText(activity, "Successfully Logged Out", Toast.LENGTH_SHORT).show();
                                        LoginActivity.userLogOut(activity, true);

                                        activity.stopService(new Intent(activity, ScreenMonitorService.class));

                                        SetAlarm alarm = new SetAlarm();
                                        alarm.CancelAlarm(activity,5);
//                                        EventDatabaseHelper.getInstance(activity).deleteDatabase(activity);
                                        activity.finishAffinity();
                                    }
                                });


                            }
                        }, waitTime * 1000);

                    }else {
                        Toast.makeText(activity, "Please turn on internet to log out.", Toast.LENGTH_SHORT).show();
                    }
                    dialog.cancel();

                });

                builder.setNegativeButton("No", (DialogInterface.OnClickListener) (dialog, which) -> {

                    dialog.cancel();
                });

                AlertDialog alertDialog = builder.create();
                alertDialog.show();


                break;

                // For Dump log button

//            case 2:
//                // Create and upload logcat.
////                boolean result = LogMaker.uploadLog(LogMaker.dumpLogcat(activity));
////                Toast.makeText(activity, result ? R.string.toast_dumplog_success : R.string.toast_dumplog_fail, Toast.LENGTH_SHORT).show();
//
//                int a = 5/0;
//
//                break;
        }
    }




    // Memory management New function

    // This function is used to calculate the estimated time the user will have to wait before logging out. So we can upload our remaining data to firebase.

    private static int estimateLogoutWaitTime(Activity activity) {
        EventDatabaseHelper dbHelper = EventDatabaseHelper.getInstance(activity);
        int totalEntries = dbHelper.getTotalEventCount(); // Get total SQLite entries
        int batchSize = 500; // Recommended Firestore batch size
        int uploadTimePerBatch = 3; // Approximate seconds per batch (can be adjusted)

        if (totalEntries == 0) return 0; // No data, no wait time

        int totalBatches = (int) Math.ceil((double) totalEntries / batchSize);
        return totalBatches * uploadTimePerBatch; // Estimated wait time in seconds
    }
    
    
    /**
     * Run comprehensive diagnostics
     */
    private static void runDiagnostics(Activity activity) {
        Toast.makeText(activity, "Running diagnostics... Check logs", Toast.LENGTH_SHORT).show();
        
        try {
            // Run diagnostics in background thread
            new Thread(() -> {
                DiagnosticHelper.runDiagnostics(activity);
                DiagnosticHelper.checkUnsyncedData(activity);
                
                // Show completion on UI thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(activity, "Diagnostics complete! Check logcat for details", Toast.LENGTH_LONG).show();
                });
            }).start();
            
        } catch (Exception e) {
            Toast.makeText(activity, "Diagnostics Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

}
