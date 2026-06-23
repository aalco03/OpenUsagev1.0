package com.openusage.app.Alarm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.openusage.app.Activity.LoginActivity;
import com.openusage.app.PermissionScreens.PermissionParentActivity;
import com.openusage.app.R;


public class NotificationManage {


    private final static String CHANNEL_ID = "Service Alert";
    private final Context context;
    NotificationManagerCompat manager;

    public NotificationManage(Context context) {
        this.context = context;
        manager = NotificationManagerCompat.from(context);
    }

    public void Create_Channel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = context.getSystemService(NotificationManager.class);

            manager.createNotificationChannel(channel);
        }

    }


    public void SendStartServiceNotification(String Message,String status) {

        PendingIntent contentIntent = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {


            if (status.equals("")){

                contentIntent = AfterNotificationClickProcess(false, LoginActivity.class);

            }else {

                contentIntent =  AfterNotificationClickProcess(false, PermissionParentActivity.class);
//                contentIntent =  AfterNotificationClickProcess(false, CapturePermissionActivity.class);
            }

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {


            if (status.equals("")){

                contentIntent = AfterNotificationClickProcess(true, LoginActivity.class);
            }else {

//                contentIntent = AfterNotificationClickProcess(true, CapturePermissionActivity.class);
//                contentIntent = AfterNotificationClickProcess(true, CapturePermissionActivity.class);
                contentIntent = AfterNotificationClickProcess(true, PermissionParentActivity.class);

            }

        }


        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID);
        builder.setContentTitle("Screenomics");
        builder.setContentText(Message);
        builder.setAutoCancel(true);
        builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        builder.setContentIntent(contentIntent);
        builder.setSmallIcon(R.drawable.logo_stanford);

        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        manager.notify(2, builder.build());



    }

    private PendingIntent AfterNotificationClickProcess(boolean Immutable, Class WhichClass){

        PendingIntent contentIntent = null;


        Intent intent = new Intent(context, WhichClass);

        intent.putExtra(context.getString(R.string.open_by_notification),context.getString(R.string.open_by_notification));

        if (Immutable){
            contentIntent = PendingIntent.getActivity(context, 0,
                    intent, PendingIntent.FLAG_IMMUTABLE);
            return contentIntent;
        }else {
            contentIntent = PendingIntent.getActivity(context, 0,
                    intent, PendingIntent.FLAG_MUTABLE);

            return contentIntent;
        }


    }


}
