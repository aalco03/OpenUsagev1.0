package com.openusage.app.TextBasedEventData;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Configuration manager for GDP-B Dashboard connection settings
 * Handles ngrok tunnel configuration and connection preferences
 */
public class DashboardConfigManager {
    private static final String TAG = "DashboardConfigManager";
    
    // SharedPreferences keys
    private static final String PREFS_NAME = "gdpb_dashboard_config";
    private static final String KEY_DASHBOARD_URL = "dashboard_url";
    private static final String KEY_STUDY_ID = "study_id";
    private static final String KEY_USE_NGROK = "use_ngrok";
    private static final String KEY_NGROK_URL = "ngrok_url";
    private static final String KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled";
    private static final String KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes";
    private static final String KEY_DEBUG_MODE = "debug_mode";
    private static final String KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp";
    private static final String KEY_CONNECTION_TESTED = "connection_tested";
    
    // Default values
    private static final String DEFAULT_DASHBOARD_URL = "http://localhost:8080";
    private static final String DEFAULT_STUDY_ID = "SCREENOMICS_TEST_001";
    private static final boolean DEFAULT_USE_NGROK = false;
    private static final boolean DEFAULT_AUTO_SYNC = true;
    private static final int DEFAULT_SYNC_INTERVAL = 15; // minutes
    private static final boolean DEFAULT_DEBUG_MODE = true;
    
    private final SharedPreferences prefs;
    private static volatile DashboardConfigManager instance;
    
    private DashboardConfigManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Initialize default values if first run
        initializeDefaults();
        
