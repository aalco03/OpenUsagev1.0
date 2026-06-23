package com.openusage.app.DataPipeline;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ========== TESTING ENVIRONMENT: DATA PIPELINE MANAGER ==========
 * Coordinates the complete data pipeline: SQLite → JSON → CSV → Google Drive
 * Provides simple interface for demo data export and upload
 */
public class DataPipelineManager {
    private static final String TAG = "DataPipelineManager";
    
    private final Context context;
    private final DataExporter dataExporter;
    private final GoogleDriveUploader driveUploader;
    private final ExecutorService executor;
    
    // Configuration
    private String googleDriveAccessToken;
    private String webhookUrl;
    private boolean autoUploadEnabled = false;
    
    public DataPipelineManager(Context context) {
        this.context = context.getApplicationContext();
        this.dataExporter = new DataExporter(context);
        this.driveUploader = new GoogleDriveUploader(context);
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    /**
     * Configure Google Drive access token
     */
    public void setGoogleDriveAccessToken(String accessToken) {
        this.googleDriveAccessToken = accessToken;
        driveUploader.setAccessToken(accessToken);
        Log.d(TAG, "Google Drive access token configured");
    }
    
    /**
     * Configure webhook URL (alternative to Google Drive API)
     */
    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        Log.d(TAG, "Webhook URL configured: " + webhookUrl);
    }
    
