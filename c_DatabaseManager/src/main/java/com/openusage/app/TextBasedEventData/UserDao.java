package com.openusage.app.TextBasedEventData;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Data Access Object for user management operations.
 * Handles CRUD for the users table.
 * Extracted from EventDatabaseHelper to follow Single Responsibility Principle.
 */
public class UserDao {

    private static final String TAG = "UserDao";
    private static final String TABLE_USERS = "users";

    private final EventDatabaseHelper dbHelper;

    public UserDao(EventDatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public long insert(String userId, String firebaseUid, String email, String studyCode,
                       String participantNumber, String installCode, String deviceInfo) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("user_id", userId);
            values.put("firebase_uid", firebaseUid);
            values.put("email", email);
            values.put("study_code", studyCode);
            values.put("participant_number", participantNumber);
            values.put("install_code", installCode);
            values.put("created_at", System.currentTimeMillis());
            values.put("device_info", deviceInfo);
            values.put("is_active", 1);
            values.put("consent_given", 1);

            long id = db.insert(TABLE_USERS, null, values);
            Log.d(TAG, "Inserted user: " + userId + " with ID: " + id);
            return id;
        } catch (SQLException e) {
            Log.e(TAG, "Error inserting user: ", e);
            return -1;
        }
    }

    public void updateLastLogin(String userId, long loginTime) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("last_login", loginTime);

            db.update(TABLE_USERS, values, "user_id = ?", new String[]{userId});
            Log.d(TAG, "Updated last login for user: " + userId);
        } catch (SQLException e) {
            Log.e(TAG, "Error updating user last login: ", e);
        }
    }

    public Cursor getById(String userId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_USERS +
                " WHERE user_id = ? LIMIT 1", new String[]{userId});
    }
}
