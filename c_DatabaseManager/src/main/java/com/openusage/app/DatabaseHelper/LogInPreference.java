package com.openusage.app.DatabaseHelper;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;


/**
 * May 7, 2025 this file is to save the user login related data
 *
 * // Add prefix means This is use to add the data into the shared pref
 * // Get prefix means this is use to get data from the shared pref
 *
 */

public class LogInPreference {

    private Context context;

    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;

    private final String SharedPref = "user_login_data";

    private final String user_subj_id = "user_subj_id";
    private final String user_password = "user_password";
    private final String user_code = "user_code";
    private final String user_email = "user_email";
    private final String user_number = "user_number";
    private final String install_code = "install_code";
    private final String study_group = "study_group";
    
    // Demographic data keys
    private final String age_range = "age_range";
    private final String gender = "gender";
    private final String location = "location";
    private final String income_level = "income_level";
    private final String usage_level = "usage_level";


    public LogInPreference(Context context) {
        this.context = context;
    }

    public void clearAllPreferences() {
        // Get the default shared preferences
        SharedPreferences sharedPreferences = context.getSharedPreferences(SharedPref, Context.MODE_PRIVATE);

        // Clear all the preferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }

    public void AddSHARED_PREF_PREFIXSetting(String key,int value){
        editor = context.getSharedPreferences(SharedPref, MODE_PRIVATE).edit();
        editor.putInt(key,value);
        editor.apply();
    }


    // This function stores the user id

    public void AddUserSubjId(String value){
        editor = context.getSharedPreferences(SharedPref, MODE_PRIVATE).edit();
        editor.putString(user_subj_id,value);
        editor.apply();
    }

    public void AddInstallCode(String value){
        editor = context.getSharedPreferences(SharedPref, MODE_PRIVATE).edit();
        editor.putString(install_code,value);
        editor.apply();
    }

    // This function stores the user password
    public void AddUserPassword(String value){
        editor = context.getSharedPreferences(SharedPref, MODE_PRIVATE).edit();
        editor.putString(user_password,value);
        editor.apply();
    }

    public void AddUserCode(String value){
        editor = context.getSharedPreferences(SharedPref, MODE_PRIVATE).edit();
        editor.putString(user_code,value);
        editor.apply();
    }

    public void AddUserNumber(String value){
        editor = context.getSharedPreferences(SharedPref, MODE_PRIVATE).edit();
        editor.putString(user_number,value);
        editor.apply();
    }

    public void AddUserEmail(String value){
        editor = context.getSharedPreferences(SharedPref, MODE_PRIVATE).edit();
        editor.putString(user_email,value);
        editor.apply();
    }

    // This function is used to retrieve user id from shared pref
    public String GetUserSubjId(){
        prefs = context.getSharedPreferences(SharedPref, MODE_PRIVATE);
        return prefs.getString(user_subj_id,"");
    }

    // This function is used to retrieve user password from shared pref

    public String GetUserPassword(){
        prefs = context.getSharedPreferences(SharedPref, MODE_PRIVATE);
        return prefs.getString(user_password,"");
    }

    public String GetUserEmail(){
        prefs = context.getSharedPreferences(SharedPref, MODE_PRIVATE);
        return prefs.getString(user_email,"");
    }

    public String GetInstallCode(){
        prefs = context.getSharedPreferences(SharedPref, MODE_PRIVATE);
        return prefs.getString(install_code,"");
    }

    public String GetUserCode(){
        prefs = context.getSharedPreferences(SharedPref, MODE_PRIVATE);
        return prefs.getString(user_code,"");
    }

    public String GetUserNumber(){
        prefs = context.getSharedPreferences(SharedPref, MODE_PRIVATE);
        return prefs.getString(user_number,"");
    }

    public void AddStudyGroup(String value){
        editor = context.getSharedPreferences(SharedPref, MODE_PRIVATE).edit();
        editor.putString(study_group, value);
        editor.apply();
    }

    public String GetStudyGroup(){
        prefs = context.getSharedPreferences(SharedPref, MODE_PRIVATE);
        return prefs.getString(study_group, "DEFAULT");
    }

    public Map<String, ?> SharedPrefGetAll(){
        prefs = context.getSharedPreferences(SharedPref, MODE_PRIVATE);
        return prefs.getAll();
    }
    
    // ========== DEMOGRAPHIC DATA METHODS ==========
    
    /**
     * Store all demographic data at once
     */
    public void AddDemographicData(String ageRange, String genderValue, String locationValue, 
                                   String incomeLevelValue, String usageLevelValue) {
        editor = context.getSharedPreferences(SharedPref, MODE_PRIVATE).edit();
        editor.putString(age_range, ageRange);
        editor.putString(gender, genderValue);
        editor.putString(location, locationValue);
        editor.putString(income_level, incomeLevelValue);
        editor.putString(usage_level, usageLevelValue);
        editor.apply();
    }
    
    public String GetAgeRange() {
        prefs = context.getSharedPreferences(SharedPref, MODE_PRIVATE);
        return prefs.getString(age_range, "");
    }
    
    public String GetGender() {
        prefs = context.getSharedPreferences(SharedPref, MODE_PRIVATE);
        return prefs.getString(gender, "");
    }
    
    public String GetLocation() {
        prefs = context.getSharedPreferences(SharedPref, MODE_PRIVATE);
        return prefs.getString(location, "");
    }
    
    public String GetIncomeLevel() {
        prefs = context.getSharedPreferences(SharedPref, MODE_PRIVATE);
        return prefs.getString(income_level, "");
    }
    
    public String GetUsageLevel() {
        prefs = context.getSharedPreferences(SharedPref, MODE_PRIVATE);
        return prefs.getString(usage_level, "");
    }


}
