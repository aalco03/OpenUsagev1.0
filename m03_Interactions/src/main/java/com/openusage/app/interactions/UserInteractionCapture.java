package com.openusage.app.interactions;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import java.io.File;
import java.util.HashMap;

import com.openusage.app.DatabaseHelper.LogInPreference;
import com.openusage.app.TextBasedEventData.EventDatabaseHelper;
import com.openusage.app.TextBasedEventData.EventOperationManager;
import com.openusage.app.TextBasedEventData.GdpbDashboardSyncService;
import com.openusage.app.TextBasedEventData.HashMapPool;
import com.openusage.app.TextBasedEventData.SessionManager;
import com.openusage.app.TextBasedEventData.UITextExtractionManager;
import com.openusage.app.modulemanager.ModuleCharacteristics;
import com.openusage.app.modulemanager.ModuleController;

/**
 * ========== TESTING ENVIRONMENT: ENHANCED FOR INDEPENDENT TEXT EXTRACTION ==========
 * Modified to extract UI text independently of screenshot capture
 * Stores text in hierarchical database schema for session-based logging
 */
public class UserInteractionCapture extends AccessibilityService {

    private static final String TAG = "UserInteractionCapture";

    /**
     * May 7, 2025
     */

    int ScrolledX = 0;
    int ScrolledY = 0;

    // ========== SESSION-BASED TEXT EXTRACTION ==========
    private SessionManager sessionManager;
    private UITextExtractionManager textExtractionManager;
    private EventDatabaseHelper dbHelper;
    private GdpbDashboardSyncService dashboardSyncService;
    private String currentUserSessionId = null;
    private String currentAppPackage = "";
    private String currentAppSessionId = null;
    private long sessionStartTime = 0;
    
    // Text extraction rate limiting
    private long lastTextExtractionTime = 0;
    private static final long TEXT_EXTRACTION_MIN_INTERVAL = 5000; // 5 seconds
    
    // Text deduplication - track last extracted text to avoid duplicates
    private String lastExtractedText = "";
    
    // Text quality threshold for screenshot fallback (tweakable based on empirical testing)
    private static final int MIN_TEXT_LENGTH_THRESHOLD = 50; // Minimum chars for "sufficient" text
    private boolean screenshotFallbackEnabled = true;
    
