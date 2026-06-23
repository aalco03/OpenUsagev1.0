package com.openusage.app.TextBasedEventData;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * UserProfileManager for managing user profiles and study integration
 * Based on UsageStats application architecture
 */
public class UserProfileManager {
    private static final String TAG = "UserProfileManager";
    private static final String TABLE_USERS = "users";
    
    private final EventDatabaseHelper dbHelper;
    private static volatile UserProfileManager instance;
    
    private UserProfileManager(Context context) {
        this.dbHelper = EventDatabaseHelper.getInstance(context);
    }
    
    public static UserProfileManager getInstance(Context context) {
        if (instance == null) {
            synchronized (UserProfileManager.class) {
                if (instance == null) {
                    instance = new UserProfileManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }
    
    /**
     * Create or update user profile
     */
    public boolean createOrUpdateUserProfile(UserProfile userProfile) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            ContentValues values = new ContentValues();
            values.put("user_id", userProfile.getUserId());
            values.put("firebase_uid", userProfile.getFirebaseUid());
            values.put("email", userProfile.getEmail());
            values.put("study_code", userProfile.getStudyCode());
            values.put("study_id", userProfile.getStudyId());
            values.put("participant_number", userProfile.getParticipantNumber());
            values.put("install_code", userProfile.getInstallCode());
            values.put("device_id", userProfile.getDeviceId());
            values.put("registration_date", userProfile.getRegistrationDate());
            values.put("created_at", userProfile.getCreatedAt());
            values.put("last_login", userProfile.getLastLogin());
            values.put("last_active_date", userProfile.getLastActiveDate());
            values.put("is_active", userProfile.isActive() ? 1 : 0);
            values.put("consent_given", userProfile.isConsentGiven() ? 1 : 0);
            values.put("study_start_date", userProfile.getStudyStartDate());
            values.put("study_end_date", userProfile.getStudyEndDate());
            values.put("device_info", userProfile.getDeviceInfo());
            values.put("study_group", userProfile.getStudyGroup());
            
            // Try to update first, then insert if no rows affected
            int rowsUpdated = db.update(TABLE_USERS, values, "user_id = ?", 
                    new String[]{userProfile.getUserId()});
            
            if (rowsUpdated == 0) {
                long result = db.insert(TABLE_USERS, null, values);
                if (result > 0) {
                    Log.i(TAG, "Created new user profile: " + userProfile.getUserId());
                    return true;
                } else {
                    Log.e(TAG, "Failed to create user profile: " + userProfile.getUserId());
                    return false;
                }
            } else {
                Log.i(TAG, "Updated user profile: " + userProfile.getUserId());
                return true;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating/updating user profile", e);
            return false;
        }
    }
    
    /**
     * Get user profile by user ID
     */
    public UserProfile getUserProfile(String userId) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            
            Cursor cursor = db.query(TABLE_USERS, null, "user_id = ?", 
                    new String[]{userId}, null, null, null);
            
            if (cursor != null && cursor.moveToFirst()) {
                UserProfile profile = cursorToUserProfile(cursor);
                cursor.close();
                return profile;
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting user profile", e);
        }
        
        return null;
    }
    
    /**
     * Get all user profiles
     */
    public List<UserProfile> getAllUserProfiles() {
        List<UserProfile> profiles = new ArrayList<>();
        
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            
            Cursor cursor = db.query(TABLE_USERS, null, null, null, null, null, "created_at DESC");
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    profiles.add(cursorToUserProfile(cursor));
                }
                cursor.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting all user profiles", e);
        }
        
        return profiles;
    }
    
    /**
     * Update user's study ID for dashboard integration
     */
    public boolean updateStudyId(String userId, String studyId) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            ContentValues values = new ContentValues();
            values.put("study_id", studyId);
            values.put("last_active_date", System.currentTimeMillis());
            
            int rowsUpdated = db.update(TABLE_USERS, values, "user_id = ?", new String[]{userId});
            
            if (rowsUpdated > 0) {
                Log.i(TAG, "Updated study ID for user: " + userId + " -> " + studyId);
                return true;
            } else {
                Log.w(TAG, "No user found to update study ID: " + userId);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating study ID", e);
            return false;
        }
    }
    
    /**
     * Update user's last active date
     */
    public boolean updateLastActiveDate(String userId) {
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            ContentValues values = new ContentValues();
            values.put("last_active_date", System.currentTimeMillis());
            values.put("last_login", System.currentTimeMillis());
            
            int rowsUpdated = db.update(TABLE_USERS, values, "user_id = ?", new String[]{userId});
            
            return rowsUpdated > 0;
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating last active date", e);
            return false;
        }
    }
    
    /**
     * Get users by study ID (for dashboard integration)
     */
    public List<UserProfile> getUsersByStudyId(String studyId) {
        List<UserProfile> profiles = new ArrayList<>();
        
        try {
            dbHelper.openDatabase();
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            
            Cursor cursor = db.query(TABLE_USERS, null, "study_id = ?", 
                    new String[]{studyId}, null, null, "created_at DESC");
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    profiles.add(cursorToUserProfile(cursor));
                }
                cursor.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting users by study ID", e);
        }
        
        return profiles;
    }
    
    /**
     * Convert cursor to UserProfile object
     */
    private UserProfile cursorToUserProfile(Cursor cursor) {
        UserProfile profile = new UserProfile();
        
        profile.setUserId(cursor.getString(cursor.getColumnIndexOrThrow("user_id")));
        profile.setFirebaseUid(cursor.getString(cursor.getColumnIndexOrThrow("firebase_uid")));
        profile.setEmail(cursor.getString(cursor.getColumnIndexOrThrow("email")));
        profile.setStudyCode(cursor.getString(cursor.getColumnIndexOrThrow("study_code")));
        profile.setStudyId(cursor.getString(cursor.getColumnIndexOrThrow("study_id")));
        profile.setParticipantNumber(cursor.getString(cursor.getColumnIndexOrThrow("participant_number")));
        profile.setInstallCode(cursor.getString(cursor.getColumnIndexOrThrow("install_code")));
        profile.setDeviceId(cursor.getString(cursor.getColumnIndexOrThrow("device_id")));
        profile.setRegistrationDate(cursor.getLong(cursor.getColumnIndexOrThrow("registration_date")));
        profile.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
        profile.setLastLogin(cursor.getLong(cursor.getColumnIndexOrThrow("last_login")));
        profile.setLastActiveDate(cursor.getLong(cursor.getColumnIndexOrThrow("last_active_date")));
        profile.setActive(cursor.getInt(cursor.getColumnIndexOrThrow("is_active")) == 1);
        profile.setConsentGiven(cursor.getInt(cursor.getColumnIndexOrThrow("consent_given")) == 1);
        profile.setStudyStartDate(cursor.getLong(cursor.getColumnIndexOrThrow("study_start_date")));
        profile.setStudyEndDate(cursor.getLong(cursor.getColumnIndexOrThrow("study_end_date")));
        profile.setDeviceInfo(cursor.getString(cursor.getColumnIndexOrThrow("device_info")));
        profile.setStudyGroup(cursor.getString(cursor.getColumnIndexOrThrow("study_group")));
        
        return profile;
    }
}
