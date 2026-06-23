package com.openusage.app.TextBasedEventData;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

/**
 * UITextExtractionManager - Manages UI text extractions with session awareness
 * 
 * This class handles the storage and management of UI text extractions,
 * linking them to active app sessions for better behavioral analysis.
 * It provides content validation and session-aware storage.
 */
public class UITextExtractionManager {
    private static final String TAG = "UITextExtractionManager";
    
    private static volatile UITextExtractionManager instance;
    private final Context context;
    private final EventDatabaseHelper dbHelper;
    private SessionManager sessionManager;
    
    private UITextExtractionManager(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = EventDatabaseHelper.getInstance(context);
    }
    
    public static UITextExtractionManager getInstance(Context context) {
        if (instance == null) {
            synchronized (UITextExtractionManager.class) {
                if (instance == null) {
                    instance = new UITextExtractionManager(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * Set the session manager for session-aware operations
     */
    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    
    /**
     * Store text extraction with session linking
     */
    public long storeTextExtraction(String sessionId, String screenshotFilename, 
                                   String extractedText, long timestamp) {
        return storeTextExtraction(sessionId, screenshotFilename, extractedText, timestamp, null);
    }
    
    /**
     * Store text extraction with full metadata
     */
    public long storeTextExtraction(String sessionId, String screenshotFilename, 
                                   String extractedText, long timestamp, String windowTitle) {
        try {
            // Validate and process the extracted text
            boolean hasValidContent = determineValidContent(extractedText);
            
            // Store in session-aware database
            long id = dbHelper.insertUITextExtraction(sessionId, screenshotFilename, 
                    extractedText, timestamp, hasValidContent, windowTitle);
            
            if (id > 0) {
                Log.d(TAG, "Stored text extraction (ID: " + id + ") for session: " + sessionId + 
                        " (valid: " + hasValidContent + ", length: " + extractedText.length() + ")");
            } else {
                Log.e(TAG, "Failed to store text extraction for session: " + sessionId);
            }
            
            return id;
        } catch (Exception e) {
            Log.e(TAG, "Error storing text extraction", e);
            return -1;
        }
    }
    
    /**
     * Store text extraction using current active session for an app
     * All text extractions MUST be linked to a session
     */
    public long storeTextExtractionForApp(String appPackageName, String screenshotFilename, 
                                         String extractedText, long timestamp, String windowTitle) {
        // Validate package name
        if (appPackageName == null || appPackageName.isEmpty()) {
            Log.e(TAG, "Cannot store text extraction: app package name is null or empty");
            return -1;
        }
        
        // Validate session manager
        if (sessionManager == null) {
            Log.e(TAG, "Cannot store text extraction: SessionManager not set");
            return -1;
        }
        
        // Get active session for the app
        String sessionId = sessionManager.getActiveSessionId(appPackageName);
        if (sessionId == null) {
            Log.e(TAG, "Cannot store text extraction: No active session for app: " + appPackageName);
            return -1;
        }
        
        // Store with active session link
        return storeTextExtraction(sessionId, screenshotFilename, extractedText, timestamp, windowTitle);
    }
    
    
    /**
     * Determine if extracted text contains meaningful content
     * 
     * This method filters out common non-meaningful text patterns
     * to help identify when UI text extraction provides useful data.
     */
    private boolean determineValidContent(String extractedText) {
        if (TextUtils.isEmpty(extractedText)) {
            return false;
        }
        
        String cleanText = extractedText.trim().toLowerCase();
        
        // Check for common non-meaningful patterns
        String[] invalidPatterns = {
            "(no text)",
            "loading...",
            "loading",
            "please wait",
            "...",
            "null",
            "undefined",
            "error",
            "failed to load",
            "retry",
            "try again"
        };
        
        // If text is too short, likely not meaningful
        if (cleanText.length() < 3) {
            return false;
        }
        
        // Check if text consists only of invalid patterns
        for (String pattern : invalidPatterns) {
            if (cleanText.equals(pattern) || cleanText.startsWith(pattern)) {
                return false;
            }
        }
        
        // Check if text is mostly whitespace or special characters
        String alphanumericOnly = cleanText.replaceAll("[^a-zA-Z0-9]", "");
        if (alphanumericOnly.length() < cleanText.length() * 0.3) {
            return false; // Less than 30% alphanumeric content
        }
        
        // If we get here, consider it valid content
        return true;
    }
    
    /**
     * Get content validation statistics
     */
    public ContentValidationStats getValidationStats(String sessionId) {
        // This would require additional database queries to implement
        // For now, return a placeholder
        return new ContentValidationStats(0, 0, 0.0);
    }
    
    /**
     * Helper class for content validation statistics
     */
    public static class ContentValidationStats {
        public final int totalExtractions;
        public final int validExtractions;
        public final double validPercentage;
        
        public ContentValidationStats(int totalExtractions, int validExtractions, double validPercentage) {
            this.totalExtractions = totalExtractions;
            this.validExtractions = validExtractions;
            this.validPercentage = validPercentage;
        }
        
        @Override
        public String toString() {
            return "ContentValidationStats{" +
                    "totalExtractions=" + totalExtractions +
                    ", validExtractions=" + validExtractions +
                    ", validPercentage=" + validPercentage +
                    '}';
        }
    }
}
