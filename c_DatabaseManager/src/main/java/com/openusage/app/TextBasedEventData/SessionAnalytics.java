package com.openusage.app.TextBasedEventData;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SessionAnalytics - Provides analytics and reporting for app usage sessions
 * 
 * This class offers various analytical queries and reports based on the
 * session-based data structure. It enables behavioral analysis and usage
 * pattern identification.
 */
public class SessionAnalytics {
    private static final String TAG = "SessionAnalytics";
    
    private final Context context;
    private final EventDatabaseHelper dbHelper;
    
    public SessionAnalytics(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = EventDatabaseHelper.getInstance(context);
    }
    
    /**
     * Get app usage summary statistics
     */
    public AppUsageSummary getAppUsageSummary(String appPackageName, long startTime, long endTime) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT " +
                "COUNT(*) as session_count, " +
                "AVG(duration) as avg_duration, " +
                "SUM(duration) as total_duration, " +
                "MIN(duration) as min_duration, " +
                "MAX(duration) as max_duration " +
                "FROM app_sessions " +
                "WHERE app_package_name = ? AND start_time >= ? AND start_time <= ? AND is_active = 0";
        
        Cursor cursor = db.rawQuery(query, new String[]{appPackageName, String.valueOf(startTime), String.valueOf(endTime)});
        
        AppUsageSummary summary = new AppUsageSummary();
        if (cursor.moveToFirst()) {
            summary.sessionCount = cursor.getInt(0);
            summary.avgDuration = cursor.getLong(1);
            summary.totalDuration = cursor.getLong(2);
            summary.minDuration = cursor.getLong(3);
            summary.maxDuration = cursor.getLong(4);
            summary.appPackageName = appPackageName;
        }
        cursor.close();
        
