package com.openusage.app.TextBasedEventData;


import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.openusage.app.modulemanager.EventTimestamp;


/** May 7, 2025
 * This class is used to perform CURD operations on local database
 */


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EventOperationManager {
    private static final String TAG = "EventOperationManager";
    private static EventOperationManager instance;  // Singleton instance
    private final EventDatabaseHelper databaseHelper;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    private final List<EventData> eventBuffer = new ArrayList<>(); // Buffer to store events
    private static final int BATCH_SIZE = 50;  // Adjust batch size for optimization
    private static final long FLUSH_INTERVAL_MS = 60000;  // Flush every 60 seconds
    private static final Object LOCK = new Object();
    EventTimestamp timestamp = new EventTimestamp();// Synchronization lock


    // Memory management New function

    // Private constructor for Singleton pattern
    private EventOperationManager(Context context) {
        this.databaseHelper = EventDatabaseHelper.getInstance(context.getApplicationContext());
        this.executorService = Executors.newSingleThreadExecutor(); // Background thread
        this.scheduledExecutor = Executors.newScheduledThreadPool(1); // Scheduled flush thread
        
        // Start periodic flush timer
        scheduledExecutor.scheduleAtFixedRate(this::flushEventsToDB, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
        Log.d(TAG, "EventOperationManager initialized with periodic flush every " + (FLUSH_INTERVAL_MS / 1000) + " seconds");
    }

    // Memory management New function


    // Singleton instance method (Thread-Safe)
    public static synchronized EventOperationManager getInstance(Context context) {
        if (instance == null) {
            instance = new EventOperationManager(context);
        }
        return instance;
    }


    // Memory management New function


    public void addEvent(Map<String, String> moduleInfo, HashMap<String, String> eventDetails) {
        synchronized (LOCK) {
            EventData event = new EventData.Builder(moduleInfo.get("type"),moduleInfo.get("className"))
                    .addFields(eventDetails)
                    .build();

            Log.d(TAG, "Event : " + event.toMap().toString());


            eventBuffer.add(event);  // Add event to buffer

            if (Objects.equals(moduleInfo.get("updateTicker"), "1")) {
                    DataStorage.getInstance().addEvent("MostRecentEventTime", timestamp.getTimestringFriendly());
                    DataStorage.getInstance().addEvent(moduleInfo.get("className"), timestamp.getTimestringFriendly() + UpdateTickerField(moduleInfo.get("className"), eventDetails));

            }

            // Check if we need to batch write
            if (eventBuffer.size() >= BATCH_SIZE) {
                flushEventsToDB(); // Batch insert into SQLite
            }
        }
    }

    // Memory management New function

    // Batch insert events into SQLite
    private void flushEventsToDB() {

        if (!executorService.isShutdown()) {
            executorService.execute(() -> {
                synchronized (LOCK) {
                    if (!eventBuffer.isEmpty()) {
                        try {
                            List<EventData> batch = new ArrayList<>(eventBuffer); // Copy buffer
                            eventBuffer.clear(); // Clear buffer before inserting

                            databaseHelper.openDatabase(); // Ensure DB is open
                            for (EventData event : batch) {
                                databaseHelper.insertEvent(event.getUniqueEventId(), MapUtils.serializeMap(event.toMap()));
                            }
                            Log.d(TAG, "Batch inserted " + batch.size() + " events into DB.");
                        } catch (Exception e) {
                            Log.e(TAG, "Error inserting batch events: " + e.getMessage());
                        }
                    }
                }
            });

        }
    }

    public String UpdateTickerField(String ModuleInfo, HashMap<String, String> EventData) {
        Log.e(TAG, "ModuleInfo: " + ModuleInfo);
        StringBuilder result = new StringBuilder(" [");
        if (Objects.equals(ModuleInfo, "InternetEvent")){
            result.append(EventData.get("activity"));
        } else if (Objects.equals(ModuleInfo, "LogInOutEvent")) {
            result.append(EventData.get("type"));
        } else if (Objects.equals(ModuleInfo, "ScreenOnOffEvent")) {
            result.append(EventData.get("screen"));
        } else if (Objects.equals(ModuleInfo, "ScreenshotPauseEvent")) {
            result.append(EventData.get("type"));
        }else{
            return "";
        }
        result.append("]");
        return result.toString();
    }

    // Flush remaining events on app shutdown or when needed
//    public void flushAndShutdown() {
//        flushEventsToDB();  // Final flush
//        executorService.shutdown(); // Shut down executor service
//    }

    public void flushAndShutdown() {
        flushEventsToDB();  // Final flush
        executorService.shutdown(); // Shut down executor service

        try {
            // Wait for tasks to complete with a timeout
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }



    // FIX: Add proper cleanup method
    public void destroy() {
        // Shutdown scheduled executor
        if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
            scheduledExecutor.shutdown();
            try {
                if (!scheduledExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Shutdown main executor
        if (executorService != null && !executorService.isShutdown()) {
            flushEventsToDB();
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Release singleton instance
        synchronized (EventOperationManager.class) {
            instance = null;
        }
    }

}

