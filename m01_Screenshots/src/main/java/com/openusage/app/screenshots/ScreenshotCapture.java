package com.openusage.app.screenshots;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.Semaphore;


import com.openusage.app.DatabaseHelper.InterCommunicationPreference;
import com.openusage.app.DatabaseHelper.LogInPreference;
import com.openusage.app.EventTimestamp;
import com.openusage.app.FirebaseSettings.SettingsManager;
import com.openusage.app.MediaProjectionDied;
import com.openusage.app.NonTextBasedEventData.ImageUploaderToCloudStorage;
import com.openusage.app.TextBasedEventData.EventOperationManager;
import com.openusage.app.TextBasedEventData.HashMapPool;
import com.openusage.app.TextBasedEventData.EventDatabaseHelper;
import com.openusage.app.modulemanager.ModuleCharacteristics;
import com.openusage.app.modulemanager.ModuleController;
import com.openusage.app.policy.OcrClassifier;
import com.openusage.app.policy.PolicyAuditLogger;
import com.openusage.app.policy.PolicyConfigManager;
import com.openusage.app.policy.PolicyVerdict;
import com.openusage.app.policy.RiskTierRouter;
import com.openusage.app.policy.SensitiveContentPolicy;

public class ScreenshotCapture {

    public MediaProjection mediaProjection;


    private long screenshotStartMillis;
    private long lastScreenshotMillis;

    public static String startupInstigator = "";


    public boolean screenAwake;
    
    // Screenshot fallback mode: only capture on-demand, no automatic periodic screenshots
    public boolean fallbackModeOnly = true;
    
    // Session tracking for database linkage
    public String currentSessionId = null;  // Set when fallback screenshot triggered
    private EventDatabaseHelper dbHelper;
    
    // Screenshot quality settings
    private static final int REGULAR_SCREENSHOT_QUALITY = 85; // High quality for regular screenshots
    private static final int FALLBACK_SCREENSHOT_QUALITY = 45; // Lower quality for LLM processing
    public boolean isFallbackScreenshot = false; // Flag set when triggered by fallback

    // Foreground package for the pending capture (set by the fallback receiver). Used by the
    // Gate 2 package re-check and the Gate 3 risk-tier router.
    public String currentPackage = null;
    // Whether the triggering context carried a sensitive signal (forces Tier 3 in the router).
    public boolean lastTriggerHadSensitiveSignal = false;

    // Sensitive-content policy (Gate 2 package re-check + Gate 3 tier routing / OCR)
    private SensitiveContentPolicy sensitivePolicy;
    private RiskTierRouter riskTierRouter;
    private OcrClassifier ocrClassifier;

    // Directory (under storeDirName) where Tier-3 frames are quarantined for deferred OCR.
    private static final String QUARANTINE_DIR = "quarantine";


    String ScreenShotTakenTime;
    String ScreenShotTakenTimeLocale;

    public boolean mJustRestarted = false;

    private LogInPreference LogPref;
    private InterCommunicationPreference interCommunicationPreference;
    public Handler handler;

    public static final String ACTION_SCREENSHOT = "edu.stanford.communication.screenomics.ACTION_SCREENSHOT";

    public boolean wakeFlag;


    public Runnable screenchecker;

    public ImageUploaderToCloudStorage uploader;

    private static final String TAG = "ScreenshotCapture";

    public int screenWidth = 1, screenHeight = 1;
    public VirtualDisplay virtualDisplay;
    public ImageReader imageReader;
    public Runnable screengrabber;
    private Context context;

    private String storeDirName;

    private Semaphore imageReaderMutex;


    private int notTextInterval, KillSwitch;

    private int useWifi;

    public long lastSettingsPullMillis;

    MediaProjectionDied mediaProjectionDied;

    private int ScreenshotInterval, ScreenshotCheckInterval, ScreenshotAbsoluteTiming;

    EventOperationManager eventOperationManager;
    ModuleCharacteristics moduleCharacteristics;


