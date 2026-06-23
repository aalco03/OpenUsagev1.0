package com.openusage.app.TextBasedEventData;

import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;



// Memory management New Class

public class HashMapPool {
    private static final int POOL_SIZE = 100; // Adjust based on event load
    private static final Queue<HashMap<String, String>> pool = new ConcurrentLinkedQueue<>();

    public static HashMap<String, String> getMap() {
        HashMap<String, String> map = pool.poll(); // Get from pool
        return (map != null) ? map : new HashMap<>(); // Create new if pool is empty
    }

    public static void releaseMap(HashMap<String, String> map) {
        map.clear(); // Ensure old data is removed
        if (pool.size() < POOL_SIZE) {
            pool.offer(map); // Return to pool if not full
        }
    }
}


