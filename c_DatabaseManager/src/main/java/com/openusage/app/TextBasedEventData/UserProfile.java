package com.openusage.app.TextBasedEventData;

/**
 * UserProfile entity for study management and dashboard integration
 * Based on UsageStats application architecture
 */
public class UserProfile {
    private String userId;
    private String firebaseUid;
    private String email;
    private String studyCode;
    private String studyId; // For dashboard integration (tenantId)
    private String participantNumber;
    private String installCode;
    private String deviceId; // Device identifier for dashboard
    private long registrationDate; // UsageStats compatibility
    private long createdAt;
    private long lastLogin;
    private long lastActiveDate; // UsageStats compatibility
    private boolean isActive;
    private boolean consentGiven;
    private long studyStartDate;
    private long studyEndDate;
    private String deviceInfo;
    private String studyGroup; // For study management
    
    // Constructors
    public UserProfile() {
        this.createdAt = System.currentTimeMillis();
        this.registrationDate = System.currentTimeMillis();
        this.lastActiveDate = System.currentTimeMillis();
        this.isActive = true;
        this.consentGiven = false;
    }
    
    public UserProfile(String userId, String email, String studyCode, String deviceId) {
        this();
        this.userId = userId;
        this.email = email;
        this.studyCode = studyCode;
        this.deviceId = deviceId;
    }
    
    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getFirebaseUid() { return firebaseUid; }
    public void setFirebaseUid(String firebaseUid) { this.firebaseUid = firebaseUid; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getStudyCode() { return studyCode; }
    public void setStudyCode(String studyCode) { this.studyCode = studyCode; }
    
    public String getStudyId() { return studyId; }
    public void setStudyId(String studyId) { this.studyId = studyId; }
    
    public String getParticipantNumber() { return participantNumber; }
    public void setParticipantNumber(String participantNumber) { this.participantNumber = participantNumber; }
    
    public String getInstallCode() { return installCode; }
    public void setInstallCode(String installCode) { this.installCode = installCode; }
    
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    
    public long getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(long registrationDate) { this.registrationDate = registrationDate; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getLastLogin() { return lastLogin; }
    public void setLastLogin(long lastLogin) { this.lastLogin = lastLogin; }
    
    public long getLastActiveDate() { return lastActiveDate; }
    public void setLastActiveDate(long lastActiveDate) { this.lastActiveDate = lastActiveDate; }
    
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    
    public boolean isConsentGiven() { return consentGiven; }
    public void setConsentGiven(boolean consentGiven) { this.consentGiven = consentGiven; }
    
    public long getStudyStartDate() { return studyStartDate; }
    public void setStudyStartDate(long studyStartDate) { this.studyStartDate = studyStartDate; }
    
    public long getStudyEndDate() { return studyEndDate; }
    public void setStudyEndDate(long studyEndDate) { this.studyEndDate = studyEndDate; }
    
    public String getDeviceInfo() { return deviceInfo; }
    public void setDeviceInfo(String deviceInfo) { this.deviceInfo = deviceInfo; }
    
    public String getStudyGroup() { return studyGroup; }
    public void setStudyGroup(String studyGroup) { this.studyGroup = studyGroup; }
    
    @Override
    public String toString() {
        return "UserProfile{" +
                "userId='" + userId + '\'' +
                ", email='" + email + '\'' +
                ", studyCode='" + studyCode + '\'' +
                ", studyId='" + studyId + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}