        return summary;
    }
    
    /**
     * Get top apps by usage time
     */
    public List<AppUsageSummary> getTopAppsByUsage(int limit, long startTime, long endTime) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT " +
                "app_package_name, " +
                "app_name, " +
                "COUNT(*) as session_count, " +
                "AVG(duration) as avg_duration, " +
                "SUM(duration) as total_duration " +
                "FROM app_sessions " +
                "WHERE start_time >= ? AND start_time <= ? AND is_active = 0 " +
                "GROUP BY app_package_name " +
                "ORDER BY total_duration DESC " +
                "LIMIT ?";
        
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(startTime), String.valueOf(endTime), String.valueOf(limit)});
        
        List<AppUsageSummary> topApps = new ArrayList<>();
        while (cursor.moveToNext()) {
            AppUsageSummary summary = new AppUsageSummary();
            summary.appPackageName = cursor.getString(0);
            summary.appName = cursor.getString(1);
            summary.sessionCount = cursor.getInt(2);
            summary.avgDuration = cursor.getLong(3);
            summary.totalDuration = cursor.getLong(4);
            topApps.add(summary);
        }
        cursor.close();
        
        return topApps;
    }
    
    /**
     * Get session count by hour of day
     */
    public Map<Integer, Integer> getSessionsByHour(long startTime, long endTime) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT " +
                "strftime('%H', datetime(start_time/1000, 'unixepoch', 'localtime')) as hour, " +
                "COUNT(*) as session_count " +
                "FROM app_sessions " +
                "WHERE start_time >= ? AND start_time <= ? " +
                "GROUP BY hour " +
                "ORDER BY hour";
        
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(startTime), String.valueOf(endTime)});
        
        Map<Integer, Integer> hourlyUsage = new HashMap<>();
        while (cursor.moveToNext()) {
            int hour = cursor.getInt(0);
            int count = cursor.getInt(1);
            hourlyUsage.put(hour, count);
        }
        cursor.close();
        
        return hourlyUsage;
    }
    
    /**
     * Get sessions with meaningful text content
     */
    public List<SessionTextSummary> getSessionsWithText(long startTime, long endTime) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT " +
                "s.session_id, " +
                "s.app_package_name, " +
                "s.app_name, " +
                "s.duration, " +
                "COUNT(t.id) as text_extractions, " +
                "SUM(CASE WHEN t.has_valid_content = 1 THEN 1 ELSE 0 END) as valid_extractions " +
                "FROM app_sessions s " +
                "LEFT JOIN ui_text_extractions t ON s.session_id = t.session_id " +
                "WHERE s.start_time >= ? AND s.start_time <= ? AND s.is_active = 0 " +
                "GROUP BY s.session_id " +
                "HAVING text_extractions > 0 " +
                "ORDER BY valid_extractions DESC";
        
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(startTime), String.valueOf(endTime)});
        
        List<SessionTextSummary> sessions = new ArrayList<>();
        while (cursor.moveToNext()) {
            SessionTextSummary summary = new SessionTextSummary();
            summary.sessionId = cursor.getString(0);
            summary.appPackageName = cursor.getString(1);
            summary.appName = cursor.getString(2);
            summary.duration = cursor.getLong(3);
            summary.textExtractions = cursor.getInt(4);
            summary.validExtractions = cursor.getInt(5);
            sessions.add(summary);
        }
        cursor.close();
        
        return sessions;
    }
    
    /**
     * Get average session duration by app category
     */
    public Map<String, Long> getAvgDurationByCategory() {
        // This would require app categorization logic
        // For now, return a simple implementation
        Map<String, Long> categoryDurations = new HashMap<>();
        
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT " +
                "CASE " +
                "  WHEN app_package_name LIKE '%social%' OR app_package_name LIKE '%facebook%' OR app_package_name LIKE '%instagram%' THEN 'Social' " +
                "  WHEN app_package_name LIKE '%game%' OR app_package_name LIKE '%play%' THEN 'Gaming' " +
                "  WHEN app_package_name LIKE '%browser%' OR app_package_name LIKE '%chrome%' THEN 'Browser' " +
                "  ELSE 'Other' " +
                "END as category, " +
                "AVG(duration) as avg_duration " +
                "FROM app_sessions " +
                "WHERE is_active = 0 AND duration > 0 " +
                "GROUP BY category";
        
        Cursor cursor = db.rawQuery(query, null);
        while (cursor.moveToNext()) {
            String category = cursor.getString(0);
            long avgDuration = cursor.getLong(1);
            categoryDurations.put(category, avgDuration);
        }
        cursor.close();
        
        return categoryDurations;
    }
    
    /**
     * Get daily usage statistics
     */
    public DailyUsageStats getDailyUsageStats(long dayStartTime, long dayEndTime) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT " +
                "COUNT(*) as total_sessions, " +
                "COUNT(DISTINCT app_package_name) as unique_apps, " +
                "SUM(duration) as total_duration, " +
                "AVG(duration) as avg_session_duration " +
                "FROM app_sessions " +
                "WHERE start_time >= ? AND start_time <= ? AND is_active = 0";
        
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(dayStartTime), String.valueOf(dayEndTime)});
        
        DailyUsageStats stats = new DailyUsageStats();
        if (cursor.moveToFirst()) {
            stats.totalSessions = cursor.getInt(0);
            stats.uniqueApps = cursor.getInt(1);
            stats.totalDuration = cursor.getLong(2);
            stats.avgSessionDuration = cursor.getLong(3);
        }
        cursor.close();
        
        return stats;
    }
    
    // Data classes for analytics results
    
    public static class AppUsageSummary {
        public String appPackageName;
        public String appName;
        public int sessionCount;
        public long avgDuration;
        public long totalDuration;
        public long minDuration;
        public long maxDuration;
        
        @Override
        public String toString() {
            return "AppUsageSummary{" +
                    "appPackageName='" + appPackageName + '\'' +
                    ", appName='" + appName + '\'' +
                    ", sessionCount=" + sessionCount +
                    ", avgDuration=" + avgDuration +
                    ", totalDuration=" + totalDuration +
                    '}';
        }
    }
    
    public static class SessionTextSummary {
        public String sessionId;
        public String appPackageName;
        public String appName;
        public long duration;
        public int textExtractions;
        public int validExtractions;
        
        public double getValidTextPercentage() {
            return textExtractions > 0 ? (double) validExtractions / textExtractions * 100 : 0;
        }
        
        @Override
        public String toString() {
            return "SessionTextSummary{" +
                    "sessionId='" + sessionId + '\'' +
                    ", appPackageName='" + appPackageName + '\'' +
                    ", duration=" + duration +
                    ", textExtractions=" + textExtractions +
                    ", validExtractions=" + validExtractions +
                    ", validPercentage=" + getValidTextPercentage() +
                    '}';
        }
    }
    
    public static class DailyUsageStats {
        public int totalSessions;
        public int uniqueApps;
        public long totalDuration;
        public long avgSessionDuration;
        
        public String getTotalDurationFormatted() {
            long seconds = totalDuration / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            return String.format("%dh %dm", hours, minutes % 60);
        }
        
        @Override
        public String toString() {
            return "DailyUsageStats{" +
                    "totalSessions=" + totalSessions +
                    ", uniqueApps=" + uniqueApps +
                    ", totalDuration=" + getTotalDurationFormatted() +
                    ", avgSessionDuration=" + (avgSessionDuration / 1000) + "s" +
                    '}';
        }
    }
}