    public ScreenshotCapture(Context context,int ScreenshotInterval,
                             int ScreenshotCheckInterval,int useWifi,
                             int notTextInterval,int KillSwitch,
                             int ScreenshotAbsolute, MediaProjectionDied mediaProjectionDied) {
        this.mediaProjectionDied = mediaProjectionDied;
        this.ScreenshotCheckInterval = ScreenshotCheckInterval;

        LogPref = new LogInPreference(context);
        interCommunicationPreference = new InterCommunicationPreference(context);
        this.context = context;
        
        // Initialize database helper for session-linked screenshot storage
        this.dbHelper = EventDatabaseHelper.getInstance(context);
        this.ScreenshotAbsoluteTiming = ScreenshotAbsolute;
        this.useWifi = useWifi;
        imageReaderMutex = new Semaphore(1);
        mJustRestarted = false;
        screenAwake = false;

        lastScreenshotMillis = 0;
        lastSettingsPullMillis = 0;

//        SettingsManager.val("data-nontext-upload-wifi-only")
        this.notTextInterval = notTextInterval;
//        SettingsManager.val("data-nontext-upload-interval")
        this.KillSwitch = KillSwitch;
//        SettingsManager.val("kill-switch")
//        SettingsManager.val("screenshot-interval")
        this.ScreenshotInterval = ScreenshotInterval;
//        SettingsManager.val("screenshot-check-interval")

        eventOperationManager = EventOperationManager.getInstance(context);
        moduleCharacteristics = ModuleCharacteristics.getInstance();

        // Sensitive-content policy: Gate 2 (package re-check) + Gate 3 (tier routing / OCR).
        this.sensitivePolicy = PolicyConfigManager.getInstance(context).getPolicy();
        this.riskTierRouter = new RiskTierRouter(context, sensitivePolicy);
        this.ocrClassifier = new OcrClassifier(sensitivePolicy);
    }

    public void StartNewThreadToCapture(){

        if (!initStorage())
        {
            // TODO: handle storage failure
        }


        new Thread()
        {
            @Override
            public void run()
            {

                // Prepare a message looper and create a handler.
                Looper.prepare();
                handler = new Handler();

                // We have to wait until the handler exists to post stuff to it. So do that now.
                startRepeatingEvents();

                // Now start processing queued items.
                Looper.loop();

            }
        }.start();
    }

    public void AcquireImageMutex() throws InterruptedException {
        imageReaderMutex.acquire();
    }
    public void ReleaseImageMutex(){
        imageReaderMutex.release();
    }

    public void startRepeatingEvents()
    {
        // Set up a runnable that acquires an image at a fixed interval.
        screengrabber = new Runnable() {
            @Override
            public void run() {
                grabScreen();

            }
        };

        // MODIFIED: Do NOT start automatic periodic screenshots
        // Screenshots are now triggered on-demand as fallback when text extraction is insufficient
        // The screengrabber runnable is still available for on-demand triggering
        // handler.postDelayed(screengrabber, ScreenshotInterval); // DISABLED - fallback mode only
        Log.i(TAG, "Screenshot capture initialized in FALLBACK MODE (on-demand only)");
        screenshotStartMillis = SystemClock.elapsedRealtime();

        // Set up a runnable that checks that screenshots are actually being taken.
        screenchecker = new Runnable() {
            @Override
            public void run() {
                checkScreengrabbing();
            }
        };
        // DISABLED: Screenchecker not needed in fallback mode (on-demand screenshots only)
        // handler.postDelayed(screenchecker, ScreenshotCheckInterval);

        // Set up a runnable that uploads the accumulated images at a fixed interval.
        uploader = new ImageUploaderToCloudStorage(context,handler,useWifi,notTextInterval,KillSwitch);
        handler.postDelayed(uploader, notTextInterval);

        // Get app version info.
        String versionCode = "", versionName = "";
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            versionName = pInfo.versionName;
            versionCode = "" + pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

//        // Report capture startup.

        LogInPreference sharedPref = new LogInPreference(context);
        String installCode = sharedPref.GetInstallCode();
        HashMap<String, String> map = HashMapPool.getMap();

        map.put("instigator", startupInstigator);

        // Add info for debugging and user testing.
        map.put("app-version-code", versionCode);
        map.put("app-version-name", versionName);
        map.put("install-code", installCode);

        EventOperationManager.getInstance(context).addEvent(moduleCharacteristics.getCaptureStartupCharacteristics(),map);

        startupInstigator = "";

    }


    public void destroyVirtualDisplay()
    {
        if(virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) mediaProjection.stop();
        mediaProjection = null;

    }

    public boolean IsVirtualDisplayNull(){

        if (virtualDisplay != null) {
            return false;
        }
        return true;
    }


