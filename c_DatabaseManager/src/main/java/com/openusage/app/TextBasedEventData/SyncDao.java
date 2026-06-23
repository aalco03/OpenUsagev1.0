package com.openusage.app.TextBasedEventData;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Data Access Object for Firestore sync operations.
 * Handles querying unsynced records and marking them as synced
 * across all session-based tables.
 * Extracted from EventDatabaseHelper to follow Single Responsibility Principle.
 */
public class SyncDao {

    private static final String TAG = "SyncDao";

    private static final String TABLE_USER_SESSIONS = "user_sessions";
    private static final String TABLE_APP_SESSIONS = "app_sessions";
    private static final String TABLE_UI_TEXT_EXTRACTIONS = "ui_text_extractions";
    private static final String TABLE_SCREENSHOTS = "screenshots";

    private final EventDatabaseHelper dbHelper;

    public SyncDao(EventDatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    // ========== GET UNSYNCED RECORDS ==========

    public Cursor getUnsyncedUserSessions(int limit) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_USER_SESSIONS +
                " WHERE is_synced = 0 ORDER BY created_at ASC LIMIT ?", new String[]{String.valueOf(limit)});
    }

    public Cursor getUnsyncedAppSessions(int limit) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_APP_SESSIONS +
                " WHERE is_synced = 0 ORDER BY created_at ASC LIMIT ?", new String[]{String.valueOf(limit)});
    }

    public Cursor getUnsyncedTextExtractions(int limit) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_UI_TEXT_EXTRACTIONS +
                " WHERE is_synced = 0 ORDER BY created_at ASC LIMIT ?", new String[]{String.valueOf(limit)});
    }

    public Cursor getUnsyncedScreenshots(int limit) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_SCREENSHOTS +
                " WHERE is_synced = 0 ORDER BY created_at ASC LIMIT ?", new String[]{String.valueOf(limit)});
    }

    // ========== MARK RECORDS AS SYNCED ==========

    public void markUserSessionSynced(String userSessionId) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("is_synced", 1);
            values.put("updated_at", System.currentTimeMillis());
            db.update(TABLE_USER_SESSIONS, values,
                    "user_session_id = ?", new String[]{userSessionId});
        } catch (SQLException e) {
            Log.e(TAG, "Error marking user session as synced: ", e);
        }
    }

    public void markAppSessionSynced(String sessionId) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("is_synced", 1);
            values.put("updated_at", System.currentTimeMillis());
            db.update(TABLE_APP_SESSIONS, values,
                    "session_id = ?", new String[]{sessionId});
        } catch (SQLException e) {
            Log.e(TAG, "Error marking app session as synced: ", e);
        }
    }

    public void markTextExtractionSynced(long id) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("is_synced", 1);
            db.update(TABLE_UI_TEXT_EXTRACTIONS, values,
                    "id = ?", new String[]{String.valueOf(id)});
        } catch (SQLException e) {
            Log.e(TAG, "Error marking text extraction as synced: ", e);
        }
    }

    public void markScreenshotSynced(long id) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("is_synced", 1);
            db.update(TABLE_SCREENSHOTS, values,
                    "id = ?", new String[]{String.valueOf(id)});
        } catch (SQLException e) {
            Log.e(TAG, "Error marking screenshot as synced: ", e);
        }
    }
}
