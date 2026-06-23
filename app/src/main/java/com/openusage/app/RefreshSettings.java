package com.openusage.app;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;

import com.openusage.app.FirebaseSettings.SettingsManager;
import com.openusage.app.Services.ScreenMonitorService;

public class RefreshSettings {

    private Context context;
    private long intervalMillis;
    private Handler handler;
    private Runnable runnableTask;
    ScreenMonitorService service1;

    public RefreshSettings(ScreenMonitorService service, Context context, long intervalMillis) {
        this.context = context;
        this.service1 = service;
        this.intervalMillis = intervalMillis;
        this.handler = new Handler();
    }

    public void start() {
        runnableTask = new Runnable() {
            @Override
            public void run() {
                // Call your database or perform any operation here
                performTask();

                // Re-run the task after the specified interval
                handler.postDelayed(this, intervalMillis);
            }
        };

        // Start the first task
        handler.post(runnableTask);
    }

    // Updates the interval only if it's different from the current one
    public void updateIntervalIfNeeded(long newIntervalMillis) {
        if (this.intervalMillis != newIntervalMillis) {
            // Stop the current task
            stop();

            // Update interval
            this.intervalMillis = newIntervalMillis;

            // Restart the task with the new interval
            start();
        }
    }


    public void stop() {
        // Stop the task from being run again
        handler.removeCallbacks(runnableTask);
    }

    private void performTask() {
        if (SettingsManager.exists())
        {
            // Create a listener for the database response.
            ScreenMonitorService service = service1;
            ScreenMonitorService.SettingsDatabaseListener listener = null;
            if (service != null) listener = new ScreenMonitorService.SettingsDatabaseListener();

            // Load settings from database.
            if (isMyServiceRunning(ScreenMonitorService.class, context)){
                SettingsManager.get().loadFromDatabase(context, false, listener);

            }else {
                stop();
            }
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
}