    // ========== DATA PIPELINE ==========
    private Handler pipelineHandler;
    private Runnable pipelineRunnable;
    private static final long DATA_EXPORT_INTERVAL = 300000; // 5 minutes for production
    private int exportCount = 0;
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {



        if (CheckIsUserLogin()) {

            if (ModuleController.ENABLE_INTERACTIONS){

                HashMap<String, String> userInput = HashMapPool.getMap();


                if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {

                    if (event.getScrollX() > ScrolledX) {

                        userInput.put("activity","scroll-right");

                        EventOperationManager.getInstance(this).addEvent(ModuleCharacteristics.getInstance().getInteractionEventCharacteristics(), userInput);


                        HashMapPool.releaseMap(userInput);

//                        logEventsInFirebase.LogEvent(LogEventsInFirebase.GetSimpleClassName("AccessibilityEvent"), LogEventsInFirebase.GetDefaultMapWithAdditionalValue("accessibility", "activity", ), getApplicationContext());
                        ScrolledX = event.getScrollX();
                    } else if (event.getScrollX() < ScrolledX) {
//                        logEventsInFirebase.LogEvent(LogEventsInFirebase.GetSimpleClassName("AccessibilityEvent"), LogEventsInFirebase.GetDefaultMapWithAdditionalValue("accessibility", "activity", "scroll-left"), getApplicationContext());


                        userInput.put("activity","scroll-left");


                        EventOperationManager.getInstance(this).addEvent(ModuleCharacteristics.getInstance().getInteractionEventCharacteristics(), userInput);

                        HashMapPool.releaseMap(userInput);


                        ScrolledX = event.getScrollX();

                    }

                    if (event.getScrollY() > ScrolledY) {
//                        logEventsInFirebase.LogEvent(LogEventsInFirebase.GetSimpleClassName("AccessibilityEvent"), LogEventsInFirebase.GetDefaultMapWithAdditionalValue("accessibility", "activity", "scroll-up"), getApplicationContext());

                        userInput.put("activity","scroll-up");


                        EventOperationManager.getInstance(this).addEvent(ModuleCharacteristics.getInstance().getInteractionEventCharacteristics(), userInput);

                        HashMapPool.releaseMap(userInput);

                        ScrolledY = event.getScrollY();

                    } else if (event.getScrollY() < ScrolledY) {
//                        logEventsInFirebase.LogEvent(LogEventsInFirebase.GetSimpleClassName("AccessibilityEvent"), LogEventsInFirebase.GetDefaultMapWithAdditionalValue("accessibility", "activity", "scroll-down"), getApplicationContext());

                        userInput.put("activity","scroll-down");


                        EventOperationManager.getInstance(this).addEvent(ModuleCharacteristics.getInstance().getInteractionEventCharacteristics(), userInput);

                        HashMapPool.releaseMap(userInput);

                        ScrolledY = event.getScrollY();

                    }


                } else if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {

//                logEventsInFirebase.LogEvent(LogEventsInFirebase.GetSimpleClassName("AccessibilityEvent"), LogEventsInFirebase.GetDefaultMapWithAdditionalValue("accessibility", "activity", "clicked"), getApplicationContext());

                    userInput.put("activity","clicked");


                    EventOperationManager.getInstance(this).addEvent(ModuleCharacteristics.getInstance().getInteractionEventCharacteristics(), userInput);

                    HashMapPool.releaseMap(userInput);

                }else if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {

//                logEventsInFirebase.LogEvent(LogEventsInFirebase.GetSimpleClassName("AccessibilityEvent"), LogEventsInFirebase.GetDefaultMapWithAdditionalValue("accessibility", "activity", "long-clicked"), getApplicationContext());
                    userInput.put("activity","long-clicked");


                    EventOperationManager.getInstance(this).addEvent(ModuleCharacteristics.getInstance().getInteractionEventCharacteristics(), userInput);
                    HashMapPool.releaseMap(userInput);


                }
                else if (event.getEventType() == AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START) {
//                logEventsInFirebase.LogEvent(LogEventsInFirebase.GetSimpleClassName("AccessibilityEvent"), LogEventsInFirebase.GetDefaultMapWithAdditionalValue("accessibility", "activity", "touch-exploration-start"), getApplicationContext());
                    userInput.put("activity","touch-exploration-start");


                    EventOperationManager.getInstance(this).addEvent(ModuleCharacteristics.getInstance().getInteractionEventCharacteristics(), userInput);

                    HashMapPool.releaseMap(userInput);

                }
                else if (event.getEventType() == AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END) {
//                logEventsInFirebase.LogEvent(LogEventsInFirebase.GetSimpleClassName("AccessibilityEvent"), LogEventsInFirebase.GetDefaultMapWithAdditionalValue("accessibility", "activity", "touch-exploration-end"), getApplicationContext());
                    userInput.put("activity","touch-exploration-end");


                    EventOperationManager.getInstance(this).addEvent(ModuleCharacteristics.getInstance().getInteractionEventCharacteristics(), userInput);
                    HashMapPool.releaseMap(userInput);

                }
            }

            // ========== SESSION-BASED TEXT EXTRACTION ==========
            // Extract text on significant UI events with rate limiting
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                long currentTime = System.currentTimeMillis();
                
                // Only extract if enough time has passed since last extraction
                if (currentTime - lastTextExtractionTime >= TEXT_EXTRACTION_MIN_INTERVAL) {
                    extractAndStoreUIText();
                    lastTextExtractionTime = currentTime;
                }
            }
        }

    }


    @Override
    public void onInterrupt() {

    }



    @Override
    protected void onServiceConnected() {

        super.onServiceConnected();
        
        Log.d(TAG, "UserInteractionCapture service connected!");

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();

        info.eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED | AccessibilityEvent.TYPE_VIEW_CLICKED | AccessibilityEvent.TYPE_VIEW_LONG_CLICKED | AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START | AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;

        info.notificationTimeout = 100;

        this.setServiceInfo(info);

        // ========== SESSION-BASED DATA PIPELINE ==========
        // Initialize session management and data export pipeline
        initializeDataPipeline();
    }


    /**
     * ========== SESSION-BASED DATA PIPELINE INITIALIZATION ==========
     * Initialize session management and data export pipeline
     */
    private void initializeDataPipeline() {
        try {
            // Initialize session manager and database helper
            sessionManager = new SessionManager(this);
            textExtractionManager = UITextExtractionManager.getInstance(this);
            dbHelper = EventDatabaseHelper.getInstance(this);
            
            // Initialize dashboard sync service
            dashboardSyncService = new GdpbDashboardSyncService(this);
            Log.i(TAG, "Dashboard sync service initialized");
            
            // Start user session
            LogInPreference loginPref = new LogInPreference(this);
            String userId = loginPref.GetUserSubjId();
            
            if (!TextUtils.isEmpty(userId)) {
                String deviceInfo = Build.MODEL + " (" + Build.VERSION.RELEASE + ")";
                String appVersion;
                try {
                    appVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                } catch (Exception e) {
                    appVersion = "unknown";
                }
                
                currentUserSessionId = dbHelper.startUserSession(userId, deviceInfo, appVersion);
                
                if (currentUserSessionId != null) {
                    // Set user session context in SessionManager
                    sessionManager.setUserSession(currentUserSessionId, userId);
                    Log.i(TAG, "User session started: " + currentUserSessionId);
                    sessionStartTime = System.currentTimeMillis();
                } else {
                    Log.e(TAG, "Failed to start user session");
                    return;
                }
            } else {
                Log.e(TAG, "No user ID found - cannot start session");
                return;
            }
            
            // Data export pipeline ready for dashboard integration
            
            // Create handler for periodic data export
            pipelineHandler = new Handler(Looper.getMainLooper());
            
            // Create runnable for periodic data export
            pipelineRunnable = new Runnable() {
                @Override
                public void run() {
                    exportDataPeriodically();
                    // Schedule next export
                    pipelineHandler.postDelayed(this, DATA_EXPORT_INTERVAL);
                }
            };
            
            // Start periodic data export (delayed start to allow some data collection first)
            pipelineHandler.postDelayed(pipelineRunnable, DATA_EXPORT_INTERVAL);
            
            Log.i(TAG, "SESSION-BASED DATA PIPELINE INITIALIZED");
            Log.i(TAG, "📤 Export interval: " + DATA_EXPORT_INTERVAL + "ms (" + (DATA_EXPORT_INTERVAL/1000/60) + " minutes)");
            Log.i(TAG, "👤 User ID: " + userId);
            Log.i(TAG, "User Session ID: " + currentUserSessionId);
            Log.i(TAG, "Storage format: Database → CSV export");
            
            // Dashboard integration will be configured here
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing data pipeline: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ========== SESSION-BASED TEXT EXTRACTION ==========
     * Extract UI text and store in database with proper session management
     */
    private void extractAndStoreUIText() {
        if (currentUserSessionId == null) {
            Log.w(TAG, "No active user session - cannot extract text");
            return;
        }
        
        try {
            long currentTime = System.currentTimeMillis();
            
            // Get current window root
            AccessibilityNodeInfo currentRoot = getRootInActiveWindow();
            if (currentRoot == null) {
                Log.w(TAG, "No active window root available for text extraction");
                return;
            }
            
            // Get current app package name
            String packageName = getCurrentAppPackageName();
            if (TextUtils.isEmpty(packageName)) {
                Log.w(TAG, "No app package name available");
                return;
            }
            
            // Check if we need to start a new app session
            if (!packageName.equals(currentAppPackage) || currentAppSessionId == null) {
                // End previous app session if exists
                if (currentAppSessionId != null) {
                    long duration = currentTime - sessionStartTime;
                    sessionManager.endSession(currentAppSessionId, currentTime, duration);
                    Log.d(TAG, "Ended app session: " + currentAppSessionId);
                }
                
                // Start new app session
                String sessionId = "app_session_" + currentTime + "_" + packageName.hashCode();
                currentAppSessionId = sessionManager.startSession(sessionId, packageName, currentTime);
                sessionStartTime = currentTime;
                currentAppPackage = packageName;
                
                // Reset last extracted text when switching apps
                lastExtractedText = "";
                
                if (currentAppSessionId != null) {
                    Log.i(TAG, "Started app session: " + currentAppSessionId + " for " + packageName);
                } else {
                    Log.e(TAG, "Failed to start app session for " + packageName);
                    return;
                }
            }
            
            // Extract text from the UI hierarchy
            String extractedText = extractTextFromNode(currentRoot);
            
            // Evaluate text quality and determine if screenshot fallback is needed
            boolean textIsSufficient = evaluateTextQuality(extractedText);
            
            if (extractedText != null && extractedText.trim().length() > 10) {
                Log.d(TAG, "NEW TEXT CONTENT DETECTED - storing in database...");
                
                // Store text extraction in database
                long extractionId = textExtractionManager.storeTextExtraction(
                    currentAppSessionId, null, extractedText, currentTime);
                boolean success = extractionId > 0;
                
                if (success) {
                    Log.i(TAG, "Text extraction stored in database");
                    Log.d(TAG, "Length: " + extractedText.length() + " chars, App: " + packageName);
                    Log.d(TAG, "Session: " + currentAppSessionId);
                    
                    // Update last extracted text
                    lastExtractedText = extractedText;
                } else {
                    Log.e(TAG, "Failed to store text extraction");
                }
            } else if (extractedText == null || extractedText.trim().length() <= 10) {
                Log.d(TAG, "No significant text content found");
            }
            
            // SCREENSHOT FALLBACK LOGIC
            // Trigger screenshot capture if text is insufficient or unavailable
            if (screenshotFallbackEnabled && !textIsSufficient) {
                String reason = (extractedText == null || extractedText.trim().isEmpty()) 
                    ? "no_text" 
                    : (extractedText.trim().length() < MIN_TEXT_LENGTH_THRESHOLD 
                        ? "insufficient_text" 
                        : "low_quality_text");
                
                Log.i(TAG, "SCREENSHOT FALLBACK triggered: " + reason + 
                    " (text length: " + (extractedText != null ? extractedText.length() : 0) + " chars)");
                
                triggerScreenshotFallback(packageName, reason);
            } else if (textIsSufficient) {
                Log.d(TAG, "Text quality sufficient - screenshot fallback skipped");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during text extraction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Evaluate if extracted text meets quality threshold
     * Simple quantitative check - easily tweakable based on empirical testing
     * 
     * @param extractedText The text that was extracted
     * @return true if text is sufficient quality, false if screenshot fallback needed
     */
    private boolean evaluateTextQuality(String extractedText) {
        if (extractedText == null || extractedText.trim().isEmpty()) {
            return false; // No text at all
        }
        
        String trimmed = extractedText.trim();
        int textLength = trimmed.length();
        
        // Simple quantitative check: must meet minimum character threshold
        if (textLength < MIN_TEXT_LENGTH_THRESHOLD) {
            Log.d(TAG, "Text quality check: INSUFFICIENT (" + textLength + " < " + MIN_TEXT_LENGTH_THRESHOLD + " chars)");
            return false;
        }
        
        Log.d(TAG, "Text quality check: SUFFICIENT (" + textLength + " chars)");
        return true;
    }
    
    /**
     * Trigger screenshot capture as fallback when text extraction is insufficient
     * 
     * @param appPackage Current app package name
     * @param reason Reason for fallback (for logging/debugging)
     */
    private void triggerScreenshotFallback(String appPackage, String reason) {
        try {
            // Send broadcast to trigger screenshot capture
            // The screenshot service should be listening for this intent
            Intent screenshotIntent = new Intent("edu.stanford.communication.screenomics.CAPTURE_SCREENSHOT");
            screenshotIntent.putExtra("trigger_source", "text_extraction_fallback");
            screenshotIntent.putExtra("fallback_reason", reason);
            screenshotIntent.putExtra("app_package", appPackage);
            screenshotIntent.putExtra("session_id", currentAppSessionId);
            screenshotIntent.putExtra("timestamp", System.currentTimeMillis());
            
            sendBroadcast(screenshotIntent);
            
            Log.d(TAG, "📤 Screenshot fallback broadcast sent: reason=" + reason);
            
        } catch (Exception e) {
            Log.e(TAG, "Error triggering screenshot fallback: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ========== SESSION-BASED DATA EXPORT ==========
     * Export data periodically for dashboard integration
     */
    private void exportDataPeriodically() {
        if (currentUserSessionId == null) {
            Log.w(TAG, "No active user session - skipping export");
            return;
        }
        
        exportCount++;
        Log.i(TAG, "=== SESSION-BASED DATA EXPORT #" + exportCount + " ===");
        
        try {
            // Create session-based data exporter
            SessionBasedDataExporter exporter = new SessionBasedDataExporter(this);
            
            // Export data for current user session
            File csvFile = exporter.exportUserSessionData(currentUserSessionId);
            
            if (csvFile != null && csvFile.exists()) {
                Log.i(TAG, "CSV export successful: " + csvFile.getName());
                Log.i(TAG, "File size: " + csvFile.length() + " bytes");
                Log.i(TAG, "📁 CSV saved locally");
            } else {
                Log.w(TAG, "No data to export or export failed");
            }
            
            // Sync unsynced text extractions to dashboard
            syncTextExtractionsToDashboard();
            
        } catch (Exception e) {
            Log.e(TAG, "Error during data export: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Sync unsynced text extractions to GDP-B Dashboard
     */
    private void syncTextExtractionsToDashboard() {
        if (dashboardSyncService == null) {
            Log.w(TAG, "Dashboard sync service not initialized");
            return;
        }
        
        try {
            Log.i(TAG, "Starting dashboard sync...");
            dashboardSyncService.syncUnsyncedData(new GdpbDashboardSyncService.SyncCallback() {
                @Override
                public void onSyncComplete(GdpbDashboardSyncService.SyncResult result) {
                    if (result.success) {
                        Log.i(TAG, "Dashboard sync successful!");
                        Log.i(TAG, "📤 Synced " + result.syncedTexts + " text extractions");
                        Log.i(TAG, "📤 Synced " + result.syncedSessions + " app sessions");
                    } else {
                        Log.e(TAG, "Dashboard sync failed: " + result.errorMessage);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error starting dashboard sync: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get current foreground app package name using UsageStatsManager
     * This is more reliable than using accessibility node package names
     */
    private String getCurrentAppPackageName() {
        try {
            UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usageStatsManager == null) {
                Log.w(TAG, "UsageStatsManager not available");
                return null;
            }
            
            long currentTime = System.currentTimeMillis();
            // Query usage stats for the last 10 seconds to get current foreground app
            List<UsageStats> appList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, 
                currentTime - 10000, 
                currentTime
            );
            
            if (appList != null && !appList.isEmpty()) {
                // Sort by last time used to get the most recent app
                SortedMap<Long, UsageStats> sortedMap = new TreeMap<>();
                for (UsageStats usageStats : appList) {
                    sortedMap.put(usageStats.getLastTimeUsed(), usageStats);
                }
                
                if (!sortedMap.isEmpty()) {
                    String packageName = sortedMap.get(sortedMap.lastKey()).getPackageName();
                    Log.d(TAG, "Detected foreground app: " + packageName);
                    return packageName;
                }
            }
            
            Log.w(TAG, "No foreground app found in UsageStats");
        } catch (Exception e) {
            Log.e(TAG, "Error getting foreground app package name: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }

    /**
     * Extract text content from accessibility node hierarchy
     */
    private String extractTextFromNode(AccessibilityNodeInfo node) {
        if (node == null) return "";
        
        StringBuilder textBuilder = new StringBuilder();
        
        try {
            // Get text from current node
            CharSequence nodeText = node.getText();
            if (nodeText != null && nodeText.length() > 0) {
                textBuilder.append(nodeText.toString().trim()).append(" ");
            }
            
            // Get content description
            CharSequence contentDesc = node.getContentDescription();
            if (contentDesc != null && contentDesc.length() > 0) {
                textBuilder.append(contentDesc.toString().trim()).append(" ");
            }
            
            // Recursively extract from child nodes
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    String childText = extractTextFromNode(child);
                    if (!TextUtils.isEmpty(childText)) {
                        textBuilder.append(childText);
                    }
                    child.recycle();
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error extracting text from node: " + e.getMessage());
        }
        
        return textBuilder.toString().trim();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // End current app session if exists
        if (currentAppSessionId != null && sessionManager != null) {
            long currentTime = System.currentTimeMillis();
            long duration = currentTime - sessionStartTime;
            sessionManager.endSession(currentAppSessionId, currentTime, duration);
            Log.d(TAG, "Ended app session on service destroy: " + currentAppSessionId);
        }
        
        // End user session if exists
        if (currentUserSessionId != null && dbHelper != null) {
            dbHelper.endUserSession(currentUserSessionId, System.currentTimeMillis());
            Log.d(TAG, "Ended user session on service destroy: " + currentUserSessionId);
        }
        
        // Clean up handlers
        if (pipelineHandler != null && pipelineRunnable != null) {
            pipelineHandler.removeCallbacks(pipelineRunnable);
        }
        
        Log.i(TAG, "UserInteractionCapture service destroyed");
    }

    boolean CheckIsUserLogin(){
        LogInPreference sharedPref = new LogInPreference(getApplicationContext());
        String subject_id = sharedPref.GetUserSubjId().replace(".","").replace("@","").replace("_","");

        if (!subject_id.equals("")) {
            return true;
        }else{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                disableSelf();
            }else {
                stopSelf();
            }
            return false;
        }

    }

}
