package com.openusage.app.Activity;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

import com.openusage.app.DatabaseHelper.InterCommunicationPreference;
import com.openusage.app.Services.CaptureUploadService;

/**
 * @author Jack Boffa
 * Invisible activity that launches the CaptureUploadService. An activity is required to do this
 * because we need to use startActivityForResult() to get the MediaProjection object.
 */
public class CaptureUploadStarter extends Activity
{

    // Media projection objects.
    private static final int REQUEST_CODE = 100;
    private MediaProjectionManager mediaProjectionManager;
    private static MediaProjection mediaProjection;

    public static int ResultCode;
    public static Intent intent1;


    private InterCommunicationPreference interCommunicationPrefrence;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        interCommunicationPrefrence = new InterCommunicationPreference(CaptureUploadStarter.this);

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        interCommunicationPrefrence.WhichActivityCalledStartService(0);

        Intent intent = mediaProjectionManager.createScreenCaptureIntent();

        // Call startActivityForResult() to make the system start screen-capping. This can only
        // be done from an activity, hence why this activity still exists at all.
        startActivityForResult(intent, REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        // Make sure this is the result of starting media projection.
        if (requestCode == REQUEST_CODE)
        {

            // If the result is not OK, the user probably didn't give us permission. Abort.
            if (resultCode != Activity.RESULT_OK)
            {
                setResult(resultCode);
                finish();
                return;
            }

            interCommunicationPrefrence.WhichActivityCalledStartService(0);
            
            // Store resultCode and intent in static variables
            // Service will create MediaProjection AFTER it starts foreground service
            ResultCode = resultCode;
            intent1 = intent;
            
            android.util.Log.i("CaptureUploadStarter", "MediaProjection data stored in static variables");
            android.util.Log.i("CaptureUploadStarter", "   ResultCode: " + resultCode);
            android.util.Log.i("CaptureUploadStarter", "   Intent: " + (intent != null ? "present" : "null"));

            // Start the CaptureUploadService.
            Intent serv_intent = new Intent(this.getApplicationContext(), CaptureUploadService.class);
            ContextCompat.startForegroundService(this.getApplicationContext(), serv_intent);

//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                startForegroundService(serv_intent);
////                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, intent);
//
//            } else {
//                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, intent);
//
//                startService(serv_intent);
//
//            }

            // Close this activity.
            setResult(Activity.RESULT_OK);
//            CaptureUploadService.SchedulestartScreenshotCaptureAgain = false;

            startActivity(new Intent(CaptureUploadStarter.this, AppRunningActivity.class));
            finish();
        }
    }

    public static MediaProjection getMediaProjection()
    {
        return mediaProjection;
    }

    public static Intent getIntents()
    {
        return intent1;
    }

    public static int getResultCode()
    {
        return ResultCode;
    }

}
