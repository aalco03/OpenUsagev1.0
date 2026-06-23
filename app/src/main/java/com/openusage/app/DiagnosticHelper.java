package com.openusage.app;

import android.content.Context;
import android.util.Log;
import com.openusage.app.TextBasedEventData.EventDatabaseHelper;

/**
 * Diagnostic helper to check app status and data generation
 */
public class DiagnosticHelper {
    private static final String TAG = "DiagnosticHelper";
    
    /**
     * Run comprehensive diagnostics
     */
    public static void runDiagnostics(Context context) {
        Log.i(TAG, "=== SCREENOMICS DIAGNOSTICS ===");
        
        // Check database status
        checkDatabaseStatus(context);
        
        // Check service status
        checkServiceStatus(context);
        
        Log.i(TAG, "=== DIAGNOSTICS COMPLETE ===");
    }
    
    /**
     * Check database for data generation
     */
    private static void checkDatabaseStatus(Context context) {
        Log.i(TAG, "--- DATABASE STATUS ---");
        
        try {
            EventDatabaseHelper dbHelper = EventDatabaseHelper.getInstance(context);
            
            // Check total events
            int totalEvents = dbHelper.getTotalEventCount();
            Log.i(TAG, "Total events in database: " + totalEvents);
            
            // Note: Database access methods are limited, checking basic status only
            Log.i(TAG, "Database helper initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking database status", e);
        }
    }
    
    
    /**
     * Check service status
     */
    private static void checkServiceStatus(Context context) {
        Log.i(TAG, "--- SERVICE STATUS ---");
        
        try {
            // Check if CaptureUploadService is running
            boolean captureServiceRunning = com.openusage.app.Services.CaptureUploadService.isRunning();
            Log.i(TAG, "CaptureUploadService running: " + captureServiceRunning);
            
            // Check if ScreenMonitorService is running
            boolean screenServiceRunning = isServiceRunning(context, "ScreenMonitorService");
            Log.i(TAG, "ScreenMonitorService running: " + screenServiceRunning);
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking service status", e);
        }
    }
    
    /**
     * Helper to check if a service is running
     */
    private static boolean isServiceRunning(Context context, String serviceName) {
        try {
            android.app.ActivityManager manager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (service.service.getClassName().contains(serviceName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking service: " + serviceName, e);
        }
        return false;
    }
    
    /**
     * Quick check for unsynced data
     */
    public static void checkUnsyncedData(Context context) {
        Log.i(TAG, "=== UNSYNCED DATA CHECK ===");
        
        try {
            EventDatabaseHelper dbHelper = EventDatabaseHelper.getInstance(context);
            
            // Note: Direct database access methods not available
            // Checking if database helper is working
            Log.i(TAG, "Database helper available for sync operations");
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking unsynced data", e);
        }
    }
}
