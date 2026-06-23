package com.openusage.app.Services;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import com.openusage.app.Activity.AppRunningActivity;
import com.openusage.app.Activity.CaptureUploadStarter;
import com.openusage.app.Alarm.SetAlarm;
import com.openusage.app.DatabaseHelper.InterCommunicationPreference;
import com.openusage.app.DatabaseHelper.LogInPreference;
import com.openusage.app.FirebaseSettingsObserver;
import com.openusage.app.MediaProjectionDied;
import com.openusage.app.TextBasedEventData.EventMapBuilder;
import com.openusage.app.Activity.LoginActivity;
import com.openusage.app.PermissionScreens.PermissionParentActivity;
import com.openusage.app.R;
import com.openusage.app.FirebaseSettings.SettingsManager;
import com.openusage.app.TextBasedEventData.EventOperationManager;
import com.openusage.app.TextBasedEventData.HashMapPool;
import com.openusage.app.TextBasedEventData.EventUploaderToFireStore;
import com.openusage.app.modulemanager.ModuleCharacteristics;
import com.openusage.app.modulemanager.ModuleController;
import com.openusage.app.screenshots.ScreenshotCapture;
import eventreporter.EventTimestamp;

public class CaptureUploadService extends Service {
    private final String TAG = "CaptureUploadService";
    private static boolean mIsRunning = false;


    // Broadcasts
    public static final String ACTION_SCREENSHOT = "edu.stanford.communication.screenomics.ACTION_SCREENSHOT";
    EventUploaderToFireStore uploaderToFireStore;
    

    // User login data
    private String subject_id;

    // Media projection
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;

    // Foreground service notification
    private Notification notification;
    private static final int NOTIFICATION_ID = 1472894;
    private static final String NOTIFICATION_CHANNEL_ID = "23742";
    private static final CharSequence NOTIFICATION_CHANNEL_NAME = "Screenomics_Channel";

    LogInPreference LogPref;


    // Screen orientation change detection
    private boolean detectOrientationChanges;
    private int lastScreenOrientation;


    // Screen on/off
    private BroadcastReceiver wakeReceiver;
    
    // Screenshot fallback trigger (from text extraction)
    private ScreenshotFallbackReceiver screenshotFallbackReceiver;

    private InterCommunicationPreference prefrence;

    ScreenshotCapture screenshotCapture;

    SetAlarm alarm;





