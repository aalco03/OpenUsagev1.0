package com.openusage.app.TextBasedEventData;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

public class MapUtils {

    // Use a single static Gson instance to avoid creating new objects repeatedly
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    public static String serializeMap(Map<String, String> map) {
        return GSON.toJson(map, MAP_TYPE);
    }

    public static Map<String, String> deserializeMap(String jsonString) {
        return GSON.fromJson(jsonString, MAP_TYPE);
    }
}



