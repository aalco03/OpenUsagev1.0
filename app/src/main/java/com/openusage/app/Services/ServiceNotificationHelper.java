package com.openusage.app.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.openusage.app.Activity.AppRunningActivity;
import com.openusage.app.R;
import com.openusage.app.modulemanager.ModuleController;

/**
 * Handles foreground notification setup for CaptureUploadService.
 * Extracted from CaptureUploadService to follow Single Responsibility Principle.
 */
public class ServiceNotificationHelper {

    private static final String TAG = "ServiceNotificationHelper";
    static final int NOTIFICATION_ID = 1472894;
    private static final String NOTIFICATION_CHANNEL_ID = "23742";
    private static final CharSequence NOTIFICATION_CHANNEL_NAME = "Screenomics_Channel";

    private final Service service;
    private Notification notification;

    public ServiceNotificationHelper(Service service) {
        this.service = service;
    }

    /**
     * Creates notification, starts the service in foreground, and returns the notification.
     */
    public Notification startForegroundNotification(String title, String text) {
        NotificationManager nm = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            Log.e(TAG, "Could not get NotificationManager");
            return null;
        }

        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (service.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted");
            }
        }

        PendingIntent contentIntent = PendingIntent.getActivity(service, 0,
                new Intent(service, AppRunningActivity.class),
                PendingIntent.FLAG_IMMUTABLE);

        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            notificationChannel.enableLights(false);
            notificationChannel.setShowBadge(true);
            notificationChannel.enableVibration(false);
            nm.createNotificationChannel(notificationChannel);
        }

        notification = new NotificationCompat.Builder(service, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setSmallIcon(R.drawable.logo_stanford)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();

        nm.notify(NOTIFICATION_ID, notification);

        // Start foreground service with appropriate type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ModuleController.ENABLE_SCREENSHOTS) {
                service.startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                service.startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            }
        } else {
            service.startForeground(NOTIFICATION_ID, notification);
        }
        Log.i(TAG, "Foreground service started");

        return notification;
    }

    public Notification getNotification() {
        return notification;
    }
}
