package com.openusage.app.TextBasedEventData;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * API models for GDP-B Dashboard integration
 * Matches the expected request format for the dashboard endpoints
 */
public class GdpbApiModels {
    
    /**
     * Usage data request model for GDP-B dashboard
     * Matches UsageDataRequest DTO in dashboard backend
     */
    public static class UsageDataRequest {
        private String tenantId; // Study ID
        private Long userId; // Optional, null for participant data
        private String deviceId;
        private String appPackageName;
        private String appName;
        private String category;
        private Long usageTimeMs;
        private LocalDateTime timestamp;
        private LocalDateTime lastTimeUsed;
        private LocalDateTime firstTimeStamp;
        private Integer launchCount;
        private Long totalTimeInForeground;
        private String sessionId;
        private String interactionType;
        private Double screenTimeMinutes;
        private Double productivityScore;
        private Double economicValue;
        private String contentText; // UI text content for NLP analysis
        
        // Constructors
        public UsageDataRequest() {}
        
        public UsageDataRequest(String tenantId, String deviceId, String appPackageName, 
                               Long usageTimeMs, LocalDateTime timestamp) {
            this.tenantId = tenantId;
            this.deviceId = deviceId;
            this.appPackageName = appPackageName;
            this.usageTimeMs = usageTimeMs;
            this.timestamp = timestamp;
        }
        
        // Getters and Setters
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        
        public String getAppPackageName() { return appPackageName; }
        public void setAppPackageName(String appPackageName) { this.appPackageName = appPackageName; }
        
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        
        public Long getUsageTimeMs() { return usageTimeMs; }
        public void setUsageTimeMs(Long usageTimeMs) { this.usageTimeMs = usageTimeMs; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public LocalDateTime getLastTimeUsed() { return lastTimeUsed; }
        public void setLastTimeUsed(LocalDateTime lastTimeUsed) { this.lastTimeUsed = lastTimeUsed; }
        
        public LocalDateTime getFirstTimeStamp() { return firstTimeStamp; }
        public void setFirstTimeStamp(LocalDateTime firstTimeStamp) { this.firstTimeStamp = firstTimeStamp; }
        
        public Integer getLaunchCount() { return launchCount; }
        public void setLaunchCount(Integer launchCount) { this.launchCount = launchCount; }
        
        public Long getTotalTimeInForeground() { return totalTimeInForeground; }
        public void setTotalTimeInForeground(Long totalTimeInForeground) { this.totalTimeInForeground = totalTimeInForeground; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public String getInteractionType() { return interactionType; }
        public void setInteractionType(String interactionType) { this.interactionType = interactionType; }
        
        public Double getScreenTimeMinutes() { return screenTimeMinutes; }
        public void setScreenTimeMinutes(Double screenTimeMinutes) { this.screenTimeMinutes = screenTimeMinutes; }
        
        public Double getProductivityScore() { return productivityScore; }
        public void setProductivityScore(Double productivityScore) { this.productivityScore = productivityScore; }
        
        public Double getEconomicValue() { return economicValue; }
        public void setEconomicValue(Double economicValue) { this.economicValue = economicValue; }
        
        public String getContentText() { return contentText; }
        public void setContentText(String contentText) { this.contentText = contentText; }
        
        @Override
        public String toString() {
            return "UsageDataRequest{" +
                    "tenantId='" + tenantId + '\'' +
                    ", deviceId='" + deviceId + '\'' +
                    ", appPackageName='" + appPackageName + '\'' +
                    ", sessionId='" + sessionId + '\'' +
                    ", usageTimeMs=" + usageTimeMs +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
    
    /**
     * UI Text Extraction request model for GDP-B dashboard
     * Matches UITextExtractionRequest in dashboard backend
     */
    public static class UITextExtractionRequest {
        private String tenantId; // Study ID
        private Long participantId; // Will be set by backend
        private String extractedText;
        private String appPackageName;
        private String windowTitle;
        private String timestamp; // ISO string format
        private Boolean hasValidContent;
        private String sessionId; // Session ID for grouping
        private Long sessionStartTime; // Session start timestamp (ms)
        private Long sessionDuration; // Session duration (ms)
        
        // Constructors
        public UITextExtractionRequest() {}
        
        public UITextExtractionRequest(String tenantId, String extractedText, 
                                     String appPackageName, String timestamp) {
            this.tenantId = tenantId;
            this.extractedText = extractedText;
            this.appPackageName = appPackageName;
            this.timestamp = timestamp;
            this.hasValidContent = extractedText != null && !extractedText.trim().isEmpty();
        }
        
        // Getters and Setters
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        
        public Long getParticipantId() { return participantId; }
        public void setParticipantId(Long participantId) { this.participantId = participantId; }
        
        public String getExtractedText() { return extractedText; }
        public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
        
        public String getAppPackageName() { return appPackageName; }
        public void setAppPackageName(String appPackageName) { this.appPackageName = appPackageName; }
        
        public String getWindowTitle() { return windowTitle; }
        public void setWindowTitle(String windowTitle) { this.windowTitle = windowTitle; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        
        public Boolean getHasValidContent() { return hasValidContent; }
        public void setHasValidContent(Boolean hasValidContent) { this.hasValidContent = hasValidContent; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public Long getSessionStartTime() { return sessionStartTime; }
        public void setSessionStartTime(Long sessionStartTime) { this.sessionStartTime = sessionStartTime; }
        
        public Long getSessionDuration() { return sessionDuration; }
        public void setSessionDuration(Long sessionDuration) { this.sessionDuration = sessionDuration; }
        
        @Override
        public String toString() {
            return "UITextExtractionRequest{" +
                    "tenantId='" + tenantId + '\'' +
                    ", appPackageName='" + appPackageName + '\'' +
                    ", hasValidContent=" + hasValidContent +
                    ", textLength=" + (extractedText != null ? extractedText.length() : 0) +
                    ", timestamp='" + timestamp + '\'' +
                    '}';
        }
    }
    
    /**
     * API Response models
     */
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;
        private int count;
        
        public ApiResponse() {}
        
        public ApiResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public T getData() { return data; }
        public void setData(T data) { this.data = data; }
        
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }
    
    /**
     * Utility class for converting between Screenomics data and GDP-B API format
     */
    public static class DataConverter {
        private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        /**
         * Convert timestamp to LocalDateTime
         */
        public static LocalDateTime parseTimestamp(long timestampMs) {
            return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestampMs),
                java.time.ZoneId.systemDefault()
            );
        }
        
        /**
         * Convert LocalDateTime to ISO string
         */
        public static String formatTimestamp(LocalDateTime dateTime) {
            return dateTime.format(ISO_FORMATTER);
        }
        
        /**
         * Convert timestamp milliseconds to ISO string
         */
        public static String formatTimestamp(long timestampMs) {
            return formatTimestamp(parseTimestamp(timestampMs));
        }
    }
}
