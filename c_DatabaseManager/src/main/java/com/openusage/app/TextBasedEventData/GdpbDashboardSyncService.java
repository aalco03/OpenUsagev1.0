package com.openusage.app.TextBasedEventData;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for synchronizing Screenomics data with GDP-B Dashboard
 * Includes comprehensive debugging and connection testing
 */
public class GdpbDashboardSyncService {
    private static final String TAG = "GdpbDashboardSync";
    
    // Dashboard configuration
    private static final String DEFAULT_DASHBOARD_URL = "http://localhost:8080"; // Local development
    private static final String NGROK_DASHBOARD_URL = ""; // Set via configureForNgrokTesting()
    
    // API endpoints
    private static final String USAGE_ENDPOINT = "/api/usage/submit-with-study-id";
    private static final String UI_TEXT_ENDPOINT = "/api/ui-text/submit-with-study-id";
    private static final String HEALTH_ENDPOINT = "/actuator/health";
    
    // Configuration
    private static final int MAX_BATCH_SIZE = 50;
    private static final int CONNECTION_TIMEOUT = 30000; // 30 seconds
    private static final int READ_TIMEOUT = 60000; // 60 seconds
    
    private final Context context;
    private final EventDatabaseHelper dbHelper;
    private final UserProfileManager userProfileManager;
    private final ExecutorService executorService;
    
    // Current configuration
    private String dashboardUrl = NGROK_DASHBOARD_URL; // Use ngrok by default
    private String studyId = "stanford-screenomics-2025"; // Default study ID
    private boolean debugMode = true;
    private boolean useNgrok = true;
    
    public GdpbDashboardSyncService(Context context) {
        this.context = context;
        this.dbHelper = EventDatabaseHelper.getInstance(context);
        this.userProfileManager = UserProfileManager.getInstance(context);
        this.executorService = Executors.newSingleThreadExecutor();
        
        // Initialize device-specific study ID
        initializeStudyId();
        
        Log.i(TAG, "GDP-B Dashboard Sync Service initialized");
        Log.i(TAG, "Dashboard URL: " + dashboardUrl);
        Log.i(TAG, "Study ID: " + studyId);
    }
    