    /**
     * Enable/disable automatic upload after data export
     */
    public void setAutoUploadEnabled(boolean enabled) {
        this.autoUploadEnabled = enabled;
        Log.d(TAG, "Auto upload " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Run complete data pipeline: Export → Upload
     */
    public void runCompletePipeline(PipelineCallback callback) {
        executor.execute(() -> {
            try {
                Log.i(TAG, "Starting complete data pipeline...");
                
                // Step 1: Get data summary
                JSONObject summary = dataExporter.getDataSummary();
                Log.i(TAG, "Data summary: " + summary.toString());
                
                // Step 2: Export to JSON
                JSONArray jsonData = dataExporter.exportUITextExtractionsToJSON();
                Log.i(TAG, "📄 Exported " + jsonData.length() + " records to JSON");
                
                if (jsonData.length() == 0) {
                    String message = "No data available to export";
                    Log.w(TAG, "" + message);
                    if (callback != null) {
                        callback.onError(message);
                    }
                    return;
                }
                
                // Step 3: Export to CSV file
                File csvFile = dataExporter.exportToCSVFile();
                if (csvFile == null) {
                    String message = "Failed to create CSV file";
                    Log.e(TAG, "" + message);
                    if (callback != null) {
                        callback.onError(message);
                    }
                    return;
                }
                
                Log.i(TAG, "📁 CSV file created: " + csvFile.getAbsolutePath());
                
                // Step 4: Upload if auto-upload is enabled
                if (autoUploadEnabled) {
                    uploadData(csvFile, callback);
                } else {
                    String message = "Data exported successfully. File: " + csvFile.getAbsolutePath();
                    Log.i(TAG, "" + message);
                    if (callback != null) {
                        callback.onSuccess(message, csvFile, summary);
                    }
                }
                
            } catch (Exception e) {
                String error = "Error in data pipeline: " + e.getMessage();
                Log.e(TAG, "" + error);
                e.printStackTrace();
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }
    
    /**
     * Export data only (no upload)
     */
    public void exportDataOnly(PipelineCallback callback) {
        executor.execute(() -> {
            try {
                Log.i(TAG, "📤 Exporting data only...");
                
                // Get summary
                JSONObject summary = dataExporter.getDataSummary();
                
                // Export to CSV
                File csvFile = dataExporter.exportToCSVFile();
                
                if (csvFile != null) {
                    String message = "Data exported successfully to: " + csvFile.getAbsolutePath();
                    Log.i(TAG, "" + message);
                    if (callback != null) {
                        callback.onSuccess(message, csvFile, summary);
                    }
                } else {
                    String error = "Failed to export data";
                    Log.e(TAG, "" + error);
                    if (callback != null) {
                        callback.onError(error);
                    }
                }
                
            } catch (Exception e) {
                String error = "Error exporting data: " + e.getMessage();
                Log.e(TAG, "" + error);
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }
    
    /**
     * Upload existing CSV file
     */
    public void uploadData(File csvFile, PipelineCallback callback) {
        if (webhookUrl != null && !webhookUrl.isEmpty()) {
            // Use webhook approach
            uploadViaWebhook(csvFile, callback);
        } else if (googleDriveAccessToken != null && !googleDriveAccessToken.isEmpty()) {
            // Use Google Drive API
            uploadToGoogleDrive(csvFile, callback);
        } else {
            String error = "No upload method configured. Set either webhook URL or Google Drive access token.";
            Log.e(TAG, "" + error);
            if (callback != null) {
                callback.onError(error);
            }
        }
    }
    
    /**
     * Upload to Google Drive using API
     */
    private void uploadToGoogleDrive(File csvFile, PipelineCallback callback) {
        Log.i(TAG, "Uploading to Google Drive...");
        
        driveUploader.uploadCSVFile(csvFile, new GoogleDriveUploader.UploadCallback() {
            @Override
            public void onSuccess(String message) {
                Log.i(TAG, "Google Drive upload successful: " + message);
                if (callback != null) {
                    JSONObject summary = dataExporter.getDataSummary();
                    callback.onSuccess("Uploaded to Google Drive: " + message, csvFile, summary);
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Google Drive upload failed: " + error);
                if (callback != null) {
                    callback.onError("Google Drive upload failed: " + error);
                }
            }
        });
    }
    
    /**
     * Upload via webhook
     */
    private void uploadViaWebhook(File csvFile, PipelineCallback callback) {
        try {
            Log.i(TAG, "🔗 Uploading via webhook...");
            
            // Convert file to string
            JSONArray jsonData = dataExporter.exportUITextExtractionsToJSON();
            String csvContent = dataExporter.convertJSONToCSV(jsonData);
            
            driveUploader.uploadViaWebhook(csvContent, webhookUrl, new GoogleDriveUploader.UploadCallback() {
                @Override
                public void onSuccess(String message) {
                    Log.i(TAG, "Webhook upload successful: " + message);
                    if (callback != null) {
                        JSONObject summary = dataExporter.getDataSummary();
                        callback.onSuccess("Uploaded via webhook: " + message, csvFile, summary);
                    }
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Webhook upload failed: " + error);
                    if (callback != null) {
                        callback.onError("Webhook upload failed: " + error);
                    }
                }
            });
            
        } catch (Exception e) {
            String error = "Error preparing webhook upload: " + e.getMessage();
            Log.e(TAG, "" + error);
            if (callback != null) {
                callback.onError(error);
            }
        }
    }
    
    /**
     * Test connection to configured upload service
     */
    public void testConnection(PipelineCallback callback) {
        if (googleDriveAccessToken != null && !googleDriveAccessToken.isEmpty()) {
            Log.i(TAG, "Testing Google Drive connection...");
            driveUploader.testConnection(new GoogleDriveUploader.UploadCallback() {
                @Override
                public void onSuccess(String message) {
                    if (callback != null) {
                        callback.onSuccess("Google Drive connection test: " + message, null, null);
                    }
                }
                
                @Override
                public void onError(String error) {
                    if (callback != null) {
                        callback.onError("Google Drive connection test failed: " + error);
                    }
                }
            });
        } else {
            String message = "No Google Drive access token configured for testing";
            Log.w(TAG, "" + message);
            if (callback != null) {
                callback.onError(message);
            }
        }
    }
    
    /**
     * Get setup instructions for Google Apps Script webhook
     */
    public String getSetupInstructions() {
        return driveUploader.getGoogleAppsScriptInstructions();
    }
    
    /**
     * Clean up resources
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        driveUploader.shutdown();
    }
    
    /**
     * Callback interface for pipeline operations
     */
    public interface PipelineCallback {
        void onSuccess(String message, File csvFile, JSONObject summary);
        void onError(String error);
    }
}
