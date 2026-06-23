package com.openusage.app.TextBasedEventData;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Data Access Object for user session and app session operations.
 * Handles CRUD for user_sessions and app_sessions tables.
 * Extracted from EventDatabaseHelper to follow Single Responsibility Principle.
 */
public class SessionDao {

    private static final String TAG = "SessionDao";
    private static final String TABLE_USER_SESSIONS = "user_sessions";
    private static final String TABLE_APP_SESSIONS = "app_sessions";

    private final EventDatabaseHelper dbHelper;
    private final UserDao userDao;
    private final Context context;

    public SessionDao(EventDatabaseHelper dbHelper, UserDao userDao, Context context) {
        this.dbHelper = dbHelper;
        this.userDao = userDao;
        this.context = context;
    }

    // ========== USER SESSION METHODS ==========

    public String startUserSession(String userId, String deviceInfo, String appVersion) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            String userSessionId = "user_session_" + System.currentTimeMillis() + "_" + userId.hashCode();
            long loginTime = System.currentTimeMillis();

            ContentValues values = new ContentValues();
            values.put("user_session_id", userSessionId);
            values.put("user_id", userId);
            values.put("login_time", loginTime);
            values.put("device_info", deviceInfo);
            values.put("app_version", appVersion);
            values.put("is_active", 1);

            long id = db.insert(TABLE_USER_SESSIONS, null, values);

            if (id > 0) {
                userDao.updateLastLogin(userId, loginTime);
                Log.d(TAG, "Started user session: " + userSessionId + " for user: " + userId);
                return userSessionId;
            } else {
                Log.e(TAG, "Failed to start user session for user: " + userId);
                return null;
            }
        } catch (SQLException e) {
            Log.e(TAG, "Error starting user session: ", e);
            return null;
        }
    }

    public void endUserSession(String userSessionId, long logoutTime) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            Cursor cursor = db.rawQuery("SELECT login_time FROM " + TABLE_USER_SESSIONS +
                    " WHERE user_session_id = ?", new String[]{userSessionId});

            if (cursor.moveToFirst()) {
                long loginTime = cursor.getLong(0);
                long duration = logoutTime - loginTime;

                ContentValues values = new ContentValues();
                values.put("logout_time", logoutTime);
                values.put("session_duration", duration);
                values.put("is_active", 0);

                db.update(TABLE_USER_SESSIONS, values,
                        "user_session_id = ?", new String[]{userSessionId});
                Log.d(TAG, "Ended user session: " + userSessionId + " (duration: " + duration + "ms)");
            }
            cursor.close();
        } catch (SQLException e) {
            Log.e(TAG, "Error ending user session: ", e);
        }
    }

    public Cursor getActiveUserSession(String userId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_USER_SESSIONS +
                " WHERE user_id = ? AND is_active = 1 LIMIT 1", new String[]{userId});
    }

    public Cursor getUserSessions(String userId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_USER_SESSIONS +
                " WHERE user_id = ? ORDER BY login_time DESC", new String[]{userId});
    }

    // ========== APP SESSION METHODS ==========

    public long insertAppSession(String sessionId, String userSessionId, String userId,
                                 String appPackageName, String appName, long startTime,
                                 String deviceOrientation, Integer batteryLevel, String networkType) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("session_id", sessionId);
            values.put("user_session_id", userSessionId);
            values.put("user_id", userId);
            values.put("app_package_name", appPackageName);
            values.put("app_name", appName);
            values.put("start_time", startTime);
            values.put("device_orientation", deviceOrientation);
            values.put("battery_level", batteryLevel);
            values.put("network_type", networkType);
            values.put("is_active", 1);

            long id = db.insert(TABLE_APP_SESSIONS, null, values);
            Log.d(TAG, "Inserted app session: " + sessionId + " for app: " + appPackageName +
                    " (user: " + userId + ", user_session: " + userSessionId + ")");
            return id;
        } catch (SQLException e) {
            Log.e(TAG, "Error inserting app session: ", e);
            return -1;
        }
    }

    /**
     * Backward compatibility overload — resolves user context automatically.
     */
    public long insertAppSession(String sessionId, String appPackageName, String appName,
                                 long startTime, String deviceOrientation, Integer batteryLevel, String networkType) {
        Log.w(TAG, "Using deprecated insertAppSession method - consider updating to include user context");

        try {
            com.openusage.app.DatabaseHelper.LogInPreference loginPref =
                    new com.openusage.app.DatabaseHelper.LogInPreference(context);
            String userId = loginPref.GetUserSubjId();

            if (userId == null || userId.isEmpty()) {
                Log.w(TAG, "No user logged in - using default user for app session");
                userId = "default_user";
            }

            String userSessionId = getOrCreateDefaultUserSession(userId);

            return insertAppSession(sessionId, userSessionId, userId, appPackageName, appName, startTime,
                    deviceOrientation, batteryLevel, networkType);
        } catch (Exception e) {
            Log.e(TAG, "Error in backward compatibility method: " + e.getMessage());
            Log.w(TAG, "Skipping app session creation due to schema constraints");
            return -1;
        }
    }

    private String getOrCreateDefaultUserSession(String userId) {
        try {
            Cursor cursor = getActiveUserSession(userId);
            if (cursor != null && cursor.moveToFirst()) {
                String userSessionId = cursor.getString(cursor.getColumnIndexOrThrow("user_session_id"));
                cursor.close();
                return userSessionId;
            }
            if (cursor != null) cursor.close();

            String userSessionId = startUserSession(userId, "Android Device", "Unknown");
            if (userSessionId != null) {
                return userSessionId;
            }

            return "default_session_" + System.currentTimeMillis();
        } catch (Exception e) {
            Log.e(TAG, "Error getting/creating user session: " + e.getMessage());
            return "default_session_" + System.currentTimeMillis();
        }
    }

    public void endAppSession(String sessionId, long endTime, long duration) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("end_time", endTime);
            values.put("duration", duration);
            values.put("is_active", 0);

            db.update(TABLE_APP_SESSIONS, values, "session_id = ?", new String[]{sessionId});
            Log.d(TAG, "Ended app session: " + sessionId + " (duration: " + duration + "ms)");
        } catch (SQLException e) {
            Log.e(TAG, "Error ending app session: ", e);
        }
    }

    public Cursor getActiveSession(String appPackageName) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_APP_SESSIONS +
                " WHERE app_package_name = ? AND is_active = 1 LIMIT 1",
                new String[]{appPackageName});
    }

    public Cursor getAllSessions() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_APP_SESSIONS +
                " ORDER BY start_time DESC", null);
    }

    public Cursor getAppSessionsForUserSession(String userSessionId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_APP_SESSIONS +
                " WHERE user_session_id = ? ORDER BY start_time ASC",
                new String[]{userSessionId});
    }

    public Cursor getAppSessionsForUser(String userId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_APP_SESSIONS +
                " WHERE user_id = ? ORDER BY start_time DESC",
                new String[]{userId});
    }
}