    private boolean initStorage()
    {
        File sdCardDirectory = context.getExternalFilesDir(null);
        if (sdCardDirectory != null)
        {
            storeDirName = sdCardDirectory.getAbsolutePath()+"/screenshots";

            File storeDirectory = new File(storeDirName);
            if (!storeDirectory.exists())
            {
                boolean createdStorage = storeDirectory.mkdirs();
                if (createdStorage)
                {
                    Log.w(TAG, "Successfully created storage");
                    return true;
                }
                else
                {
                    Log.w(TAG, "Could not create storage");
                    return false;
                }
            }
        }
        else
        {
            Log.w(TAG, "Failed to find external storage on this device");
            return false;
        }

        return true;
    }



    public void createVirtualDisplay()
    {

        if (ModuleController.ENABLE_SCREENSHOTS){

            DisplayMetrics displayMetrics = new DisplayMetrics();
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

            if (wm == null) return;

            wm.getDefaultDisplay().getRealMetrics(displayMetrics);

            // Initialize display parameters.
            int density = displayMetrics.densityDpi;

            // Get screen size.
            screenWidth = displayMetrics.widthPixels;
            screenHeight = displayMetrics.heightPixels;

            // Set up imageReader and virtualDisplay.
            int virtual_flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
            virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture", screenWidth, screenHeight, density, virtual_flags, imageReader.getSurface(), new VirtualDisplay.Callback() {
                @Override
                public void onPaused() {
                    Log.e(TAG, "Virtual Display Paused");


                    super.onPaused();
                }



                @Override
                public void onResumed() {
//                    imageReader.acquireLatestImage();
                    Log.e(TAG, "Virtual Display resumed");

                    super.onResumed();
                }

                @Override
                public void onStopped() {
                    Log.e(TAG, "Virtual Display Stopped");

                    try{
                        mediaProjectionDied.MaybeMediaProjectionDied();

//                        destroyVirtualDisplay();
                    }catch (Exception ignored){

                    }

                    super.onStopped();
                }

            }, handler);

        }

    }


    public int TryToKnowIfMediaProjectionDied = 0;

    private void grabScreen()
    {

        if (ModuleController.ENABLE_SCREENSHOTS){

            // Initialization.
            int pixelStride;
            int rowStride;
            int rowPadding;
            Image capturedImage = null;
            FileOutputStream fileOutputStream = null;
            Bitmap bitmap = null;
            String name_suffix = "";

            // Add a suffix for the name if this is a significant screenshot.
            if (lastScreenshotMillis == 0) name_suffix = "_START";
            if (wakeFlag) name_suffix = "_ONSET";

            // Get the date and name the screenshot.
            EventTimestamp timestamp = new EventTimestamp();
            ScreenShotTakenTimeLocale = timestamp.getSystemClockTimestring();
            String name =  LogPref.GetUserCode() +"_"+ LogPref.GetUserNumber() +"_"+ Long.parseLong(ScreenShotTakenTimeLocale) + name_suffix ;
            ScreenShotTakenTime = timestamp.getGMTTime();


            // Schedule the next screengrab and determine if we should proceed. Also deal with wakeflag.
            if (!scheduleGrabScreen()) return;

            // Don't screenshot if the kill switch is active.
            if (KillSwitch == 1) return;

            // Opportunistically process any quarantined Tier-3 frames (Mode B, throttled).
            processQuarantine();

            // ========== GATE 2: capture-time package suppression (authoritative) ==========
            // This is the capture choke point: it catches ALL senders of the capture broadcast,
            // closing the trigger->capture race where the user switches into a sensitive app.
            String fgPackage = resolveForegroundPackage();
            if (sensitivePolicy != null) {
                PolicyVerdict captureVerdict = sensitivePolicy.evaluateCapture(fgPackage);
                if (captureVerdict.isSuppress()) {
                    PolicyAuditLogger.log(context, "capture", fgPackage, captureVerdict);
                    Log.i(TAG, "Gate 2 SUPPRESS - skipping capture for " + fgPackage);
                    isFallbackScreenshot = false;
                    return;
                }
            }

            // Make sure there's a sufficient amount of space.
            if (!hasEnoughSpaceForScreenshot())
            {
                Log.w(TAG, "grabScreen() -- storage limit reached");

                HashMap<String, String> screenshotMap = HashMapPool.getMap();
                screenshotMap.put("filename",name);
                screenshotMap.put("error","StorageLimitReached");

                EventOperationManager.getInstance(context).addEvent(moduleCharacteristics.getScreenshotFailureCharacteristics(), screenshotMap);

                return;
            }


            // Grab the latest screenshot.
            try {
                imageReaderMutex.acquire();

                if (imageReader == null)
                {

                    HashMap<String, String> screenshotMap = HashMapPool.getMap();
                    screenshotMap.put("filename",name);
                    screenshotMap.put("error","ImageReaderNull");

                    Log.i(TAG, "grabScreen() -- ImageReader is null");
                    EventOperationManager.getInstance(context).addEvent(moduleCharacteristics.getScreenshotFailureCharacteristics(), screenshotMap);

                    return;
                }

                capturedImage = imageReader.acquireLatestImage();

                imageReaderMutex.release();
            }
            catch (IllegalStateException e)
            {
                if (!IsVirtualDisplayNull()){
                    e.printStackTrace();
                    Log.i(TAG, "grabScreen() -- IllegalStateException");
                    HashMap<String, String> screenshotMap = HashMapPool.getMap();
                    screenshotMap.put("filename",name);
                    screenshotMap.put("error","IllegalStateException");

                    EventOperationManager.getInstance(context).addEvent(moduleCharacteristics.getScreenshotFailureCharacteristics(), screenshotMap);

                    HashMapPool.releaseMap(screenshotMap);
                }

                return;
            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.i(TAG, "grabScreen() -- " + e.getMessage().toString());

            }


            if (capturedImage == null)
            {




                if (!IsVirtualDisplayNull()){

                    if (screenAwake){
                        TryToKnowIfMediaProjectionDied = TryToKnowIfMediaProjectionDied + 1;

                        if (TryToKnowIfMediaProjectionDied >= 3){
                            mediaProjectionDied.MaybeMediaProjectionDied();

                        }
                    }

                    Log.d(TAG, "grabScreen() -- No image was available");


                    HashMap<String, String> screenshotMap = HashMapPool.getMap();
                    screenshotMap.put("filename","(no image)");
                    screenshotMap.put("screenshot-ordered-time", ScreenShotTakenTime);
                    screenshotMap.put("screenshot-ordered-time-local", ScreenShotTakenTimeLocale);

                    EventOperationManager.getInstance(context).addEvent(moduleCharacteristics.getScreenshotEventCharacteristics(), screenshotMap);

                    HashMapPool.releaseMap(screenshotMap);
                }


                return;
            }

            try{
                Image.Plane[] imagePlanes = capturedImage.getPlanes();
                ByteBuffer buffer = imagePlanes[0].getBuffer();
                pixelStride = imagePlanes[0].getPixelStride();
                rowStride = imagePlanes[0].getRowStride();
                rowPadding = rowStride - pixelStride * screenWidth;
                bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);

                // Close the image cause we don't need it no more.
                capturedImage.close();
            }catch (Exception e){
                e.printStackTrace();
                HashMap<String, String> screenshotMap = HashMapPool.getMap();
                screenshotMap.put("filename","(no image)");
                screenshotMap.put("error","Buffer not large enough for pixels");

                EventOperationManager.getInstance(context).addEvent(moduleCharacteristics.getScreenshotFailureCharacteristics(), screenshotMap);
            }

            // ========== GATE 3: post-capture, pre-persist ==========
            // Black-frame check + risk-tier routing + (Tier-3) OCR escalation, all on the
            // in-memory bitmap before anything touches disk.
            if (bitmap != null && sensitivePolicy != null) {
                // 1. Black-frame check: FLAG_SECURE apps yield blank frames.
                if (isBlankFrame(bitmap)) {
                    Log.i(TAG, "Gate 3 - blank frame (FLAG_SECURE), discarding");
                    logPolicyEvent("flag_secure_blank", fgPackage, "SUPPRESS", "flag_secure_blank");
                    bitmap.recycle();
                    isFallbackScreenshot = false;
                    return;
                }

                // 2. Risk-tier router: decide whether OCR is warranted at all.
                RiskTierRouter.Tier tier = riskTierRouter.route(fgPackage, lastTriggerHadSensitiveSignal);
                boolean ocrEnabled = sensitivePolicy.getRules() != null
                        && sensitivePolicy.getRules().ocrEscalationEnabled;

                if (tier == RiskTierRouter.Tier.TIER_1_SUPPRESS) {
                    logPolicyEvent("bitmap", fgPackage, "SUPPRESS", "tier1_suppress");
                    bitmap.recycle();
                    isFallbackScreenshot = false;
                    return;
                } else if (tier == RiskTierRouter.Tier.TIER_3_OCR && ocrEnabled) {
                    String fallbackMode = sensitivePolicy.getRules().fallbackMode;
                    if ("quarantine".equalsIgnoreCase(fallbackMode)) {
                        // Mode B: persist to quarantine dir for deferred, off-foreground OCR.
                        quarantineBitmap(bitmap, name, fgPackage);
                        bitmap.recycle();
                        isFallbackScreenshot = false;
                        return;
                    } else {
                        // Mode A: inline OCR on this capture thread (background), suppress if sensitive.
                        PolicyVerdict ocrVerdict = ocrClassifier.classify(bitmap, fgPackage);
                        if (!ocrVerdict.isAllow()) {
                            PolicyAuditLogger.log(context, "bitmap", fgPackage, ocrVerdict);
                            Log.i(TAG, "Gate 3 OCR SUPPRESS for " + fgPackage + " (" + ocrVerdict.reason + ")");
                            bitmap.recycle();
                            isFallbackScreenshot = false;
                            return;
                        }
                    }
                }
                // Tier 2 (media, capture-direct) and Tier 3 that passed OCR fall through to persist.
            }

            // Create an output stream for the JPEG and write it to the file.
            try
            {
                fileOutputStream = new FileOutputStream(storeDirName + "/" + name + ".jpg");


                boolean result = false;
                if (bitmap != null){
                    // Determine quality based on screenshot type
                    int quality = isFallbackScreenshot ? FALLBACK_SCREENSHOT_QUALITY : 
                                  SettingsManager.val("force-image-quality");
                    
                    result = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fileOutputStream);
                    
                    Log.d(TAG, "Screenshot compressed with quality: " + quality + 
                          (isFallbackScreenshot ? " (fallback mode)" : " (regular mode)"));
                    
                    // Reset fallback flag after capture
                    isFallbackScreenshot = false;


                    int width=Resources.getSystem().getDisplayMetrics().widthPixels;
                    int height= Resources.getSystem().getDisplayMetrics().heightPixels;

//                Bitmap.createScaledBitmap(bitmap, width,height , true);
                    Bitmap.createScaledBitmap(bitmap, width,height , true);


                    fileOutputStream.close();
                }


                // If writing doesn't work, we may be out of storage space.
                if (!result)
                {
                    TryToKnowIfMediaProjectionDied = 0;

                    Log.e(TAG, "Could not write screenshot to disk.");
                    HashMap<String, String> screenshotMap = HashMapPool.getMap();
                    screenshotMap.put("filename",name);
                    screenshotMap.put("error","WriteFailure");

                    EventOperationManager.getInstance(context).addEvent(moduleCharacteristics.getScreenshotFailureCharacteristics(), screenshotMap);

                    return;
                }
            }
            // Catch errors where the file couldn't be created (this probably won't happen).
            catch (FileNotFoundException e)
            {
                Log.e(TAG, "could not create screenshot file");
                e.printStackTrace();
                HashMap<String, String> screenshotMap = HashMapPool.getMap();
                screenshotMap.put("filename",name);
                screenshotMap.put("error","FileNotFoundException");

                EventOperationManager.getInstance(context).addEvent(moduleCharacteristics.getScreenshotFailureCharacteristics(), screenshotMap);


                HashMapPool.releaseMap(screenshotMap);
//            EventReporter.report(new ScreenshotFailureEvent(name, "FileNotFoundException"));
                return;
            }
            catch (IOException e)
            {
                Log.e(TAG, "error closing screenshot writer");
                e.printStackTrace();
                return;
            }

            // Screenshot success! Report to the EventReporter.
            Log.w(TAG, "Captured image " + name);

            TryToKnowIfMediaProjectionDied = 0;

            HashMap<String, String> screenshotMap = HashMapPool.getMap();
            screenshotMap.put("filename",name);
            screenshotMap.put("screenshot-ordered-time", ScreenShotTakenTime);
            screenshotMap.put("screenshot-ordered-time-local", ScreenShotTakenTimeLocale);

            EventOperationManager.getInstance(context).addEvent(moduleCharacteristics.getScreenshotEventCharacteristics(), screenshotMap);

            HashMapPool.releaseMap(screenshotMap);
