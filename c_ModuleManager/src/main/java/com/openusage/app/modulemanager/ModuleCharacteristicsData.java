package com.openusage.app.modulemanager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ModuleCharacteristicsData {
    private final String className;
    private final String type;
    private final String updateTicker;
    private final String id;
    private final String timestamp;
    private final String timestampLocal;

    public ModuleCharacteristicsData(String className, String type, String updateTicker) {
        this.className = className;
        this.type = type;
        this.updateTicker = updateTicker;
        this.id = generateShortID();
        this.timestamp = new EventTimestamp().getTimestring();
        this.timestampLocal = new EventTimestamp().getSystemClockTimestring();
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        map.put("className", className);
        map.put("type", type);
        map.put("updateTicker", updateTicker);
        map.put("id", id);
        map.put("timestamp", timestamp);
        map.put("timestampLocal", timestampLocal);
        return map;
    }

    private static String generateShortID() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

