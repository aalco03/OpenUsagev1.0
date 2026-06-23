package com.openusage.app.DatabaseHelper;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

public class InterCommunicationPreference {

    /** May 7, 2025 This file helps the app to send signals about the work **/

    private Context context;
    private final String Shared_prefrence = "inter_communication_pref";
    private final String IsNewNotificationPopped = "notificationPopped";
    private final String IsUserTurnedOffTheService = "user_turn_off_service_or_device_stopped";

    private final String IsDeviceOff = "isDeviceOff";

    // This is used to send message to the screen capture service So that we can get mediaprojection instance according to activity
    private final String WhichActivityStartedService = "WhichActivityStartedService";
    private final String BATTERY_OPTIMIZATION = "BatteryOptimization";

    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;

    public InterCommunicationPreference(Context context) {
        this.context = context;
    }

    public void PutNewNotificationPopped(boolean value){

        editor = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE).edit();
        editor.putBoolean(IsNewNotificationPopped,value);
        editor.apply();
    }

    public void WhichActivityCalledStartService(int value){
        editor = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE).edit();
        editor.putInt(WhichActivityStartedService,value);
        editor.apply();
    }

    public boolean GET_NewNotificationPopped(){
        prefs = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE);
        return prefs.getBoolean(IsNewNotificationPopped,false);
    }

    public int GET_WhichActivityStartedService(){
        prefs = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE);
        // 0 for the CaptureUploadStater
        // 1 for the appRunningActivity
        return prefs.getInt(WhichActivityStartedService,0);
    }

    // New Function Battery Optimization


    public void PutShowBatteryOptimizationDialog(int value){

        editor = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE).edit();
        editor.putInt(BATTERY_OPTIMIZATION,value);
        editor.apply();
    }


    // New Function Battery Optimization

    public int GetShowBatteryOptimizationDialog(){

        prefs = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE);
        return prefs.getInt(BATTERY_OPTIMIZATION,0);
    }


}
