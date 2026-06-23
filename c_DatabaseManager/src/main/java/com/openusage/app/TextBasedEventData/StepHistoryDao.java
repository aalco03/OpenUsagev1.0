package com.openusage.app.TextBasedEventData;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Data Access Object for Health Connect step-history records.
 * Mirrors HistoricalUsageDao. Stores daily and hourly step-count buckets read from
 * Health Connect for the PASSIVE study group.
 */
public class StepHistoryDao {

    private static final String TAG = "StepHistoryDao";
    private static final String TABLE_STEP_HISTORY = "step_history";

    private final EventDatabaseHelper dbHelper;

    public StepHistoryDao(EventDatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    /**
     * Insert a step-history bucket. Uses INSERT OR REPLACE so re-syncing an overlapping
     * range updates rather than duplicates (UNIQUE on user_id, granularity, period_label).
     */
    public long insert(String userId, String granularity, String periodLabel,
                       long periodStartMs, long periodEndMs, long stepCount, String dataOrigin) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("user_id", userId);
            values.put("granularity", granularity);
            values.put("period_label", periodLabel);
            values.put("period_start_ms", periodStartMs);
            values.put("period_end_ms", periodEndMs);
            values.put("step_count", stepCount);
            values.put("data_origin", dataOrigin);
            values.put("captured_at", System.currentTimeMillis());
            values.put("is_synced", 0);

            return db.insertWithOnConflict(TABLE_STEP_HISTORY, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLException e) {
            Log.e(TAG, "Error inserting step history: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Get unsynced step-history records for Firestore upload.
     */
    public Cursor getUnsynced(int limit) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_STEP_HISTORY +
                " WHERE is_synced = 0 ORDER BY captured_at ASC LIMIT ?", new String[]{String.valueOf(limit)});
    }

    /**
     * Mark a step-history record as synced.
     */
    public void markSynced(long id) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("is_synced", 1);
            db.update(TABLE_STEP_HISTORY, values,
                    "id = ?", new String[]{String.valueOf(id)});
        } catch (SQLException e) {
            Log.e(TAG, "Error marking step history as synced: " + e.getMessage());
        }
    }

    /**
     * Get the count of step-history records for a user.
     */
    public int getCount(String userId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_STEP_HISTORY +
                " WHERE user_id = ?", new String[]{userId});
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }
}
