package com.openusage.app.PermissionScreens;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;


import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import com.openusage.app.Activity.AppRunningActivity;
import com.openusage.app.DatabaseHelper.LogInPreference;
import com.openusage.app.FirebaseSettingsObserver;
import com.openusage.app.Services.CaptureUploadService;
import com.openusage.app.Services.ScreenomicsAccessService;
import com.openusage.app.R;
import com.openusage.app.FirebaseSettings.SettingsManager;
import com.openusage.app.TextBasedEventData.EventOperationManager;
import com.openusage.app.TextBasedEventData.HashMapPool;
import com.openusage.app.UsagePermissionActivity;
import com.openusage.app.modulemanager.ModuleCharacteristics;
import com.openusage.app.network.NetworkStatusCapture;

/**
 * PermissionParentActivity - Handles app permission requests for Screenomics study
 * 
 * ========== SCREENSHOT FALLBACK MODE ==========
 * 
 * REQUIRED PERMISSIONS FOR TEXT-FIRST WITH SCREENSHOT FALLBACK:
 * Usage Stats - For app usage tracking and session management
 * Accessibility Service - For UI text extraction (primary data collection)
 * Media Projection - For screenshot capture (fallback when text insufficient)
 * 
 * OPTIONAL PERMISSIONS (For full study - currently disabled):
 * Location Services - GPS tracking  
 * Notification Access - Notification monitoring
 * Physical Activity - Activity recognition
 * Post Notifications - App notifications
 * 
 * DESIGN:
 * - Text extraction runs every 5 seconds (primary method)
 * - Screenshots triggered on-demand only when text quality insufficient
 * - MediaProjection stays initialized for fast fallback response
 * 
 * Modified by: Screenshot fallback implementation
 * Date: January 2026
 * Purpose: Intelligent data collection with fallback strategy
 */
public class PermissionParentActivity extends AppCompatActivity {

    private boolean IsTheAppRunFirstTime = true;
    private ViewPager Viewpager;
    private ImageView BackwardImg;
    private ImageView ForwardImg;
    private ViewPagerAdapter viewPagerAdapter;

    private TextView CounterTxt;

    private int TotalPages = 0;

    private static final int REQUEST_CODE = 90;

