package com.openusage.app.FirebaseSettings;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.openusage.app.DatabaseHelper.LogInPreference;
import com.openusage.app.FirebaseSettingsObserver;

public class SettingsManager
{
    private static SettingsManager mInstance;
    public final Map<String, Integer> settings;

    public static final String TAG = "SettingsManager ";
    private static final String SHARED_PREF_PREFIX = "setting_";



    // ====================================================================== //
    //                            INITIALIZATION                              //
    // ====================================================================== //


    private SettingsManager() {
        this.settings = new HashMap<>();
    }


    FirebaseSettingsObserver observer;

    public static SettingsManager create(FirebaseSettingsObserver observer) {
        if (mInstance != null) {
            return mInstance;
        }else{
            mInstance = new SettingsManager();
        }

        mInstance.observer = observer;
        return mInstance;
    }

    public static void destroy(Context context) {
        if (mInstance != null) {
            mInstance.saveToDisk(context);
            mInstance = null;
        }
    }

    public static SettingsManager get() {
        return mInstance;
    }

    public static boolean exists() {
        return mInstance != null;
    }

    public void load(Context context, DatabaseListener listener)
    {
        Log.i(TAG,"load from setting manager called");
        resetToLocalDefaults();
        loadFromDisk(context);
        loadFromDatabase(context, false, listener);
    }


    // ====================================================================== //
    //                               SETTINGS                                 //
    // ====================================================================== //


    public int getVal(String name) {
        return getVal(name, -1, true);
    }

    public int getVal(String name, int defaultValue, boolean errorIfDefault)
    {
        // In theory, the desired setting always exists.
        Integer result = settings.get(name);
        if (result != null) return result;

        // But if it doesn't, report an error.
//        if (errorIfDefault && EventReporter.exists())
//        {
//            String message = "Setting " + name + " has defaulted to " + defaultValue;
//            Log.e(TAG, message);
//            EventReporter.get().reportError(TAG, message);
//        }

        return -1;
    }

    /**
     * Static convenience method for getVal(String)
     */
    public static int val(String name)
    {
        if (mInstance == null) return 0;
        return mInstance.getVal(name);
    }


    // ====================================================================== //
    //                           OFFLINE MANAGEMENT                           //
    // ====================================================================== //


    /**
     * Sets the settings to the built-in defaults. Ideally this never happens because we've loaded from DB.
     */
    public void resetToLocalDefaults()
    {
        settings.clear();
        settings.put("settings-group-override", 1);
        settings.put("settings-refresh-interval", 250000);
        settings.put("data-nontext-upload-wifi-only", 1);
        settings.put("screenshot-interval", 5000);
        settings.put("screenshot-absolute-timing", 1);
        settings.put("screenshot-check-interval", 70000);
        settings.put("data-nontext-upload-interval", 5 * 60 * 1000);
        settings.put("foreground-app-check-interval", 1000);
        settings.put("gps-location-interval", 60 * 1000);
        // Gps is enabled if set to one Or if set to zero it wont record Gps data
        settings.put("gps-enabled", 1);
        settings.put("force-image-quality", 10);
        settings.put("kill-switch", 0);
        settings.put("pa-stepcounts-interval", 60 * 1000);
        settings.put("pa-enabled", 1);
        // TEMPORARY: Reduced to 1 minute for testing - REVERT TO 5 * 60 * 1000 for production
        settings.put("data-text-upload-interval", 1 * 60 * 1000);
        settings.put("data-text-upload-wifi-only", 1);
        settings.put("specs-check-interval", 86400000); // one day (24 hours)
        settings.put("screenshots-enabled", 1); // 1 = require media projection, 0 = text-only mode
    }

    public void loadFromDisk(Context context)
    {
        // Load all preferences from shared prefs.
        LogInPreference sharedPref = new LogInPreference(context);
//        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        Map<String, ?> allPrefs = sharedPref.SharedPrefGetAll();

        // Enumerate preferences and load ones that match "setting_...".
        for (String pref : allPrefs.keySet())
        {
            if (pref.startsWith(SHARED_PREF_PREFIX))
            {
                // Get the un-prefixed name of the setting and its value.
                String setting_name = pref.substring(SHARED_PREF_PREFIX.length());
                Integer val = (Integer) allPrefs.get(pref);

                if (val != null)
                {
                    settings.put(setting_name, val);
                }
            }
        }
    }

