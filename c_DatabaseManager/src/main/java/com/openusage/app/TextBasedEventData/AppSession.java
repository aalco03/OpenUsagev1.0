package com.openusage.app.TextBasedEventData;

/**
 * AppSession - Data model for app usage sessions
 * 
 * This class represents an app usage session with all associated metadata.
 * It provides a structured way to work with session data retrieved from
 * the database.
 */
public class AppSession {
    private long id;
    private String sessionId;
    private String appPackageName;
    private String appName;
    private long startTime;
    private long endTime;
    private long duration;
    private boolean isActive;
    private String deviceOrientation;
    private Integer batteryLevel;
    private String networkType;
    private long createdAt;
    
    // Default constructor
    public AppSession() {
    }
    
    // Full constructor
    public AppSession(long id, String sessionId, String appPackageName, String appName,
                     long startTime, long endTime, long duration, boolean isActive,
                     String deviceOrientation, Integer batteryLevel, String networkType, long createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.appPackageName = appPackageName;
        this.appName = appName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = duration;
        this.isActive = isActive;
        this.deviceOrientation = deviceOrientation;
        this.batteryLevel = batteryLevel;
        this.networkType = networkType;
        this.createdAt = createdAt;
    }
    
    // Builder pattern for easy construction
    public static class Builder {
        private AppSession session = new AppSession();
        
        public Builder setId(long id) {
            session.id = id;
            return this;
        }
        
        public Builder setSessionId(String sessionId) {
            session.sessionId = sessionId;
            return this;
        }
        
        public Builder setAppPackageName(String appPackageName) {
            session.appPackageName = appPackageName;
            return this;
        }
        
        public Builder setAppName(String appName) {
            session.appName = appName;
            return this;
        }
        
        public Builder setStartTime(long startTime) {
            session.startTime = startTime;
            return this;
        }
        
        public Builder setEndTime(long endTime) {
            session.endTime = endTime;
            return this;
        }
        
        public Builder setDuration(long duration) {
            session.duration = duration;
            return this;
        }
        
        public Builder setIsActive(boolean isActive) {
            session.isActive = isActive;
            return this;
        }
        
        public Builder setDeviceOrientation(String deviceOrientation) {
            session.deviceOrientation = deviceOrientation;
            return this;
        }
        
        public Builder setBatteryLevel(Integer batteryLevel) {
            session.batteryLevel = batteryLevel;
            return this;
        }
        
        public Builder setNetworkType(String networkType) {
            session.networkType = networkType;
            return this;
        }
        
        public Builder setCreatedAt(long createdAt) {
            session.createdAt = createdAt;
            return this;
        }
        
        public AppSession build() {
            return session;
        }
    }
    
    // Getters and Setters
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getAppPackageName() {
        return appPackageName;
    }
    
    public void setAppPackageName(String appPackageName) {
        this.appPackageName = appPackageName;
    }
    
    public String getAppName() {
        return appName;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public long getEndTime() {
        return endTime;
    }
    
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
    
    public long getDuration() {
        return duration;
    }
    
    public void setDuration(long duration) {
        this.duration = duration;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    public String getDeviceOrientation() {
        return deviceOrientation;
    }
    
    public void setDeviceOrientation(String deviceOrientation) {
        this.deviceOrientation = deviceOrientation;
    }
    
    public Integer getBatteryLevel() {
        return batteryLevel;
    }
    
    public void setBatteryLevel(Integer batteryLevel) {
        this.batteryLevel = batteryLevel;
    }
    
    public String getNetworkType() {
        return networkType;
    }
    
    public void setNetworkType(String networkType) {
        this.networkType = networkType;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    
    // Utility methods
    public boolean isSessionEnded() {
        return !isActive && endTime > 0;
    }
    
    public long calculateDuration() {
        if (isActive) {
            return System.currentTimeMillis() - startTime;
        } else {
            return duration;
        }
    }
    
    public String getDurationFormatted() {
        long durationMs = calculateDuration();
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    @Override
    public String toString() {
        return "AppSession{" +
                "id=" + id +
                ", sessionId='" + sessionId + '\'' +
                ", appPackageName='" + appPackageName + '\'' +
                ", appName='" + appName + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", duration=" + duration +
                ", isActive=" + isActive +
                ", deviceOrientation='" + deviceOrientation + '\'' +
                ", batteryLevel=" + batteryLevel +
                ", networkType='" + networkType + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        AppSession that = (AppSession) o;
        
        return sessionId != null ? sessionId.equals(that.sessionId) : that.sessionId == null;
    }
    
    @Override
    public int hashCode() {
        return sessionId != null ? sessionId.hashCode() : 0;
    }
}
