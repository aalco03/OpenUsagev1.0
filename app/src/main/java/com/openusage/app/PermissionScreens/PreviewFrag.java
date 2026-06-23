package com.openusage.app.PermissionScreens;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import com.openusage.app.Services.CaptureUploadService;
import com.openusage.app.Services.ScreenomicsAccessService;
import com.openusage.app.Activity.CaptureUploadStarter;
import com.openusage.app.FirebaseSettings.SettingsManager;
import com.openusage.app.R;
import com.openusage.app.UsagePermissionActivity;
import com.openusage.app.screenshots.ScreenshotCapture;


/**
 * PreviewFrag - Individual permission screen fragment
 * 
 * ========== TESTING ENVIRONMENT MODIFICATIONS ==========
 * 
 * UPDATED FOR SIMPLIFIED PERMISSION FLOW:
 * - Accessibility Service permission is now the FINAL step
 * - When accessibility service is granted AND all permissions are satisfied,
 *   the button changes to "Begin Tracking" instead of "Allowed"
 * - Clicking "Begin Tracking" starts the capture service
 * 
 * FLOW LOGIC:
 * 1. Usage Stats Permission → Settings page
 * 2. Accessibility Service Permission → Settings page OR "Begin Tracking"
 * 
 * Modified by: Session-based logging implementation  
 * Date: December 2025
 * Purpose: Streamline permission flow for testing hierarchical session schema
 */
public class PreviewFrag extends Fragment {

    private ConstraintLayout ParentLay;
    private ConstraintLayout contentDesLayout;
    private TextView Header;
    private CardView AllowCard;
    private TextView AllowTxt;
    private LinearLayout DoNotAllowCard;
    private TextView DoNotAllowTxt;
    private ScrollView DescriptionTxt;
    private TextView Description;

    public static String WhichScreenIsSelected = null;

    Boolean checkForIsPermissionAllowed = false;
    String PermissionHeader;
    String PermissionDescription;

    PermissionChecker permissionChecker;

    public PreviewFrag() {
        // Required empty public constructor
    }

    public PreviewFrag(String PermissionHeader, String PermissionDescription) {
        this.PermissionDescription = PermissionDescription;
        this.PermissionHeader = PermissionHeader;
    }

    public PreviewFrag(String PermissionHeader, String PermissionDescription,boolean IsBold) {
        this.PermissionDescription = PermissionDescription;
        this.PermissionHeader = PermissionHeader;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_preview, container, false);

        contentDesLayout = view.findViewById(R.id.content_des_layout);
        Header =  view.findViewById(R.id.Header);
        AllowCard = view.findViewById(R.id.AllowCard);
        AllowTxt = view.findViewById(R.id.AllowTxt);
        DoNotAllowCard = view.findViewById(R.id.DoNotAllowCard);
        DoNotAllowTxt = view.findViewById(R.id.DoNotAllowTxt);
        DescriptionTxt = view.findViewById(R.id.DescriptionTxt);
        Description = view.findViewById(R.id.Description);

        permissionChecker = new PermissionChecker(requireActivity());

        Header.setText(PermissionHeader);

        Description.setText(PermissionDescription);

        DoNotAllowCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                permissionChecker.ShowDialog(getString(R.string.do_not_allow_note),PermissionHeader,null,false);

            }
        });

        AllowCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkForIsPermissionAllowed = true;
                if (PermissionHeader.equals(getString(R.string.usage_permission_header))){
                    WhichScreenIsSelected = getString(R.string.usage_permission_header);

                    Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    startActivity(intent);


                } else if (PermissionHeader.equals(getString(R.string.notification_permission_header))) {
                    AskForPermission(Manifest.permission.POST_NOTIFICATIONS);
                    WhichScreenIsSelected = "NOPE";
                }else if (PermissionHeader.equals(getString(R.string.read_notification_permission_header))) {
                    WhichScreenIsSelected = getString(R.string.read_notification_permission_header);

                    Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                    startActivity(intent);

                } else if (PermissionHeader.equals(getString(R.string.accessibility_permission_header))) {
                    if (!permissionChecker.CheckIsAnyPermissionLeftFromBeingAllowed()){
                        // All permissions granted
                        WhichScreenIsSelected = "NOPE";
                        if (SettingsManager.val("screenshots-enabled") == 1) {
                            beginCapture(); // Will request MediaProjection
                        } else {
                            // Text-only mode — skip MediaProjection, go straight to tracking
                            startTrackingWithoutScreenshots();
                        }
                    } else {
                        WhichScreenIsSelected = getString(R.string.accessibility_permission_header);
                        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                }else if (PermissionHeader.equals(getString(R.string.physical_permission_header))) {
                    WhichScreenIsSelected = "NOPE";
                    AskForPermission(Manifest.permission.ACTIVITY_RECOGNITION);
                }else if (PermissionHeader.equals(getString(R.string.location_permission_header))) {
                    WhichScreenIsSelected = "NOPE";
                    AskForPermission(Manifest.permission.ACCESS_FINE_LOCATION);
                }else if (PermissionHeader.equals(getString(R.string.media_projection_permission_header))) {
                    if (!permissionChecker.CheckIsAnyPermissionLeftFromBeingAllowed()){
                        WhichScreenIsSelected = "NOPE";
                        beginCapture();
                    }else {
                        Toast.makeText(requireContext(), "First allow all other permissions to start", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        return view;
    }

    /**
     * Start tracking in text-only mode (no MediaProjection required).
     * Navigates directly to AppRunningActivity.
     */
    private void startTrackingWithoutScreenshots() {
        Intent appRunningIntent = new Intent(requireContext(), com.openusage.app.Activity.AppRunningActivity.class);
        startActivity(appRunningIntent);
        requireActivity().finish();
    }
    /**
     * Start full monitoring including screenshot capture fallback.
     * Requests MediaProjection permission via CaptureUploadStarter.
     */
    private void beginCapture()
    {
        // Start CaptureUploadStarter which will request MediaProjection permission
        // This is required even in fallback mode so screenshots can be captured on-demand
        ScreenshotCapture.startupInstigator = "user";
        Intent intent = new Intent(requireContext(), CaptureUploadStarter.class);
        startActivityForResult(intent, 90);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 90) {
            if (resultCode == android.app.Activity.RESULT_OK) {
                // MediaProjection granted, navigate to AppRunningActivity
                Intent appRunningIntent = new Intent(requireContext(), com.openusage.app.Activity.AppRunningActivity.class);
                startActivity(appRunningIntent);
                requireActivity().finish();
            } else {
                // User denied MediaProjection permission
                Toast.makeText(requireContext(), "Screenshot permission is required for visual content capture", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        CheckForPermissionAndSetButtonToFade(true);

    }


    public void CheckForPermissionAndSetButtonToFade(boolean IsPermission){

        checkForIsPermissionAllowed = IsPermission;

        if (checkForIsPermissionAllowed){
            if (PermissionHeader != null){
                if (PermissionHeader.equals(getString(R.string.usage_permission_header))){
                    if (UsagePermissionActivity.appHasUsageAccess(requireContext())){
//                    ((PermissionParentActivity)requireActivity()).onResume();
//                    super.onResume();
                        if (!permissionChecker.CheckIsAnyPermissionLeftFromBeingAllowed()) {
                            SetButtonTextToBeginTracking();
                        } else {
                            SetButtonTextToAllowed();
                        }
                    }

                }else if (PermissionHeader.equals(getString(R.string.physical_permission_header))) {
                    if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACTIVITY_RECOGNITION)
                            == PackageManager.PERMISSION_GRANTED) {
                        SetButtonTextToAllowed();

//                    ((PermissionParentActivity)requireActivity()).ScrollToNextPage();

                    }
                }else if (PermissionHeader.equals(getString(R.string.location_permission_header))) {
                    if (ContextCompat.checkSelfPermission(requireActivity(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                        SetButtonTextToAllowed();

                    }
                }

                else if (PermissionHeader.equals(getString(R.string.read_notification_permission_header))) {
                    ContentResolver contentResolver = requireActivity().getContentResolver();
                    String enabledNotificationListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners");
                    String packageName = requireActivity().getPackageName();

                    if (PermissionChecker.isNotificationServiceEnabled(requireContext())){
                        SetButtonTextToAllowed();
                    }


                }else if (PermissionHeader.equals(getString(R.string.accessibility_permission_header))) {
                    if (PermissionChecker.isAccessibilityServiceEnabled(requireContext(), ScreenomicsAccessService.class)){
                        if (!permissionChecker.CheckIsAnyPermissionLeftFromBeingAllowed()){
                            // All permissions granted
                            if (SettingsManager.val("screenshots-enabled") != 1) {
                                // Text-only mode — this is the final step
                                SetButtonTextToBeginTracking();
                            } else {
                                // Screenshots enabled — Media Projection page comes next
                                SetButtonTextToAllowed();
                            }
                        } else {
                            SetButtonTextToAllowed();
                        }
                    }

                } else if (PermissionHeader.equals(getString(R.string.notification_permission_header))) {

                    if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED) {

                        SetButtonTextToAllowed();
                    }

                } else if (PermissionHeader.equals(getString(R.string.media_projection_permission_header))) {
                    // Check if CaptureUploadService is running (indicates MediaProjection granted)
                    if (((PermissionParentActivity)requireActivity()).isServiceRunning(CaptureUploadService.class)){
                        // Check if this is the last permission needed
                        if (!permissionChecker.CheckIsAnyPermissionLeftFromBeingAllowed()){
                            // All permissions granted - change button to "Begin Tracking"
                            SetButtonTextToBeginTracking();
                        } else {
                            // Just mark as allowed
                            SetButtonTextToAllowed();
                        }
                    }
                } else {
                    checkForIsPermissionAllowed = false;
                }
            }

        }
    }

    private void SetButtonTextToAllowed(){
        AllowCard.setAlpha(0.5f);
        AllowCard.setClickable(false);
        AllowTxt.setText(R.string.allowed);
        DoNotAllowCard.setVisibility(View.GONE);
    }

    /**
     * Sets the button to "Begin Tracking" when all permissions are granted.
     */
    private void SetButtonTextToBeginTracking(){
        AllowCard.setAlpha(1.0f);
        AllowCard.setClickable(true);
        AllowTxt.setText("Begin Tracking");
        DoNotAllowCard.setVisibility(View.GONE);
        
        // Update click listener to start tracking
        AllowCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (SettingsManager.val("screenshots-enabled") == 1) {
                    beginCapture();
                } else {
                    startTrackingWithoutScreenshots();
                }
            }
        });
    }

    private void AskForPermission(String Permission){

        Dexter.withContext(requireContext())
                .withPermission(Permission)
                .withListener(new PermissionListener() {
                    @Override public void onPermissionGranted(PermissionGrantedResponse response) {
                        ((PermissionParentActivity)requireActivity()).ScrollToNextPage();

                        SetButtonTextToAllowed();
                    }
                    @Override public void onPermissionDenied(PermissionDeniedResponse response) {

                        permissionChecker.ShowDialog(getString(R.string.do_not_allow_note) + " Click on opens settings and allow " + PermissionHeader.toLowerCase()+".",PermissionHeader,getString(R.string.open_setting),false);
//                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
//                        Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
//                        intent.setData(uri);
//                        startActivity(intent);
                    }
                    @Override public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                        token.cancelPermissionRequest();
                        token.continuePermissionRequest();


                    }
                }).check();
    }


}