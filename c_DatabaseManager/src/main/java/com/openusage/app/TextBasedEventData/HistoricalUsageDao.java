package com.openusage.app.TextBasedEventData;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Data Access Object for historical usage snapshot records.
 * Handles CRUD operations for the historical_usage_snapshot table.
 * Extracted from EventDatabaseHelper to follow Single Responsibility Principle.
 */
public class HistoricalUsageDao {

    private static final String TAG = "HistoricalUsageDao";
    private static final String TABLE_HISTORICAL_USAGE = "historical_usage_snapshot";

    private final EventDatabaseHelper dbHelper;

    public HistoricalUsageDao(EventDatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    /**
     * Insert a historical usage record from UsageStatsManager.
     * Uses INSERT OR REPLACE to handle the UNIQUE constraint.
     */
    public long insert(String userId, String intervalType, String periodLabel,
                       long periodStartMs, long periodEndMs, String appPackage,
                       long foregroundTimeMs, long visibleTimeMs, long lastUsedMs) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("user_id", userId);
            values.put("interval_type", intervalType);
            values.put("period_label", periodLabel);
            values.put("period_start_ms", periodStartMs);
            values.put("period_end_ms", periodEndMs);
            values.put("app_package", appPackage);
            values.put("foreground_time_ms", foregroundTimeMs);
            values.put("visible_time_ms", visibleTimeMs);
            values.put("last_used_ms", lastUsedMs);
            values.put("captured_at", System.currentTimeMillis());
            values.put("is_synced", 0);

            return db.insertWithOnConflict(TABLE_HISTORICAL_USAGE, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLException e) {
            Log.e(TAG, "Error inserting historical usage: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Get unsynced historical usage records for Firestore upload.
     */
    public Cursor getUnsynced(int limit) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_HISTORICAL_USAGE +
                " WHERE is_synced = 0 ORDER BY captured_at ASC LIMIT ?", new String[]{String.valueOf(limit)});
    }

    /**
     * Mark a historical usage record as synced.
     */
    public void markSynced(long id) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("is_synced", 1);
            db.update(TABLE_HISTORICAL_USAGE, values,
                    "id = ?", new String[]{String.valueOf(id)});
        } catch (SQLException e) {
            Log.e(TAG, "Error marking historical usage as synced: " + e.getMessage());
        }
    }

    /**
     * Get the count of historical usage records for a user.
     */
    public int getCount(String userId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_HISTORICAL_USAGE +
                " WHERE user_id = ?", new String[]{userId});
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }
}