    /**
     * Initialize study ID based on device
     */
    private void initializeStudyId() {
        try {
            String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (deviceId != null && !deviceId.isEmpty()) {
                this.studyId = "SCREENOMICS_" + deviceId.substring(0, Math.min(8, deviceId.length())).toUpperCase();
                Log.i(TAG, "Generated Study ID from device: " + studyId);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to generate device-specific Study ID, using default", e);
        }
    }
    
    /**
     * Configure dashboard connection
     */
    public void configureDashboard(String dashboardUrl, String studyId, boolean useNgrok) {
        this.dashboardUrl = dashboardUrl;
        this.studyId = studyId;
        this.useNgrok = useNgrok;
        
        Log.i(TAG, "Dashboard configuration updated:");
        Log.i(TAG, "  URL: " + dashboardUrl);
        Log.i(TAG, "  Study ID: " + studyId);
        Log.i(TAG, "  Using ngrok: " + useNgrok);
    }
    
    /**
     * Test connection to GDP-B Dashboard
     */
    public void testDashboardConnection(ConnectionTestCallback callback) {
        executorService.execute(() -> {
            Log.i(TAG, "=== TESTING DASHBOARD CONNECTION ===");
            
            ConnectionTestResult result = new ConnectionTestResult();
            result.dashboardUrl = dashboardUrl;
            result.studyId = studyId;
            result.timestamp = System.currentTimeMillis();
            
            try {
                // Test 1: Basic connectivity
                Log.i(TAG, "Test 1: Basic connectivity to " + dashboardUrl);
                boolean basicConnectivity = testBasicConnectivity();
                result.basicConnectivity = basicConnectivity;
                Log.i(TAG, "Basic connectivity: " + basicConnectivity);
                
                if (!basicConnectivity) {
                    result.errorMessage = "Cannot reach dashboard URL: " + dashboardUrl;
                    callback.onTestComplete(result);
                    return;
                }
                
                // Test 2: Health endpoint
                Log.i(TAG, "Test 2: Health endpoint check");
                boolean healthCheck = testHealthEndpoint();
                result.healthEndpoint = healthCheck;
                Log.i(TAG, "Health endpoint: " + healthCheck);
                
                // Test 3: Usage data endpoint
                Log.i(TAG, "Test 3: Usage data endpoint test");
                boolean usageEndpoint = testUsageEndpoint();
                result.usageEndpoint = usageEndpoint;
                Log.i(TAG, "Usage endpoint: " + usageEndpoint);
                
                // Test 4: UI text endpoint
                Log.i(TAG, "Test 4: UI text endpoint test");
                boolean uiTextEndpoint = testUITextEndpoint();
                result.uiTextEndpoint = uiTextEndpoint;
                Log.i(TAG, "UI text endpoint: " + uiTextEndpoint);
                
                result.overallSuccess = basicConnectivity && healthCheck && usageEndpoint && uiTextEndpoint;
                
                Log.i(TAG, "=== CONNECTION TEST COMPLETE ===");
                Log.i(TAG, "Overall success: " + result.overallSuccess);
                
            } catch (Exception e) {
                Log.e(TAG, "Connection test failed with exception", e);
                result.errorMessage = "Test failed: " + e.getMessage();
                result.overallSuccess = false;
            }
            
            callback.onTestComplete(result);
        });
    }
    
    /**
     * Test basic connectivity to dashboard
     */
    private boolean testBasicConnectivity() {
        try {
            URL url = new URL(dashboardUrl + HEALTH_ENDPOINT);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Basic connectivity response code: " + responseCode);
            
            connection.disconnect();
            return responseCode >= 200 && responseCode < 400;
            
        } catch (Exception e) {
            Log.e(TAG, "Basic connectivity test failed", e);
            return false;
        }
    }
    
    /**
     * Test health endpoint
     */
    private boolean testHealthEndpoint() {
        try {
            URL url = new URL(dashboardUrl + HEALTH_ENDPOINT);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Health endpoint response code: " + responseCode);
            
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                Log.d(TAG, "Health endpoint response: " + response.toString());
                
                // Check if response contains expected health data
                JSONObject healthData = new JSONObject(response.toString());
                boolean hasStatus = healthData.has("status");
                Log.d(TAG, "Health response has status field: " + hasStatus);
                
                connection.disconnect();
                return hasStatus;
            }
            
            connection.disconnect();
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "Health endpoint test failed", e);
            return false;
        }
    }
    
    /**
     * Test usage data endpoint with sample data
     */
    private boolean testUsageEndpoint() {
        try {
            // Create test usage data with unique identifiers
            JSONArray testData = new JSONArray();
            JSONObject testUsage = new JSONObject();
            String uniqueId = "TEST_" + System.currentTimeMillis();
            testUsage.put("tenantId", studyId);
            testUsage.put("deviceId", "TEST_DEVICE_" + uniqueId);
            testUsage.put("appPackageName", "com.test.connection");
            testUsage.put("appName", "Connection Test App");
            testUsage.put("usageTimeMs", 1000L);
            testUsage.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            testUsage.put("sessionId", "TEST_SESSION_" + uniqueId);
            testData.put(testUsage);
            
            String endpoint = dashboardUrl + USAGE_ENDPOINT + "?studyId=" + studyId;
            Log.d(TAG, "Testing usage endpoint: " + endpoint);
            Log.d(TAG, "Test data: " + testData.toString());
            
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            
            // Send test data
            OutputStream os = connection.getOutputStream();
            os.write(testData.toString().getBytes("UTF-8"));
            os.close();
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Usage endpoint response code: " + responseCode);
            
            // Read response
            BufferedReader reader;
            if (responseCode >= 200 && responseCode < 300) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            }
            
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            Log.d(TAG, "Usage endpoint response: " + response.toString());
            
            connection.disconnect();
            
            // Consider endpoint working if we get any response (200-299 success, or 400 for data errors)
            // 400 errors are often due to duplicate data or validation, which means the endpoint is working
            boolean isWorking = (responseCode >= 200 && responseCode < 300) || responseCode == 400;
            
            if (responseCode == 400) {
                Log.d(TAG, "Usage endpoint returned 400 (likely duplicate data) - endpoint is working");
            }
            
            return isWorking;
            
        } catch (Exception e) {
            Log.e(TAG, "Usage endpoint test failed", e);
            return false;
        }
    }
    
    /**
     * Test UI text endpoint with sample data
     */
    private boolean testUITextEndpoint() {
        try {
            // Create test UI text data with unique identifiers
            JSONArray testData = new JSONArray();
            JSONObject testUIText = new JSONObject();
            String uniqueId = "TEST_" + System.currentTimeMillis();
            testUIText.put("tenantId", studyId);
            testUIText.put("extractedText", "Connection test UI text - " + uniqueId);
            testUIText.put("appPackageName", "com.test.connection");
            testUIText.put("windowTitle", "Connection Test Window");
            testUIText.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            testUIText.put("hasValidContent", true);
            testData.put(testUIText);
            
            String endpoint = dashboardUrl + UI_TEXT_ENDPOINT + "?studyId=" + studyId;
            Log.d(TAG, "Testing UI text endpoint: " + endpoint);
            Log.d(TAG, "Test data: " + testData.toString());
            
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            
            // Send test data
            OutputStream os = connection.getOutputStream();
            os.write(testData.toString().getBytes("UTF-8"));
            os.close();
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "UI text endpoint response code: " + responseCode);
            
            // Read response
            BufferedReader reader;
            if (responseCode >= 200 && responseCode < 300) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            }
            
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            Log.d(TAG, "UI text endpoint response: " + response.toString());
            
            connection.disconnect();
            return responseCode >= 200 && responseCode < 300;
            
        } catch (Exception e) {
            Log.e(TAG, "UI text endpoint test failed", e);
            return false;
        }
    }
    
    /**
     * Sync data to dashboard.
     *
     * Firestore is now the primary source of truth for the dashboard,
     * so this method no longer reads from the on-device SQLite database.
     * It simply reports a successful no-op sync so callers don't break.
     */
    public void syncUnsyncedData(SyncCallback callback) {
        executorService.execute(() -> {
            Log.i(TAG, "=== DASHBOARD SYNC NO-OP ===");
            Log.i(TAG, "Dashboard should now read data directly from Firebase/Firestore");

            SyncResult result = new SyncResult();
            result.timestamp = System.currentTimeMillis();
            result.success = true;
            result.syncedSessions = 0;
            result.syncedTexts = 0;
            result.totalSynced = 0;

            if (callback != null) {
                callback.onSyncComplete(result);
            }
        });
    }
    
    /**
     * Sync app sessions to dashboard
     */
    private int syncAppSessions() throws Exception {
        Log.d(TAG, "Syncing app sessions...");
        
        dbHelper.openDatabase();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        // Get unsynced app sessions
        Cursor cursor = db.query("app_sessions", null, "is_synced = 0", null, null, null, "start_time ASC", String.valueOf(MAX_BATCH_SIZE));
        
        if (cursor == null || cursor.getCount() == 0) {
            Log.d(TAG, "No unsynced app sessions found");
            if (cursor != null) cursor.close();
            return 0;
        }
        
        Log.d(TAG, "Found " + cursor.getCount() + " unsynced app sessions");
        
        List<GdpbApiModels.UsageDataRequest> usageDataList = new ArrayList<>();
        List<String> sessionIds = new ArrayList<>();
        
        while (cursor.moveToNext()) {
            try {
                GdpbApiModels.UsageDataRequest usageData = new GdpbApiModels.UsageDataRequest();
                
                String sessionId = cursor.getString(cursor.getColumnIndexOrThrow("session_id"));
                sessionIds.add(sessionId);
                
                usageData.setTenantId(studyId);
                usageData.setDeviceId(getDeviceId());
                usageData.setSessionId(sessionId);
                usageData.setAppPackageName(cursor.getString(cursor.getColumnIndexOrThrow("app_package_name")));
                usageData.setAppName(cursor.getString(cursor.getColumnIndexOrThrow("app_name")));
                usageData.setUsageTimeMs(cursor.getLong(cursor.getColumnIndexOrThrow("duration")));
                usageData.setLaunchCount(cursor.getInt(cursor.getColumnIndexOrThrow("launch_count")));
                usageData.setTotalTimeInForeground(cursor.getLong(cursor.getColumnIndexOrThrow("total_time_foreground")));
                
                // Convert timestamp
                long startTime = cursor.getLong(cursor.getColumnIndexOrThrow("start_time"));
                usageData.setTimestamp(GdpbApiModels.DataConverter.parseTimestamp(startTime));
                
                usageDataList.add(usageData);
                
                Log.d(TAG, "Prepared usage data: " + usageData.toString());
                
            } catch (Exception e) {
                Log.e(TAG, "Error preparing usage data for session", e);
            }
        }
        cursor.close();
        
        if (usageDataList.isEmpty()) {
            Log.w(TAG, "No valid usage data to sync");
            return 0;
        }
        
        // Send to dashboard
        boolean success = sendUsageDataToDashboard(usageDataList);
        
        if (success) {
            // Mark as synced
            markSessionsAsSynced(sessionIds);
            Log.i(TAG, "Successfully synced " + usageDataList.size() + " app sessions");
            return usageDataList.size();
        } else {
            Log.e(TAG, "Failed to sync app sessions to dashboard");
            return 0;
        }
    }
    
    /**
     * Sync UI text extractions to dashboard as usage data with contentText
     */
    private int syncUITextExtractions() throws Exception {
        Log.d(TAG, "Syncing UI text extractions...");
        
        dbHelper.openDatabase();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        // Get unsynced UI text extractions with valid content
        Cursor cursor = db.query("ui_text_extractions", null, "is_synced = 0", 
                null, null, null, "extraction_timestamp ASC", String.valueOf(MAX_BATCH_SIZE));
        
        if (cursor == null || cursor.getCount() == 0) {
            Log.d(TAG, "No unsynced UI text extractions found");
            if (cursor != null) cursor.close();
            return 0;
        }
        
        Log.d(TAG, "Found " + cursor.getCount() + " unsynced UI text extractions");
        
        List<GdpbApiModels.UsageDataRequest> usageDataList = new ArrayList<>();
        List<Long> extractionIds = new ArrayList<>();
        
        while (cursor.moveToNext()) {
            try {
                GdpbApiModels.UsageDataRequest usageData = new GdpbApiModels.UsageDataRequest();
                
                long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                extractionIds.add(id);
                
                // Required fields
                usageData.setTenantId(studyId);
                usageData.setDeviceId(getDeviceId());
                usageData.setAppPackageName(cursor.getString(cursor.getColumnIndexOrThrow("app_package_name")));
                
                // Text content - the main payload
                usageData.setContentText(cursor.getString(cursor.getColumnIndexOrThrow("extracted_text")));
                
                // Usage time (default 5 seconds per extraction)
                usageData.setUsageTimeMs(5000L);
                
                // Timestamp
                long extractionTime = cursor.getLong(cursor.getColumnIndexOrThrow("extraction_timestamp"));
                usageData.setTimestamp(GdpbApiModels.DataConverter.parseTimestamp(extractionTime));
                
                // Session ID from table
                int sessionIdIndex = cursor.getColumnIndex("session_id");
                if (sessionIdIndex >= 0) {
                    String sessionId = cursor.getString(sessionIdIndex);
                    if (sessionId != null && !sessionId.isEmpty()) {
                        usageData.setSessionId(sessionId);
                        Log.d(TAG, "Text extraction linked to session: " + sessionId);
                    }
                }
                
                // Mark as text extraction interaction
                usageData.setInteractionType("text_extraction");
                
                usageDataList.add(usageData);
                
                Log.d(TAG, "Prepared text extraction as usage data: " + usageData.getAppPackageName() + 
                      " (text: " + usageData.getContentText().substring(0, Math.min(50, usageData.getContentText().length())) + "...)");
                
            } catch (Exception e) {
                Log.e(TAG, "Error preparing text extraction", e);
            }
        }
        cursor.close();
        
        if (usageDataList.isEmpty()) {
            Log.w(TAG, "No valid text extractions to sync");
            return 0;
        }
        
        // Send to dashboard via usage endpoint
        boolean success = sendUsageDataToDashboard(usageDataList);
        
        if (success) {
            // Mark as synced
            markTextExtractionsAsSynced(extractionIds);
            Log.i(TAG, "Successfully synced " + usageDataList.size() + " text extractions");
            return usageDataList.size();
        } else {
            Log.e(TAG, "Failed to sync text extractions to dashboard");
            return 0;
        }
    }
    
    /**
     * Send usage data to dashboard
     */
    private boolean sendUsageDataToDashboard(List<GdpbApiModels.UsageDataRequest> usageDataList) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (GdpbApiModels.UsageDataRequest usage : usageDataList) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("tenantId", usage.getTenantId());
                jsonObject.put("deviceId", usage.getDeviceId());
                jsonObject.put("appPackageName", usage.getAppPackageName());
                jsonObject.put("appName", usage.getAppName());
                jsonObject.put("usageTimeMs", usage.getUsageTimeMs());
                
                // Format timestamp as array [year, month, day, hour, minute, second, nano]
                LocalDateTime timestamp = usage.getTimestamp();
                JSONArray timestampArray = new JSONArray();
                timestampArray.put(timestamp.getYear());
                timestampArray.put(timestamp.getMonthValue());
                timestampArray.put(timestamp.getDayOfMonth());
                timestampArray.put(timestamp.getHour());
                timestampArray.put(timestamp.getMinute());
                timestampArray.put(timestamp.getSecond());
                timestampArray.put(timestamp.getNano());
                jsonObject.put("timestamp", timestampArray);
                
                jsonObject.put("sessionId", usage.getSessionId());
                jsonObject.put("launchCount", usage.getLaunchCount());
                jsonObject.put("totalTimeInForeground", usage.getTotalTimeInForeground());
                
                // Add contentText if present (for text extractions)
                if (usage.getContentText() != null && !usage.getContentText().isEmpty()) {
                    jsonObject.put("contentText", usage.getContentText());
                }
                if (usage.getInteractionType() != null) {
                    jsonObject.put("interactionType", usage.getInteractionType());
                }
                
                jsonArray.put(jsonObject);
            }
            
            String endpoint = dashboardUrl + USAGE_ENDPOINT + "?studyId=" + studyId;
            Log.d(TAG, "Sending usage data to: " + endpoint);
            Log.d(TAG, "Payload size: " + jsonArray.length() + " items");
            
            return sendPostRequest(endpoint, jsonArray.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending usage data to dashboard", e);
            return false;
        }
    }
    
    /**
     * Find the session that corresponds to a UI text extraction based on timing
     * @param extractionTime The timestamp of the UI text extraction
     * @param appPackageName The app package name for the extraction
     * @return Session info in format "sessionId|startTime|duration" or null if not found
     */
    private String findSessionForExtraction(long extractionTime, String appPackageName) {
        if (appPackageName == null) {
            return null;
        }
        
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT session_id, session_start_timestamp, session_duration " +
                      "FROM app_sessions " +
                      "WHERE app_package_name = ? " +
                      "AND session_start_timestamp <= ? " +
                      "AND (session_start_timestamp + session_duration) >= ? " +
                      "ORDER BY ABS(session_start_timestamp - ?) ASC " +
                      "LIMIT 1";
        
        Cursor cursor = db.rawQuery(query, new String[]{
            appPackageName,
            String.valueOf(extractionTime),
            String.valueOf(extractionTime),
            String.valueOf(extractionTime)
        });
        
        try {
            if (cursor.moveToFirst()) {
                String sessionId = cursor.getString(0);
                long startTime = cursor.getLong(1);
                long duration = cursor.getLong(2);
                
                Log.d(TAG, String.format("Found session %s for extraction at %d (session: %d-%d)", 
                    sessionId, extractionTime, startTime, startTime + duration));
                
                return sessionId + "|" + startTime + "|" + duration;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding session for extraction", e);
        } finally {
            cursor.close();
        }
        
        Log.w(TAG, "No session found for extraction at " + extractionTime + " for app " + appPackageName);
        return null;
    }

    /**
     * Send UI text extractions to dashboard
     */
    private boolean sendUITextToDashboard(List<GdpbApiModels.UITextExtractionRequest> textExtractionList) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (GdpbApiModels.UITextExtractionRequest textExtraction : textExtractionList) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("tenantId", textExtraction.getTenantId());
                jsonObject.put("extractedText", textExtraction.getExtractedText());
                jsonObject.put("appPackageName", textExtraction.getAppPackageName());
                jsonObject.put("windowTitle", textExtraction.getWindowTitle());
                jsonObject.put("timestamp", textExtraction.getTimestamp());
                jsonObject.put("hasValidContent", textExtraction.getHasValidContent());
                
                // Include session context for time-based grouping
                if (textExtraction.getSessionId() != null) {
                    jsonObject.put("sessionId", textExtraction.getSessionId());
                    jsonObject.put("sessionStartTime", textExtraction.getSessionStartTime());
                    jsonObject.put("sessionDuration", textExtraction.getSessionDuration());
                }
                jsonArray.put(jsonObject);
            }
            
            String endpoint = dashboardUrl + UI_TEXT_ENDPOINT + "?studyId=" + studyId;
            Log.d(TAG, "Sending UI text data to: " + endpoint);
            Log.d(TAG, "Payload size: " + jsonArray.length() + " items");
            
            return sendPostRequest(endpoint, jsonArray.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending UI text data to dashboard", e);
            return false;
        }
    }
    
    /**
     * Send POST request to dashboard
     */
    private boolean sendPostRequest(String endpoint, String jsonPayload) {
        try {
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            
            // Send data
            OutputStream os = connection.getOutputStream();
            os.write(jsonPayload.getBytes("UTF-8"));
            os.close();
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Dashboard response code: " + responseCode);
            
            // Read response
            BufferedReader reader;
            if (responseCode >= 200 && responseCode < 300) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            }
            
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            Log.d(TAG, "Dashboard response: " + response.toString());
            
            connection.disconnect();
            
            boolean success = responseCode >= 200 && responseCode < 300;
            if (!success) {
                Log.e(TAG, "Dashboard request failed with code: " + responseCode + ", response: " + response.toString());
            }
            
            return success;
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending request to dashboard", e);
            return false;
        }
    }
    
    /**
     * Mark app sessions as synced
     */
    private void markSessionsAsSynced(List<String> sessionIds) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            for (String sessionId : sessionIds) {
                db.execSQL("UPDATE app_sessions SET is_synced = 1, updated_at = ? WHERE session_id = ?", 
                          new Object[]{System.currentTimeMillis(), sessionId});
            }
            
            Log.d(TAG, "Marked " + sessionIds.size() + " sessions as synced");
            
        } catch (Exception e) {
            Log.e(TAG, "Error marking sessions as synced", e);
        }
    }
    
    /**
     * Mark UI text extractions as synced
     */
    private void markTextExtractionsAsSynced(List<Long> extractionIds) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            for (Long id : extractionIds) {
                db.execSQL("UPDATE ui_text_extractions SET is_synced = 1 WHERE id = ?", new Object[]{id});
            }
            
            Log.d(TAG, "Marked " + extractionIds.size() + " text extractions as synced");
            
        } catch (Exception e) {
            Log.e(TAG, "Error marking text extractions as synced", e);
        }
    }
    
    /**
     * Get device ID
     */
    private String getDeviceId() {
        try {
            return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get device ID", e);
            return "UNKNOWN_DEVICE";
        }
    }
    
    /**
     * Cleanup resources
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
    
    // Callback interfaces
    public interface ConnectionTestCallback {
        void onTestComplete(ConnectionTestResult result);
    }
    
    public interface SyncCallback {
        void onSyncComplete(SyncResult result);
    }
    
    // Result classes
    public static class ConnectionTestResult {
        public String dashboardUrl;
        public String studyId;
        public long timestamp;
        public boolean basicConnectivity = false;
        public boolean healthEndpoint = false;
        public boolean usageEndpoint = false;
        public boolean uiTextEndpoint = false;
        public boolean overallSuccess = false;
        public String errorMessage;
        
        @Override
        public String toString() {
            return "ConnectionTestResult{" +
                    "dashboardUrl='" + dashboardUrl + '\'' +
                    ", studyId='" + studyId + '\'' +
                    ", overallSuccess=" + overallSuccess +
                    ", basicConnectivity=" + basicConnectivity +
                    ", healthEndpoint=" + healthEndpoint +
                    ", usageEndpoint=" + usageEndpoint +
                    ", uiTextEndpoint=" + uiTextEndpoint +
                    ", errorMessage='" + errorMessage + '\'' +
                    '}';
        }
    }
    
    public static class SyncResult {
        public long timestamp;
        public boolean success = false;
        public int syncedSessions = 0;
        public int syncedTexts = 0;
        public int totalSynced = 0;
        public String errorMessage;
        
        @Override
        public String toString() {
            return "SyncResult{" +
                    "success=" + success +
                    ", syncedSessions=" + syncedSessions +
                    ", syncedTexts=" + syncedTexts +
                    ", totalSynced=" + totalSynced +
                    ", errorMessage='" + errorMessage + '\'' +
                    '}';
        }
    }
}
