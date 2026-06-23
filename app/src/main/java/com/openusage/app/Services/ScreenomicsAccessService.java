package com.openusage.app.Services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import com.openusage.app.DatabaseHelper.LogInPreference;
import com.openusage.app.FirebaseSettings.SettingsManager;
import com.openusage.app.TextBasedEventData.EventDatabaseHelper;
import com.openusage.app.TextBasedEventData.SessionManager;
import com.openusage.app.TextBasedEventData.TextSimilarityCalculator;
import com.openusage.app.TextBasedEventData.UITextExtractionManager;

/**
 * ScreenomicsAccessService - Accessibility service for UI text extraction.
 * Extracts UI text independently of screenshot capture and stores in
 * hierarchical database schema for session-based logging.
 */
public class ScreenomicsAccessService extends AccessibilityService {

    public static final String TAG = "ScrnmicsAccessService";

    private AccessibilityNodeInfo windowRoot;
    private BroadcastReceiver screenshotReceiver;
    
    // Independent text extraction
    private UITextExtractionManager textExtractionManager;
    private SessionManager sessionManager;
    private EventDatabaseHelper dbHelper;
    private Handler textExtractionHandler;
    private Runnable textExtractionRunnable;
    private static final long TEXT_EXTRACTION_INTERVAL = 10000; // 10 seconds
    private String lastExtractedText = "";
    private long lastExtractionTime = 0;
    private static final long MIN_EXTRACTION_INTERVAL = 5000; // Minimum 5 seconds between extractions
    private int extractionCount = 0;
    
    // Session tracking
    private String currentUserSessionId = null;
    private String currentAppPackage = "";
    private String currentAppSessionId = null;
    private long sessionStartTime = 0;
    private boolean newSessionNeedsExtraction = false;
    
    // Fallback screenshot tracking
    private long lastFallbackScreenshotTime = 0;
    private static final float MAX_NO_TEXT_RATIO = 0.4f; // 40%
    private static final int MIN_MEANINGFUL_TEXT_LENGTH = 100; // Fallback screenshot if text below this
    private static final String PREF_FALLBACK_COUNT = "fallback_screenshot_count";
    private static final String PREF_FALLBACK_DATE = "fallback_screenshot_date";
    private static final int MAX_DAILY_FALLBACK = 100;
    
    // Text similarity deduplication settings
    private static final double SIMILARITY_THRESHOLD = 0.90; // 90% similar = skip storage
    private double lastSimilarityScore = 0.0;

    public ScreenomicsAccessService() {
    }

