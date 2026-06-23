package com.openusage.app.Alarm;

import static android.content.Context.ALARM_SERVICE;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

    public class SetAlarm {

        public void SetAlarmFor4HoursToAskUserAgainToOpenTheAppAndStartTheService(Context context){
            // Initialize the AlarmManager
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            // Create an Intent for the BroadcastReceiver
            Intent intent = new Intent(context, BroadcastReceiverForAlarm.class);
            intent.putExtra("request_code", 6);
            // Create a PendingIntent to be triggered when the alarm goes off
            PendingIntent pendingIntent = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pendingIntent = PendingIntent.getBroadcast(context, 6, intent, PendingIntent.FLAG_MUTABLE);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingIntent = PendingIntent.getBroadcast(context, 6, intent, PendingIntent.FLAG_IMMUTABLE);
            }
            // Set the initial alarm to go off after 30 minutes
            long intervalMillis = 240 * 60 * 1000; // 30 minutes in milliseconds
            long triggerTimeMillis = System.currentTimeMillis() + intervalMillis;

            // Set the exact alarm using AlarmManager
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
            );
        }

        public void SetAlarmFor30MinToAskUserAgainToStartService(Context context){
            // Initialize the AlarmManager
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            // Create an Intent for the BroadcastReceiver
            Intent intent = new Intent(context, BroadcastReceiverForAlarm.class);
            intent.putExtra("request_code", 5);
            // Create a PendingIntent to be triggered when the alarm goes off
            PendingIntent pendingIntent = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pendingIntent = PendingIntent.getBroadcast(context, 5, intent, PendingIntent.FLAG_MUTABLE);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingIntent = PendingIntent.getBroadcast(context, 5, intent, PendingIntent.FLAG_IMMUTABLE);
            }
            // Set the initial alarm to go off after 30 minutes
            long intervalMillis = 30 * 60 * 1000; // 30 minutes in milliseconds
            long triggerTimeMillis = System.currentTimeMillis() + intervalMillis;

            // Set the exact alarm using AlarmManager
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
            );
        }

        public void setPeriodicAlarm(Context context) {

            if (context != null){
                // Initialize the AlarmManager
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);

                // Create an Intent for the BroadcastReceiver
                Intent intent = new Intent(context, BroadcastReceiverForAlarm.class);
                intent.putExtra("request_code", 0);
                // Create a PendingIntent to be triggered when the alarm goes off
                PendingIntent pendingIntent = null;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_MUTABLE);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
                }
                // Set the initial alarm to go off after 1 minutes
                long intervalMillis = 2 * 60 * 1000; // 1 minutes in milliseconds
                long triggerTimeMillis = System.currentTimeMillis() + intervalMillis;

                // Set the exact alarm using AlarmManager
                if (pendingIntent != null){
                    alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            triggerTimeMillis,
                            pendingIntent
                    );
                }
            }

        }



        public void setRestartScreenObserverServiceAfter1Min(Context context) {

            if (context != null){
                // Initialize the AlarmManager
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);

                // Create an Intent for the BroadcastReceiver
                Intent intent = new Intent(context, BroadcastReceiverForAlarm.class);
                intent.putExtra("request_code", 7);
                // Create a PendingIntent to be triggered when the alarm goes off
                PendingIntent pendingIntent = null;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    pendingIntent = PendingIntent.getBroadcast(context, 7, intent, PendingIntent.FLAG_MUTABLE);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    pendingIntent = PendingIntent.getBroadcast(context, 7, intent, PendingIntent.FLAG_IMMUTABLE);
                }
                // Set the initial alarm to go off after 1 minutes
                long intervalMillis = 60 * 1000; // 1 minutes in milliseconds
                long triggerTimeMillis = System.currentTimeMillis() + intervalMillis;

                // Set the exact alarm using AlarmManager
                if (pendingIntent != null){
                    alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            triggerTimeMillis,
                            pendingIntent
                    );
                }
            }

        }


        public void CancelAlarm(Context context, int AlarmCode) {
            AlarmManager aManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
            Intent intent = new Intent(context, BroadcastReceiverForAlarm.class);

            PendingIntent pendingIntent = null;


            try{
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    pendingIntent = PendingIntent.getBroadcast(context, AlarmCode, intent, PendingIntent.FLAG_MUTABLE);

                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    pendingIntent = PendingIntent.getBroadcast(context, AlarmCode, intent, PendingIntent.FLAG_IMMUTABLE);
                }
            }catch (Exception e){
                e.printStackTrace();
            }

            if (pendingIntent != null){
                aManager.cancel(pendingIntent);
            }

        }


        public void SetAlarmFor7Pm(Context context) {
            // Create PendingIntent for 7 PM
            Intent intent = new Intent(context, BroadcastReceiverForAlarm.class);
            intent.putExtra("request_code", 1);

            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());

            // Set the alarm time to 7 PM
            calendar.set(Calendar.HOUR_OF_DAY, 19);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);

            // If the set time is before the current time, add one day to the calendar
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1);
            }

            PendingIntent pendingIntent = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pendingIntent = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_MUTABLE);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingIntent = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_IMMUTABLE);
            }

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
        }

        public void SetAlarmFor7Am(Context context) {
            // Create PendingIntent for 7 AM
            Intent intent = new Intent(context, BroadcastReceiverForAlarm.class);
            intent.putExtra("request_code", 2);

            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());

            // Set the alarm time to 7 AM
            calendar.set(Calendar.HOUR_OF_DAY, 7);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);

            // If the set time is before the current time, add one day to the calendar
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1);
            }

            PendingIntent pendingIntent = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pendingIntent = PendingIntent.getBroadcast(context, 2, intent, PendingIntent.FLAG_MUTABLE);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingIntent = PendingIntent.getBroadcast(context, 2, intent, PendingIntent.FLAG_IMMUTABLE);
            }

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
        }

    }
