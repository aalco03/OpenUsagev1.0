package com.openusage.app.Services;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.openusage.app.Activity.AppRunningActivity;
import com.openusage.app.Activity.CaptureUploadStarter;
import com.openusage.app.Activity.LoginActivity;
import com.openusage.app.Alarm.SetAlarm;
import com.openusage.app.DatabaseHelper.LogInPreference;
import com.openusage.app.FirebaseSettings.FirebaseManagerSingleton;
import com.openusage.app.FirebaseSettings.SettingsManager;
import com.openusage.app.FirebaseSettingsObserver;
import com.openusage.app.R;
import com.openusage.app.RefreshSettings;
import com.openusage.app.TextBasedEventData.AndroidSpecLogger;
import com.openusage.app.TextBasedEventData.EventOperationManager;
import com.openusage.app.TextBasedEventData.EventUploader;
import com.openusage.app.TextBasedEventData.EventUploaderToFireStore;
import com.openusage.app.TextBasedEventData.SessionDataUploaderToFireStore;
import com.openusage.app.activites.UserStepCapture;
import com.openusage.app.apps.ForegroundAppCapture;
import com.openusage.app.apps.HistoricalUsageCapture;
import com.openusage.app.apps.ScreenOnOffStatusCapture;
import com.openusage.app.battery.BatteryStatusCapture;
import com.openusage.app.locations.LocationUpdater;
import com.openusage.app.modulemanager.ModuleController;
import com.openusage.app.network.NetworkStatusCapture;
import com.openusage.app.screenshots.ScreenshotCapture;

/**
 * This file take care of all the text based data
 * From Adding Data to offline database and then uploading
 * it online to firebase
*/
public class ScreenMonitorService extends Service {
    private Notification notification;

    private static final String TAG = "ScreenMonitorService";
    private static final String NOTIFICATION_CHANNEL_ID = "32312";
    private static final String NOTIFICATION_CHANNEL_NAME = "Screemoniter_name";
    private static final int NOTIFICATION_ID = 1001;
    private static final long MIN_SERVICE_RUNTIME = TimeUnit.HOURS.toMillis(5); // 5 hours
    private static final long MAX_SERVICE_RUNTIME = TimeUnit.HOURS.toMillis(6); // 6 hours
    private BroadcastReceiver screenReceiver;
    private Handler autoStopHandler;
    private Runnable autoStopRunnable;
    private BatteryStatusCapture batteryReceiver;

    private long plannedStopTime;

    private final ScreenOnOffStatusCapture screenEventReceiver = new ScreenOnOffStatusCapture();


    BroadcastReceiver broadcastReceiver;

    RefreshSettings refreshSettings;

    UserStepCapture stepCapture;

    EventUploaderToFireStore uploaderToFireStore;

    ForegroundAppCapture appChecker;

    EventUploader uploader;
    private final Handler UploadTextDataHandler = new Handler(Looper.getMainLooper());

    AndroidSpecLogger logger;

    private LocationUpdater locationUpdater;

    private PowerManager.WakeLock wakeLock;


    @Override
    public void onCreate() {
        super.onCreate();

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp::ScreenCaptureWakeLock");
        wakeLock.acquire();

        // Must call startForeground() before stopSelf() on Android 12+
        PostNotification("Syncing data...");

        // Defense-in-depth: if user toggled tracking off, refuse to run
        boolean userStoppedTracking = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("user_stopped_tracking", false);
        if (userStoppedTracking) {
            Log.d(TAG, "user_stopped_tracking is true — stopping self immediately");
            stopSelf();
            return;
        }

        // Register receivers for screen and user presence events
        registerReceivers();

        EventOperationManager.getInstance(this);
        EventUploader.getInstance(this);
        FirebaseManagerSingleton.getFirestore();
        EventUploaderToFireStore.getInstance(this);

        // Initialize settings manager and load settings
        initializeSettingsManager();

        // Initialize all monitoring components
        initializeComponents();

        // Start monitoring components
        startMonitoringComponents();

        // Set up auto-stop mechanism
        scheduleServiceAutoStop();

        Log.d(TAG, "Service created and started in foreground");
    }