    ModuleCharacteristics moduleCharacteristics = ModuleCharacteristics.getInstance();



    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();


    }

    /**
     * Service startup.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand called (flags=" + flags + ", startId=" + startId + ")");
        
        try {
            // Start notification first to avoid ANR
            startNotification(getResources().getString(R.string.noti_title), getResources().getString(R.string.noti_text));

            // Initialize key components
            initializeComponents();

            // Check if user is logged in
            if (!initLoginData()) {
                Log.e(TAG, "User not logged in - stopping service");
                LoginActivity.userLogOut(this, true);
                return START_NOT_STICKY;
            }

            // Initialize the SettingsManager
            initializeSettingsManager();

            // Start wake detection
            startWakeDetection();
            
            // Start screenshot fallback receiver for text extraction fallback
            startScreenshotFallbackReceiver();

            // Initialize screen capture
            initializeScreenCapture();

            // Mark service as running
            mIsRunning = true;
            Log.i(TAG, "Service fully started (START_STICKY)");

            return START_STICKY;
        } catch (Exception e) {
            Log.e(TAG, "Critical error starting service", e);
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    /**
     * Initialize core components
     */
    private void initializeComponents() {

        uploaderToFireStore = EventUploaderToFireStore.getInstance(this);

        // Log service resumed event
        uploaderToFireStore.uploadSingleEvent("ScreenshotPauseEvent",
                EventMapBuilder.buildCompleteMap(null, "Resumed"), getApplicationContext());


        // Initialize alarm and preferences
        alarm = new SetAlarm();
        prefrence = new InterCommunicationPreference(getApplicationContext());
        alarm.CancelAlarm(getApplicationContext(), 0);
        LogPref = new LogInPreference(getApplicationContext());

        alarm.SetAlarmFor4HoursToAskUserAgainToOpenTheAppAndStartTheService(getApplicationContext());


        // Initialize media projection manager
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        // Create screenshots directory if it doesn't exist
        createScreenshotsDirectory();
        

    }

    /**
     * Create screenshots directory if it doesn't exist
     */
    private void createScreenshotsDirectory() {
        try {
            File screenshotsDir = new File(getExternalFilesDir(null), "screenshots");
            if (!screenshotsDir.exists()) {
                if (!screenshotsDir.mkdirs()) {
                    Log.w(TAG, "Failed to create screenshots directory");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating screenshots directory", e);
        }
    }

    /**
     * Initialize Settings Manager
     */
    private void initializeSettingsManager() {
        if (!SettingsManager.exists()) {
            Log.i("SettingsManager", "start setting manager");
            SettingsManager.create(new FirebaseSettingsObserver() {
                @Override
                public void onSettingsChanged(List<String> changedSettings) {

                }
            }).load(this, new SettingsDatabaseListener());
        }

    }

    /**
     * Initialize screen capture functionality
     */
    private void initializeScreenCapture() {
        // Request screen capture permission if needed
        AskForScreenCapture();

        if (ModuleController.ENABLE_SCREENSHOTS) {
            postStartProjection();

            // Check if we have the necessary components for screenshots
            if (mediaProjectionManager == null || screenshotCapture == null || 
                (screenshotCapture != null && screenshotCapture.mediaProjection == null)) {
                // In testing mode, continue without screenshots
                Log.w(TAG, "MediaProjection not available - running in testing mode without screenshots");
                Log.i(TAG, "Service will continue running for data collection (app usage, UI text, etc.)");
                // Don't call init_handleMediaProjectionNull() which would stop the service
                // Just continue without screenshot capture
            }
        }

        // Initialize screen orientation tracking
        detectOrientationChanges = true;
        lastScreenOrientation = getResources().getConfiguration().orientation;

        // Check current screen state
        checkScreenState();

        // Start screen capture
        if (screenshotCapture != null) {
            screenshotCapture.createVirtualDisplay();
        }

        // Start uploading text data
    }

    /**
     * Check the current screen state
     */
    private void checkScreenState() {
        DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (dm != null && screenshotCapture != null) {
            for (Display display : dm.getDisplays()) {
                if (display.getState() != Display.STATE_OFF) {
                    screenshotCapture.screenAwake = true;
                    Log.i(TAG, "Screen is on");
                    break;
                }
            }
        }

        // Kick off retrieval of server time for use in timestamps
        EventTimestamp.retrieveServerTime();
    }

    /**
     * Request screen capture permission and initialize screen capture
     */
    private void AskForScreenCapture() {
        try {
            // In testing mode, MediaProjection data may not be available
            // Allow service to continue without screenshots
            if (prefrence.GET_WhichActivityStartedService() == 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        // Create MediaProjection from static resultCode and intent
                        // This must happen AFTER foreground service is running
                        int resultCode = CaptureUploadStarter.getResultCode();
                        Intent intent = CaptureUploadStarter.getIntents();
                        
                        if (resultCode == Activity.RESULT_OK && intent != null) {
                            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, intent);
                        } else {
                            Log.w(TAG, "Cannot create MediaProjection - invalid resultCode or null intent");
                            mediaProjection = null;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to create MediaProjection from CaptureUploadStarter", e);
                        mediaProjection = null;
                    }

                    if (ModuleController.ENABLE_SCREENSHOTS && mediaProjection != null) {
                        initializeScreenshotCapture();
                        screenshotCapture.mediaProjection = mediaProjection;
                        screenshotCapture.StartNewThreadToCapture();
                        registerMediaProjectionCallback();
                    }
                } else {
                    try {
                        mediaProjection = CaptureUploadStarter.getMediaProjection();
                    } catch (Exception e) {
                        Log.w(TAG, "MediaProjection not available from CaptureUploadStarter (testing mode): " + e.getMessage());
                        mediaProjection = null;
                    }

                    if (ModuleController.ENABLE_SCREENSHOTS && mediaProjection != null) {
                        initializeScreenshotCapture();
                        screenshotCapture.mediaProjection = mediaProjection;
                        screenshotCapture.StartNewThreadToCapture();
                    }
                }
            } else if (prefrence.GET_WhichActivityStartedService() == 1) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        // Create MediaProjection from static resultCode and intent
                        // This must happen AFTER foreground service is running
                        int resultCode = AppRunningActivity.getResultCode();
                        Intent intent = AppRunningActivity.getIntents();
                        
                        if (resultCode == Activity.RESULT_OK && intent != null) {
                            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, intent);
                        } else {
                            Log.w(TAG, "Cannot create MediaProjection - invalid resultCode or null intent");
                            mediaProjection = null;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to create MediaProjection from AppRunningActivity", e);
                        mediaProjection = null;
                    }

                    if (ModuleController.ENABLE_SCREENSHOTS && mediaProjection != null) {
                        initializeScreenshotCapture();
                        screenshotCapture.mediaProjection = mediaProjection;
                        screenshotCapture.StartNewThreadToCapture();
                        registerMediaProjectionCallback();
                    }
                } else {
                    try {
                        mediaProjection = AppRunningActivity.getMediaProjection();
                    } catch (Exception e) {
                        Log.w(TAG, "MediaProjection not available from AppRunningActivity (testing mode): " + e.getMessage());
                        mediaProjection = null;
                    }

                    if (ModuleController.ENABLE_SCREENSHOTS && mediaProjection != null) {
                        initializeScreenshotCapture();
                        screenshotCapture.mediaProjection = mediaProjection;
                        screenshotCapture.StartNewThreadToCapture();
                    }
                }
            }
            
            // Log service status
            if (mediaProjection == null) {
                Log.i(TAG, "Service running in testing mode - no screenshot capture, but app usage and UI text tracking active");
            } else {
                Log.i(TAG, "Service running with full screenshot capture enabled");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing media projection", e);
            // Don't launch permission activity in testing mode - just continue without screenshots
            Log.w(TAG, "Continuing service operation without screenshot capture");
        }
    }

    /**
     * Initialize screenshot capture component
     */
    private void initializeScreenshotCapture() {
        screenshotCapture = new ScreenshotCapture(
                getApplicationContext(),
                SettingsManager.val("screenshot-interval"),
                SettingsManager.val("screenshot-check-interval"),
                SettingsManager.val("data-nontext-upload-wifi-only"),
                SettingsManager.val("data-nontext-upload-interval"),
                SettingsManager.val("kill-switch"),
                SettingsManager.val("screenshot-absolute-timing"),
                new MediaProjectionDied() {
                    @Override
                    public void MaybeMediaProjectionDied() {
                        if (mediaProjection != null) {
                            mediaProjection.stop();
                        }
                        if (screenshotCapture != null) {
                            screenshotCapture.destroyVirtualDisplay();
                        }
                        stopSelf();
                    }
                }
        );
    }

    /**
     * Register media projection callback
     */
    private void registerMediaProjectionCallback() {
        if (screenshotCapture != null && screenshotCapture.mediaProjection != null) {
            screenshotCapture.mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onCapturedContentResize(int width, int height) {
                    super.onCapturedContentResize(width, height);
                }
            }, screenshotCapture.handler);
        } else if (SettingsManager.val("screenshots-enabled") == 1) {
            launchPermissionActivity();
        } else {
            Log.d(TAG, "Screenshots disabled — skipping MediaProjection re-request");
        }
    }

    /**
     * Launch permission activity when media projection is not available
     */
    private void launchPermissionActivity() {
        if (SettingsManager.val("screenshots-enabled") != 1) {
            Log.d(TAG, "Screenshots disabled — not launching permission activity");
            return;
        }
        Log.i(TAG, "Launching permission activity to re-request MediaProjection");
        Intent permissionIntent = new Intent(this, AppRunningActivity.class);
        permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(permissionIntent);
    }

    /**
     * Register callback for media projection
     */
    private void postStartProjection() {
        if (prefrence.GET_WhichActivityStartedService() == 0 && screenshotCapture != null && screenshotCapture.mediaProjection != null) {
            // Register a callback for when screen capturing is stopped
            screenshotCapture.mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    try {

                        if (!isMyServiceRunning(ScreenMonitorService.class,getApplicationContext())){
                            ContextCompat.startForegroundService(getApplicationContext(), new Intent(getApplicationContext(), ScreenMonitorService.class));
                        }

                        screenshotCapture.destroyVirtualDisplay();

                        uploaderToFireStore.uploadSingleEvent("ScreenshotPauseEvent",
                                EventMapBuilder.buildCompleteMap(null, "Paused"),
                                getApplicationContext());

                        Log.d("ScreenshotCapture", "mediaProjection stopped early");

                        if (screenshotCapture.mediaProjection != null) {
                            screenshotCapture.mediaProjection.unregisterCallback(this);
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error in onStop callback", e);
                    }
                    
                    Log.w(TAG, "MediaProjection stopped - service continues for data collection");

                }

                @Override
                public void onCapturedContentResize(int width, int height) {
                    super.onCapturedContentResize(width, height);
                }

                @Override
                public void onCapturedContentVisibilityChanged(boolean isVisible) {
                    super.onCapturedContentVisibilityChanged(isVisible);
                }
            }, screenshotCapture.handler);
        }
    }


    /**
     * Restart service with permission
     */
    private void restartServiceWithPermission() {
        Intent intent = new Intent(CaptureUploadService.this, PermissionParentActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        stopSelf();
    }

    /**
     * Handle media projection null error
     */
    private void init_handleMediaProjectionNull() {
        if (prefrence.GET_WhichActivityStartedService() == 0) {
            String err_message;
            Intent start_intent;

            // If we were here recently, something is really wrong
            if (screenshotCapture.mJustRestarted) {
                err_message = "MediaProjection was null AGAIN! That's not good -- prompting the user to restart now.";
                start_intent = new Intent(this, PermissionParentActivity.class);
            } else {
                // If this is a novel occurrence, open CaptureUploadStarter to silently reboot
                err_message = "MediaProjection was null on startup, Android probably killed us. Restarting now...";
                start_intent = new Intent(this, CaptureUploadStarter.class);
                start_intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                screenshotCapture.startupInstigator = "media-projection-null";
                screenshotCapture.mJustRestarted = true;
            }

            // Log and report this error
            Log.e(TAG, err_message);

            // Start the desired activity
            start_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(start_intent);

            // Kill this instance of the service
            stopSelf();
        }
    }


    /**
     * Restart the screenshot capture process
     */
    private void restartScreenshotCapture() {
        stopScreenshotCapture();
    }

    /**
     * Stop the screenshot capture process
     */
    private void stopScreenshotCapture() {
        Log.d("ScreenshotMonitor", "Stopping screenshot capture...");

        try {
            if (screenshotCapture != null) {
                screenshotCapture.AcquireImageMutex();
                screenshotCapture.destroyVirtualDisplay();
                screenshotCapture.createVirtualDisplay();
                screenshotCapture.ReleaseImageMutex();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Error stopping screenshot capture", e);
        }
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "onDestroy called");
        
        DestroyTheObject();
        stopWakeDetection();
        stopScreenshotFallbackReceiver();

        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        mIsRunning = false;
        super.onDestroy();
    }

    private void DestroyTheObject(){
        try {




            // Log service paused event
            if (uploaderToFireStore != null) {
                uploaderToFireStore.uploadSingleEvent("ScreenshotPauseEvent",
                        EventMapBuilder.buildCompleteMap(null, "Paused"),
                        getApplicationContext());
            }



            // Clean up resources
            cleanupResources();

            // Mark service as not running
            mIsRunning = false;

            // Destroy virtual display
            if (screenshotCapture != null) {
                screenshotCapture.destroyVirtualDisplay();
            }



        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
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

    /**
     * Clean up resources
     */
    private void cleanupResources() {
        try {
            if (alarm != null) {
                alarm.setPeriodicAlarm(getApplicationContext());
                alarm.SetAlarmFor30MinToAskUserAgainToStartService(getApplicationContext());
                alarm.CancelAlarm(getApplicationContext(), 4);
            }


        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up resources", e);
        }
    }


    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        // Pause upload on low memory
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            screenshotCapture.uploader.pauseUpload();
            EventUploaderToFireStore.getInstance(this).SetIsMemoryLow(true);
        }
        // Resume on moderate memory
        else if (level <= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && screenshotCapture.uploader.isUploadPaused()) {
            screenshotCapture.uploader.resumeUpload();
            EventUploaderToFireStore.getInstance(this).SetIsMemoryLow(false);

        }

        // Report an event that memory is low
        if (moduleCharacteristics != null) {
            HashMap<String, String> lowEventMap = HashMapPool.getMap(); // Get from pool
            lowEventMap.put("level", "" + level);

            EventOperationManager.getInstance(this).addEvent(
                    moduleCharacteristics.getLowMemoryEventCharacteristics(), lowEventMap);

            HashMapPool.releaseMap(lowEventMap);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    /**
     * Kills the service and optionally displays an activity to the user.
     * @param context Context to use
     * @param activityClass Class of the activity to open. Null for no activity.
     */
    public static void killCaptureUpload(Context context, Class<?> activityClass) {
        if (activityClass != null) {
            Intent activity_intent = new Intent(context, activityClass);
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(activity_intent);
        }

        // Stop the CaptureUploadService
        Intent stopIntent = new Intent(context, CaptureUploadService.class);
        context.stopService(stopIntent);
    }

    /**
     * Initialize login data
     * @return true if login data is valid, false otherwise
     */
    private boolean initLoginData() {
        LogInPreference sharedPref = new LogInPreference(this);
        subject_id = sharedPref.GetUserSubjId();
        return !TextUtils.isEmpty(subject_id);
    }

    /**
     * Sets up and displays an ongoing notification for this service.
     * Delegates to ServiceNotificationHelper.
     */
    private void startNotification(String title, String text) {
        ServiceNotificationHelper notificationHelper = new ServiceNotificationHelper(this);
        notification = notificationHelper.startForegroundNotification(title, text);
        if (notification == null) {
            throw new RuntimeException("Failed to create foreground notification");
        }
    }

    /**
     * 15 May 2025
     * Now when ever the screen turns off then we will stop the
     * media projection on android version 15 or higher
     * because according to the android documentation media projection will
     * be released when the screen turns off in android version 15 QPR1 or higher
     * Start wake detection to handle screen on/off events
     */
    private void startWakeDetection() {
        try {
            wakeReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action != null) {
                        if (action.equals(Intent.ACTION_SCREEN_ON)) {
                            if (screenshotCapture != null) {
                                screenshotCapture.screenAwake = true;

                            }else{
                                // Don't crash if mediaProjection is null
                                if (mediaProjection != null) {
                                    mediaProjection.stop();
                                }
                                if (screenshotCapture != null) {
                                    screenshotCapture.destroyVirtualDisplay();
                                }
                                stopSelf();
                            }
                        } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                            if (screenshotCapture != null) {
                                
                                if (isAndroid15Higher()){
                                    Log.e(TAG,"Android version higher stop the service");
                                    if (mediaProjection != null) {
                                        mediaProjection.stop();
                                    }
                                    screenshotCapture.destroyVirtualDisplay();
                                    stopSelf();
                                }
                                else{
                                    Log.e(TAG,"In screen off else part  ");

                                    screenshotCapture.screenAwake = false;
                                }

                            }
                        }
                    }
                }
            };

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(wakeReceiver, filter);
        } catch (Exception e) {
            Log.e(TAG, "Error starting wake detection", e);
        }
    }
    
    /**
     * Start screenshot fallback receiver.
     * Delegates to ScreenshotFallbackReceiver.
     */
    private void startScreenshotFallbackReceiver() {
        screenshotFallbackReceiver = new ScreenshotFallbackReceiver(this, new ScreenshotFallbackReceiver.FallbackCallback() {
            @Override
            public void onLaunchPermissionActivity() {
                launchPermissionActivity();
            }
        });
        screenshotFallbackReceiver.register(new ScreenshotFallbackReceiver.ScreenshotCaptureSupplier() {
            @Override
            public ScreenshotCapture get() {
                return screenshotCapture;
            }
        });
    }

    /**
     * Stop screenshot fallback receiver.
     */
    private void stopScreenshotFallbackReceiver() {
        if (screenshotFallbackReceiver != null) {
            screenshotFallbackReceiver.unregister();
            screenshotFallbackReceiver = null;
        }
    }


    public  boolean isDeviceSecure(Context context) {
        KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager != null) {
            return keyguardManager.isDeviceSecure();  // true if any lock (PIN/pattern/password/biometric) is set
        }
        return false;
    }

    public boolean isAndroid15Higher() {
        return Build.VERSION.SDK_INT >= 35; // Future versions beyond Android 15
    }


    /**
     * Stop wake detection
     */
    private void stopWakeDetection() {
        try {
            if (wakeReceiver != null) {
                unregisterReceiver(wakeReceiver);
                wakeReceiver = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping wake detection", e);
        }
    }

    /**
     * Check if the service is running
     * @return true if the service is running, false otherwise
     */
    public static boolean isRunning() {
        return mIsRunning;
    }


    /**
     * Inner class to handle Settings database callbacks
     */
    public static class SettingsDatabaseListener implements SettingsManager.DatabaseListener{

        @Override
        public void onSuccess() {

        }

        @Override
        public void onFailure() {

        }
    }
}