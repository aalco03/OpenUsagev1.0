package com.openusage.app.TextBasedEventData;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SessionManager - Handles app session lifecycle and management
 * 
 * This class provides a high-level interface for managing app usage sessions
 * in the Screenomics application. It works with the EventDatabaseHelper to
 * store session data in the new session-based schema.
 * 
 * Key Features:
 * - Session creation and termination
 * - Active session tracking
 * - Session metadata management
 * - Integration with existing event system
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    
    private final Context context;
    private final EventDatabaseHelper dbHelper;
    
    // Track active sessions in memory for quick access
    private final ConcurrentHashMap<String, ActiveSessionInfo> activeSessions;
    
    // Track current user session
    private String currentUserSessionId = null;
    private String currentUserId = null;
    
    public SessionManager(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = EventDatabaseHelper.getInstance(context);
        this.activeSessions = new ConcurrentHashMap<>();
    }
    
    /**
     * Set the current user session context for app sessions
     */
    public void setUserSession(String userSessionId, String userId) {
        this.currentUserSessionId = userSessionId;
        this.currentUserId = userId;
        Log.d(TAG, "Set user session context: " + userSessionId + " for user: " + userId);
    }
    
    /**
     * Start a new app session
     */
    public String startSession(String sessionId, String appPackageName, long startTime) {
        if (currentUserSessionId == null || currentUserId == null) {
            Log.e(TAG, "Cannot start app session: user session not set. Call setUserSession() first.");
            return null;
        }
        return startSession(sessionId, appPackageName, null, startTime, "portrait", null, null);
    }
    
    /**
     * Start a new app session with full metadata
     */
    public String startSession(String sessionId, String appPackageName, String appName, 
                              long startTime, String deviceOrientation, Integer batteryLevel, String networkType) {
        try {
            // Validate user session context
            if (currentUserSessionId == null || currentUserId == null) {
                Log.e(TAG, "Cannot start app session: user session not set. Call setUserSession() first.");
                return null;
            }
            
            // Insert session into database with user context
            long dbId = dbHelper.insertAppSession(sessionId, currentUserSessionId, currentUserId,
                    appPackageName, appName, startTime, deviceOrientation, batteryLevel, networkType);
            
            if (dbId > 0) {
                // Track in memory for quick access
                ActiveSessionInfo sessionInfo = new ActiveSessionInfo(
                        sessionId, appPackageName, appName, startTime, dbId);
                activeSessions.put(appPackageName, sessionInfo);
                
                Log.i(TAG, "Started session: " + sessionId + " for app: " + appPackageName + 
                        " (user: " + currentUserId + ", user_session: " + currentUserSessionId + ")");
                return sessionId;
            } else {
                Log.e(TAG, "Failed to insert session into database");
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting session", e);
            return null;
        }
    }
    
    /**
     * End an app session
     */
    public void endSession(String sessionId, long endTime, long duration) {
        try {
            // Update database
            dbHelper.endAppSession(sessionId, endTime, duration);
            
            // Remove from active sessions
            String appPackage = findAppPackageBySessionId(sessionId);
            if (appPackage != null) {
                activeSessions.remove(appPackage);
                Log.i(TAG, "Ended session: " + sessionId + " (duration: " + duration + "ms)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error ending session", e);
        }
    }
    
    /**
     * Get current active session for an app
     */
    public String getActiveSessionId(String appPackageName) {
        ActiveSessionInfo sessionInfo = activeSessions.get(appPackageName);
        return sessionInfo != null ? sessionInfo.sessionId : null;
    }
    
    /**
     * Check if an app has an active session
     */
    public boolean hasActiveSession(String appPackageName) {
        return activeSessions.containsKey(appPackageName);
    }
    
    /**
     * Get all active sessions
     */
    public ConcurrentHashMap<String, ActiveSessionInfo> getActiveSessions() {
        return new ConcurrentHashMap<>(activeSessions);
    }
    
    /**
     * End all active sessions (e.g., when app is closing)
     */
    public void endAllActiveSessions() {
        long currentTime = System.currentTimeMillis();
        
        for (ActiveSessionInfo sessionInfo : activeSessions.values()) {
            long duration = currentTime - sessionInfo.startTime;
            endSession(sessionInfo.sessionId, currentTime, duration);
        }
        
        activeSessions.clear();
        Log.i(TAG, "Ended all active sessions");
    }
    
    /**
     * Store UI text extraction linked to current session
     */
    public void storeUITextExtraction(String appPackageName, String screenshotFilename, 
                                     String extractedText, long timestamp, boolean hasValidContent, String windowTitle) {
        String sessionId = getActiveSessionId(appPackageName);
        if (sessionId != null) {
            dbHelper.insertUITextExtraction(sessionId, screenshotFilename, extractedText, 
                    timestamp, hasValidContent, windowTitle);
            Log.d(TAG, "Stored UI text extraction for session: " + sessionId);
        } else {
            Log.w(TAG, "No active session for app: " + appPackageName + " - text extraction not linked");
        }
    }
    
    /**
     * Store screenshot linked to current session
     */
    public void storeScreenshot(String appPackageName, String filename, String filePath, 
                               long timestamp, long fileSize) {
        String sessionId = getActiveSessionId(appPackageName);
        if (sessionId != null) {
            dbHelper.insertScreenshot(sessionId, filename, filePath, timestamp, fileSize);
            Log.d(TAG, "Stored screenshot for session: " + sessionId);
        } else {
            Log.w(TAG, "No active session for app: " + appPackageName + " - screenshot not linked");
        }
    }
    
    /**
     * Store session event
     */
    public void storeSessionEvent(String appPackageName, String eventType, String eventData, 
                                 long timestamp, String eventName) {
        String sessionId = getActiveSessionId(appPackageName);
        if (sessionId != null) {
            dbHelper.insertSessionEvent(sessionId, eventType, eventData, timestamp, eventName);
            Log.d(TAG, "Stored session event: " + eventType + " for session: " + sessionId);
        } else {
            // Store event without session link (backward compatibility)
            Log.w(TAG, "No active session for app: " + appPackageName + " - storing event without session link");
        }
    }
    
    /**
     * Get session analytics data
     */
    public Cursor getSessionAnalytics() {
        return dbHelper.getAllSessions();
    }
    
    /**
     * Get UI text extractions for a specific session
     */
    public Cursor getUITextForSession(String sessionId) {
        return dbHelper.getUITextForSession(sessionId);
    }
    
    // Helper method to find app package by session ID
    private String findAppPackageBySessionId(String sessionId) {
        for (ConcurrentHashMap.Entry<String, ActiveSessionInfo> entry : activeSessions.entrySet()) {
            if (entry.getValue().sessionId.equals(sessionId)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Inner class to track active session information
     */
    public static class ActiveSessionInfo {
        public final String sessionId;
        public final String appPackageName;
        public final String appName;
        public final long startTime;
        public final long dbId;
        
        public ActiveSessionInfo(String sessionId, String appPackageName, String appName, 
                               long startTime, long dbId) {
            this.sessionId = sessionId;
            this.appPackageName = appPackageName;
            this.appName = appName;
            this.startTime = startTime;
            this.dbId = dbId;
        }
        
        @Override
        public String toString() {
            return "ActiveSessionInfo{" +
                    "sessionId='" + sessionId + '\'' +
                    ", appPackageName='" + appPackageName + '\'' +
                    ", appName='" + appName + '\'' +
                    ", startTime=" + startTime +
                    ", dbId=" + dbId +
                    '}';
        }
    }
}
