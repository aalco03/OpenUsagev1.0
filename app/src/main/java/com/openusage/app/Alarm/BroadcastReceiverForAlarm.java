package com.openusage.app.Alarm;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.HashMap;

import com.openusage.app.DatabaseHelper.InterCommunicationPreference;
import com.openusage.app.DatabaseHelper.LogInPreference;
import com.openusage.app.Services.CaptureUploadService;
import com.openusage.app.Services.ScreenMonitorService;
import com.openusage.app.Activity.CaptureUploadStarter;
import com.openusage.app.Activity.LoginActivity;
import com.openusage.app.TextBasedEventData.EventOperationManager;
import com.openusage.app.TextBasedEventData.HashMapPool;
import com.openusage.app.Utils;

import com.openusage.app.FirebaseSettings.SettingsManager;
import com.openusage.app.modulemanager.EventTimestamp;
import com.openusage.app.modulemanager.ModuleCharacteristics;
import com.openusage.app.screenshots.ScreenshotCapture;

public class BroadcastReceiverForAlarm extends BroadcastReceiver
{


    EventOperationManager eventOperationManager;
    ModuleCharacteristics moduleCharacteristics;

    EventTimestamp timestamp = new EventTimestamp();


    @Override
    public void onReceive(Context context, Intent intent) {

        SetAlarm alarm = new SetAlarm();

        eventOperationManager = EventOperationManager.getInstance(context);

        moduleCharacteristics = ModuleCharacteristics.getInstance();

        InterCommunicationPreference InterPrefrence = new InterCommunicationPreference(context);


        int requestCode = intent.getIntExtra("request_code", 0);

//        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

        LogInPreference sharedPref = new LogInPreference(context);

//        String status = sharedPref.getString("user_subj_id", "");
        String status = sharedPref.GetUserSubjId();


        switch (requestCode) {

            case 0:

                if (SettingsManager.val("screenshots-enabled") == 1 && !isMyServiceRunning(CaptureUploadService.class, context) && isMyServiceRunning(ScreenMonitorService.class, context)) {

                        NotificationManage manage = new NotificationManage(context);
                        manage.Create_Channel();
                        manage.SendStartServiceNotification("Screen capture is paused. Please reopen the app to resume.", status);
//                        alarm.SetAlarmFor7Am(context);
//                        alarm.SetAlarmFor7Pm(context);
//                        alarm.CancelAlarm(context, 0);
//                    alarm.check7AmIsCloserOrNotTime(context);

                        Log.d("Alarm","Request code 0 in if");

                } else if (SettingsManager.val("screenshots-enabled") == 1 && !isMyServiceRunning(CaptureUploadService.class, context) && !isMyServiceRunning(ScreenMonitorService.class, context)) {
                    NotificationManage manage = new NotificationManage(context);
                    manage.Create_Channel();
                    manage.SendStartServiceNotification("App is not running. To resume data collection, please reopen the app.", status);

                    Log.d("Alarm","Request code 0 in if");
                } else {

                    Log.d("Alarm","Request code 0 in else");
                    alarm.CancelAlarm(context, 0);
                }
                break;

            case 1:
                // For 7 Pm


                if (SettingsManager.val("screenshots-enabled") == 1 && !isMyServiceRunning(CaptureUploadService.class, context) && isMyServiceRunning(ScreenMonitorService.class, context)) {

                    NotificationManage manage = new NotificationManage(context);
                    manage.Create_Channel();
                    manage.SendStartServiceNotification("Screen capture is paused. Please reopen the app to resume.", status);
                    alarm.SetAlarmFor7Am(context);
                    Log.d("Alarm","Request code 1 in if");
                    LogEvent(context);

                } else if (SettingsManager.val("screenshots-enabled") == 1 && !isMyServiceRunning(CaptureUploadService.class, context) && !isMyServiceRunning(ScreenMonitorService.class, context)) {

                    NotificationManage manage = new NotificationManage(context);
                    manage.Create_Channel();
                    manage.SendStartServiceNotification("App is not running. To resume data collection, please reopen the app.", status);
                    alarm.SetAlarmFor7Am(context);
                    Log.d("Alarm","Request code 1 in if");
                    LogEvent(context);
                }





//
//                if (!isMyServiceRunning(CaptureUploadService.class, context)) {
//                    NotificationManage manage = new NotificationManage(context);
//                    manage.Create_Channel();
//                    manage.SendStartServiceNotification("It seems that screenomics is not running. Please start again.", status);
//                    alarm.SetAlarmFor7Am(context);
//                    Log.d("Alarm","Request code 1 in if");
//                    LogEvent(context);
//
//                }

                break;

            case 2:
                // For 7 Am




                if (SettingsManager.val("screenshots-enabled") == 1 && !isMyServiceRunning(CaptureUploadService.class, context) && isMyServiceRunning(ScreenMonitorService.class, context)) {

                    NotificationManage manage = new NotificationManage(context);
                    manage.Create_Channel();
                    manage.SendStartServiceNotification("Screen capture is paused. Please reopen the app to resume.", status);
                    alarm.SetAlarmFor7Pm(context);
                    Log.d("Alarm","Request code 2 in if");
                    LogEvent(context);

                } else if (SettingsManager.val("screenshots-enabled") == 1 && !isMyServiceRunning(CaptureUploadService.class, context) && !isMyServiceRunning(ScreenMonitorService.class, context)) {

                    NotificationManage manage = new NotificationManage(context);
                    manage.Create_Channel();
                    manage.SendStartServiceNotification("App is not running. To resume data collection, please reopen the app.", status);
                    alarm.SetAlarmFor7Pm(context);
                    Log.d("Alarm","Request code 2 in if");
                    LogEvent(context);
                }


//                if (!isMyServiceRunning(CaptureUploadService.class, context)) {
//                    NotificationManage manage = new NotificationManage(context);
//                    manage.Create_Channel();
//                    manage.SendStartServiceNotification("It seems that screenomics is not running. Please start again.", status);
////                    alarm.SetAlarmFor7Am(context);
////                    alarm.check7AmIsCloserOrNotTime(context);
//                    alarm.SetAlarmFor7Pm(context);
//
//                    Log.d("Alarm","Request code 2 in if");
//                    LogEvent(context);
//
//                }
                break;

//            case 4:
//
//                LogEventsInFirebase firebase = new LogEventsInFirebase();
//
//                firebase.LogEvent(LogEventsInFirebase.GetSimpleClassName("StepCountEvent"),LogEventsInFirebase.GetDefaultMapWithAdditionalValue("step-count","count",pref.GetStepCount()),context);
//                pref.StepCount("0");
//                pref.ResetStepCount(true);
//
////                UploadEventsToFireStore.LogTicker(context,);
//
//                SetAlarm alarm1 = new SetAlarm();
//                alarm1.SetAlarmFor30MinToUploadStepCount(context);
//
//                break;


            case 5:

//                context.startService(new Intent(context, ScreenMonitorService.class));

                SetAlarm alarm2 = new SetAlarm();
                if (SettingsManager.val("screenshots-enabled") == 1 && !isMyServiceRunning(CaptureUploadService.class,context)){

                        StartTheService(context);
                    alarm2.SetAlarmFor30MinToAskUserAgainToStartService(context);

                }else {

                    alarm2.CancelAlarm(context,5);
                }

                break;

            case 6:


                SetAlarm alarm3 = new SetAlarm();
                if (!isMyServiceRunning(ScreenMonitorService.class,context)){

                    attemptServiceRestart(context);

                }

                alarm3.SetAlarmFor4HoursToAskUserAgainToOpenTheAppAndStartTheService(context);

                break;

            case 7:


                SetAlarm alarm4 = new SetAlarm();
                if (!isMyServiceRunning(ScreenMonitorService.class,context)){

                    attemptServiceRestart(context);

                }

                alarm4.SetAlarmFor4HoursToAskUserAgainToOpenTheAppAndStartTheService(context);

                break;

        }

    }

