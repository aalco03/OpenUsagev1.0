package com.openusage.app.TextBasedEventData;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.openusage.app.DatabaseHelper.LogInPreference;
import com.openusage.app.EventTimestamp;
import com.openusage.app.FirebaseSettings.FirebaseManagerSingleton;
import com.openusage.app.FirebaseSettings.UtilsForFirebaseSettings;
import com.openusage.app.modulemanager.ModuleCharacteristics;

/**
 * May 7, 2025
 * This class is used to upload events to firebase
 *
 */

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;


// Memory management New Class
// some changes were done in existing functions to reduce overload on memory

public class EventUploaderToFireStore {

    private static final String TAG = "EventUploaderToFireStore";
    private static EventUploaderToFireStore instance;
    private final EventTimestamp timestamp;
    private final Context context;
    LogInPreference sharedPref;


    private EventUploaderToFireStore(Context context) {
        this.timestamp = new EventTimestamp();
        this.context = context.getApplicationContext();
        sharedPref = new LogInPreference(context.getApplicationContext());
    }

    public static synchronized EventUploaderToFireStore getInstance(Context context) {
        if (instance == null) {
            instance = new EventUploaderToFireStore(context);
        }
        return instance;
    }

    private AtomicBoolean isUploading = new AtomicBoolean(false);

    private AtomicBoolean isMemoryLow = new AtomicBoolean(false);

    public void startUploadOfflineToOnlineEvents(boolean IsUserTryingToLogout) {

        if (!NetworkUtils.isInternetAvailable(context)) {
            return; // No internet, skip upload
        }


        if (IsUserTryingToLogout){

            isUploading.set(false);
        }else{
            if (isMemoryLow.get()) {
                return;
            }
        }

        if (isUploading.get()) {
            Log.d(TAG, "Upload in progress, waiting for next round...");

            return; // Wait for current upload to finish
        }

        isUploading.set(true); // Mark upload as in progress

        WeakReference<Context> contextRef = new WeakReference<>(context);
        EventDatabaseHelper dbHelper = EventDatabaseHelper.getInstance(contextRef.get());

        Cursor cursor = dbHelper.getLimitedEvents(300);


        try { // Fetch only 10 entries at a time
            if (cursor == null || cursor.getCount() == 0) {
                Log.d(TAG, "No offline events to upload.");
                isUploading.set(false);
                return;
            }

            String subjectId = UtilsForFirebaseSettings.getCodeAndNumber(context);

            if (subjectId.isEmpty()) {
                Log.d(TAG, "Subject ID is empty. Cannot upload.");
                isUploading.set(false);
                return;
            }

            FirebaseFirestore db = FirebaseManagerSingleton.getFirestore();


            CollectionReference eventCollection = db.collection("users").document(subjectId).collection("events");

            List<Integer> eventIds = new ArrayList<>(); // Store IDs for deletion
            List<Map<String, Object>> eventsList = new ArrayList<>();

            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String eventName = cursor.getString(cursor.getColumnIndexOrThrow("event_name"));
                String eventData = cursor.getString(cursor.getColumnIndexOrThrow("event_data"));

                eventIds.add(id);

                Map<String, Object> eventMap = new HashMap<>();
                eventMap.put("event_name", eventName);
                eventMap.put("event_data", eventData);

                eventsList.add(eventMap);
            }

            uploadBatch(eventCollection, eventsList, eventIds, dbHelper,IsUserTryingToLogout);
        } catch (Exception e) {
            Log.e(TAG, "Error processing events from SQLite: ", e);
            isUploading.set(false);
        }finally {
            isUploading.set(false);
        }
    }



// Memory management New function

    private void uploadBatch(CollectionReference eventCollection, List<Map<String, Object>> eventsData, List<Integer> eventIds, EventDatabaseHelper dbHelper, boolean IsUserTryingLogout) {


        FirebaseFirestore db = FirebaseManagerSingleton.getFirestore();

        WriteBatch batch = db.batch();

        if (eventsData.isEmpty()) {
            isUploading.set(false);
            return;
        }


        Log.d(TAG, "Uploading batch of " + eventsData.size() + " events");

        for (Map<String, Object> event : eventsData) {
            String eventName = (String) event.get("event_name");
            String eventData = (String) event.get("event_data");

            if (eventName != null && !eventName.isEmpty()) {
                DocumentReference ref = eventCollection.document(eventName);
                batch.set(ref, new Gson().fromJson(eventData, Map.class));
            }
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Batch upload successful. Deleting uploaded events...");

            for (int id : eventIds) {
                dbHelper.deleteEvent(id);
                Log.d(TAG, "Deleted event ID: " + id);
            }

            isUploading.set(false);

            if (IsUserTryingLogout){
                if (EventDatabaseHelper.getInstance(context).getTotalEventCount() > 0){
                    startUploadOfflineToOnlineEvents(true);
                }else {
                    EventDatabaseHelper.getInstance(context).deleteDatabase(context);
                }
            }

        }).addOnFailureListener(e -> {
            Log.e(TAG, "Batch upload failed: " + e.getMessage());
            isUploading.set(false);
        });
    }


    // Memory management New function


    public void uploadSingleEvent(String eventName, HashMap<String, String> eventData, Context context) {



        if (!NetworkUtils.isInternetAvailable(context)) return;

        String subjectId = UtilsForFirebaseSettings.getCodeAndNumber(context);

        if (!subjectId.isEmpty()) {


            FirebaseFirestore db = FirebaseManagerSingleton.getFirestore();


            HashMap<String, String> map = HashMapPool.getMap();
            HashMap<String, String> map1 = HashMapPool.getMap();
            map.put(eventName, timestamp.getTimestringFriendly() + EventOperationManager.getInstance(context).UpdateTickerField(eventName, eventData));
            map1.put("MostRecentEventTime", timestamp.getTimestringFriendly());


            db.collection("users")
                    .document(subjectId)
                    .collection("events")
                    .document(GenerateEventId(eventName))
                    .set(eventData)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Event Added"))
                    .addOnFailureListener(e -> Log.d(TAG, e.getMessage()));

            EventUploader.getInstance(context).uploadImmediately(map);
            EventUploader.getInstance(context).uploadImmediately(map1);

            HashMapPool.releaseMap(map);
            HashMapPool.releaseMap(map1);
        }
    }

    public void SetIsMemoryLow(boolean isLowMemory) {
        isMemoryLow.set(isLowMemory);
    }


    public String GenerateEventId(String className) {
        return  className + " "+ timestamp +"_"+ ModuleCharacteristics.getInstance().getLocationEventCharacteristics().get("id");
    }

    // Add a cleanup method
    public static synchronized void destroy() {
        if (instance != null) {
            // Release any resources
            instance = null;
        }
    }

}

