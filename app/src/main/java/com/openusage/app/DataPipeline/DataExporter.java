package com.openusage.app.DataPipeline;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.openusage.app.TextBasedEventData.EventDatabaseHelper;

/**
 * ========== TESTING ENVIRONMENT: DATA EXPORT PIPELINE ==========
 * Exports UI text extraction data from SQLite to JSON/CSV for cloud upload
 * Provides simple data pipeline for demo purposes
 */
public class DataExporter {
    private static final String TAG = "DataExporter";
    
    private final Context context;
    private final EventDatabaseHelper dbHelper;
    
    public DataExporter(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = EventDatabaseHelper.getInstance(context);
    }
    
    /**
     * Export all UI text extractions to JSON format
     */
    public JSONArray exportUITextExtractionsToJSON() {
        JSONArray jsonArray = new JSONArray();
        
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            
            // Query all UI text extractions
            String query = "SELECT * FROM ui_text_extractions ORDER BY timestamp DESC";
            Cursor cursor = db.rawQuery(query, null);
            
            Log.d(TAG, "Found " + cursor.getCount() + " UI text extraction records");
            
            if (cursor.moveToFirst()) {
                do {
                    JSONObject record = new JSONObject();
                    
                    // Extract all columns
                    record.put("id", cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                    record.put("session_id", cursor.getString(cursor.getColumnIndexOrThrow("session_id")));
                    record.put("screenshot_filename", cursor.getString(cursor.getColumnIndexOrThrow("screenshot_filename")));
                    record.put("extracted_text", cursor.getString(cursor.getColumnIndexOrThrow("extracted_text")));
                    record.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")));
                    record.put("has_valid_content", cursor.getInt(cursor.getColumnIndexOrThrow("has_valid_content")) == 1);
                    record.put("window_title", cursor.getString(cursor.getColumnIndexOrThrow("window_title")));
                    
                    // Add human-readable timestamp
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));
                    String formattedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
                    record.put("formatted_timestamp", formattedDate);
                    
                    // Add text length for analysis
                    String extractedText = cursor.getString(cursor.getColumnIndexOrThrow("extracted_text"));
                    record.put("text_length", extractedText != null ? extractedText.length() : 0);
                    
                    jsonArray.put(record);
                    
                } while (cursor.moveToNext());
            }
            
            cursor.close();
            
        } catch (Exception e) {
            Log.e(TAG, "Error exporting UI text extractions to JSON: " + e.getMessage());
            e.printStackTrace();
        }
        
        return jsonArray;
    }
    
    /**
     * Export data to JSON file in app's external files directory
     */
    public File exportToJSONFile() {
        try {
            JSONArray data = exportUITextExtractionsToJSON();
            
            // Create filename with timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = "screenomics_data_" + timestamp + ".json";
            
            // Write to external files directory (accessible via file manager)
            File externalDir = context.getExternalFilesDir(null);
            File jsonFile = new File(externalDir, filename);
            
            FileWriter writer = new FileWriter(jsonFile);
            writer.write(data.toString(2)); // Pretty print with 2-space indentation
            writer.close();
            
            Log.i(TAG, "Exported " + data.length() + " records to: " + jsonFile.getAbsolutePath());
            
            return jsonFile;
            
        } catch (Exception e) {
            Log.e(TAG, "Error exporting to JSON file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Convert JSON data to CSV format
     */
    public String convertJSONToCSV(JSONArray jsonData) {
        StringBuilder csv = new StringBuilder();
        
        try {
            if (jsonData.length() == 0) {
                return "";
            }
            
            // Create CSV header
            csv.append("id,session_id,screenshot_filename,timestamp,formatted_timestamp,has_valid_content,window_title,text_length,extracted_text\n");
            
            // Add data rows
            for (int i = 0; i < jsonData.length(); i++) {
                JSONObject record = jsonData.getJSONObject(i);
                
                csv.append(record.optLong("id", 0)).append(",");
                csv.append(escapeCSV(record.optString("session_id", ""))).append(",");
                csv.append(escapeCSV(record.optString("screenshot_filename", ""))).append(",");
                csv.append(record.optLong("timestamp", 0)).append(",");
                csv.append(escapeCSV(record.optString("formatted_timestamp", ""))).append(",");
                csv.append(record.optBoolean("has_valid_content", false)).append(",");
                csv.append(escapeCSV(record.optString("window_title", ""))).append(",");
                csv.append(record.optInt("text_length", 0)).append(",");
                csv.append(escapeCSV(record.optString("extracted_text", ""))).append("\n");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting JSON to CSV: " + e.getMessage());
            e.printStackTrace();
        }
        
        return csv.toString();
    }
    
    /**
     * Export data to CSV file
     */
    public File exportToCSVFile() {
        try {
            JSONArray data = exportUITextExtractionsToJSON();
            String csvContent = convertJSONToCSV(data);
            
            // Create filename with timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = "screenomics_data_" + timestamp + ".csv";
            
            // Write to external files directory
            File externalDir = context.getExternalFilesDir(null);
            File csvFile = new File(externalDir, filename);
            
            FileWriter writer = new FileWriter(csvFile);
            writer.write(csvContent);
            writer.close();
            
            Log.i(TAG, "Exported " + data.length() + " records to CSV: " + csvFile.getAbsolutePath());
            
            return csvFile;
            
        } catch (Exception e) {
            Log.e(TAG, "Error exporting to CSV file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Escape CSV special characters
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        
        // Escape quotes and wrap in quotes if contains comma, quote, or newline
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            value = value.replace("\"", "\"\""); // Escape quotes
            return "\"" + value + "\"";
        }
        
        return value;
    }
    
    /**
     * Get summary statistics of collected data
     */
    public JSONObject getDataSummary() {
        JSONObject summary = new JSONObject();
        
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            
            // Total records
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM ui_text_extractions", null);
            if (cursor.moveToFirst()) {
                summary.put("total_records", cursor.getInt(0));
            }
            cursor.close();
            
            // Date range
            cursor = db.rawQuery("SELECT MIN(timestamp), MAX(timestamp) FROM ui_text_extractions", null);
            if (cursor.moveToFirst()) {
                long minTimestamp = cursor.getLong(0);
                long maxTimestamp = cursor.getLong(1);
                
                if (minTimestamp > 0 && maxTimestamp > 0) {
                    summary.put("earliest_record", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(minTimestamp)));
                    summary.put("latest_record", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(maxTimestamp)));
                    summary.put("duration_hours", (maxTimestamp - minTimestamp) / (1000.0 * 60 * 60));
                }
            }
            cursor.close();
            
            // Unique apps
            cursor = db.rawQuery("SELECT COUNT(DISTINCT window_title) FROM ui_text_extractions WHERE window_title IS NOT NULL", null);
            if (cursor.moveToFirst()) {
                summary.put("unique_apps", cursor.getInt(0));
            }
            cursor.close();
            
            // Average text length
            cursor = db.rawQuery("SELECT AVG(LENGTH(extracted_text)) FROM ui_text_extractions WHERE extracted_text IS NOT NULL", null);
            if (cursor.moveToFirst()) {
                summary.put("avg_text_length", cursor.getDouble(0));
            }
            cursor.close();
            
            Log.d(TAG, "Data summary: " + summary.toString(2));
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating data summary: " + e.getMessage());
            e.printStackTrace();
        }
        
        return summary;
    }
}
