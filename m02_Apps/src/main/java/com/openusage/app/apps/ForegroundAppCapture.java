package com.openusage.app.apps;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import com.openusage.app.TextBasedEventData.EventOperationManager;
import com.openusage.app.TextBasedEventData.HashMapPool;
import com.openusage.app.modulemanager.ModuleCharacteristics;

public class ForegroundAppCapture {

    private static final String TAG = "ForegroundAppChecker";
    private Context context;
    private long interval;
    private String lastForegroundApp;
    private Handler handler;
    private Runnable runnableTask;


    public ForegroundAppCapture(Context context, long interval) {
        this.context = context;
        this.interval = interval;
        this.lastForegroundApp = "";
        this.handler = new Handler();
    }

    // Updates the interval only if it's different from the current one
    public void updateIntervalIfNeeded(long newInterval) {
        if (this.interval != newInterval) {
            Log.d(TAG, "Updating foreground app check interval from " + this.interval + " to " + newInterval);

            // Stop current task
            stopChecking();

            // Update interval
            this.interval = newInterval;

            // Restart the checking with new interval
            startChecking();
        } else {
            Log.d(TAG, "New interval is same as current. No update applied.");
        }
    }


    public void startChecking() {
        runnableTask = new Runnable() {
            @Override
            public void run() {
                String currentForegroundApp = getForegroundApp();

                if (!currentForegroundApp.equals(lastForegroundApp)) {
                    logEventToOfflineDatabase(currentForegroundApp);
                    lastForegroundApp = currentForegroundApp;
                }

                // Schedule the next check after the specified interval
                handler.postDelayed(this, interval);
            }
        };

        // Start the first check
        handler.post(runnableTask);
    }

    public void stopChecking() {
        handler.removeCallbacks(runnableTask);
    }

    private String getForegroundApp() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();
        List<UsageStats> appList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 10, time);

        if (appList != null && appList.size() > 0) {
            SortedMap<Long, UsageStats> sortedMap = new TreeMap<>();
            for (UsageStats usageStats : appList) {
                sortedMap.put(usageStats.getLastTimeUsed(), usageStats);
            }

            if (!sortedMap.isEmpty()) {
                String packageName = sortedMap.get(sortedMap.lastKey()).getPackageName();
                Log.d(TAG, "Current foreground app: " + packageName);
                return packageName;
            }
        }

        return ""; // Return empty if no foreground app is found
    }

    private void logEventToOfflineDatabase(String packageName) {

        if (packageName.isEmpty()) return;
        HashMap<String, String> foregroundAppMap = HashMapPool.getMap();
        foregroundAppMap.put("package", packageName);


        EventOperationManager.getInstance(context).addEvent(ModuleCharacteristics.getInstance().getForegroundAppModuleCharacteristics(),foregroundAppMap);

//        HashMapPool.releaseMap(foregroundAppMap);

    }
}

