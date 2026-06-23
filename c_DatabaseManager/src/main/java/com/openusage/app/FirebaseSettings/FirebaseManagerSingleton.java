package com.openusage.app.FirebaseSettings;


import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import java.lang.ref.WeakReference;

/**
 * A safer implementation of Firebase manager that avoids memory leaks
 * by not storing FirebaseFirestore in a static field.
 */
public class FirebaseManagerSingleton {
    private static final String TAG = "FirebaseManager";

    // Using a ThreadLocal to confine FirebaseFirestore instances to specific threads
    private static final ThreadLocal<FirebaseFirestore> firestoreThreadLocal =
            new ThreadLocal<>();

    // WeakReference to application context to avoid activity/service leaks
    private static WeakReference<Context> applicationContextRef;

    /**
     * Initialize the FirebaseManager with application context
     * Call this once in your Application class onCreate()
     */
    public static void init(Context context) {
        if (context != null) {
            applicationContextRef = new WeakReference<>(context.getApplicationContext());
        }
    }

    /**
     * Get FirebaseFirestore instance in a way that avoids static Context references
     * Each thread will get its own instance, reused for that thread
     */
    public static FirebaseFirestore getFirestore() {
        FirebaseFirestore instance = firestoreThreadLocal.get();

        if (instance == null) {
            instance = FirebaseFirestore.getInstance();
            firestoreThreadLocal.set(instance);
            Log.d(TAG, "Created new FirebaseFirestore instance for thread: " +
                    Thread.currentThread().getName());
        }

        return instance;
    }

    /**
     * Clear the FirebaseFirestore instance for the current thread
     * Call this when you're done using FirebaseFirestore in a thread
     */
    public static void clearFirestoreForThread() {
        firestoreThreadLocal.remove();
        Log.d(TAG, "Cleared FirebaseFirestore instance for thread: " +
                Thread.currentThread().getName());
    }

    /**
     * Clean up resources when your app is shutting down
     * Call this in your Application class onTerminate() or service onDestroy()
     */
    public static void shutdown() {
        firestoreThreadLocal.remove();
        applicationContextRef = null;
        Log.d(TAG, "FirebaseManager shutdown complete");
    }

    /**
     * Get application context safely
     */
    public static Context getApplicationContext() {
        return applicationContextRef != null ? applicationContextRef.get() : null;
    }
}
