package com.openusage.app.TextBasedEventData;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.openusage.app.FirebaseSettings.FirebaseManagerSingleton;
import com.openusage.app.FirebaseSettings.UtilsForFirebaseSettings;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.SetOptions;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


// Memory management New Class

public class EventUploader {

    private static volatile EventUploader instance;
    private final String TAG = "EventUploader";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DataStorage dataStorage = DataStorage.getInstance();
    private boolean isUploading = false;
    private final Context context;
    private final Lock uploadLock = new ReentrantLock();

    private boolean lowMemoryLock = false;

    // Private constructor to prevent direct instantiation
    private EventUploader(Context context) {
        this.context = context.getApplicationContext(); // Prevent memory leaks
    }

    // Singleton getInstance() method (Thread-safe)
    public static EventUploader getInstance(Context context) {
        if (instance == null) {
            synchronized (EventUploader.class) {
                if (instance == null) {
                    instance = new EventUploader(context);
                }
            }
        }
        return instance;
    }

    public void acquireLowMemoryLock() {
        lowMemoryLock = true;
    }

    public void releaseLowMemoryLock(){
        lowMemoryLock = false;
    }


    // Runnable to upload events every 10 seconds
    private final Runnable uploadRunnable = new Runnable() {
        @Override
        public void run() {
            uploadEvents(); // Upload in a synchronized manner
            handler.postDelayed(this, 5000);
        }
    };

    public void startUploading() {
        handler.postDelayed(uploadRunnable, 5000);
    }

    public void stopUploading() {
        handler.removeCallbacks(uploadRunnable);
    }



    private void uploadEvents() {
        if (uploadLock.tryLock()) { // Acquire lock if available (non-blocking)
            try {
                if (!isUploading && NetworkUtils.isInternetAvailable(context)) {
                    isUploading = true;

                    String subject_id = UtilsForFirebaseSettings.getCodeAndNumber(context);

                    if (!subject_id.isEmpty()) {
                        Map<String, String> eventData = dataStorage.getEvents();
                        if (eventData.isEmpty()) {
                            isUploading = false;
                            return;
                        }

                        FirebaseManagerSingleton.getFirestore().collection("ticker").document(subject_id)
                                .set(eventData, SetOptions.merge())
                                .addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> task) {
                                        Log.d(TAG, "Events " + eventData);
                                        dataStorage.clearEvents();
                                        isUploading = false;
                                        Log.d(TAG, "Pushed");
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.d(TAG, e.toString());
                                    isUploading = false;
                                });
                    }
                }
            } finally {
                uploadLock.unlock(); // Ensure lock is released after execution
            }
        } else {
            Log.d(TAG, "Upload already in progress, skipping this request.");
        }

    }




    public void uploadImmediately(Map<String, String> eventData) {
        if (eventData == null || eventData.isEmpty()) {
            Log.d(TAG, "No data to upload.");
            return;
        }

        // Acquire lock to ensure only one thread uploads at a time
//        uploadLock.lock();

            if (NetworkUtils.isInternetAvailable(context)) {
                String subject_id = UtilsForFirebaseSettings.getCodeAndNumber(context);

                if (!subject_id.isEmpty()) {
                    Log.d(TAG, "Uploading event data immediately: " + eventData);

                    FirebaseManagerSingleton.getFirestore().collection("ticker").document(subject_id)
                            .set(eventData, SetOptions.merge())
                            .addOnCompleteListener(task -> {
                                Log.d(TAG, "Immediate upload successful: " + eventData);
                                dataStorage.clearEvents(); // Clear stored events after upload
                            })
                            .addOnFailureListener(e -> Log.e(TAG, "Immediate upload failed", e));
                }
            }

//        finally {
//            uploadLock.unlock(); // Always release the lock
//        }
    }

    public void destroy() {
        stopUploading();
        handler.removeCallbacksAndMessages(null);  // Remove all callbacks

        // Release singleton instance
        synchronized (EventUploader.class) {
            instance = null;
        }
    }


}
