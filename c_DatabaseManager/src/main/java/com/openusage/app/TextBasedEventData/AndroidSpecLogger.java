package com.openusage.app.TextBasedEventData;

import android.content.Context;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

import com.openusage.app.FirebaseSettings.UtilsForFirebaseSettings;
import com.openusage.app.modulemanager.ModuleCharacteristics;

public class AndroidSpecLogger {
    private final Handler handler = new Handler();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private long intervalMs;
    CollectionReference collectionReference;
    EventData event;
    // Time interval in milliseconds
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            sendDataToFirestore(); // Send data to Firestore
            handler.postDelayed(this, intervalMs); // Restart timer
        }
    };

    private Context context;

    // Constructor to initialize timer
    public AndroidSpecLogger(long intervalMs, Context context) {
        this.intervalMs = intervalMs;
        this.context = context;
    }

    // Start the timer
    public void startTimer() {
        handler.postDelayed(runnable, intervalMs);
    }

    // Stop the timer
    public void stopTimer() {
        handler.removeCallbacks(runnable);
    }

    // Function to send data to Firestore
    private void sendDataToFirestore() {

         event = new EventData.Builder(ModuleCharacteristics.getInstance().getAndroidSpecsEventCharacteristics().get("type"),ModuleCharacteristics.getInstance().getAndroidSpecsEventCharacteristics().get("className"))
                .addFields(createPhoneSpecMap())
                .build();

        collectionReference = FirebaseFirestore.getInstance().collection("users").document(UtilsForFirebaseSettings.getCodeAndNumber(context)).collection("specs");
        collectionReference.document(event.getUniqueEventId()).set(event.toMap()).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {

            }
        });

    }


    private Map<String, String> createPhoneSpecMap()
    {


        HashMap<String, String> map = new HashMap<>();
        map.put("fingerprint", Build.FINGERPRINT);
        map.put("manufacturer", Build.MANUFACTURER);
        map.put("brand", Build.BRAND);
        map.put("model", Build.MODEL);
        map.put("product", Build.PRODUCT);
        map.put("display-id", Build.DISPLAY);
        map.put("android-version", Build.VERSION.RELEASE);

        return map;
    }
}

