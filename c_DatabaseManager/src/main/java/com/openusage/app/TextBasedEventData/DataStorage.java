package com.openusage.app.TextBasedEventData;

import java.util.concurrent.ConcurrentHashMap;

// Memory management New Class


public class DataStorage {
    private static DataStorage instance;
    private final ConcurrentHashMap<String, String> eventTimestampMap;

    private DataStorage() {
        eventTimestampMap = new ConcurrentHashMap<>();
    }

    public static synchronized DataStorage getInstance() {
        if (instance == null) {
            instance = new DataStorage();
        }
        return instance;
    }

    // Store or update latest timestamp
    public void addEvent(String eventName, String timestamp) {
        eventTimestampMap.put(eventName, timestamp);
    }

    // Get all events for batch upload
    public ConcurrentHashMap<String, String> getEvents() {
        return new ConcurrentHashMap<>(eventTimestampMap); // Return a copy
    }

    // Clear events after upload
    public void clearEvents() {
        eventTimestampMap.clear();
    }
}