    BroadcastReceiver broadcastReceiver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.permission_parent);


        IsTheAppRunFirstTime = true;

        CounterTxt = (TextView) findViewById(R.id.CounterTxt);

        Viewpager = (ViewPager) findViewById(R.id.Viewpager);
        BackwardImg = (ImageView) findViewById(R.id.BackwardImg);
        ForwardImg = (ImageView) findViewById(R.id.ForwardImg);

        SetStatusBarColor();



        viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager());

        if (isMyServiceRunning(CaptureUploadService.class)
                || isMyServiceRunning(com.openusage.app.Services.ScreenMonitorService.class)){
            startActivity(new Intent(PermissionParentActivity.this, AppRunningActivity.class));
            finish();
        }else {
            AddViewPageAccordingToPermission();
        }




        LogIsAppOpenedByNotificationOrManually();
    }

    private void LogIsAppOpenedByNotificationOrManually(){
        if (Objects.equals(getIntent().getStringExtra(getString(R.string.open_by_notification)), getString(R.string.open_by_notification))){

            HashMap<String, String> map = HashMapPool.getMap();
            map.put("activity", "opened");

            ModuleCharacteristics moduleCharacteristics = ModuleCharacteristics.getInstance();

            EventOperationManager.getInstance(PermissionParentActivity.this).addEvent(moduleCharacteristics.getAlarmManagerCharacteristics(),map);

            HashMapPool.releaseMap(map);
        }
    }


    private void SetStatusBarColor(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }

        WindowInsetsController controller = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            controller = getWindow().getInsetsController();


            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());

                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

            }

            // Create a fake status bar (Scrim View) to add color
            View statusBarScrim = new View(this);
            statusBarScrim.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, getStatusBarHeight() + 20));
            statusBarScrim.setBackgroundColor(Color.parseColor("#BFDAE2")); // Open Usage brand color

            // Add the scrim to your root layout
            FrameLayout rootLayout = findViewById(android.R.id.content);
            rootLayout.addView(statusBarScrim);
        }
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
    }

    public void ScrollToNextPage(){
//        Viewpager.arrowScroll(View.FOCUS_RIGHT);
        Viewpager.setCurrentItem(Viewpager.getCurrentItem() + 1,true);
    }


    private void AddViewPageAccordingToPermission(){


        ProgressDialog dialog = new ProgressDialog(PermissionParentActivity.this);
        dialog.setMessage("Loading Settings. Please Wait....");
        dialog.setCancelable(false);
        dialog.show();


        if (!SettingsManager.exists()) {
            SettingsManager.create(new FirebaseSettingsObserver() {
                @Override
                public void onSettingsChanged(List<String> changedSettings) {

                }
            });
        }

        SettingsManager.get().load(this, new SettingsManager.DatabaseListener() {
            @Override
            public void onSuccess() {


                // Settings loaded success fully
            }

            @Override
            public void onFailure() {
                // Failed to load the settings? Too bad!
            }
        });


        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // PRODUCTION NOTE: Simplified permission flow for testing/demo.
                // Before full deployment, re-enable optional permissions (Notification, Location, Activity Recognition).
                // Currently only requesting: Usage Stats, Accessibility Service, Media Projection.
                
                // Determine study group for permission gating
                String studyGroup = new LogInPreference(PermissionParentActivity.this).GetStudyGroup();
                boolean isPassive = "PASSIVE".equalsIgnoreCase(studyGroup);

                // REQUIRED: Usage Stats Permission (for app usage tracking)
                boolean hasUsageAccess = UsagePermissionActivity.appHasUsageAccess(PermissionParentActivity.this);
                if (!hasUsageAccess){
                    viewPagerAdapter.add(new PreviewFrag(getString(R.string.usage_permission_header),getString(R.string.usage_permission_description)));
                    TotalPages = TotalPages + 1;
                }

                // Physical Activity Recognition (non-PASSIVE only, must come BEFORE Accessibility
                // so that "Begin Tracking" on the Accessibility/Media Projection page works correctly)
                if (!isPassive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (ContextCompat.checkSelfPermission(PermissionParentActivity.this, Manifest.permission.ACTIVITY_RECOGNITION)
                            != PackageManager.PERMISSION_GRANTED) {
                        viewPagerAdapter.add(new PreviewFrag(getString(R.string.physical_permission_header),getString(R.string.physical_permission_description)));
                        TotalPages = TotalPages + 1;
                    }
                }

                // REQUIRED (non-PASSIVE only): Accessibility Service (for UI text extraction)
                boolean hasAccessibilityService = PermissionChecker.isAccessServiceEnabled(PermissionParentActivity.this, ScreenomicsAccessService.class);
                if(!isPassive && !hasAccessibilityService){
                    viewPagerAdapter.add(new PreviewFrag(getString(R.string.accessibility_permission_header),getString(R.string.accessibility_permission_description)));
                    TotalPages = TotalPages + 1;
                }

                // Media Projection (non-PASSIVE only, for screenshot fallback when text insufficient)
                // Only required when screenshots-enabled == 1 in Firebase settings
                if (!isPassive && SettingsManager.val("screenshots-enabled") == 1 && !isMyServiceRunning(CaptureUploadService.class)){
                    viewPagerAdapter.add(new PreviewFrag(getString(R.string.media_projection_permission_header),getString(R.string.media_projection_permission_description)));
                    TotalPages = TotalPages + 1;
                }

                // DEBUG: Log permission status
                android.util.Log.d("PermissionParent", "Study group: " + studyGroup + ", Usage Access: " + hasUsageAccess + ", Accessibility: " + hasAccessibilityService + ", Total Pages: " + TotalPages);

                // ========== OPTIONAL PERMISSIONS - DISABLED FOR NOW ==========
                // TODO: Uncomment these when ready for full study deployment
                
                /*
                // OPTIONAL: Notification Permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(PermissionParentActivity.this, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                        viewPagerAdapter.add(new PreviewFrag(getString(R.string.notification_permission_header),getString(R.string.notification_permission_description)));
                        TotalPages = TotalPages + 1;
                    }
                }

                // DISABLED: Notification Listener Service
                ContentResolver contentResolver = getContentResolver();
                String enabledNotificationListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners");
                String packageName = getPackageName();
                if (enabledNotificationListeners == null || !enabledNotificationListeners.contains(packageName)) {
                    viewPagerAdapter.add(new PreviewFrag(getString(R.string.read_notification_permission_header),getString(R.string.read_notification_permission_description)));
                    TotalPages = TotalPages + 1;
                }

                // DISABLED: Location Permission
                if (SettingsManager.val("gps-enabled") == 1) {
                    if (ContextCompat.checkSelfPermission(PermissionParentActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {
                        viewPagerAdapter.add(new PreviewFrag(getString(R.string.location_permission_header),getString(R.string.location_access_permission_description)));
                        TotalPages = TotalPages + 1;
                    }
                }
                */

                // ========== HANDLE COMPLETION ==========
                // If all permissions are granted, go directly to AppRunningActivity
                if (TotalPages == 0) {
                    Intent intent = new Intent(PermissionParentActivity.this, AppRunningActivity.class);
                    startActivity(intent);
                    finish();
                    return;
                }

                // ========== DEBUG: ENSURE WE HAVE CONTENT ==========
                // Add a fallback screen if something went wrong
                if (viewPagerAdapter.getCount() == 0) {
                    // Force add Usage Stats permission as fallback
                    viewPagerAdapter.add(new PreviewFrag(getString(R.string.usage_permission_header),getString(R.string.usage_permission_description)));
                    TotalPages = 1;
                }

                CounterTxt.setText(String.valueOf(Viewpager.getCurrentItem() + 1) + "/" + TotalPages);

                Viewpager.setAdapter(viewPagerAdapter);

                broadcastReceiver = new NetworkStatusCapture();

                registerReceiver(broadcastReceiver,new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));


                if (Viewpager.getCurrentItem() == 0) {
                    BackwardImg.setVisibility(View.INVISIBLE);
                } else {
                    BackwardImg.setVisibility(View.VISIBLE);
                }


                Viewpager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                    @Override
                    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

                    }

                    @Override
                    public void onPageSelected(int position) {

                        androidx.fragment.app.Fragment fragment = viewPagerAdapter.getItem(position);

                        if (fragment instanceof PreviewFrag){

                            ((PreviewFrag)fragment).CheckForPermissionAndSetButtonToFade(true);
                        }


                        CounterTxt.setText(String.valueOf(position + 1) +"/"+ TotalPages);


                        int currentItem = Viewpager.getCurrentItem();

                        if (currentItem == (Viewpager.getAdapter().getCount()-1)) {
                            ForwardImg.setVisibility(View.INVISIBLE);
                        } else {
                            ForwardImg.setVisibility(View.VISIBLE);
                        }

                        if (currentItem == 0) {
                            BackwardImg.setVisibility(View.INVISIBLE);
                        } else {
                            BackwardImg.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onPageScrollStateChanged(int state) {

                    }



                });

                BackwardImg.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        Viewpager.setCurrentItem(Viewpager.getCurrentItem() -1 );
                    }
                });

                ForwardImg.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Viewpager.setCurrentItem(Viewpager.getCurrentItem() + 1 );

                    }
                });

                if (!isFinishing() && !isDestroyed() && dialog.isShowing()){
                    dialog.dismiss();
                }

            }
        }, 2000);





    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {


        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    
    // Public method for fragments to check service status
    public boolean isServiceRunning(Class<?> serviceClass) {
        return isMyServiceRunning(serviceClass);
    }


    @Override
    protected void onResume() {
        super.onResume();

//        LogIsAppOpenedByNotificationOrManually();
        // ========== TESTING ENVIRONMENT: SIMPLIFIED PERMISSION HANDLING ==========
        // Only handle Usage Stats and Accessibility Service permissions
        if (!IsTheAppRunFirstTime){
            if (PreviewFrag.WhichScreenIsSelected != null) {
                // ENABLED: Usage Stats Permission
                if (PreviewFrag.WhichScreenIsSelected.equals(getString(R.string.usage_permission_header))) {
                    if (UsagePermissionActivity.appHasUsageAccess(PermissionParentActivity.this)) {
                        ScrollToNextPage();
                    }
                }
                // ENABLED: Accessibility Service Permission
                else if (PreviewFrag.WhichScreenIsSelected.equals(getString(R.string.accessibility_permission_header))) {
                    int accessEnabled = 0;
                    try {
                        accessEnabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
                    } catch (Settings.SettingNotFoundException e) {
                        e.printStackTrace();
                    }

                    if(accessEnabled != 0){
                        ScrollToNextPage();
                    }
                }

                // ========== TEMPORARILY DISABLED FOR TESTING ==========
                // TODO: Uncomment when ready for full study deployment
                /*
                // DISABLED: Notification Listener Service
                else if (PreviewFrag.WhichScreenIsSelected.equals(getString(R.string.read_notification_permission_header))) {
                    if (PermissionChecker.isNotificationServiceEnabled(PermissionParentActivity.this)){
                        ScrollToNextPage();
                    }
                }
                */
            }
        }

        IsTheAppRunFirstTime = false;


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE) return;

        // If starting capture succeeded, go to the "App Running" screen.
        if (resultCode == Activity.RESULT_OK) {
//            Intent ar_intent = new Intent(this, AppRunningActivity.class);
//            startActivity(ar_intent);
            finish();
        }

        // Otherwise, notify the user of the issue.
        else {
            Toast.makeText(this, R.string.cp_toast_nostart, Toast.LENGTH_SHORT).show();
        }
    }
}