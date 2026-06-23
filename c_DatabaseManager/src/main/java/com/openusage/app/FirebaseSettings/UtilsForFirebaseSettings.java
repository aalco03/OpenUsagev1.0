package com.openusage.app.FirebaseSettings;


import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;


import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.openusage.app.DatabaseHelper.LogInPreference;


public class UtilsForFirebaseSettings {

    public static String getSubjectId(Context context)
    {
        LogInPreference sharedPref = new LogInPreference(context);
        String code = sharedPref.GetUserCode();
        String number = sharedPref.GetUserNumber();
        if (number.isEmpty() || number.equals("000")) {
            return code;
        }
        return code + number;
    }

    public static String getGroupCode(Context context)
    {
        LogInPreference sharedPref = new LogInPreference(context);
        return sharedPref.GetStudyGroup();
    }


    public static String getCodeAndNumber(Context context){
        LogInPreference sharedPref = new LogInPreference(context);
        String code = sharedPref.GetUserCode();
        String number = sharedPref.GetUserNumber();
        // Web-enrolled users have number set to "000" or empty — use code as-is
        if (number.isEmpty() || number.equals("000")) {
            return code;
        }
        return code + "_" + number;
    }

    public static String getDataSubjectId(Context context)
    {
        String subject_id = getSubjectId(context);
        return getDataSubjectId(subject_id);
    }

    public static String getDataSubjectId(String subjectId)
    {
        return subjectId.replaceAll("[^a-zA-Z0-9]", "");
    }


    public static Map<String, String> createPhoneSpecMap()
    {
        HashMap<String, String> map = new HashMap<>();
        map.put("fingerprint", Build.FINGERPRINT);
        map.put("manufacturer", Build.MANUFACTURER);
        map.put("brand", Build.BRAND);
        map.put("model", Build.MODEL);
        map.put("product", Build.PRODUCT);
        map.put("display-id", Build.DISPLAY);
        return map;
    }

    public static boolean isInstallCodeSet(Context context)
    {
        return !TextUtils.isEmpty(getInstallCode(context));
    }

    public static String getInstallCode(Context context)
    {
        LogInPreference sharedPref = new LogInPreference(context);
        return sharedPref.GetInstallCode();
    }

    public static String setRandomInstallCode(Context context)
    {
        LogInPreference pref = new LogInPreference(context);

        String installcode = UUID.randomUUID().toString();
        pref.AddInstallCode(installcode);
        return installcode;
    }

    public static String getCrashLogDirectory(Context context) {
        File ext = context.getExternalFilesDir(null);
        if (ext != null) return ext.getAbsolutePath() + "/logs/";
        return getBackupCrashLogDirectory();
    }

    public static String getBackupCrashLogDirectory() {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/screenomics_logs/";
    }

    public static int getBatteryPercentage(Context context) {
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            float percent = (level * 100.f) / scale;
            return (int) percent;
        }

        return -1;
    }
}
