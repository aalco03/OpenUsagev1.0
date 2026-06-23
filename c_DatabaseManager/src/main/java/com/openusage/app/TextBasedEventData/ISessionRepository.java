package com.openusage.app.TextBasedEventData;

import android.database.Cursor;

/**
 * Interface for session data operations.
 * Abstracts the database layer to enable unit testing with mock implementations.
 * Combines UserDao and SessionDao contracts into a single repository interface.
 */
public interface ISessionRepository {

    // ========== USER OPERATIONS ==========

    long insertUser(String userId, String firebaseUid, String email, String studyCode,
                    String participantNumber, String installCode, String deviceInfo);

    void updateUserLastLogin(String userId, long loginTime);

    Cursor getUser(String userId);

    // ========== USER SESSION OPERATIONS ==========

    String startUserSession(String userId, String deviceInfo, String appVersion);

    void endUserSession(String userSessionId, long logoutTime);

    Cursor getActiveUserSession(String userId);

    Cursor getUserSessions(String userId);

    // ========== APP SESSION OPERATIONS ==========

    long insertAppSession(String sessionId, String userSessionId, String userId,
                          String appPackageName, String appName, long startTime,
                          String deviceOrientation, Integer batteryLevel, String networkType);

    void endAppSession(String sessionId, long endTime, long duration);

    Cursor getActiveSession(String appPackageName);

    Cursor getAllSessions();

    Cursor getAppSessionsForUserSession(String userSessionId);

    Cursor getAppSessionsForUser(String userId);

    // ========== TEXT EXTRACTION OPERATIONS ==========

    long insertUITextExtraction(String sessionId, String screenshotFilename,
                                String extractedText, long timestamp,
                                boolean hasValidContent, String windowTitle);

    Cursor getUITextForSession(String sessionId);

    // ========== SCREENSHOT OPERATIONS ==========

    long insertScreenshot(String sessionId, String filename, String filePath,
                          long timestamp, long fileSize);

    // ========== SYNC OPERATIONS ==========

    Cursor getUnsyncedUserSessions(int limit);
    Cursor getUnsyncedAppSessions(int limit);
    Cursor getUnsyncedTextExtractions(int limit);
    Cursor getUnsyncedScreenshots(int limit);

    void markUserSessionSynced(String userSessionId);
    void markAppSessionSynced(String sessionId);
    void markTextExtractionSynced(long id);
    void markScreenshotSynced(long id);
}
