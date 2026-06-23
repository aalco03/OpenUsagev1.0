package com.openusage.app.NonTextBasedEventData;


import com.openusage.app.sharedresources.R; // Import the shared resources

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.HashMap;

import com.openusage.app.DatabaseHelper.LogInPreference;
import com.openusage.app.FirebaseSettings.UtilsForFirebaseSettings;
import com.openusage.app.TextBasedEventData.EventOperationManager;
import com.openusage.app.TextBasedEventData.HashMapPool;
import com.openusage.app.modulemanager.ModuleCharacteristics;


public class ImageUploaderToCloudStorage implements Runnable
{
    public static final String TAG = "ImageUploader";

    private boolean onlyUseWifi;

    // Whether an upload is already in progress.
    private boolean uploadInProgress;

    // Important things from CaptureUploadService.
    private Context context;
    private Handler handler;


    private boolean uploadPaused = false;
    private int currentUploadIndex = 0;

    // User login info.
    private String subject_id;
    private String user_password;
    private String dataSubjectId;

    // Firebase.
    private StorageReference mStorageRef;

    // Image uploading.
    private File[] stagedFiles;
    private StorageTask<UploadTask.TaskSnapshot> uploadTask;

    private int notTextInterval, KillSwitch;

    private int useWifi;

    EventOperationManager eventOperationManager;


    public ImageUploaderToCloudStorage(Context context, Handler handler, int useWifi, int notTextInterval, int KillSwitch) {
        this.context = context;
        this.handler = handler;
        this.useWifi = useWifi;
        this.notTextInterval = notTextInterval;
        this.KillSwitch = KillSwitch;
        this.uploadInProgress = false;

        eventOperationManager = EventOperationManager.getInstance(context);
    }

