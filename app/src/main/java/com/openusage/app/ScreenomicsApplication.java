package com.openusage.app;

import android.app.Application;

import com.openusage.app.FirebaseSettings.FirebaseManagerSingleton;


public class ScreenomicsApplication extends Application {

    @Override
    public void onCreate()
    {
        super.onCreate();

        FirebaseManagerSingleton.init(this);

        // Set a custom uncaught exception handler so we can upload logs of crashes.
//        Context context = getApplicationContext();
//        Thread.setDefaultUncaughtExceptionHandler(new ScreenomicsExceptionHandler(context));

    }

}