//        EventReporter.report(new ScreenshotEvent(name + ".jpg",ScreenShotTakenTime,ScreenShotTakenTimeLocale));

            // ========== INSERT SCREENSHOT INTO DATABASE WITH SESSION LINKAGE ==========
            // Store screenshot record in database linked to current app session
            try {
                String fullFilePath = storeDirName + "/" + name + ".jpg";
                File screenshotFile = new File(fullFilePath);
                long fileSize = screenshotFile.length();
                long captureTimestamp = System.currentTimeMillis();
                
                long screenshotId = dbHelper.insertScreenshot(
                    currentSessionId,  // Link to current app session (may be null if not set)
                    name + ".jpg",
                    fullFilePath,
                    captureTimestamp,
                    fileSize
                );
                
                if (screenshotId > 0) {
                    Log.i(TAG, "Screenshot stored in database: ID=" + screenshotId + 
                        ", Session=" + (currentSessionId != null ? currentSessionId : "null") +
                        ", File=" + name + ".jpg");
                } else {
                    Log.e(TAG, "Failed to store screenshot in database");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error storing screenshot in database", e);
            }

            // Broadcast an intent that this screenshot happened for the AccessibilityService.
            Intent broadcast = new Intent(ACTION_SCREENSHOT);
            broadcast.putExtra("directory", storeDirName + "/");
            broadcast.putExtra("name", name);
            broadcast.putExtra("write-text-contents", true);
            context.sendBroadcast(broadcast);
        }

    }

    /**
     * Resolves the foreground package for the pending capture. Prefers the package supplied by
     * the fallback trigger, then falls back to a fresh UsageStatsManager query (authoritative at
     * capture time, guarding the trigger->capture race).
     */
    private String resolveForegroundPackage() {
        if (currentPackage != null && !currentPackage.isEmpty()) {
            return currentPackage;
        }
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            long now = System.currentTimeMillis();
            java.util.List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, now - 1000 * 10, now);
            if (stats == null || stats.isEmpty()) return null;
            UsageStats recent = null;
            for (UsageStats s : stats) {
                if (recent == null || s.getLastTimeUsed() > recent.getLastTimeUsed()) {
                    recent = s;
                }
            }
            return recent != null ? recent.getPackageName() : null;
        } catch (Exception e) {
            Log.w(TAG, "resolveForegroundPackage failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Sparse black-frame detector: samples a 16x16 grid and reports blank if pixel variance is
     * effectively zero (as produced by FLAG_SECURE surfaces). ~1 ms.
     */
    private boolean isBlankFrame(Bitmap bitmap) {
        try {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            if (w <= 0 || h <= 0) return true;
            int grid = 16;
            int first = bitmap.getPixel(0, 0);
            for (int gy = 0; gy < grid; gy++) {
                for (int gx = 0; gx < grid; gx++) {
                    int x = Math.min(w - 1, gx * w / grid);
                    int y = Math.min(h - 1, gy * h / grid);
                    if (bitmap.getPixel(x, y) != first) {
                        return false; // found variance -> not blank
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false; // never discard on detector error
        }
    }

    /**
     * Mode B: writes a Tier-3 frame to the quarantine subdirectory for deferred OCR classification.
     * Quarantined files are NOT inserted into the DB and NOT broadcast, so the uploader never
     * stages them. {@link #processQuarantine()} classifies them off the foreground later.
     */
    private void quarantineBitmap(Bitmap bitmap, String name, String fgPackage) {
        try {
            File qdir = new File(storeDirName, QUARANTINE_DIR);
            if (!qdir.exists()) qdir.mkdirs();
            File out = new File(qdir, name + "__" + (fgPackage != null ? fgPackage : "unknown") + ".jpg");
            FileOutputStream fos = new FileOutputStream(out);
            bitmap.compress(Bitmap.CompressFormat.JPEG, FALLBACK_SCREENSHOT_QUALITY, fos);
            fos.close();
            logPolicyEvent("quarantine", fgPackage, "QUARANTINE", "deferred_ocr");
            Log.i(TAG, "Gate 3 - quarantined frame for deferred OCR: " + out.getName());
        } catch (Exception e) {
            Log.e(TAG, "quarantineBitmap failed: " + e.getMessage());
        }
    }

    /** Emits a policy audit event directly (used where no PolicyVerdict object is available). */
    private void logPolicyEvent(String gate, String pkg, String decision, String reason) {
        try {
            HashMap<String, String> map = HashMapPool.getMap();
            map.put("gate", gate);
            map.put("decision", decision);
            map.put("reason", reason);
            map.put("package", pkg != null ? pkg : "");
            EventOperationManager.getInstance(context)
                    .addEvent(moduleCharacteristics.getPolicySuppressionCharacteristics(), map);
            HashMapPool.releaseMap(map);
        } catch (Exception e) {
            Log.e(TAG, "logPolicyEvent failed: " + e.getMessage());
        }
    }

    // Throttle for deferred quarantine processing (Mode B).
    private long lastQuarantineSweepMillis = 0;
    private static final long QUARANTINE_SWEEP_INTERVAL_MS = 2 * 60 * 1000; // 2 min

    /**
     * Deferred (Mode B) classification of quarantined Tier-3 frames. For each file: OCR-classify;
     * if the policy flags it, delete it; otherwise promote it into the normal store directory,
     * insert a DB record, and broadcast so the accessibility service extracts its text.
     *
     * <p>Runs on the capture background thread; near-zero cost when the quarantine dir is empty.
     * Throttled to at most once per {@link #QUARANTINE_SWEEP_INTERVAL_MS}.
     */
    private void processQuarantine() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastQuarantineSweepMillis < QUARANTINE_SWEEP_INTERVAL_MS) return;
        lastQuarantineSweepMillis = now;

        try {
            File qdir = new File(storeDirName, QUARANTINE_DIR);
            File[] files = qdir.listFiles();
            if (files == null || files.length == 0) return;

            for (File f : files) {
                Bitmap bmp = null;
                try {
                    String fname = f.getName();
                    String pkg = "unknown";
                    int sep = fname.indexOf("__");
                    if (sep >= 0) {
                        pkg = fname.substring(sep + 2).replace(".jpg", "");
                    }

                    bmp = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
                    if (bmp == null) { f.delete(); continue; }

                    PolicyVerdict verdict = ocrClassifier.classify(bmp, pkg);
                    if (!verdict.isAllow()) {
                        PolicyAuditLogger.log(context, "quarantine", pkg, verdict);
                        f.delete();
                        Log.i(TAG, "Quarantine: suppressed & deleted " + fname);
                    } else {
                        // Promote clean frame into the normal store.
                        String cleanName = (sep >= 0 ? fname.substring(0, sep) : fname.replace(".jpg", ""));
                        File dest = new File(storeDirName, cleanName + ".jpg");
                        if (f.renameTo(dest)) {
                            long fileSize = dest.length();
                            long ts = System.currentTimeMillis();
                            dbHelper.insertScreenshot(currentSessionId, cleanName + ".jpg",
                                    dest.getAbsolutePath(), ts, fileSize);
                            Intent broadcast = new Intent(ACTION_SCREENSHOT);
                            broadcast.putExtra("directory", storeDirName + "/");
                            broadcast.putExtra("name", cleanName);
                            broadcast.putExtra("write-text-contents", true);
                            context.sendBroadcast(broadcast);
                            Log.i(TAG, "Quarantine: promoted clean frame " + cleanName);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "processQuarantine item failed: " + e.getMessage());
                } finally {
                    if (bmp != null) bmp.recycle();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "processQuarantine failed: " + e.getMessage());
        }
    }

    private boolean scheduleGrabScreen()
    {
        // In fallback mode, do NOT reschedule automatic screenshots
        // Screenshots are only triggered on-demand via broadcast
        if (fallbackModeOnly) {
            Log.d(TAG, "Fallback mode: NOT rescheduling screenshot (on-demand only)");
            return true; // Return true so the screenshot proceeds, but don't reschedule
        }
        
        int screenshot_interval = ScreenshotInterval;
        long currentMillis = SystemClock.elapsedRealtime();

        // If the screen is off, don't bother with anything. We'll start again when
        // the screen turns back on.
        if (!screenAwake)
        {
            HashMap<String, String> screenshotMap = HashMapPool.getMap();
            screenshotMap.put("filename","(no image)");
            screenshotMap.put("error","ScreenNotOn");


            EventOperationManager.getInstance(context).addEvent(moduleCharacteristics.getScreenshotFailureCharacteristics(), screenshotMap);

            HashMapPool.releaseMap(screenshotMap);
            return false;
        }

        // Ensure we only have ONE screenshot interval running, and not more. This shouldn't
        // happen, but if it does, at least one of the intervals will be happening within
        // HALF the interval time of the previous one (trust me the math works). Check that.
        // Also make sure we haven't pulled new settings recently, in which case the screenshot
        // interval could have changed.
        if (!wakeFlag
                && currentMillis - lastScreenshotMillis <= screenshot_interval / 2
                && currentMillis - lastSettingsPullMillis > screenshot_interval)
        {

            HashMap<String, String> screenshotMap = HashMapPool.getMap();
            screenshotMap.put("filename","(no image)");
            screenshotMap.put("error","ExtraneousScreenshotInterval");


            EventOperationManager.getInstance(context).addEvent(moduleCharacteristics.getScreenshotFailureCharacteristics(), screenshotMap);

            HashMapPool.releaseMap(screenshotMap);
            Log.e(TAG, "scheduleGrabScreen() -- An extraneous screenshot interval was stopped.");
//            EventReporter.report(new ScreenshotFailureEvent("(no image)", "ExtraneousScreenshotInterval"));
            return false;
        }

        // If we just woke up, take a screenshot now and schedule the next screenshot
        // to align with the desired interval.
        if (wakeFlag)
        {
            wakeFlag = false;

            if (ScreenshotAbsoluteTiming == 1)
            {
                // Round off the current system time to the next system time at which an interval happens.
                long numIntervals = (currentMillis - screenshotStartMillis) / screenshot_interval + 1;
                long nextIntervalMillis = screenshotStartMillis + (numIntervals * screenshot_interval);

                // Schedule the next screenshot for that time.
                long delayMillis = Math.max(0, Math.min(nextIntervalMillis - currentMillis, screenshot_interval));
                handler.postDelayed(screengrabber, delayMillis);

                // Pretend the last screenshot happened at the time of the previous interval.
                lastScreenshotMillis = (currentMillis + delayMillis) - screenshot_interval;

                return true;
            }
            else
            {
                // If we're using a relative interval, we just schedule an interval normally.
                handler.postDelayed(screengrabber, screenshot_interval);

                lastScreenshotMillis = currentMillis;
                return true;
            }
        }

        // This is a regularly scheduled screenshot.
        else
        {
            handler.postDelayed(screengrabber, screenshot_interval);

            lastScreenshotMillis = currentMillis;
            return true;
        }
    }


    private void checkScreengrabbing()
    {

        handler.postDelayed(screenchecker, ScreenshotCheckInterval);

        // We only want to do anything if the screen has been on for awhile.
        if (screenAwake && !wakeFlag)
        {
            // Has it been a long time since the last screenshot?
            if (SystemClock.elapsedRealtime() - lastScreenshotMillis > ScreenshotInterval * 10)
            {
                Log.e(TAG, "checkScreengrabbing() -- starting a new interval");

                HashMap<String, String> screenshotMap = HashMapPool.getMap();
                screenshotMap.put("filename","checkScreengrabbing()");
                screenshotMap.put("error","RestartingCaptureInterval");


                EventOperationManager.getInstance(context).addEvent(moduleCharacteristics.getScreenshotFailureCharacteristics(), screenshotMap);

                HashMapPool.releaseMap(screenshotMap);

//                EventReporter.report(new ScreenshotFailureEvent("checkScreengrabbing()", "RestartingCaptureInterval"));
                handler.postDelayed(screengrabber, ScreenshotInterval);
            }

            else
            {
                Log.d(TAG, "checkScreengrabbing() -- all is well!");
            }
        }
    }

    /**
     * Checks that there's a sufficient amount of space for us to be screenshotting. This will leave
     * a bit of space free so we don't completely fill up the user's phone.
     * @return Whether we can screenshot
     */
    private boolean hasEnoughSpaceForScreenshot()
    {

        // Get the amount of free space.
        long remaining_bytes = new File(storeDirName).getUsableSpace();

        // If this returns 0/-1, the function itself is probably broken, so just let storage fill up.
        if (remaining_bytes <= 0) return true;

        // Keep 50 MB free.
        return remaining_bytes >= 50 * 1024 * 1024;

    }

}