    @Override
    public void run()
    {

        onlyUseWifi  = (useWifi == 1);

        // Do this again after another interval has passed.
        handler.postDelayed(this, notTextInterval);

        // Don't upload if the kill switch is active.
        if (KillSwitch == 1) return;

        // Take measures to prevent multiple of these tasks from happening at once.
        if (uploadInProgress)
        {
            reportUploadEvent("start", false, "Upload already in progress -- not starting a new one.", null);
            return;
        }
        uploadInProgress = true;

        LogInPreference sharedPref = new LogInPreference(context);
        subject_id = sharedPref.GetUserSubjId();
        user_password = sharedPref.GetUserPassword();
        dataSubjectId = UtilsForFirebaseSettings.getCodeAndNumber(context);

        // Make sure they're logged in.
        if (TextUtils.isEmpty(subject_id))
        {
            Log.d(TAG, "Could not upload -- user not logged in!");
            handleLoginFailure();
            return;
        }

        // Get Firebase instances.
        FirebaseAuth auth = FirebaseAuth.getInstance();
        // Access the cloud_storage_url resource
        String cloudStorageUrl = context.getString(R.string.cloud_storage_url);
        // Initialize FirebaseStorage with the resource
        mStorageRef = FirebaseStorage.getInstance(cloudStorageUrl).getReference();

        // Make sure Firebase thinks we're logged in too. If not, try to log in.
        if(auth.getCurrentUser() == null)
        {
            auth.signInWithEmailAndPassword(subject_id, user_password)
                    .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                beginUpload();
                            }
                            else {
                                handleLoginFailure();
                            }
                        }
                    });
        }
        else
        {
            beginUpload();
        }
    }

    /**
     * Call this method to abort the current upload operation.
     */
    public void killUpload()
    {
        // Cancel any current upload process.
        if (uploadTask != null && uploadTask.isInProgress())
        {
            // TODO: does this invoke onFailure()? if so, filter this out cause that'll start the next upload
            uploadTask.cancel();
        }

        onUploadEnd(false);
    }

    private void beginUpload()
    {
        // Call prepareUpload() asynchronously.
        new UploadFiles().execute();
    }

    private class UploadFiles extends AsyncTask<Void, Void, Void > {
        protected Void doInBackground(Void... params) {
            prepareUpload();
            return null;
        }

        protected void onProgressUpdate(Void... params) {
            Log.w(TAG, "In progress");


        }

        protected void onPostExecute(Void... params) {
            Log.w(TAG, "Done moving 180");

        }
    }

    private void handleLoginFailure()
    {
        // Notify the user.
//        Toast.makeText(context, R.string.iu_toast_loginfailure, Toast.LENGTH_LONG).show();

        // This upload process has ended.
        onUploadEnd(false);

        // Fully log out and stop the CaptureUploadService.
//        LoginActivity.userLogOut(context, true);
    }

    private void prepareUpload()
    {
        // Get storage directory.
        File sdCardDirectory = context.getExternalFilesDir(null);
        if (sdCardDirectory == null) {
            reportUploadEvent("start", true, "could not create storage dir", null);
            onUploadEnd(false);
            return;
        }

        // Get subdirectories.
        String source = sdCardDirectory.getAbsolutePath() + "/screenshots/";
        String destination = sdCardDirectory.getAbsolutePath() + "/uploadscreenshots/";
        File srcDirectory = new File(source);
        final File destDirectory = new File(destination);

        if (!srcDirectory.exists()) {
            reportUploadEvent("start", true, "screenshot directory does not exist", null);
            onUploadEnd(false);
            return;
        }

        // Attempt to create uploadscreenshots folder if it doesn't exist.
        boolean createdStorage = true;
        if (!destDirectory.exists()) {
            createdStorage = destDirectory.mkdirs();
        }

        // If it exists now...
        if (createdStorage || destDirectory.isDirectory())
        {
            // Move files from screenshots to uploadscreenshots.
            try {
                if (srcDirectory.isDirectory()) {
                    Log.w(TAG, "Moving files now");
                    String[] subFiles = srcDirectory.list();
                    for (int i = 0; i < subFiles.length; i++) {
                        new File(srcDirectory, subFiles[i]).renameTo(new File(destDirectory, subFiles[i]));
                        Log.w(TAG, "Moved file");
                    }
                    Log.w(TAG, "Done moving 237");
                }
            } catch (Exception e) {
                e.printStackTrace();
                onUploadEnd(false);
                return;
            }
        }

        // Couldn't create uploadscreenshots.
        else {
            reportUploadEvent("start", true, "could not create dest directory", srcDirectory);
            onUploadEnd(false);
            return;
        }

        // Set Firebase Storage to point to the user's folder.
        mStorageRef = mStorageRef.child(UtilsForFirebaseSettings.getCodeAndNumber(context));

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null)
        {
            Log.w(TAG, "Cm is null in if 259");

            reportUploadEvent("start", true, "could not get connectivity info, aborting upload", destDirectory);
            onUploadEnd(false);
            return;
        }
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        // Make sure we're connected to the internet.
        if (activeNetwork != null)
        {
            // If we're only allowed to upload over wifi, check that.
            if (onlyUseWifi && activeNetwork.getType() != ConnectivityManager.TYPE_WIFI) {
                Log.w(TAG,"use wifi but connection is not wifi");
                reportUploadEvent("start", true, "user online but not on wifi, aborting upload", null);
                onUploadEnd(false);
                return;
            }
            else if (onlyUseWifi && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                startUpload(destDirectory);
                Log.w(TAG,"use wifi connection is also wifi");

                return;
            }else if (!onlyUseWifi && activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                startUpload(destDirectory);
                Log.w(TAG,"use mobile data connection is also mobile data");

                return;
            }else {
                onUploadEnd(false);
                return;
            }


        }
        else
        {
            reportUploadEvent("start", true, "user not connected to internet, aborting upload", destDirectory);
            onUploadEnd(false);
        }
    }

    private void startUpload(File destDirectory)
    {
        reportUploadEvent("start", false, "starting upload now", destDirectory);
        stagedFiles = destDirectory.listFiles();
        uploadTask = null;
        uploadImage(0);
    }

    private void uploadImage(final int index) {
        Log.d(TAG, "Upload Image Entered");

        // Store current index for pause/resume functionality
        currentUploadIndex = index;

        // Stop if the index has exceeded the number of files.
        if (index >= stagedFiles.length) {
            Log.d(TAG, "Upload complete!!!!");
            reportUploadEvent("complete", false, "", null);
            onUploadEnd(true);
            return;
        }

        // Stop if we've disconnected from wifi halfway through uploading.
        if (checkWifiDisconnect()) {
            Log.d(TAG, "Upload not Completed!!!!");
            reportUploadEvent("image", true, "wifi disconnected mid-upload; aborting...", null);
            abortUploadEarly();
            onUploadEnd(false);
            return;
        }

        // Skip uploading if paused and wait for resume
        if (uploadPaused) {
            Log.d(TAG, "Upload paused at index " + index + ". Will resume when memory is available.");
            return;
        }

        Log.w(TAG, "Beginning upload of image " + stagedFiles[index].getName());

        // Start async uploading the image, registering listeners for success/failure.
        Uri imguri = Uri.fromFile(stagedFiles[index]);
        uploadTask = mStorageRef.child(stagedFiles[index].getName()).putFile(imguri)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        onImageUploadSuccess(index);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        onImageUploadFailure(index, e);
                    }
                });
    }

    private boolean checkWifiDisconnect()
    {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null)
        {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

            // Make sure we're connected to the internet.
            if (activeNetwork != null)
            {
                // Check wifi connectivity.
                return onlyUseWifi && activeNetwork.getType() != ConnectivityManager.TYPE_WIFI;
            }
        }

        // We only want to abort the upload if there's an active network that ISN'T wifi.
        // So return false if there was a different problem (i.e. complete internet loss)
        // so that THAT doesn't abort the upload.
        return false;
    }

    private void onImageUploadSuccess(int index)
    {
        File file = stagedFiles[index];

        Log.w(TAG, "Successfully uploaded " + file.getName());
        // Delete from storage the file we just uploaded.
        if (!file.delete())
        {
            Log.e(TAG, "Failed to delete " + file.getName());
        }

        // Start uploading the next file.
        uploadImage(index + 1);
    }

    private void onImageUploadFailure(int index, Exception e)
    {
        // Log the error.
        String msg = "Failed to upload " + stagedFiles[index].getName() + ": " + e.getMessage();
        Log.e(TAG, msg);
        reportUploadEvent("image", true, msg, stagedFiles.length - index, -1);

        // Leave the file in the uploads folder and try the next file.
        uploadImage(index + 1);
    }

    private void abortUploadEarly()
    {
        // Get subdirectories.
        File sdCardDirectory = context.getExternalFilesDir(null);
        String source = sdCardDirectory.getAbsolutePath() + "/screenshots/";
        String destination = sdCardDirectory.getAbsolutePath() + "/uploadscreenshots/";
        File srcDirectory = new File(source);
        final File destDirectory = new File(destination);

        // Unstage files by moving them back to regular screenshots folder.
        try {
            if (srcDirectory.isDirectory()) {
                Log.w(TAG, "Moving files now");
                String[] subFiles = destDirectory.list();
                for (int i = 0; i < subFiles.length; i++) {
                    new File(srcDirectory, subFiles[i]).renameTo(new File(srcDirectory, subFiles[i]));
                    Log.w(TAG, "Moved file");
                }
                Log.w(TAG, "Done moving 417");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    private void onUploadEnd(boolean success)
    {
        if (success) Log.w(TAG, "Upload process complete.");
        else Log.e(TAG, "Upload process has failed early. We'll try again later.");

        uploadInProgress = false;
    }

    private void reportUploadEvent(String phase, boolean error, String message, File destDirectory)
    {
        if (error) Log.e(TAG, message);

        int remaining_imgs = -1;
        long remaining_bytes = -1;

        // If no uploadscreenshots folder specified, find storage directories and count.
        if (destDirectory == null) {
            File sdCardDirectory = context.getExternalFilesDir(null);
            if (sdCardDirectory != null) {
                File srcDirectory = new File(sdCardDirectory.getAbsolutePath() + "/screenshots/");
                destDirectory = new File(sdCardDirectory.getAbsolutePath() + "/uploadscreenshots/");

                if (srcDirectory.exists() && destDirectory.exists()) {
                    remaining_imgs = srcDirectory.listFiles().length + destDirectory.listFiles().length;
                    remaining_bytes = destDirectory.getUsableSpace();
                }
            }
        }

        // If specified, just count.
        else {
            remaining_imgs = destDirectory.listFiles().length;
            remaining_bytes = destDirectory.getUsableSpace();
        }

        reportUploadEvent(phase, error, message, remaining_imgs, remaining_bytes);
    }

    private void reportUploadEvent(String phase, boolean error, String message, int remaining_imgs, long remaining_bytes) {

        Log.e(TAG, message);

        HashMap<String, String> map = HashMapPool.getMap();
        map.put("phase", phase);
        map.put("error", error ? "yes" : "no");
        map.put("message", message);
        map.put("remaining-imgs", "" + remaining_imgs);
        map.put("remaining-bytes", "" + remaining_bytes);

        EventOperationManager.getInstance(context).addEvent(ModuleCharacteristics.getInstance().getScreenshotUploadEventCharacteristics(), map);

        HashMapPool.releaseMap(map);


//        EventReporter.report(new ScreenshotUploadEvent(phase, error, message, remaining_imgs, remaining_bytes));
    }

    public boolean isUploadInProgress()
    {
        return uploadInProgress;
    }



    public boolean pauseUpload() {
        if (uploadPaused || !uploadInProgress) {
            return false;
        }

        uploadPaused = true;
        reportUploadEvent("pause", false, "Upload paused due to low memory", null);

        // Pause any current upload task
        if (uploadTask != null && uploadTask.isInProgress()) {
            uploadTask.pause();
            Log.d(TAG, "Upload task paused");
        }

        Log.d(TAG, "Upload paused at index " + currentUploadIndex);
        return true;
    }

    /**
     * Resume a paused upload - can be called when memory is available again
     * @return true if upload was resumed, false if not paused or not uploading
     */
    public boolean resumeUpload() {
        if (!uploadPaused || !uploadInProgress) {
            return false;
        }

        uploadPaused = false;
        reportUploadEvent("resume", false, "Upload resumed after memory recovery", null);

        // Resume the upload task if it was paused
        if (uploadTask != null && uploadTask.isPaused()) {
            uploadTask.resume();
            Log.d(TAG, "Upload task resumed");
        } else {
            // Otherwise continue from where we left off
            uploadImage(currentUploadIndex);
            Log.d(TAG, "Upload restarted from index " + currentUploadIndex);
        }

        return true;
    }

    /**
     * Check if upload is currently paused
     * @return true if upload is paused
     */
    public boolean isUploadPaused() {
        return uploadPaused;
    }

}