    private void saveToDisk(Context context)
    {

        LogInPreference sharedPref = new LogInPreference(context);

        // Enumerate settings and save them to shared prefs.
        for (String setting : settings.keySet())
        {
            Integer val = settings.get(setting);
            if (val != null) {
                sharedPref.AddSHARED_PREF_PREFIXSetting(SHARED_PREF_PREFIX + setting, val);
//                sharedPref.edit().putInt(SHARED_PREF_PREFIX + setting, val).apply();
            }
        }
    }



    // ====================================================================== //
    //                         DATABASE INTERACTION                           //
    // ====================================================================== //


    /**
     * Loads desired settings for this user from Firebase. This will load from either the user's
     * settings or the collection group's settings, depending on whether the settings override is
     * enabled.
     * @param context Application context
     * @param clear Whether to clear the existing settings data before loading.
     * @param listener For receiving success/failure of the load operation.
     */
    public void loadFromDatabase(final Context context, final boolean clear, final DatabaseListener listener)
    {

        Log.i(TAG,"Load From Database Called");


        FirebaseFirestore.getInstance().collection("users").document(UtilsForFirebaseSettings.getCodeAndNumber(context)).collection("settings").document("user_settings").addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot dataSnapshot, @Nullable FirebaseFirestoreException error) {


                if (dataSnapshot != null){
                    if (!dataSnapshot.exists()
                            || (dataSnapshot.get("settings-group-override") != null
                            && dataSnapshot.get("settings-group-override").toString().equals("1")))
                    {

                        Log.i(TAG,String.valueOf(dataSnapshot.getData()));
                        Log.i(TAG,String.valueOf(dataSnapshot.get("settings-group-override")));

                        Log.d(TAG, "cloning collection group settings 268");
                        Log.d(TAG, "Group code " + UtilsForFirebaseSettings.getGroupCode(context));
//                        cloneCollectionGroupSettings(
//                                context,
//                                UtilsForFirebaseSettings.getDataSubjectId(context),
//                                UtilsForFirebaseSettings.getGroupCode(context),
//                                listener);


                        cloneCollectionGroupSettings(
                                context,
                                UtilsForFirebaseSettings.getCodeAndNumber(context),
                                UtilsForFirebaseSettings.getGroupCode(context),
                                listener);
                    }

                    // If group override is disabled, load the user's settings.
                    else
                    {

                        downloadUserSettings(context, clear, listener);
                    }
                }else {
                    Log.d(TAG, "239 null");

                }

            }
        });

        Log.d(TAG, "Begin load settings override option");

    }

    /**
     * Loads settings data from this user's profile on Firebase.
     * @param context Application context
     * @param clear Whether to clear the existing settings data before loading.
     * @param listener For receiving success/failure of the load operation.
     */
    public void downloadUserSettings(final Context context, final boolean clear, final DatabaseListener listener)
    {
        if (UtilsForFirebaseSettings.getSubjectId(context).isEmpty())
        {
            Log.d(TAG, "downloadUserSettings() -- not logged in!");
            if (listener != null) listener.onFailure();
            return;
        }


        FirebaseFirestore.getInstance().collection("users").document(UtilsForFirebaseSettings.getCodeAndNumber(context)).collection("settings").document("user_settings").get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot dataSnapshot) {

                if (dataSnapshot.exists())
                {
                    Log.d(TAG, "Complete load settings from user profile");

                    Log.i(TAG,dataSnapshot.getData().toString());
                    // We got the settings data! Load it.
                    loadFromDataSnapshot(dataSnapshot, clear,true);
                    saveToDisk(context);

                    if (listener != null) listener.onSuccess();
                }
                else
                {
                    Log.d(TAG, "User settings didn't exist; loading group settings profile now 314");
                    cloneCollectionGroupSettings(
                            context,
                            UtilsForFirebaseSettings.getCodeAndNumber(context),
                            UtilsForFirebaseSettings.getGroupCode(context),
                            listener);

                }

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                String message = "loadFromDatabase() listener cancelled";
                Log.e(TAG, message);
//                if (EventReporter.exists()) EventReporter.get().reportError(TAG, message);
                if (listener != null) listener.onFailure();
            }
        });
    }

    /**
     * Initializes the settings for this user based on study group. This should be called during
     * user registration.
     */
    public void cloneCollectionGroupSettings(final Context context, final String dataSubjectId, final String code, DatabaseListener resultListener)
    {
        final CollectionReference groupSettings = FirebaseFirestore.getInstance().collection("settings_profiles");


        Log.d(TAG, "cloneCollectionGroupSettings() -- begin");
        final DatabaseListener listener;

        // If they didn't give us a listener, create a dummy.
        if (resultListener == null) {
            listener = new DatabaseListener() {
                @Override
                public void onSuccess() { }

                @Override
                public void onFailure() { }
            };
        }
        else {
            listener = resultListener;
        }


        DocumentReference groupSettingsRef = groupSettings.document(code.toUpperCase());
        DocumentReference defaultSettingsRef = groupSettings.document("_default_");

        // Fetch default settings
        defaultSettingsRef.get().addOnCompleteListener(defaultsTask -> {
            if (defaultsTask.isSuccessful()) {
                DocumentSnapshot defaultsSnapshot = defaultsTask.getResult();
                if (defaultsSnapshot != null && defaultsSnapshot.exists()) {
                    Log.d(TAG, "cloneCollectionGroupSettings() -- fetched default settings");
                    loadFromDataSnapshot(defaultsSnapshot, false,true);

                    // Fetch group-specific settings
                    groupSettingsRef.get().addOnCompleteListener(groupTask -> {
                        if (groupTask.isSuccessful()) {
                            DocumentSnapshot groupSnapshot = groupTask.getResult();
                            if (groupSnapshot != null && groupSnapshot.exists()) {
                                Log.d(TAG, "cloneCollectionGroupSettings() -- fetched group-specific settings");
                                loadFromDataSnapshot(groupSnapshot, false,false);
                            } else {
                                Log.d(TAG, "cloneCollectionGroupSettings() -- no specific settings for this group; using just the defaults.");
                            }

                            // If we got here, all group settings have been loaded!
                            saveToDisk(context);
                            saveToDatabase(dataSubjectId, listener);
                        } else {
                            Log.e(TAG, "cloneCollectionGroupSettings() -- failed to fetch group-specific settings", groupTask.getException());
                            listener.onFailure();
                        }
                    });
                } else {
                    Log.d(TAG, "cloneCollectionGroupSettings() -- no default settings found");
                }
            } else {
                Log.e(TAG, "cloneCollectionGroupSettings() -- failed to fetch default settings", defaultsTask.getException());
                listener.onFailure();
            }
        });
    }

    public void saveToDatabase(String dataSubjectId, final DatabaseListener listener)
    {

        Log.d(TAG, "Begin save settings to user profile");

        // Make sure they gave us a data subject ID.
        if (dataSubjectId.isEmpty()) {
            listener.onFailure();
        }


        FirebaseFirestore.getInstance().collection("users").document(dataSubjectId).collection("settings")
                .document("user_settings").set(settings).addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Log.d(TAG, "Complete save settings to user profile");
                        if (listener != null) listener.onSuccess();
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (listener != null) listener.onFailure();
                    }
                });

    }

    private void loadFromDataSnapshot(DocumentSnapshot dataSnapshot, boolean clear, boolean IsDefault)
    {
        if (clear) settings.clear();

        Map<String, Object> data = dataSnapshot.getData();
        if (data != null) {

            List<String> keys = new ArrayList<String>();

            Log.i(TAG,"no null data");
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue().toString();
                // Print the key and value
                settings.put(key, Integer.parseInt(value));
                keys.add(key);
                System.out.println("Key: "+ TAG + key + ", Value: " + value);
            }

            if (!IsDefault){
                Log.i(TAG,"not default settings");
                observer.onSettingsChanged(keys);

            }


        }

    }

    public interface DatabaseListener
    {
        void onSuccess();
        void onFailure();
    }
}
