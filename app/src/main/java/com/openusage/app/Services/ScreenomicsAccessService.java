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
import com.openusage.app.policy.PolicyAuditLogger;
import com.openusage.app.policy.PolicyConfigManager;
import com.openusage.app.policy.PolicyDiagnosticWriter;
import com.openusage.app.policy.PolicyDumpWriter;
import com.openusage.app.policy.PolicyEvaluationTrace;
import com.openusage.app.policy.PolicyVerdict;
import com.openusage.app.policy.SensitiveContentPolicy;

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
    // NOTE: The daily fallback cap (MAX_DAILY_FALLBACK / canTriggerFallbackToday) was a
    // testing-phase artifact and has been removed. Screenshot volume is now bounded by the
    // risk-tier router (Gate 3) rather than a hard daily count.
    
    // Text similarity deduplication settings
    private static final double SIMILARITY_THRESHOLD = 0.90; // 90% similar = skip storage
    private double lastSimilarityScore = 0.0;

    // Sensitive-content policy (Gate 1: text redaction/suppression + fallback veto)
    private SensitiveContentPolicy sensitivePolicy;
    // Policy signals collected during the most recent node traversal (password/resource-ids).
    private SensitiveContentPolicy.EvalContext lastEvalContext;
    // Verdict from the most recent text evaluation; used to veto the fallback screenshot trigger.
    private PolicyVerdict lastPolicyVerdict = PolicyVerdict.allow();

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

            // Initialize sensitive-content policy (Gate 1) and begin listening for remote rules
            PolicyConfigManager policyConfig = PolicyConfigManager.getInstance(this);
            policyConfig.startListening();
            sensitivePolicy = policyConfig.getPolicy();

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

            // Mark the throttle as soon as we commit to walking the tree. This previously lived
            // at the end of the method, inside the storage-success branch, so every early exit
            // (Gate 1 suppress, similarity dedup, too-short text) left it stale - and the 5s
            // rate limit above then compared against an old timestamp and let redundant
            // extractions straight through.
            lastExtractionTime = currentTime;

            // Extract text from the UI hierarchy
            String extractedText = extractTextFromNode(currentRoot);
            
            Log.d(TAG, "Text extraction result: " + 
                  (extractedText != null ? extractedText.length() + " characters" : "null"));

            // ========== GATE 1: SENSITIVE-CONTENT POLICY ==========
            // Evaluate the extraction (plus node-metadata signals) before any storage.
            // SUPPRESS -> drop entirely; REDACT -> replace matched spans in stored text.
            lastPolicyVerdict = PolicyVerdict.allow();
            if (sensitivePolicy != null && extractedText != null) {
                String policyPackage = getCurrentAppPackageName();

                // Debug-only: record raw extraction for the offline replay harness (never uploaded).
                if (com.openusage.app.BuildConfig.DEBUG) {
                    PolicyDumpWriter.append(this, policyPackage, extractedText);
                }

                // Debug-only: attach a diagnostic trace. Passing null (the production path)
                // is byte-for-byte the previous 3-arg call, so live behavior is unchanged.
                PolicyEvaluationTrace diagTrace = null;
                if (com.openusage.app.BuildConfig.DEBUG
                        && PolicyDiagnosticWriter.shouldRecord(policyPackage)) {
                    diagTrace = new PolicyEvaluationTrace();
                }

                PolicyVerdict verdict = sensitivePolicy.evaluateText(
                        extractedText, policyPackage, lastEvalContext, diagTrace);
                lastPolicyVerdict = verdict;

                if (diagTrace != null) {
                    // Never let a diagnostic failure disturb capture behavior.
                    try {
                        PolicyDiagnosticWriter.record(this, PolicyDiagnosticWriter.PASSIVE_FILE,
                                "text", "accessibility", policyPackage, verdict, diagTrace);
                    } catch (Throwable t) {
                        Log.e(TAG, "policy diagnostic record failed: " + t.getMessage());
                    }
                }

                if (verdict.isSuppress()) {
                    PolicyAuditLogger.log(this, "text", policyPackage, verdict);
                    Log.i(TAG, "Gate 1 SUPPRESS - dropping extraction for " + policyPackage
                            + " (" + verdict.reason + ")");
                    lastExtractedText = ""; // don't let suppressed content seed dedup
                    currentRoot.recycle();
                    return;
                } else if (verdict.isRedact()) {
                    PolicyAuditLogger.log(this, "text", policyPackage, verdict);
                    extractedText = SensitiveContentPolicy.applyRedaction(extractedText, verdict.spans);
                    Log.i(TAG, "Gate 1 REDACT - redacted " + verdict.spans.size()
                            + " span(s) for " + policyPackage);
                }
            }

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
     * Extract text from accessibility node hierarchy.
     *
     * <p>While traversing, non-content policy signals (password fields, resource-id names)
     * are collected into {@link #lastEvalContext} at zero extra passes. These let Gate 1
     * catch cases where the visible text is insufficient but node metadata still reveals a
     * sensitive context (e.g. a banking screen rendered as UI components).
     */
    private String extractTextFromNode(AccessibilityNodeInfo node) {
        if (node == null) return null;

        try {
            StringWriter stringWriter = new StringWriter();
            SensitiveContentPolicy.EvalContext ctx = new SensitiveContentPolicy.EvalContext();
            writeNodeComponent(stringWriter, node, ctx);
            lastEvalContext = ctx;
            return stringWriter.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error extracting text from node: " + e.getMessage());
            return null;
        }
    }

    /**
     * Helper method to write node text content (similar to writeWindowComponent but for StringWriter).
     * Also fuses collection of policy signals (password flags, resource-id names) into the same pass.
     */
    private void writeNodeComponent(StringWriter writer, AccessibilityNodeInfo component,
                                    SensitiveContentPolicy.EvalContext ctx) throws IOException {
        if (writer != null && component != null) {
            // Write the text of this component (skip empty nodes to avoid
            // polluting cosine similarity with "(No text)" boilerplate)
            if (!TextUtils.isEmpty(component.getText())) {
                writer.write(component.getText().toString() + "\n\n");
            }
            else if (!TextUtils.isEmpty(component.getContentDescription())) {
                writer.write(component.getContentDescription().toString() + "\n\n");
            }

            // Collect non-content policy signals (fused into the existing traversal).
            if (ctx != null) {
                try {
                    if (component.isPassword()) {
                        ctx.hasPasswordField = true;
                    }
                    String resId = component.getViewIdResourceName();
                    if (!TextUtils.isEmpty(resId)) {
                        // Use only the id portion after ':id/' to reduce noise.
                        int slash = resId.lastIndexOf('/');
                        ctx.addToken(slash >= 0 ? resId.substring(slash + 1) : resId);
                    }
                    CharSequence cd = component.getContentDescription();
                    if (!TextUtils.isEmpty(cd)) {
                        ctx.addToken(cd.toString());
                    }
                } catch (Exception ignored) {
                    // Node metadata is best-effort; never fail extraction over it.
                }
            }

            // Recursively write component's children
            for (int i = 0; i < component.getChildCount(); i++) {
                AccessibilityNodeInfo child = component.getChild(i);
                if (child != null && child != component) {
                    writeNodeComponent(writer, child, ctx);
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
     * Debug-only diagnostic record for the package-level gates, which have no extracted text -
     * only the decision is meaningful. No-op in release and whenever passive capture is off.
     * Fully guarded: a diagnostic failure must never alter capture behavior.
     */
    private void recordGateDiagnostic(String gate, String appPackage, PolicyVerdict verdict) {
        if (!com.openusage.app.BuildConfig.DEBUG || verdict == null) return;
        if (!PolicyDiagnosticWriter.shouldRecord(appPackage)) return;
        try {
            PolicyEvaluationTrace trace = new PolicyEvaluationTrace();
            trace.text = "";
            trace.length = 0;
            trace.total = verdict.score;
            trace.decision = verdict.decision.name();
            trace.reason = verdict.reason;
            trace.categoriesFired.addAll(verdict.categories);
            PolicyDiagnosticWriter.record(this, PolicyDiagnosticWriter.PASSIVE_FILE,
                    gate, "accessibility", appPackage, verdict, trace);
        } catch (Throwable t) {
            Log.e(TAG, "policy gate diagnostic failed: " + t.getMessage());
        }
    }

    /**
     * Trigger fallback screenshot via broadcast
     */
    private void triggerFallbackScreenshot(String reason, String appPackage, String sessionId) {
        // Skip fallback if screenshots are disabled for this participant's group
        if (SettingsManager.val("screenshots-enabled") != 1) {
            return;
        }

        // ========== GATE 1: SENSITIVE-CONTENT VETO ==========
        // If the most recent text evaluation flagged this screen (via content or node
        // metadata such as password fields / resource ids), do NOT capture a fallback
        // screenshot of it. This covers the "banking app rendered as UI components" case
        // where visible text is insufficient but the context is clearly sensitive.
        if (sensitivePolicy != null) {
            if (lastPolicyVerdict != null && !lastPolicyVerdict.isAllow()) {
                PolicyAuditLogger.log(this, "trigger", appPackage, lastPolicyVerdict);
                recordGateDiagnostic("trigger", appPackage, lastPolicyVerdict);
                Log.i(TAG, "Fallback screenshot vetoed by policy (" + lastPolicyVerdict.reason + ")");
                return;
            }
            PolicyVerdict captureVerdict = sensitivePolicy.evaluateCapture(appPackage);
            if (captureVerdict.isSuppress()) {
                PolicyAuditLogger.log(this, "trigger", appPackage, captureVerdict);
                recordGateDiagnostic("capture", appPackage, captureVerdict);
                Log.i(TAG, "Fallback screenshot vetoed - suppressed package " + appPackage);
                return;
            }
        }

        long currentTime = System.currentTimeMillis();

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

        Log.d(TAG, "Fallback screenshot broadcast sent (reason: " + reason + ")");
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
