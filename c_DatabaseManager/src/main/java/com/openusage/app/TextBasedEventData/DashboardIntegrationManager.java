package com.openusage.app.TextBasedEventData;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main integration manager for GDP-B Dashboard connectivity
 * Handles automatic syncing, connection testing, and configuration management
 */
public class DashboardIntegrationManager {
    private static final String TAG = "DashboardIntegration";
    
    private final Context context;
    private final DashboardConfigManager configManager;
    private final GdpbDashboardSyncService syncService;
    private final UserProfileManager userProfileManager;
    
    private ScheduledExecutorService scheduledExecutor;
    private Handler mainHandler;
    
    private static volatile DashboardIntegrationManager instance;
    
    // Status tracking
    private boolean isInitialized = false;
    private boolean isAutoSyncRunning = false;
    private boolean lastConnectionTestPassed = false;
    private long lastSyncAttempt = 0;
    private String lastErrorMessage = "";
    
    private DashboardIntegrationManager(Context context) {
        this.context = context.getApplicationContext();
        this.configManager = DashboardConfigManager.getInstance(context);
        this.syncService = new GdpbDashboardSyncService(context);
        this.userProfileManager = UserProfileManager.getInstance(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        Log.i(TAG, "Dashboard Integration Manager created");
    }
    
    public static DashboardIntegrationManager getInstance(Context context) {
        if (instance == null) {
            synchronized (DashboardIntegrationManager.class) {
                if (instance == null) {
                    instance = new DashboardIntegrationManager(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize dashboard integration
     */
    public void initialize() {
        if (isInitialized) {
            Log.w(TAG, "Dashboard integration already initialized");
            return;
        }
        
        Log.i(TAG, "=== INITIALIZING DASHBOARD INTEGRATION ===");
        
        // Configure sync service with current settings
        updateSyncServiceConfiguration();
        
        // Create user profile if needed
        ensureUserProfileExists();
        
        // Start auto sync if enabled
        if (configManager.isAutoSyncEnabled()) {
            startAutoSync();
        }
        
        isInitialized = true;
        
        Log.i(TAG, "Dashboard integration initialized successfully");
        Log.i(TAG, configManager.getConfigurationSummary());
    }
    
    /**
     * Update sync service configuration from config manager
     */
    private void updateSyncServiceConfiguration() {
        String dashboardUrl = configManager.getDashboardUrl();
        String studyId = configManager.getStudyId();
        boolean useNgrok = configManager.isUseNgrok();
        
        syncService.configureDashboard(dashboardUrl, studyId, useNgrok);
        
        Log.i(TAG, "Sync service configured with:");
        Log.i(TAG, "  URL: " + dashboardUrl);
        Log.i(TAG, "  Study ID: " + studyId);
        Log.i(TAG, "  Using ngrok: " + useNgrok);
    }
    
    /**
     * Ensure user profile exists for the current study
     */
    private void ensureUserProfileExists() {
        try {
            String studyId = configManager.getStudyId();
            UserProfile existingProfile = userProfileManager.getUserProfile(studyId);
            
            if (existingProfile == null) {
                Log.i(TAG, "Creating user profile for study ID: " + studyId);
                
                UserProfile newProfile = new UserProfile();
                newProfile.setUserId(studyId);
                newProfile.setStudyId(studyId);
                newProfile.setEmail("participant@screenomics.study");
                newProfile.setStudyCode("SCREENOMICS_2024");
                newProfile.setDeviceId(android.provider.Settings.Secure.getString(
                    context.getContentResolver(), 
                    android.provider.Settings.Secure.ANDROID_ID
                ));
                newProfile.setStudyGroup("Android Beta");
                newProfile.setActive(true);
                newProfile.setConsentGiven(true);
                
                boolean created = userProfileManager.createOrUpdateUserProfile(newProfile);
                if (created) {
                    Log.i(TAG, "User profile created successfully");
                } else {
                    Log.e(TAG, "Failed to create user profile");
                }
            } else {
                Log.i(TAG, "User profile already exists for study ID: " + studyId);
                // Update last active date
                userProfileManager.updateLastActiveDate(studyId);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error ensuring user profile exists", e);
        }
    }
    
    /**
     * Test connection to dashboard
     */
    public void testConnection(ConnectionTestListener listener) {
        Log.i(TAG, "=== STARTING CONNECTION TEST ===");
        
        if (!configManager.isConfigurationValid()) {
            Log.e(TAG, "Invalid configuration - cannot test connection");
            if (listener != null) {
                mainHandler.post(() -> listener.onConnectionTestFailed("Invalid configuration"));
            }
            return;
        }
        
        updateSyncServiceConfiguration();
        
        syncService.testDashboardConnection(result -> {
            lastConnectionTestPassed = result.overallSuccess;
            configManager.setConnectionTested(true);
            
            if (result.overallSuccess) {
                Log.i(TAG, "Connection test PASSED");
                lastErrorMessage = "";
                
                if (listener != null) {
                    mainHandler.post(() -> listener.onConnectionTestPassed(result));
                }
            } else {
                Log.e(TAG, "Connection test FAILED: " + result.errorMessage);
                lastErrorMessage = result.errorMessage != null ? result.errorMessage : "Unknown error";
                
                if (listener != null) {
                    mainHandler.post(() -> listener.onConnectionTestFailed(lastErrorMessage));
                }
            }
            
            Log.i(TAG, "Connection test result: " + result.toString());
        });
    }
    
    /**
     * Start automatic syncing
     */
    public void startAutoSync() {
        if (isAutoSyncRunning) {
            Log.w(TAG, "Auto sync already running");
            return;
        }
        
        if (!configManager.isAutoSyncEnabled()) {
            Log.w(TAG, "Auto sync is disabled in configuration");
            return;
        }
        
        Log.i(TAG, "Starting automatic sync");
        
        if (scheduledExecutor == null || scheduledExecutor.isShutdown()) {
            scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        
        int intervalMinutes = configManager.getSyncIntervalMinutes();
        
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                Log.d(TAG, "Automatic sync triggered");
                performSync(null); // No callback for automatic sync
            } catch (Exception e) {
                Log.e(TAG, "Error in automatic sync", e);
            }
        }, 1, intervalMinutes, TimeUnit.MINUTES); // Start after 1 minute, then repeat
        
        isAutoSyncRunning = true;
        Log.i(TAG, "Automatic sync started with " + intervalMinutes + " minute interval");
    }
    
    /**
     * Stop automatic syncing
     */
    public void stopAutoSync() {
        if (!isAutoSyncRunning) {
            Log.w(TAG, "Auto sync is not running");
            return;
        }
        
        Log.i(TAG, "Stopping automatic sync");
        
        if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
            scheduledExecutor.shutdown();
            try {
                if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        isAutoSyncRunning = false;
        Log.i(TAG, "Automatic sync stopped");
    }
    
    /**
     * Perform manual sync
     */
    public void performSync(SyncListener listener) {
        Log.i(TAG, "=== PERFORMING MANUAL SYNC ===");
        
        lastSyncAttempt = System.currentTimeMillis();
        
        if (!configManager.isConfigurationValid()) {
            Log.e(TAG, "Invalid configuration - cannot sync");
            lastErrorMessage = "Invalid configuration";
            if (listener != null) {
                mainHandler.post(() -> listener.onSyncFailed("Invalid configuration"));
            }
            return;
        }
        
        updateSyncServiceConfiguration();
        
        syncService.syncUnsyncedData(result -> {
            configManager.setLastSyncTimestamp(System.currentTimeMillis());
            
            if (result.success) {
                Log.i(TAG, "Sync completed successfully");
                Log.i(TAG, "Synced " + result.totalSynced + " items (" + 
                      result.syncedSessions + " sessions, " + result.syncedTexts + " texts)");
                lastErrorMessage = "";
                
                if (listener != null) {
                    mainHandler.post(() -> listener.onSyncSuccess(result));
                }
            } else {
                Log.e(TAG, "Sync failed: " + result.errorMessage);
                lastErrorMessage = result.errorMessage != null ? result.errorMessage : "Unknown sync error";
                
                if (listener != null) {
                    mainHandler.post(() -> listener.onSyncFailed(lastErrorMessage));
                }
            }
            
            Log.i(TAG, "Sync result: " + result.toString());
        });
    }
    
    /**
     * Configure for ngrok testing
     */
    public void configureForNgrokTesting(String ngrokUrl, String studyId) {
        Log.i(TAG, "=== CONFIGURING FOR NGROK TESTING ===");
        
        // Stop auto sync during reconfiguration
        boolean wasAutoSyncRunning = isAutoSyncRunning;
        if (isAutoSyncRunning) {
            stopAutoSync();
        }
        
        // Update configuration
        configManager.configureForNgrokTesting(ngrokUrl, studyId);
        
        // Update sync service
        updateSyncServiceConfiguration();
        
        // Recreate user profile for new study ID
        ensureUserProfileExists();
        
        // Restart auto sync if it was running
        if (wasAutoSyncRunning && configManager.isAutoSyncEnabled()) {
            startAutoSync();
        }
        
        Log.i(TAG, "Ngrok testing configuration complete");
        Log.i(TAG, configManager.getConfigurationSummary());
    }
    
    /**
     * Configure for local development
     */
    public void configureForLocalDevelopment(String localUrl, String studyId) {
        Log.i(TAG, "=== CONFIGURING FOR LOCAL DEVELOPMENT ===");
        
        // Stop auto sync during reconfiguration
        boolean wasAutoSyncRunning = isAutoSyncRunning;
        if (isAutoSyncRunning) {
            stopAutoSync();
        }
        
        // Update configuration
        configManager.configureForLocalDevelopment(localUrl, studyId);
        
        // Update sync service
        updateSyncServiceConfiguration();
        
        // Recreate user profile for new study ID
        ensureUserProfileExists();
        
        // Restart auto sync if it was running
        if (wasAutoSyncRunning && configManager.isAutoSyncEnabled()) {
            startAutoSync();
        }
        
        Log.i(TAG, "Local development configuration complete");
        Log.i(TAG, configManager.getConfigurationSummary());
    }
    
    /**
     * Get current status summary
     */
    public String getStatusSummary() {
        StringBuilder status = new StringBuilder();
        status.append("Dashboard Integration Status:\n");
        status.append("  Initialized: ").append(isInitialized).append("\n");
        status.append("  Auto sync running: ").append(isAutoSyncRunning).append("\n");
        status.append("  Last connection test: ").append(lastConnectionTestPassed ? "PASSED" : "FAILED").append("\n");
        
        if (lastSyncAttempt > 0) {
            long timeSinceSync = System.currentTimeMillis() - lastSyncAttempt;
            long minutesSinceSync = timeSinceSync / (1000 * 60);
            status.append("  Last sync attempt: ").append(minutesSinceSync).append(" minutes ago\n");
        } else {
            status.append("  Last sync attempt: Never\n");
        }
        
        if (!lastErrorMessage.isEmpty()) {
            status.append("  Last error: ").append(lastErrorMessage).append("\n");
        }
        
        status.append("\n").append(configManager.getConfigurationSummary());
        
        return status.toString();
    }
    
    /**
     * Get ngrok setup instructions
     */
    public String getNgrokSetupInstructions() {
        return configManager.getNgrokSetupInstructions();
    }
    
    /**
     * Shutdown integration manager
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down dashboard integration");
        
        stopAutoSync();
        
        if (syncService != null) {
            syncService.shutdown();
        }
        
        isInitialized = false;
        
        Log.i(TAG, "Dashboard integration shutdown complete");
    }
    
    // Listener interfaces
    public interface ConnectionTestListener {
        void onConnectionTestPassed(GdpbDashboardSyncService.ConnectionTestResult result);
        void onConnectionTestFailed(String error);
    }
    
    public interface SyncListener {
        void onSyncSuccess(GdpbDashboardSyncService.SyncResult result);
        void onSyncFailed(String error);
    }
    
    // Getters for status monitoring
    public boolean isInitialized() { return isInitialized; }
    public boolean isAutoSyncRunning() { return isAutoSyncRunning; }
    public boolean isLastConnectionTestPassed() { return lastConnectionTestPassed; }
    public long getLastSyncAttempt() { return lastSyncAttempt; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public DashboardConfigManager getConfigManager() { return configManager; }
}
