package com.openusage.app.Alarm;


import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.openusage.app.DatabaseHelper.InterCommunicationPreference;


public class SeekForNotification extends NotificationListenerService {

    private InterCommunicationPreference prefrence;

    private boolean DoNotAddDuplicates = true;

    @Override
    public void onCreate() {
        super.onCreate();

        prefrence = new InterCommunicationPreference(getApplicationContext());

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {


        if (!sbn.isOngoing()){
            prefrence.PutNewNotificationPopped(true);

        }

    }



    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {

    }

}
