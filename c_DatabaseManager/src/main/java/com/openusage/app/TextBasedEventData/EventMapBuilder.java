package com.openusage.app.TextBasedEventData;

import java.util.HashMap;
import java.util.Map;

import com.openusage.app.EventTimestamp;


/**
 * May 7, 2025
 * This class is used to add default fields and combine with additional fields
 */

public class EventMapBuilder {

    // Single instance of EventTimestamp to avoid creating a new one every time
    private static final EventTimestamp timestamp = new EventTimestamp();

    static HashMap<String, String> completeMap = new HashMap<>();

    public static HashMap<String, String> buildCompleteMap(Map<String, String> additionalFields,String type) {


        // Create a new HashMap to hold the default fields

        completeMap.clear();

        // Adding default fields
        completeMap.put("time", timestamp.getGMTTime());
        completeMap.put("time-local", timestamp.getSystemClockTimestring());
        completeMap.put("type", type);


        // Add all additional fields passed as parameter
        if (additionalFields != null) {
            completeMap.putAll(additionalFields);  // Merge additional fields with default ones
        }

        // Return the complete map
        return completeMap;
    }


}


