package com.openusage.app.TextBasedEventData;

import java.util.HashMap;
import java.util.Map;

import com.openusage.app.modulemanager.EventTimestamp;
import com.openusage.app.modulemanager.ModuleCharacteristics;


// Memory management New Class


public class EventData {
    private final String time;
    private final String timeLocal;
    private final String type;
    private final String UniqueEventId;
    private final Map<String, String> additionalFields;

    // Private constructor to enforce the use of Builder
    private EventData(Builder builder) {
        this.time = builder.time;
        this.UniqueEventId = builder.UniqueEventId;
        this.timeLocal = builder.timeLocal;
        this.type = builder.type;
        this.additionalFields = builder.additionalFields;
    }

    // Getter Methods
    public String getTime() {
        return time;
    }

    public String getTimeLocal() {
        return timeLocal;
    }

    public String getUniqueEventId() {
        return UniqueEventId;
    }

    public String getType() {
        return type;
    }

    public Map<String, String> getAdditionalFields() {
        return additionalFields;
    }

    // Convert to a single map (for database insertion)
    public Map<String, String> toMap() {
        Map<String, String> eventMap = new HashMap<>();
        eventMap.put("time", time);
        eventMap.put("time-local", timeLocal);
        eventMap.put("type", type);
        if (additionalFields != null) {
            eventMap.putAll(additionalFields);
        }
        return eventMap;
    }



    // Builder Class for creating EventData objects efficiently
    public static class Builder {

        private final String type;
        private final String UniqueEventId;
        private final String time;
        private final String timeLocal;
        private Map<String, String> additionalFields = new HashMap<>();
        EventTimestamp timestamp = new EventTimestamp();

        public Builder(String type,String UniqueEventId) {
            this.time = timestamp.getGMTTime();
            this.UniqueEventId = GenerateEventId(UniqueEventId);
            this.timeLocal = timestamp.getSystemClockTimestring();
            this.type = type;
        }

        public String GenerateEventId(String className) {
            return  className + " "+ timestamp +"_"+ ModuleCharacteristics.getInstance().getLocationEventCharacteristics().get("id");
        }

        public Builder addField(String key, String value) {
            this.additionalFields.put(key, value);
            return this;
        }

        public Builder addFields(Map<String, String> fields) {
            this.additionalFields.putAll(fields);
            return this;
        }

        public EventData build() {
            return new EventData(this);
        }
    }
}

