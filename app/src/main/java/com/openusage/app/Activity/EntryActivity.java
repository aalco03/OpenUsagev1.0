package com.openusage.app.Activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.openusage.app.DatabaseHelper.LogInPreference;
import com.openusage.app.PermissionScreens.PermissionParentActivity;
import com.openusage.app.Services.CaptureUploadService;
import com.openusage.app.UsagePermissionActivity;

/**
 * @author Jack Boffa
 * Activity that starts when the app is opened. This activity is not visible, but dispatches
 * the user to either LoginActivity or AppRunningActivity depending on if they're logged in.
 */
public class EntryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Intent intent;
        
        // Check if user is logged in
        LogInPreference loginPref = new LogInPreference(this);
        boolean isLoggedIn = !loginPref.GetUserSubjId().isEmpty();

        if (!isLoggedIn) {
            // Not logged in - show login screen (with skip option)
            intent = new Intent(this, LoginActivity.class);
        } else if ("PASSIVE".equalsIgnoreCase(loginPref.GetStudyGroup())) {
            // PASSIVE group: go to AppRunning if Usage Stats granted, else permissions
            if (UsagePermissionActivity.appHasUsageAccess(this)) {
                intent = new Intent(this, AppRunningActivity.class);
            } else {
                intent = new Intent(this, PermissionParentActivity.class);
            }
        } else if (!CaptureUploadService.isRunning()) {
            // Logged in but service not running - show permissions
            intent = new Intent(this, PermissionParentActivity.class);
        } else {
            // Logged in and service running - go to app running screen
            intent = new Intent(this, AppRunningActivity.class);
        }

        startActivity(intent);

        // Exit this activity.
        finish();
    }
}
