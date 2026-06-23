package com.openusage.app.apps;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.openusage.app.TextBasedEventData.EventDatabaseHelper;

/**
 * HistoricalUsageCapture - Captures pre-study app usage data from UsageStatsManager.
 *
 * Runs ONCE on first login to capture historical usage data as a baseline
 * for Hawthorne effect analysis (comparing pre-study vs post-study behavior).
 *
 * Queries:
 * - INTERVAL_DAILY: Past 30 days of per-app daily usage
 * - INTERVAL_MONTHLY: Past 12 months of per-app monthly usage
 *
 * Data is stored in the local SQLite historical_usage_snapshot table
 * and synced to Firestore via SessionDataUploaderToFireStore.
 */
public class HistoricalUsageCapture {

    private static final String TAG = "HistoricalUsageCapture";
    private static final String PREFS_NAME = "historical_usage_prefs";
    private static final String KEY_SNAPSHOT_CAPTURED = "snapshot_captured";
    private static final String KEY_SNAPSHOT_TIMESTAMP = "snapshot_timestamp";

    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;
    private static final long TWELVE_MONTHS_MS = 365L * 24 * 60 * 60 * 1000;

    /**
     * Check if the historical snapshot has already been captured.
     */
    public static boolean isSnapshotCaptured(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SNAPSHOT_CAPTURED, false);
    }

    /**
     * Reset the snapshot captured flag so a fresh snapshot can be taken.
     * Used by the PASSIVE study group to re-capture on each toggle-ON.
     */
    public static void resetSnapshotFlag(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_SNAPSHOT_CAPTURED, false)
                .remove(KEY_SNAPSHOT_TIMESTAMP)
                .apply();
        Log.i(TAG, "Snapshot captured flag reset — next capture will proceed");
    }

    /**
     * Capture the historical usage snapshot. Should be called on a background thread.
     *
     * @param context Application context
     * @param userId  The current user's ID
     * @return SnapshotResult with capture statistics
     */
    public static SnapshotResult captureHistoricalSnapshot(Context context, String userId) {
        SnapshotResult result = new SnapshotResult();

        if (isSnapshotCaptured(context)) {
            Log.i(TAG, "Historical snapshot already captured, skipping");
            result.alreadyCaptured = true;
            return result;
        }

        Log.i(TAG, "Starting historical usage snapshot capture for user: " + userId);

        UsageStatsManager usageStatsManager = (UsageStatsManager)
                context.getSystemService(Context.USAGE_STATS_SERVICE);

        if (usageStatsManager == null) {
            Log.e(TAG, "UsageStatsManager is null - cannot capture historical data");
            result.error = "UsageStatsManager unavailable";
            return result;
        }

        EventDatabaseHelper dbHelper = EventDatabaseHelper.getInstance(context);
        long now = System.currentTimeMillis();
        long capturedAt = now;

        // === Capture Daily Stats (past 30 days) ===
        try {
            long dailyStart = now - THIRTY_DAYS_MS;
            List<UsageStats> dailyStats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, dailyStart, now);

            if (dailyStats != null) {
                SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

                for (UsageStats stats : dailyStats) {
                    long foregroundTime = stats.getTotalTimeInForeground();
                    if (foregroundTime <= 0) continue; // Skip unused apps

                    String packageName = stats.getPackageName();
                    long periodStart = stats.getFirstTimeStamp();
                    long periodEnd = stats.getLastTimeStamp();
                    String periodLabel = dayFormat.format(new Date(periodStart));
                    long lastUsed = stats.getLastTimeUsed();

                    long visibleTime = 0;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        visibleTime = stats.getTotalTimeVisible();
                    }

                    long id = dbHelper.insertHistoricalUsage(
                            userId, "daily", periodLabel,
                            periodStart, periodEnd, packageName,
                            foregroundTime, visibleTime, lastUsed);

                    if (id > 0) {
                        result.dailyRecords++;
                    }
                }
                Log.i(TAG, "Captured " + result.dailyRecords + " daily usage records");
            } else {
                Log.w(TAG, "Daily usage stats returned null - permission may not be granted");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error capturing daily usage stats: " + e.getMessage(), e);
            result.error = "Daily capture error: " + e.getMessage();
        }

        // === Capture Monthly Stats (past 12 months) ===
        try {
            long monthlyStart = now - TWELVE_MONTHS_MS;
            List<UsageStats> monthlyStats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_MONTHLY, monthlyStart, now);

            if (monthlyStats != null) {
                SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy-MM", Locale.US);

                for (UsageStats stats : monthlyStats) {
                    long foregroundTime = stats.getTotalTimeInForeground();
                    if (foregroundTime <= 0) continue;

                    String packageName = stats.getPackageName();
                    long periodStart = stats.getFirstTimeStamp();
                    long periodEnd = stats.getLastTimeStamp();
                    String periodLabel = monthFormat.format(new Date(periodStart));
                    long lastUsed = stats.getLastTimeUsed();

                    long visibleTime = 0;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        visibleTime = stats.getTotalTimeVisible();
                    }

                    long id = dbHelper.insertHistoricalUsage(
                            userId, "monthly", periodLabel,
                            periodStart, periodEnd, packageName,
                            foregroundTime, visibleTime, lastUsed);

                    if (id > 0) {
                        result.monthlyRecords++;
                    }
                }
                Log.i(TAG, "Captured " + result.monthlyRecords + " monthly usage records");
            } else {
                Log.w(TAG, "Monthly usage stats returned null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error capturing monthly usage stats: " + e.getMessage(), e);
            if (result.error == null || result.error.isEmpty()) {
                result.error = "Monthly capture error: " + e.getMessage();
            }
        }

        // Mark as captured
        result.totalRecords = result.dailyRecords + result.monthlyRecords;
        if (result.totalRecords > 0) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putBoolean(KEY_SNAPSHOT_CAPTURED, true)
                    .putLong(KEY_SNAPSHOT_TIMESTAMP, capturedAt)
                    .apply();

            Log.i(TAG, "Historical snapshot captured successfully: " +
                    result.dailyRecords + " daily, " + result.monthlyRecords + " monthly records");
        } else {
            Log.w(TAG, "No historical usage data captured - " +
                    "usage stats permission may not be granted or no usage history available");
        }

        return result;
    }

    /**
     * Result object for the snapshot capture operation.
     */
    public static class SnapshotResult {
        public int dailyRecords = 0;
        public int monthlyRecords = 0;
        public int totalRecords = 0;
        public boolean alreadyCaptured = false;
        public String error = "";

        @Override
        public String toString() {
            if (alreadyCaptured) return "SnapshotResult{alreadyCaptured=true}";
            return "SnapshotResult{daily=" + dailyRecords + ", monthly=" + monthlyRecords +
                    ", total=" + totalRecords +
                    (error.isEmpty() ? "" : ", error=" + error) + "}";
        }
    }
}
