package com.openusage.app.activites;

import android.content.Context;
import android.util.Log;

import com.openusage.app.TextBasedEventData.EventDatabaseHelper;

import java.util.List;

/**
 * StepHistoryCapture
 *
 * Reads historical step counts from Health Connect (via HealthConnectStepReader) and stores
 * them in the local step_history table for later upload to Firestore. Mirrors the
 * HistoricalUsageCapture pattern used for app-usage baselines.
 *
 * Designed for the PASSIVE study group's manual one-shot capture. Best-effort: if Health
 * Connect is unavailable or no data exists, it stores nothing and reports gracefully.
 *
 * IMPORTANT: call on a background thread (the read methods block).
 */
public class StepHistoryCapture {

    private static final String TAG = "StepHistoryCapture";

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    // Daily totals over the past year; hourly detail over the most recent 30 days
    // (hourly is volume-heavy, so it is bounded to the window most useful for analysis).
    private static final int DAILY_WINDOW_DAYS = 365;
    private static final int HOURLY_WINDOW_DAYS = 30;

    // Reads beyond 30 days require READ_HEALTH_DATA_HISTORY; the recent window never does.
    private static final long RECENT_WINDOW_MS = 30L * DAY_MS;

    public static class Result {
        public int dailyRecords = 0;
        public int hourlyRecords = 0;
        public boolean unavailable = false;
        public boolean providerUpdateRequired = false;

        @Override
        public String toString() {
            return "StepHistoryResult{daily=" + dailyRecords + ", hourly=" + hourlyRecords +
                    ", unavailable=" + unavailable + ", providerUpdateRequired=" + providerUpdateRequired + "}";
        }
    }

    /**
     * Capture daily + hourly step history and persist to the local DB. Best-effort.
     */
    public static Result capture(Context context, String userId) {
        Result result = new Result();

        if (HealthConnectStepReader.isProviderUpdateRequired(context)) {
            result.providerUpdateRequired = true;
        }
        if (!HealthConnectStepReader.isAvailable(context)) {
            result.unavailable = true;
            Log.i(TAG, "Health Connect unavailable — skipping step capture");
            return result;
        }
        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "Empty userId — skipping step capture");
            return result;
        }

        EventDatabaseHelper db = EventDatabaseHelper.getInstance(context);
        long now = System.currentTimeMillis();

        // --- Daily: recent window (always allowed) ---
        long recentStart = now - RECENT_WINDOW_MS;
        result.dailyRecords += storeDaily(db, userId,
                HealthConnectStepReader.readDailySteps(context, recentStart, now));

        // --- Daily: extended window (best-effort; needs history permission) ---
        long extendedStart = now - (long) DAILY_WINDOW_DAYS * DAY_MS;
        if (extendedStart < recentStart) {
            result.dailyRecords += storeDaily(db, userId,
                    HealthConnectStepReader.readDailySteps(context, extendedStart, recentStart));
        }

        // --- Hourly: recent window only ---
        long hourlyStart = now - (long) HOURLY_WINDOW_DAYS * DAY_MS;
        result.hourlyRecords += storeHourly(db, userId,
                HealthConnectStepReader.readHourlySteps(context, hourlyStart, now));

        Log.i(TAG, "Step history capture complete: " + result);
        return result;
    }

    private static int storeDaily(EventDatabaseHelper db, String userId,
                                  List<HealthConnectStepReader.StepBucket> buckets) {
        int count = 0;
        for (HealthConnectStepReader.StepBucket b : buckets) {
            long id = db.insertStepHistory(userId, b.getGranularity(), b.getPeriodLabel(),
                    b.getPeriodStartMs(), b.getPeriodEndMs(), b.getStepCount(), b.getDataOrigin());
            if (id > 0) count++;
        }
        return count;
    }

    private static int storeHourly(EventDatabaseHelper db, String userId,
                                   List<HealthConnectStepReader.StepBucket> buckets) {
        int count = 0;
        for (HealthConnectStepReader.StepBucket b : buckets) {
            long id = db.insertStepHistory(userId, b.getGranularity(), b.getPeriodLabel(),
                    b.getPeriodStartMs(), b.getPeriodEndMs(), b.getStepCount(), b.getDataOrigin());
            if (id > 0) count++;
        }
        return count;
    }
}