    private void LogEvent(Context context){
        HashMap<String, String> map = HashMapPool.getMap();
        HashMap<String, String> map1 = HashMapPool.getMap();
        HashMap<String, String> map2 = HashMapPool.getMap();
        map.put("activity", "delivered");
        map2.put(moduleCharacteristics.getAlarmManagerCharacteristics().get("className"), timestamp.getTimestringFriendly());
        map1.put("MostRecentEventTime", timestamp.getTimestringFriendly());


        EventOperationManager.getInstance(context).addEvent(moduleCharacteristics.getAlarmManagerCharacteristics(),map);


        HashMapPool.releaseMap(map);
        HashMapPool.releaseMap(map1);
        HashMapPool.releaseMap(map2);

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

        Intent activity_intent = null;

        // Report a SystemPowerEvent to Firebase.
        if (!Utils.getSubjectId(ctx).isEmpty())
        {

            HashMap<String, String> map = HashMapPool.getMap();
            map.put("power", "on");

            EventOperationManager.getInstance(ctx).addEvent(moduleCharacteristics.getSystemPowerEventCharacteristics(), map);

            HashMapPool.releaseMap(map);
        }

        // Check if the user is logged in.
        int login_result = LoginActivity.ensureCompleteLogin(ctx, null);

        if(login_result == -1) {
            SetAlarm alarm = new SetAlarm();
            alarm.CancelAlarm(ctx,5);
        }

        else {
            // Start CaptureUploadService with media projection
            activity_intent = new Intent(ctx.getApplicationContext(), CaptureUploadStarter.class);
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            ScreenshotCapture.startupInstigator = "user";
        }

        // Start the activity if we have an intent
        if (activity_intent != null) {
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            ctx.startActivity(activity_intent);
        }

    }

    private void attemptServiceRestart(Context context) {

        int login_result = LoginActivity.ensureCompleteLogin(context, null);

        if (login_result == -1) {
            SetAlarm alarm = new SetAlarm();
            alarm.CancelAlarm(context,7);
            return;
        }

        ContextCompat.startForegroundService(context, new Intent(context, ScreenMonitorService.class));

//        context.startForegroundService(new Intent(context, ScreenMonitorService.class));
//
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            context.startForegroundService(new Intent(context, ScreenMonitorService.class));
//        }else{
//            context.startService(new Intent(context, ScreenMonitorService.class));
//
//        }

    }
}
