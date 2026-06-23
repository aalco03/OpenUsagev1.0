package com.openusage.app.interactions;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.openusage.app.TextBasedEventData.EventDatabaseHelper;

/**
 * SessionBasedDataExporter
 * 
 * Exports text extraction data from database in session-scoped CSV files
 * Replaces the simplified JSON-based approach with proper database queries
 */
public class SessionBasedDataExporter {
    private static final String TAG = "SessionBasedDataExporter";
    
    private final Context context;
    private final EventDatabaseHelper dbHelper;
    
    public SessionBasedDataExporter(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = EventDatabaseHelper.getInstance(this.context);
    }
    
    /**
     * Export all text extractions for a specific user session
     */
    public File exportUserSessionData(String userSessionId) {
        Log.d(TAG, "Exporting data for user session: " + userSessionId);
        
        try {
            // Create timestamped filename
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String timestamp = dateFormat.format(new Date());
            String filename = "screenomics_session_" + timestamp + ".csv";
            
            // Create file in external files directory
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir == null) {
                Log.e(TAG, "External files directory not available");
                return null;
            }
            
            File csvFile = new File(externalDir, filename);
            
            // Query database for session data
            String query = "SELECT " +
                    "us.user_session_id, us.user_id, us.login_time, " +
                    "aps.session_id, aps.app_package_name, aps.app_name, aps.start_time, aps.end_time, " +
                    "uit.extraction_timestamp, uit.extracted_text, uit.text_length, uit.has_valid_content " +
                    "FROM user_sessions us " +
                    "LEFT JOIN app_sessions aps ON us.user_session_id = aps.user_session_id " +
                    "LEFT JOIN ui_text_extractions uit ON aps.session_id = uit.session_id " +
                    "WHERE us.user_session_id = ? " +
                    "ORDER BY aps.start_time ASC, uit.extraction_timestamp ASC";
            
            Cursor cursor = dbHelper.getReadableDatabase().rawQuery(query, new String[]{userSessionId});
            
            if (cursor == null) {
                Log.e(TAG, "Database query failed");
                return null;
            }
            
            // Write CSV file
            try (FileWriter writer = new FileWriter(csvFile)) {
                // Write CSV header
                writer.append("user_session_id,user_id,session_login_time,")
                      .append("app_session_id,app_package_name,app_name,app_start_time,app_end_time,")
                      .append("extraction_timestamp,extracted_text,text_length,has_valid_content\n");
                
                int rowCount = 0;
                
                while (cursor.moveToNext()) {
                    // Get values from cursor
                    String userSessId = cursor.getString(0);
                    String userId = cursor.getString(1);
                    long loginTime = cursor.getLong(2);
                    String appSessionId = cursor.getString(3);
                    String appPackage = cursor.getString(4);
                    String appName = cursor.getString(5);
                    long appStartTime = cursor.getLong(6);
                    long appEndTime = cursor.getLong(7);
                    long extractionTime = cursor.getLong(8);
                    String extractedText = cursor.getString(9);
                    int textLength = cursor.getInt(10);
                    int hasValidContent = cursor.getInt(11);
                    
                    // Write CSV row
                    writer.append(csvEscape(userSessId)).append(",")
                          .append(csvEscape(userId)).append(",")
                          .append(String.valueOf(loginTime)).append(",")
                          .append(csvEscape(appSessionId)).append(",")
                          .append(csvEscape(appPackage)).append(",")
                          .append(csvEscape(appName)).append(",")
                          .append(String.valueOf(appStartTime)).append(",")
                          .append(String.valueOf(appEndTime)).append(",")
                          .append(String.valueOf(extractionTime)).append(",")
                          .append(csvEscape(extractedText)).append(",")
                          .append(String.valueOf(textLength)).append(",")
                          .append(String.valueOf(hasValidContent)).append("\n");
                    
                    rowCount++;
                }
                
                cursor.close();
                
                Log.i(TAG, "CSV export completed");
                Log.i(TAG, "📄 File: " + csvFile.getName());
                Log.i(TAG, "Rows exported: " + rowCount);
                Log.i(TAG, "File size: " + csvFile.length() + " bytes");
                
                return csvFile;
                
            } catch (IOException e) {
                Log.e(TAG, "Error writing CSV file: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during data export: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Export summary analytics for a user session
     */
    public File exportUserSessionSummary(String userSessionId) {
        Log.d(TAG, "Exporting summary for user session: " + userSessionId);
        
        try {
            // Create timestamped filename
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String timestamp = dateFormat.format(new Date());
            String filename = "screenomics_summary_" + timestamp + ".csv";
            
            // Create file in external files directory
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir == null) {
                Log.e(TAG, "External files directory not available");
                return null;
            }
            
            File csvFile = new File(externalDir, filename);
            
            // Query for session summary
            String summaryQuery = "SELECT " +
                    "us.user_session_id, us.user_id, us.login_time, us.logout_time, us.session_duration, " +
                    "COUNT(DISTINCT aps.session_id) as total_app_sessions, " +
                    "COUNT(DISTINCT aps.app_package_name) as unique_apps, " +
                    "COUNT(uit.id) as total_text_extractions, " +
                    "SUM(CASE WHEN uit.has_valid_content = 1 THEN 1 ELSE 0 END) as valid_extractions, " +
                    "AVG(uit.text_length) as avg_text_length " +
                    "FROM user_sessions us " +
                    "LEFT JOIN app_sessions aps ON us.user_session_id = aps.user_session_id " +
                    "LEFT JOIN ui_text_extractions uit ON aps.session_id = uit.session_id " +
                    "WHERE us.user_session_id = ? " +
                    "GROUP BY us.user_session_id";
            
            Cursor cursor = dbHelper.getReadableDatabase().rawQuery(summaryQuery, new String[]{userSessionId});
            
            if (cursor == null) {
                Log.e(TAG, "Summary query failed");
                return null;
            }
            
            // Write summary CSV
            try (FileWriter writer = new FileWriter(csvFile)) {
                writer.append("user_session_id,user_id,login_time,logout_time,session_duration,")
                      .append("total_app_sessions,unique_apps,total_text_extractions,valid_extractions,avg_text_length\n");
                
                if (cursor.moveToFirst()) {
                    writer.append(csvEscape(cursor.getString(0))).append(",")
                          .append(csvEscape(cursor.getString(1))).append(",")
                          .append(String.valueOf(cursor.getLong(2))).append(",")
                          .append(String.valueOf(cursor.getLong(3))).append(",")
                          .append(String.valueOf(cursor.getLong(4))).append(",")
                          .append(String.valueOf(cursor.getInt(5))).append(",")
                          .append(String.valueOf(cursor.getInt(6))).append(",")
                          .append(String.valueOf(cursor.getInt(7))).append(",")
                          .append(String.valueOf(cursor.getInt(8))).append(",")
                          .append(String.valueOf(cursor.getDouble(9))).append("\n");
                }
                
                cursor.close();
                
                Log.i(TAG, "Summary export completed: " + csvFile.getName());
                return csvFile;
                
            } catch (IOException e) {
                Log.e(TAG, "Error writing summary CSV: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during summary export: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Escape CSV values to handle commas, quotes, and newlines
     */
    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        
        // If value contains comma, quote, or newline, wrap in quotes and escape internal quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        
        return value;
    }
}
