package com.openusage.app.Services;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.HashMap;

import com.openusage.app.Activity.CaptureUploadStarter;
import com.openusage.app.Activity.LoginActivity;
import com.openusage.app.Alarm.SetAlarm;
import com.openusage.app.DatabaseHelper.LogInPreference;
import com.openusage.app.TextBasedEventData.EventOperationManager;
import com.openusage.app.TextBasedEventData.HashMapPool;
import com.openusage.app.Utils;
import com.openusage.app.modulemanager.ModuleCharacteristics;
import com.openusage.app.screenshots.ScreenshotCapture;

/**
 * May 7, 2025
 * Service that starts Screenomics when the phone boots.
 */
public class AutostartService extends BroadcastReceiver
{
    public static final String TAG = "AutostartReceiver";
    public static final String QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON";

    EventOperationManager eventOperationManager;
    ModuleCharacteristics moduleCharacteristics;

    @Override
    public void onReceive(Context context, Intent intent)
    {

        eventOperationManager = EventOperationManager.getInstance(context);
        moduleCharacteristics = ModuleCharacteristics.getInstance();




            if (!isMyServiceRunning(CaptureUploadService.class, context))
            {

                Log.d(TAG, "Trying autostart now...");

                StartTheService(context);
            }
            else
            {
                Log.e(TAG, "CaptureUploadService is already running! (at least according to isRunning())");
            }

    }

    private boolean isMyServiceRunning(Class<?> serviceClass, Context context) {


        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


    private void StartTheService(Context ctx){

        Intent activity_intent;

        // Report a SystemPowerEvent to Firebase.
        if (!Utils.getSubjectId(ctx).isEmpty())
        {

            HashMap<String, String> map = HashMapPool.getMap(); // Get from pool
            map.put("power", "on");

            EventOperationManager.getInstance(ctx).addEvent(moduleCharacteristics.getSystemPowerEventCharacteristics(), map);


            HashMapPool.releaseMap(map); // Return to pool after use

        }

        // Check if the user is logged in.
        int login_result = LoginActivity.ensureCompleteLogin(ctx, null);

        // If not logged in, we gotta get in the user's face dood. Show then the login page.
//        else
        if(login_result == -1) {
            activity_intent = new Intent(ctx, LoginActivity.class);
        }
        else {

            // Don't auto-start if user explicitly turned tracking off
            boolean userStoppedTracking = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .getBoolean("user_stopped_tracking", false);
            if (userStoppedTracking) {
                Log.d(TAG, "User stopped tracking — skipping autostart");
                return;
            }

            // PASSIVE group never runs continuous tracking services
            String studyGroup = new LogInPreference(ctx).GetStudyGroup();
            if ("PASSIVE".equalsIgnoreCase(studyGroup)) {
                Log.d(TAG, "PASSIVE group — skipping autostart (no continuous tracking)");
                return;
            }

            SetAlarm alarm = new SetAlarm();
            alarm.setPeriodicAlarm(ctx);
            alarm.SetAlarmFor4HoursToAskUserAgainToOpenTheAppAndStartTheService(ctx);
            alarm.SetAlarmFor30MinToAskUserAgainToStartService(ctx);

            ContextCompat.startForegroundService(ctx,new Intent(ctx,ScreenMonitorService.class));

            // Start media projection for screenshot capture
            activity_intent = new Intent(ctx, CaptureUploadStarter.class);
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            ScreenshotCapture.startupInstigator = "boot";
            
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            ctx.startActivity(activity_intent);
            return;
        }

        // This should only execute for login case
        if (activity_intent != null) {
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            ctx.startActivity(activity_intent);
        }

    }

}