    @Override
    protected void onServiceConnected()
    {
        Log.d(TAG, "accessibility service connected!");

        // Set event types, just in case the config XML didn't suffice.
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        setServiceInfo(info);

        // ========== ORIGINAL: Screenshot-based text extraction ==========
        // Register a receiver for ACTION_SCREENSHOT, sent by CaptureUploadService.
        IntentFilter filter = new IntentFilter(CaptureUploadService.ACTION_SCREENSHOT);
        screenshotReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getBooleanExtra("write-text-contents", false)) {
                    writeWindowContentsToFile(intent.getStringExtra("directory"),
                            intent.getStringExtra("name"));
                }
            }
        };
        // Android 13+ requires explicit export flag for receivers
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenshotReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenshotReceiver, filter);
        }

        // Initialize independent text extraction for session-based logging
        initializeIndependentTextExtraction();
    }

    /**
     * Initialize independent UI text extraction that works without screenshot capture
     */
    private void initializeIndependentTextExtraction() {
        try {
            // Initialize database helper and session manager
            dbHelper = EventDatabaseHelper.getInstance(this);
            sessionManager = new SessionManager(this);
            textExtractionManager = UITextExtractionManager.getInstance(this);
            textExtractionManager.setSessionManager(sessionManager);
            
            // Start user session
            LogInPreference loginPref = new LogInPreference(this);
            String userId = loginPref.GetUserSubjId();
            
            if (!TextUtils.isEmpty(userId)) {
                String deviceInfo = Build.MODEL + " (" + Build.VERSION.RELEASE + ")";
                String appVersion = com.openusage.app.BuildConfig.VERSION_NAME;
                
                currentUserSessionId = dbHelper.startUserSession(userId, deviceInfo, appVersion);
                
                if (currentUserSessionId != null) {
                    // Set user session context in SessionManager
                    sessionManager.setUserSession(currentUserSessionId, userId);
                    Log.i(TAG, "User session started: " + currentUserSessionId);
                } else {
                    Log.e(TAG, "Failed to start user session");
                    return;
                }
            } else {
                Log.e(TAG, "No user ID found - cannot start session");
                return;
            }
            
            // Create handler for periodic text extraction
            textExtractionHandler = new Handler(Looper.getMainLooper());
            
            // Create runnable for periodic text extraction
            textExtractionRunnable = new Runnable() {
                @Override
                public void run() {
                    extractAndStoreUIText();
                    // Schedule next extraction
                    textExtractionHandler.postDelayed(this, TEXT_EXTRACTION_INTERVAL);
                }
            };
            
            // Start periodic text extraction
            textExtractionHandler.post(textExtractionRunnable);
            
            Log.i(TAG, "Independent text extraction initialized (interval: " + (TEXT_EXTRACTION_INTERVAL/1000) + "s)");
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing independent text extraction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // ========== ORIGINAL: Cleanup screenshot receiver ==========
        if (screenshotReceiver != null) {
            unregisterReceiver(screenshotReceiver);
        }
        
        // End current app session
        if (currentAppSessionId != null && sessionManager != null) {
            long currentTime = System.currentTimeMillis();
            long duration = currentTime - sessionStartTime;
            sessionManager.endSession(currentAppSessionId, currentTime, duration);
            Log.d(TAG, "Ended app session on service destroy: " + currentAppSessionId);
        }
        
        // End user session
        if (currentUserSessionId != null && dbHelper != null) {
            dbHelper.endUserSession(currentUserSessionId, System.currentTimeMillis());
            Log.d(TAG, "Ended user session on service destroy: " + currentUserSessionId);
        }
        
        // Cleanup independent text extraction
        if (textExtractionHandler != null && textExtractionRunnable != null) {
            textExtractionHandler.removeCallbacks(textExtractionRunnable);
        }
        
        Log.d(TAG, "Service destroyed - text extraction stopped");
    }

    /**
     * Extract UI text and store in database without requiring screenshot capture.
     */
    private void extractAndStoreUIText() {
        // If user explicitly turned tracking off, go dormant
        if (getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("user_stopped_tracking", false)) {
            return;
        }
        
        extractionCount++;
        
        try {
            long currentTime = System.currentTimeMillis();
            
            // Check if this is a new session that needs immediate extraction
            boolean isNewSessionExtraction = newSessionNeedsExtraction;
            
            // Rate limiting - don't extract too frequently (unless it's a new session)
            if (!isNewSessionExtraction && currentTime - lastExtractionTime < MIN_EXTRACTION_INTERVAL) {
                Log.d(TAG, "Rate limited - skipping extraction (last: " + 
                      (currentTime - lastExtractionTime) + "ms ago)");
                return;
            }
            
            if (isNewSessionExtraction) {
                Log.d(TAG, "New session - forcing immediate extraction");
            }
            
            // Get current window root
            AccessibilityNodeInfo currentRoot = getRootInActiveWindow();
            if (currentRoot == null) {
                Log.w(TAG, "No active window root available for text extraction");
                return;
            }
            
            Log.d(TAG, "Active window found - extracting text...");
            
            // Extract text from the UI hierarchy
            String extractedText = extractTextFromNode(currentRoot);
            
            Log.d(TAG, "Text extraction result: " + 
                  (extractedText != null ? extractedText.length() + " characters" : "null"));
            
            // Similarity-based deduplication: skip storage when content is largely unchanged
            double similarity = 0.0;
            if (lastExtractedText != null && !lastExtractedText.isEmpty() && extractedText != null) {
                similarity = TextSimilarityCalculator.calculateSimilarity(
                    extractedText, lastExtractedText);
                lastSimilarityScore = similarity;
            }
            
            if (extractedText != null && extractedText.trim().length() > 10 && similarity < SIMILARITY_THRESHOLD) {
                
                Log.d(TAG, String.format("Storing text (similarity: %.2f, length: %d)", similarity, extractedText.length()));
                
                // Store in database via UITextExtractionManager
                if (textExtractionManager != null && sessionManager != null) {
                    // Get current app package name
                    String packageName = getCurrentAppPackageName();
                    
                    if (packageName == null || packageName.isEmpty()) {
                        Log.w(TAG, "No app package name available - skipping text extraction");
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
                        
                        if (currentAppSessionId != null) {
                            Log.d(TAG, "Started app session: " + currentAppSessionId + " for " + packageName);
                            newSessionNeedsExtraction = true;
                        } else {
                            Log.e(TAG, "Failed to start app session for " + packageName);
                            return;
                        }
                    }
                    
                    // Store text extraction in database using app-based session linking
                    long extractionId = textExtractionManager.storeTextExtractionForApp(
                        packageName,
                        null, // screenshotFilename - not applicable for independent extraction
                        extractedText,
                        currentTime,
                        null  // windowTitle - could be enhanced later
                    );
                    
                    if (extractionId > 0) {
                        Log.d(TAG, "Text stored (ID: " + extractionId + ", app: " + packageName + ")");
                        
                        // If this was a new session extraction, reset the timer
                        if (isNewSessionExtraction) {
                            
                            // Clear flag
                            newSessionNeedsExtraction = false;
                            
                            // Cancel current scheduled extraction and reset timer
                            if (textExtractionHandler != null && textExtractionRunnable != null) {
                                textExtractionHandler.removeCallbacks(textExtractionRunnable);
                                textExtractionHandler.postDelayed(textExtractionRunnable, TEXT_EXTRACTION_INTERVAL);
                            }
                        }
                    } else {
                        Log.e(TAG, "FAILED: Could not store UI text extraction for app: " + packageName);
                    }
                } else {
                    Log.e(TAG, "ERROR: TextExtractionManager or SessionManager is null!");
                }
                
                lastExtractedText = extractedText;
                lastExtractionTime = currentTime;
                
            } else if (extractedText != null && similarity >= SIMILARITY_THRESHOLD) {
                Log.d(TAG, String.format("Text highly similar to previous (%.2f >= %.2f) - skipping duplicate storage",
                      similarity, SIMILARITY_THRESHOLD));
            } else if (extractedText != null && extractedText.trim().length() <= 10) {
                Log.d(TAG, "Text too short (" + extractedText.trim().length() + " chars) - skipping");
            } else {
                Log.w(TAG, "No valid text extracted");
            }
            
            // ========== FALLBACK SCREENSHOT LOGIC ==========
            // Check if text quality is insufficient and trigger fallback screenshot
            if (extractedText != null && isTextQualityInsufficient(extractedText)) {
                String packageName = getCurrentAppPackageName();
                if (packageName != null && currentAppSessionId != null) {
                    triggerFallbackScreenshot(
                        "insufficient_text_quality",
                        packageName,
                        currentAppSessionId
                    );
                } else {
                    Log.w(TAG, "Cannot trigger fallback - missing package name or session ID");
                }
            }
            
            // Cleanup
            currentRoot.recycle();
            
        } catch (Exception e) {
            Log.e(TAG, "ERROR during text extraction: " + e.getMessage());
            e.printStackTrace();
        }
        
    }

    /**
     * Extract text from accessibility node hierarchy
     */
    private String extractTextFromNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        
        try {
            StringWriter stringWriter = new StringWriter();
            writeNodeComponent(stringWriter, node);
            return stringWriter.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error extracting text from node: " + e.getMessage());
            return null;
        }
    }

    /**
     * Helper method to write node text content (similar to writeWindowComponent but for StringWriter)
     */
    private void writeNodeComponent(StringWriter writer, AccessibilityNodeInfo component) throws IOException {
        if (writer != null && component != null) {
            // Write the text of this component (skip empty nodes to avoid
            // polluting cosine similarity with "(No text)" boilerplate)
            if (!TextUtils.isEmpty(component.getText())) {
                writer.write(component.getText().toString() + "\n\n");
            }
            else if (!TextUtils.isEmpty(component.getContentDescription())) {
                writer.write(component.getContentDescription().toString() + "\n\n");
            }

            // Recursively write component's children
            for (int i = 0; i < component.getChildCount(); i++) {
                AccessibilityNodeInfo child = component.getChild(i);
                if (child != null && child != component) {
                    writeNodeComponent(writer, child);
                    child.recycle(); // Important: recycle child nodes
                }
            }
        }
    }

    // Own package name - used to filter out self from foreground detection
    private static final String OWN_PACKAGE = "com.openusage.app";
    
    /**
     * Get current foreground app package name using UsageStatsManager
     * This is more reliable than using accessibility node package names
     * 
     * Filters applied:
     * 1. Does NOT use getLastTimeForegroundServiceUsed() — our own foreground service
     *    would always appear "most recent", stealing sessions from the actual foreground app
     * 2. Filters out own package (com.openusage.app) to prevent self-attribution
     * 3. Filters out launcher packages to prevent spurious sessions during app transitions
     */
    private String getCurrentAppPackageName() {
        try {
            UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usageStatsManager == null) {
                Log.w(TAG, "UsageStatsManager not available");
                return null;
            }
            
            long currentTime = System.currentTimeMillis();
            
            // Use INTERVAL_BEST with a 1-minute window for better accuracy
            List<UsageStats> appList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                currentTime - 60000,  // Last 1 minute
                currentTime
            );
            
            if (appList != null && !appList.isEmpty()) {
                // Find the app with the most recent usage, filtering out own package and launchers
                UsageStats mostRecent = null;
                long mostRecentTime = 0;
                
                for (UsageStats usageStats : appList) {
                    String pkg = usageStats.getPackageName();
                    
                    // Filter out own package — our foreground service makes us always appear recent
                    if (OWN_PACKAGE.equals(pkg)) {
                        continue;
                    }
                    
                    // Use only getLastTimeUsed() — NOT getLastTimeForegroundServiceUsed()
                    // ForegroundAppCapture.getForegroundApp() proves this approach works correctly
                    long lastUsed = usageStats.getLastTimeUsed();
                    
                    if (lastUsed > mostRecentTime) {
                        mostRecentTime = lastUsed;
                        mostRecent = usageStats;
                    }
                }
                
                if (mostRecent != null) {
                    String packageName = mostRecent.getPackageName();
                    long timeSinceUse = currentTime - mostRecentTime;
                    
                    // Filter out transient launcher packages during app transitions
                    // When switching apps, the launcher briefly appears as foreground;
                    // keep the previous app's session active instead of creating a spurious one
                    if (isLauncherPackage(packageName) && currentAppPackage != null && !currentAppPackage.isEmpty()) {
                        Log.d(TAG, "Launcher detected (" + packageName + ") during transition - " +
                              "keeping previous app: " + currentAppPackage);
                        return currentAppPackage;
                    }
                    
                    Log.d(TAG, "Detected foreground app: " + packageName + 
                          " (last used: " + timeSinceUse + "ms ago)");
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
     * Check if a package is a known launcher/home screen app.
     * These create spurious sessions during app-to-app transitions.
     */
    private boolean isLauncherPackage(String packageName) {
        if (packageName == null) return false;
        // Common launcher packages across OEMs
        if (packageName.equals("com.google.android.apps.nexuslauncher") ||
            packageName.equals("com.android.launcher3") ||
            packageName.equals("com.sec.android.app.launcher") ||       // Samsung
            packageName.equals("com.huawei.android.launcher") ||        // Huawei
            packageName.equals("com.miui.home") ||                      // Xiaomi
            packageName.equals("com.oneplus.launcher") ||               // OnePlus
            packageName.equals("com.oppo.launcher") ||                  // Oppo
            packageName.equals("com.android.launcher")) {               // Generic AOSP
            return true;
        }
        // Catch-all for unknown launchers
        return packageName.toLowerCase().contains("launcher");
    }

    @SuppressLint("SwitchIntDef")
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event)
    {
        switch (event.getEventType())
        {
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                onWindowContentEvent(event);
        }
    }

    private void onWindowContentEvent(AccessibilityEvent event)
    {
        windowRoot = event.getSource();
        // windowRoot can be null for system dialogs and secure apps - this is normal
        if (windowRoot == null) {
            Log.d(TAG, "Window root is null - skipping event (normal for system dialogs)");
        }
    }

    /**
     * Creates a text file that sits alongside a screenshot, containing the current contents of
     * the window when that screenshot was taken. This should be called the moment the screenshot
     * is generated for accurate results.
     * @param screenshotDir The directory containing screenshots.
     * @param screenshotName The name of the relevant screenshot (NOT including .jpg)
     */
    public void writeWindowContentsToFile(String screenshotDir, String screenshotName)
    {
        // Create a TXT file.
        File windowfile = new File(screenshotDir, screenshotName + "_WND.txt");
        FileWriter writer;

        try {
            // Create a writer for the file.
            writer = new FileWriter(windowfile);

            // Refresh the window root, and make sure the window still exists.
            if (windowRoot == null || !windowRoot.refresh()) {
                writer.write("(WINDOW INVALIDATED SINCE LAST CONTENT EVENT)");
            }

            // If it does, recursively write the view tree.
            else {
                writeWindowComponent(writer, windowRoot);
            }

            writer.close();
        }
        catch (IOException e) {
            // Handle file writing error with detailed logging
            Log.e(TAG, "IOException writing text data for " + windowfile.getName() + ": " + e.getMessage());
            e.printStackTrace();
            
            // Attempt to delete corrupted file
            if (windowfile.exists()) {
                boolean deleted = windowfile.delete();
                Log.w(TAG, "Attempted to delete corrupted file: " + (deleted ? "success" : "failed"));
            }
            return;
        }

        Log.i(TAG, "Wrote window text data to " + windowfile.getName());
    }

    /**
     * Analyze text quality to determine if fallback screenshot needed
     * @param extractedText The extracted text to analyze
     * @return true if text quality is insufficient (needs fallback screenshot)
     */
    private boolean isTextQualityInsufficient(String extractedText) {
        if (extractedText == null || extractedText.trim().isEmpty()) {
            return true; // No text at all
        }
        
        // Since empty nodes are now skipped during extraction, quality is
        // determined solely by whether enough meaningful text was captured.
        int meaningfulChars = extractedText.trim().length();
        
        return meaningfulChars < MIN_MEANINGFUL_TEXT_LENGTH;
    }
    
    /**
     * Check if we can trigger fallback screenshot today (under daily limit)
     */
    private boolean canTriggerFallbackToday() {
        android.content.SharedPreferences prefs = getSharedPreferences("screenomics_fallback", MODE_PRIVATE);
        
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(new java.util.Date());
        
        String lastDate = prefs.getString(PREF_FALLBACK_DATE, "");
        int count = prefs.getInt(PREF_FALLBACK_COUNT, 0);
        
        if (!today.equals(lastDate)) {
            prefs.edit()
                .putString(PREF_FALLBACK_DATE, today)
                .putInt(PREF_FALLBACK_COUNT, 0)
                .apply();
            return true;
        }
        
        return count < MAX_DAILY_FALLBACK;
    }
    
    /**
     * Trigger fallback screenshot via broadcast
     */
    private void triggerFallbackScreenshot(String reason, String appPackage, String sessionId) {
        // Skip fallback if screenshots are disabled for this participant's group
        if (SettingsManager.val("screenshots-enabled") != 1) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // Check daily limit
        if (!canTriggerFallbackToday()) {
            Log.w(TAG, "Fallback screenshot skipped - daily limit reached");
            return;
        }
        
        // Sync with text extraction interval (5 seconds)
        if (currentTime - lastFallbackScreenshotTime < MIN_EXTRACTION_INTERVAL) {
            Log.d(TAG, "Fallback screenshot rate limited (last: " + 
                  (currentTime - lastFallbackScreenshotTime) + "ms ago)");
            return;
        }
        
        Log.d(TAG, "Triggering fallback screenshot (reason: " + reason + ", app: " + appPackage + ")");
        
        // Send broadcast to CaptureUploadService
        // CRITICAL: Must use explicit broadcast (setPackage) for Android 8+
        // Implicit broadcasts are restricted and won't reach dynamically registered receivers
        Intent intent = new Intent("edu.stanford.communication.screenomics.CAPTURE_SCREENSHOT");
        intent.setPackage("com.openusage.app");  // Make broadcast explicit
        intent.putExtra("trigger_source", "text_extraction");
        intent.putExtra("fallback_reason", reason);
        intent.putExtra("app_package", appPackage);
        intent.putExtra("session_id", sessionId);
        intent.putExtra("timestamp", currentTime);
        
        sendBroadcast(intent);
        
        // Update tracking
        lastFallbackScreenshotTime = currentTime;
        
        // Increment daily counter
        android.content.SharedPreferences prefs = getSharedPreferences("screenomics_fallback", MODE_PRIVATE);
        int count = prefs.getInt(PREF_FALLBACK_COUNT, 0);
        prefs.edit().putInt(PREF_FALLBACK_COUNT, count + 1).apply();
        
        Log.d(TAG, "Fallback screenshot broadcast sent (daily count: " + (count + 1) + ")");
    }

    /**
     * Recursive helper method that writes a component of the window to the file.
     * @param writer The FileWriter to write to
     * @param component Current top-level component
     */
    private void writeWindowComponent(FileWriter writer, AccessibilityNodeInfo component) throws IOException
    {
        if (writer != null && component != null)
        {
            // Write the text of this component to the file.
            if (!TextUtils.isEmpty(component.getText())) {
                writer.write(component.getText().toString() + "\n\n");
            }
            else if (!TextUtils.isEmpty(component.getContentDescription())) {
                writer.write(component.getContentDescription().toString() + "\n\n");
            }
            else {
                writer.write("(No text)\n\n");
            }

            // Recursively write component's children. The != check is a safeguard in case a node
            // has itself as a child, which someone on StackOverflow reported can actually happen.
            for (int i = 0; i < component.getChildCount(); i++) {
                if (component.getChild(i) != component) {
                    writeWindowComponent(writer, component.getChild(i));
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "onInterrupt()");
    }
}