    private void registerReceivers() {
        // Screen events receiver
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_USER_PRESENT);

        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action != null) {
                    if (action.equals(Intent.ACTION_USER_PRESENT)) {
                        // Only restart CaptureUploadService if screenshots are enabled
                        if (SettingsManager.val("screenshots-enabled") == 1
                                && !isMyServiceRunning(CaptureUploadService.class, context)) {
                            attemptServiceRestart();
                        }
                    }
                }
            }
        };


        registerReceiver(screenReceiver, screenFilter);

    }

    private static boolean isMyServiceRunning(Class<?> serviceClass, Context context) {


        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void scheduleServiceAutoStop() {
        // Calculate a random runtime between 5-6 hours
        Random random = new Random();
        long randomRuntime = MIN_SERVICE_RUNTIME +
                random.nextInt((int) (MAX_SERVICE_RUNTIME - MIN_SERVICE_RUNTIME));

        plannedStopTime = System.currentTimeMillis() + randomRuntime;

        Log.d(TAG, "Service scheduled to auto-stop after " +
                TimeUnit.MILLISECONDS.toHours(randomRuntime) + " hours and " +
                TimeUnit.MILLISECONDS.toMinutes(randomRuntime) % 60 + " minutes");

        autoStopHandler = new Handler();
        autoStopRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Auto-stop timer triggered. Stopping and scheduling restart.");
                stopSelf();
            }
        };

        // Schedule the auto-stop
        autoStopHandler.postDelayed(autoStopRunnable, randomRuntime);
    }

    private void scheduleServiceRestart() {
        SetAlarm alarm = new SetAlarm();
        alarm.setRestartScreenObserverServiceAfter1Min(this);
    }

    private void PostNotification(String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                Log.e(TAG, "Could not get NotificationManager");
                return;
            }

            // Create pending intent for notification click
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, AppRunningActivity.class),
                    PendingIntent.FLAG_IMMUTABLE);

            // Create notification channel for Android O and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int importance = NotificationManager.IMPORTANCE_LOW;
                NotificationChannel notificationChannel = new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, importance);
                notificationChannel.enableLights(false);
                notificationChannel.setShowBadge(true);
                notificationChannel.enableVibration(false);
                nm.createNotificationChannel(notificationChannel);
            }

            // Calculate remaining runtime if auto-stop is scheduled
            String statusText = text;
            if (plannedStopTime > 0) {
                long remainingTime = plannedStopTime - System.currentTimeMillis();
                if (remainingTime > 0) {
                    long hours = TimeUnit.MILLISECONDS.toHours(remainingTime);
                    long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime) % 60;
                    statusText = text + " (Auto-restart in " + hours + "h " + minutes + "m)";
                }
            }

            // Build the notification
            notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Screenomics")
                    .setContentText(statusText)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.logo_stanford)
                    .setContentIntent(contentIntent)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .build();

            // Show notification
            nm.notify(NOTIFICATION_ID, notification);

            // Start foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting notification", e);
        }
    }


    private void initializeComponents() {
        // Initialize uploaders
        uploaderToFireStore = EventUploaderToFireStore.getInstance(this);
        uploader = EventUploader.getInstance(this);
        uploader.startUploading();


        // Initialize network receiver
        broadcastReceiver = new NetworkStatusCapture();
        registerReceiver(broadcastReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        if (ModuleController.ENABLE_POWER) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            registerReceiver(screenEventReceiver, filter);
        }

    }

    
    private void initializeSettingsManager() {
        if (!SettingsManager.exists()) {
            Log.i("SettingsManager", "start setting manager");
            SettingsManager.create(new FirebaseSettingsObserver() {
                @Override
                public void onSettingsChanged(List<String> changedSettings) {

                }
            }).load(this, new CaptureUploadService.SettingsDatabaseListener());
        }

        // Start settings refresh
        refreshSettings = new RefreshSettings(this, this, SettingsManager.val("settings-refresh-interval"));
        refreshSettings.start();

        // Start specs logger
        logger = new AndroidSpecLogger(SettingsManager.val("specs-check-interval"), this);
        logger.startTimer();
    }


    private void startMonitoringComponents() {
        // Step counter — enabled for all non-PASSIVE groups
        if (ModuleController.ENABLE_ACTIVITIES) {
            long stepInterval = SettingsManager.val("pa-stepcounts-interval");
            if (stepInterval <= 0) {
                stepInterval = 300000; // Default to 5 minutes if not set
            }
            stepCapture = new UserStepCapture(this, stepInterval);
            stepCapture.start();
        }

        // Start battery monitoring if enabled
        if (ModuleController.ENABLE_BATTERY) {
            batteryReceiver = new BatteryStatusCapture();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_BATTERY_LOW);
            intentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
            intentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
            intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            registerReceiver(batteryReceiver, intentFilter);
        }

        // Start app usage monitoring if enabled
        if (ModuleController.ENABLE_APPS) {
            appChecker = new ForegroundAppCapture(this, SettingsManager.val("foreground-app-check-interval"));
            appChecker.startChecking();
        }

        // Capture historical usage snapshot (one-time, on first service start)
        if (!HistoricalUsageCapture.isSnapshotCaptured(this)) {
            final String userId = new LogInPreference(this).GetUserSubjId();
            if (userId != null && !userId.isEmpty()) {
                new Thread(() -> {
                    try {
                        Log.i(TAG, "Capturing historical usage snapshot for: " + userId);
                        HistoricalUsageCapture.SnapshotResult result =
                                HistoricalUsageCapture.captureHistoricalSnapshot(getApplicationContext(), userId);
                        Log.i(TAG, "Historical snapshot result: " + result);
                    } catch (Exception e) {
                        Log.e(TAG, "Error capturing historical usage snapshot", e);
                    }
                }).start();
            }
        }

        // Start location tracking if enabled
        if (SettingsManager.val("gps-enabled") == 1 && ModuleController.ENABLE_LOCATIONS) {
            locationUpdater = new LocationUpdater(this, SettingsManager.val("gps-location-interval"));

            if (locationUpdater.checkLocationPermission()) {
                locationUpdater.startLocationUpdates();
            }
        } else {
            Log.d("Module Manager", "Gps Module Is off");
        }

        startTextUploadInterval();

    }


    /**
     * Runnable to upload text data periodically
     */
    private final Runnable textDataUploadTask = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Sync timer fired - triggering upload");
            StartUploadingOfflineTextDataToOnline();
            long interval = SettingsManager.val("data-text-upload-interval");
            Log.d(TAG, "📅 Next sync scheduled in " + (interval / 1000) + " seconds");
            UploadTextDataHandler.postDelayed(this, interval);
        }
    };

    /**
     * Start text data upload interval
     */
    private void startTextUploadInterval() {
        long interval = SettingsManager.val("data-text-upload-interval");
        Log.d(TAG, "Starting sync timer - first sync in " + (interval / 1000) + " seconds");
        UploadTextDataHandler.postDelayed(textDataUploadTask, interval);
    }

    /**
     * Stop text data upload interval
     */
    private void stopTextUploadInterval() {
        if (UploadTextDataHandler != null) {
            UploadTextDataHandler.removeCallbacks(textDataUploadTask);
        }
    }


    /**
     * Upload offline text data to online
     */
    private void StartUploadingOfflineTextDataToOnline() {
        Log.d(TAG, "Checking network for sync...");
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

            if (activeNetwork != null) {
                boolean shouldUpload = false;
                int wifiOnly = SettingsManager.val("data-text-upload-wifi-only");
                int networkType = activeNetwork.getType();
                
                Log.d(TAG, "📶 Network type: " + networkType + ", WiFi-only: " + wifiOnly);

                // Check if we should upload based on connection type
                if (wifiOnly == 1 && networkType == ConnectivityManager.TYPE_WIFI) {
                    shouldUpload = true;
                } else if (wifiOnly == 0 &&
                        (networkType == ConnectivityManager.TYPE_MOBILE ||
                                networkType == ConnectivityManager.TYPE_WIFI)) {
                    shouldUpload = true;
                }

                if (shouldUpload) {
                    Log.i(TAG, "Network OK - starting sync");
                    uploaderToFireStore.startUploadOfflineToOnlineEvents(false);
                    new Thread(() -> {
                        try {
                            Log.d(TAG, "Background thread: Starting session sync");
                            SessionDataUploaderToFireStore.getInstance(this).uploadUnsyncedSessions(false);
                            Log.d(TAG, "Background thread: Session sync complete");
                        } catch (Exception e) {
                            Log.e(TAG, "Error in background sync", e);
                        }
                    }).start();
                } else {
                    Log.w(TAG, "Network conditions not met - skipping sync");
                }
            } else {
                Log.w(TAG, "No active network - skipping sync");
            }
        } else {
            Log.e(TAG, "ConnectivityManager is null");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not providing binding
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service is being destroyed");

        // Cancel the auto-stop timer if service is manually destroyed
        if (autoStopHandler != null && autoStopRunnable != null) {
            autoStopHandler.removeCallbacks(autoStopRunnable);
        }

        if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }

        // Unregister receivers
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering screen receiver: " + e.getMessage());
            }
        }

        // Stop uploaders
        if (uploader != null) {
            uploader.stopUploading();
        }

        // Stop logger
        if (logger != null) {
            logger.stopTimer();
        }

        if (refreshSettings != null) {
            refreshSettings.stop();
        }

        if (locationUpdater != null) {
            locationUpdater.stopLocationUpdates();
        }

        if (appChecker != null) {
            appChecker.stopChecking();
        }

        if (stepCapture != null) {
            stepCapture.stop();
        }

        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
        }

        // Stop text upload interval
        stopTextUploadInterval();

        try {
            unregisterReceiver(screenEventReceiver);

            if (batteryReceiver != null) {
                unregisterReceiver(batteryReceiver);
            }


        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receivers", e);
        }

        scheduleServiceRestart();


        EventOperationManager.getInstance(this).destroy();
        EventUploader.getInstance(this).destroy();
        FirebaseManagerSingleton.shutdown();
        EventUploaderToFireStore.destroy();
        SettingsManager.destroy(this);

        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);


        super.onDestroy();
    }




    // The restart method you provided
    private void attemptServiceRestart() {
            SystemClock.sleep(1000);

//            Intent restartIntent = new Intent(this, AppRunningActivity.class);
////            restartIntent.putExtra("start_service", true);
//            restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            restartIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_ANIMATION);
//            restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//            this.startActivity(restartIntent);




        Intent activity_intent = null;


        // Check if the user is logged in.
        int login_result = LoginActivity.ensureCompleteLogin(this, null);

        if(login_result == -1) {
            return;
        }

        else {
            // Restart CaptureUploadService with media projection
            activity_intent = new Intent(this.getApplicationContext(), CaptureUploadStarter.class);
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            ScreenshotCapture.startupInstigator = "user";
            
            if (activity_intent != null) {
                activity_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity_intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                this.startActivity(activity_intent);
            }
        }


    }

    public static class SettingsDatabaseListener implements SettingsManager.DatabaseListener{

        @Override
        public void onSuccess() {

        }

        @Override
        public void onFailure() {

        }
    }
}