        Log.i(TAG, "Dashboard Config Manager initialized");
        logCurrentConfiguration();
    }
    
    public static DashboardConfigManager getInstance(Context context) {
        if (instance == null) {
            synchronized (DashboardConfigManager.class) {
                if (instance == null) {
                    instance = new DashboardConfigManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize default configuration values
     */
    private void initializeDefaults() {
        SharedPreferences.Editor editor = prefs.edit();
        
        if (!prefs.contains(KEY_DASHBOARD_URL)) {
            editor.putString(KEY_DASHBOARD_URL, DEFAULT_DASHBOARD_URL);
        }
        
        if (!prefs.contains(KEY_STUDY_ID)) {
            editor.putString(KEY_STUDY_ID, DEFAULT_STUDY_ID);
        }
        
        if (!prefs.contains(KEY_USE_NGROK)) {
            editor.putBoolean(KEY_USE_NGROK, DEFAULT_USE_NGROK);
        }
        
        if (!prefs.contains(KEY_AUTO_SYNC_ENABLED)) {
            editor.putBoolean(KEY_AUTO_SYNC_ENABLED, DEFAULT_AUTO_SYNC);
        }
        
        if (!prefs.contains(KEY_SYNC_INTERVAL_MINUTES)) {
            editor.putInt(KEY_SYNC_INTERVAL_MINUTES, DEFAULT_SYNC_INTERVAL);
        }
        
        if (!prefs.contains(KEY_DEBUG_MODE)) {
            editor.putBoolean(KEY_DEBUG_MODE, DEFAULT_DEBUG_MODE);
        }
        
        editor.apply();
    }
    
    /**
     * Log current configuration for debugging
     */
    private void logCurrentConfiguration() {
        Log.i(TAG, "=== CURRENT DASHBOARD CONFIGURATION ===");
        Log.i(TAG, "Dashboard URL: " + getDashboardUrl());
        Log.i(TAG, "Study ID: " + getStudyId());
        Log.i(TAG, "Use ngrok: " + isUseNgrok());
        Log.i(TAG, "Ngrok URL: " + getNgrokUrl());
        Log.i(TAG, "Auto sync enabled: " + isAutoSyncEnabled());
        Log.i(TAG, "Sync interval: " + getSyncIntervalMinutes() + " minutes");
        Log.i(TAG, "Debug mode: " + isDebugMode());
        Log.i(TAG, "Connection tested: " + isConnectionTested());
        Log.i(TAG, "Last sync: " + getLastSyncTimestamp());
        Log.i(TAG, "========================================");
    }
    
    // Dashboard URL configuration
    public String getDashboardUrl() {
        if (isUseNgrok() && getNgrokUrl() != null && !getNgrokUrl().isEmpty()) {
            return getNgrokUrl();
        }
        return prefs.getString(KEY_DASHBOARD_URL, DEFAULT_DASHBOARD_URL);
    }
    
    public void setDashboardUrl(String url) {
        prefs.edit().putString(KEY_DASHBOARD_URL, url).apply();
        Log.i(TAG, "Dashboard URL updated: " + url);
    }
    
    // Study ID configuration
    public String getStudyId() {
        return prefs.getString(KEY_STUDY_ID, DEFAULT_STUDY_ID);
    }
    
    public void setStudyId(String studyId) {
        prefs.edit().putString(KEY_STUDY_ID, studyId).apply();
        Log.i(TAG, "Study ID updated: " + studyId);
    }
    
    // Ngrok configuration
    public boolean isUseNgrok() {
        return prefs.getBoolean(KEY_USE_NGROK, DEFAULT_USE_NGROK);
    }
    
    public void setUseNgrok(boolean useNgrok) {
        prefs.edit().putBoolean(KEY_USE_NGROK, useNgrok).apply();
        Log.i(TAG, "Use ngrok updated: " + useNgrok);
    }
    
    public String getNgrokUrl() {
        return prefs.getString(KEY_NGROK_URL, "");
    }
    
    public void setNgrokUrl(String ngrokUrl) {
        prefs.edit().putString(KEY_NGROK_URL, ngrokUrl).apply();
        Log.i(TAG, "Ngrok URL updated: " + ngrokUrl);
    }
    
    // Auto sync configuration
    public boolean isAutoSyncEnabled() {
        return prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, DEFAULT_AUTO_SYNC);
    }
    
    public void setAutoSyncEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply();
        Log.i(TAG, "Auto sync enabled updated: " + enabled);
    }
    
    public int getSyncIntervalMinutes() {
        return prefs.getInt(KEY_SYNC_INTERVAL_MINUTES, DEFAULT_SYNC_INTERVAL);
    }
    
    public void setSyncIntervalMinutes(int minutes) {
        prefs.edit().putInt(KEY_SYNC_INTERVAL_MINUTES, minutes).apply();
        Log.i(TAG, "Sync interval updated: " + minutes + " minutes");
    }
    
    // Debug mode
    public boolean isDebugMode() {
        return prefs.getBoolean(KEY_DEBUG_MODE, DEFAULT_DEBUG_MODE);
    }
    
    public void setDebugMode(boolean debugMode) {
        prefs.edit().putBoolean(KEY_DEBUG_MODE, debugMode).apply();
        Log.i(TAG, "Debug mode updated: " + debugMode);
    }
    
    // Connection status tracking
    public boolean isConnectionTested() {
        return prefs.getBoolean(KEY_CONNECTION_TESTED, false);
    }
    
    public void setConnectionTested(boolean tested) {
        prefs.edit().putBoolean(KEY_CONNECTION_TESTED, tested).apply();
        Log.d(TAG, "Connection tested status updated: " + tested);
    }
    
    // Last sync tracking
    public long getLastSyncTimestamp() {
        return prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0);
    }
    
    public void setLastSyncTimestamp(long timestamp) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp).apply();
        Log.d(TAG, "Last sync timestamp updated: " + timestamp);
    }
    
    /**
     * Configure for ngrok testing
     */
    public void configureForNgrokTesting(String ngrokUrl, String studyId) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_NGROK_URL, ngrokUrl);
        editor.putString(KEY_STUDY_ID, studyId);
        editor.putBoolean(KEY_USE_NGROK, true);
        editor.putBoolean(KEY_DEBUG_MODE, true);
        editor.putBoolean(KEY_CONNECTION_TESTED, false); // Reset connection test status
        editor.apply();
        
        Log.i(TAG, "=== CONFIGURED FOR NGROK TESTING ===");
        Log.i(TAG, "Ngrok URL: " + ngrokUrl);
        Log.i(TAG, "Study ID: " + studyId);
        Log.i(TAG, "Debug mode enabled");
        Log.i(TAG, "===================================");
    }
    
    /**
     * Configure for local development
     */
    public void configureForLocalDevelopment(String localUrl, String studyId) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_DASHBOARD_URL, localUrl);
        editor.putString(KEY_STUDY_ID, studyId);
        editor.putBoolean(KEY_USE_NGROK, false);
        editor.putBoolean(KEY_DEBUG_MODE, true);
        editor.putBoolean(KEY_CONNECTION_TESTED, false); // Reset connection test status
        editor.apply();
        
        Log.i(TAG, "=== CONFIGURED FOR LOCAL DEVELOPMENT ===");
        Log.i(TAG, "Local URL: " + localUrl);
        Log.i(TAG, "Study ID: " + studyId);
        Log.i(TAG, "Debug mode enabled");
        Log.i(TAG, "=======================================");
    }
    
    /**
     * Configure for production
     */
    public void configureForProduction(String productionUrl, String studyId) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_DASHBOARD_URL, productionUrl);
        editor.putString(KEY_STUDY_ID, studyId);
        editor.putBoolean(KEY_USE_NGROK, false);
        editor.putBoolean(KEY_DEBUG_MODE, false);
        editor.putBoolean(KEY_AUTO_SYNC_ENABLED, true);
        editor.putInt(KEY_SYNC_INTERVAL_MINUTES, 30); // Longer interval for production
        editor.putBoolean(KEY_CONNECTION_TESTED, false); // Reset connection test status
        editor.apply();
        
        Log.i(TAG, "=== CONFIGURED FOR PRODUCTION ===");
        Log.i(TAG, "Production URL: " + productionUrl);
        Log.i(TAG, "Study ID: " + studyId);
        Log.i(TAG, "Debug mode disabled");
        Log.i(TAG, "Auto sync enabled with 30min interval");
        Log.i(TAG, "================================");
    }
    
    /**
     * Reset to default configuration
     */
    public void resetToDefaults() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
        
        initializeDefaults();
        
        Log.i(TAG, "Configuration reset to defaults");
        logCurrentConfiguration();
    }
    
    /**
     * Get configuration summary for debugging
     */
    public String getConfigurationSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Dashboard Configuration Summary:\n");
        summary.append("  Active URL: ").append(getDashboardUrl()).append("\n");
        summary.append("  Study ID: ").append(getStudyId()).append("\n");
        summary.append("  Using ngrok: ").append(isUseNgrok()).append("\n");
        summary.append("  Auto sync: ").append(isAutoSyncEnabled()).append("\n");
        summary.append("  Sync interval: ").append(getSyncIntervalMinutes()).append(" min\n");
        summary.append("  Debug mode: ").append(isDebugMode()).append("\n");
        summary.append("  Connection tested: ").append(isConnectionTested()).append("\n");
        
        if (getLastSyncTimestamp() > 0) {
            long timeSinceSync = System.currentTimeMillis() - getLastSyncTimestamp();
            long minutesSinceSync = timeSinceSync / (1000 * 60);
            summary.append("  Last sync: ").append(minutesSinceSync).append(" minutes ago\n");
        } else {
            summary.append("  Last sync: Never\n");
        }
        
        return summary.toString();
    }
    
    /**
     * Validate current configuration
     */
    public boolean isConfigurationValid() {
        String url = getDashboardUrl();
        String studyId = getStudyId();
        
        boolean urlValid = url != null && !url.trim().isEmpty() && 
                          (url.startsWith("http://") || url.startsWith("https://"));
        boolean studyIdValid = studyId != null && !studyId.trim().isEmpty();
        
        Log.d(TAG, "Configuration validation - URL valid: " + urlValid + ", Study ID valid: " + studyIdValid);
        
        return urlValid && studyIdValid;
    }
    
    /**
     * Get ngrok setup instructions
     */
    public String getNgrokSetupInstructions() {
        return "Ngrok Setup Instructions:\n\n" +
               "1. Install ngrok: https://ngrok.com/download\n" +
               "2. Start your GDP-B dashboard locally (port 8080)\n" +
               "3. Run: ngrok http 8080\n" +
               "4. Copy the HTTPS forwarding URL (e.g., https://abc123.ngrok.io)\n" +
               "5. Use configureForNgrokTesting() with that URL\n" +
               "6. Test connection using GdpbDashboardSyncService.testDashboardConnection()\n\n" +
               "Current ngrok URL: " + (getNgrokUrl().isEmpty() ? "Not set" : getNgrokUrl());
    }
}
