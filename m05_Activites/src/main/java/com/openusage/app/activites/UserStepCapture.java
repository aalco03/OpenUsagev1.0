package com.openusage.app.activites;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.openusage.app.TextBasedEventData.EventOperationManager;
import com.openusage.app.TextBasedEventData.HashMapPool;
import com.openusage.app.modulemanager.ModuleCharacteristics;

public class UserStepCapture implements SensorEventListener {

    private static final String TAG = "StepCounter";
    private final SensorManager sensorManager;
    private final Sensor stepCounterSensor;
    private int totalSteps = 0;  // Total steps since boot
    private int previousTotalSteps = 0;  // Steps in the last interval
    private final Context context;

    // Using ScheduledExecutorService for precise timing
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledTask;
    private long intervalMillis;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public UserStepCapture(Context context, long intervalMillis) {
        this.context = context;
        this.intervalMillis = intervalMillis;

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        } else {
            stepCounterSensor = null;
            Log.e(TAG, "SensorManager is not available");
        }
    }

    /**
     * Start step counting and schedule periodic reports
     */
    public void start() {
        if (stepCounterSensor != null) {
            // Register sensor listener
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
            startScheduledReporting();
            Log.d(TAG, "Step counter started with interval: " + intervalMillis + "ms");
        } else {
            Log.e(TAG, "Step counter sensor not available on this device");
            // Even without sensor, we can still report zeros at regular intervals
            startScheduledReporting();
        }
    }

    /**
     * Stop step counting and cancel scheduled tasks
     */
    public void stop() {
        if (stepCounterSensor != null) {
            sensorManager.unregisterListener(this);
        }

        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(false);
            Log.d(TAG, "Step counter stopped");
        }

        // Shutdown the scheduler properly
        scheduler.shutdown();
    }

    /**
     * Schedule fixed-delay execution for reporting steps to prevent task accumulation
     * when the process transitions between cached/uncached states
     */
    private void startScheduledReporting() {
        // Cancel any existing scheduled task
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(false);
        }

        // Schedule with fixed delay to avoid task accumulation when process is cached/uncached
        scheduledTask = scheduler.scheduleWithFixedDelay(() -> {
            // Calculate steps in this interval
            int currentSteps = totalSteps - previousTotalSteps;

            // Log the step count
            Log.d(TAG, "Steps in interval: " + currentSteps);

            // Post to main thread for database operations
            mainHandler.post(() -> sendStepsToDatabase(currentSteps));

            // Update for next interval
            previousTotalSteps = totalSteps;
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Updates the interval only if it's different from the current one.
     * @param newIntervalMillis New reporting interval in milliseconds
     */
    public void updateIntervalIfNeeded(long newIntervalMillis) {
        if (this.intervalMillis != newIntervalMillis) {
            Log.d(TAG, "Updating interval from " + this.intervalMillis + " to " + newIntervalMillis);

            // Update interval
            this.intervalMillis = newIntervalMillis;

            // Restart scheduled task with new interval
            startScheduledReporting();
        } else {
            Log.d(TAG, "New interval is the same as current. No changes made.");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            // Update total steps since the device booted
            totalSteps = (int) event.values[0];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Log accuracy changes
        Log.d(TAG, "Step sensor accuracy changed to: " + accuracy);
    }

    /**
     * Send step count to database
     * @param steps Number of steps in the interval
     */
    private void sendStepsToDatabase(int steps) {
        HashMap<String, String> stepsCount = HashMapPool.getMap();
        stepsCount.put("count", String.valueOf(steps));

        EventOperationManager.getInstance(context).addEvent(
                ModuleCharacteristics.getInstance().getStepCountEventCharacteristics(),
                stepsCount
        );

        // Return the map to the pool after use
        HashMapPool.releaseMap(stepsCount);
    }
}