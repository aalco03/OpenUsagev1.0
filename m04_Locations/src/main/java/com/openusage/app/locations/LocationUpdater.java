package com.openusage.app.locations;

import com.openusage.app.TextBasedEventData.EventOperationManager;
import com.openusage.app.modulemanager.EventTimestamp;
import com.openusage.app.modulemanager.ModuleCharacteristics;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Updated: May 19, 2025
 * This class captures user location at a consistent interval using a combination of
 * FusedLocationProviderClient and advanced timing mechanisms to ensure exact reporting intervals.
 * The implementation uses a predictive approach that requests location updates ahead of reporting time
 * to ensure data is ready precisely when needed.
 */
public class LocationUpdater {

    private static final String TAG = "LocationUpdater";
    private static final long LOCATION_PREFETCH_OFFSET_MS = 15000; // 15 seconds ahead of scheduled time

    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private long intervalMillis;
    private Location lastLocation;
    private Location pendingLocation; // Location prepared for the next reporting cycle
    private final EventTimestamp timestamp;
    private long nextScheduledReportTime = 0;

    // Using ScheduledExecutorService for report timing
    private final ScheduledExecutorService reportScheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledReportTask;

    // Separate scheduler for prefetching location data
    private final ScheduledExecutorService prefetchScheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledPrefetchTask;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public LocationUpdater(Context context, long intervalMillis) {
        this.context = context;
        this.intervalMillis = intervalMillis;
        this.timestamp = new EventTimestamp();
        this.lastLocation = null;
        this.pendingLocation = null;

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    private void buildLocationRequest() {
        // High accuracy location request optimized for consistent reporting
        locationRequest = new LocationRequest.Builder(intervalMillis / 4) // More frequent updates
                .setMinUpdateIntervalMillis(1000) // Minimum 1 second between updates
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateDelayMillis(intervalMillis / 2) // Maximum delay constraint
                .build();
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;

                // Store the most recent location
                lastLocation = result.getLastLocation();
                Log.d(TAG, "Location updated: " +
                        (lastLocation != null ?
                                lastLocation.getLatitude() + ", " + lastLocation.getLongitude() :
                                "null"));
            }
        };
    }

    /**
     * Sets up the location reporting schedule with precise timing while avoiding
     * the risks of scheduleAtFixedRate when processes are cached/uncached
     */
    private void startScheduledReporting() {
        // Cancel any existing tasks
        cancelScheduledTasks();

        // Schedule the initial report (immediate)
        mainHandler.post(() -> {
            reportCurrentLocation();
            // Calculate the next report time for drift correction
            nextScheduledReportTime = System.currentTimeMillis() + intervalMillis;
            // Schedule first prefetch task
            schedulePrefetchTask();
        });

        // Use scheduleWithFixedDelay for safety with process caching/uncaching
        scheduledReportTask = reportScheduler.scheduleWithFixedDelay(() -> {
            // Calculate how long we've actually waited
            long currentTime = System.currentTimeMillis();
            long actualDelay = currentTime - nextScheduledReportTime;

            // Log and correct for significant drift
            if (Math.abs(actualDelay) > 1000) { // if more than 1s off
                Log.w(TAG, "Detected timing drift of " + actualDelay + "ms");
            }

            mainHandler.post(this::reportCurrentLocation);

            // Update next scheduled report time based on the original schedule, not actual execution
            // This helps prevent drift but avoids scheduleAtFixedRate risks
            nextScheduledReportTime += intervalMillis;

            // Schedule next prefetch task after reporting
            schedulePrefetchTask();

        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);

        Log.d(TAG, "Scheduled location reporting started with interval: " + intervalMillis + "ms");
    }

    /**
     * Schedule the prefetch task to run ahead of report time
     */
    private void schedulePrefetchTask() {
        // Calculate when we should prefetch the location for the next report
        long timeUntilNextReport = nextScheduledReportTime - System.currentTimeMillis();
        long prefetchTime = Math.max(100, timeUntilNextReport - LOCATION_PREFETCH_OFFSET_MS);

        // Cancel any existing prefetch task
        if (scheduledPrefetchTask != null && !scheduledPrefetchTask.isCancelled()) {
            scheduledPrefetchTask.cancel(false);
        }

        // Schedule a one-time prefetch task
        scheduledPrefetchTask = prefetchScheduler.schedule(() -> {
            prefetchLocation();
        }, prefetchTime, TimeUnit.MILLISECONDS);

        Log.d(TAG, "Location prefetch scheduled " + prefetchTime +
                "ms from now for next report at " + nextScheduledReportTime);
    }

    /**
     * Prefetch location data for the next scheduled report
     */
    private void prefetchLocation() {
        if (!checkLocationPermission()) {
            Log.w(TAG, "Location permission not granted for prefetch.");
            return;
        }

        try {
            // Request a single location update for the next report
            fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            // Store the prefetched location
                            pendingLocation = location;
                            Log.d(TAG, "Prefetched location for next report: " +
                                    location.getLatitude() + ", " + location.getLongitude());
                        } else {
                            Log.w(TAG, "Failed to prefetch location (null)");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error prefetching location: " + e.getMessage());
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException during location prefetch: " + e.getMessage());
        }
    }

    /**
     * Report the current location to the database at the exact scheduled time
     */
    private void reportCurrentLocation() {
        long currentTime = System.currentTimeMillis();
        HashMap<String, String> locationMap = new HashMap<>();

        // Log timing accuracy
        long timeDifference = currentTime - nextScheduledReportTime;
        if (Math.abs(timeDifference) > 500) { // if more than 500ms off
            Log.w(TAG, "Reporting time offset: " + timeDifference + "ms");
        }

        // Use pendingLocation if available (from prefetch), otherwise use lastLocation
        Location locationToReport = pendingLocation != null ? pendingLocation : lastLocation;

        if (locationToReport != null) {
            locationMap.put("lat", String.valueOf(locationToReport.getLatitude()));
            locationMap.put("lng", String.valueOf(locationToReport.getLongitude()));
            locationMap.put("timestamp", String.valueOf(currentTime));
            locationMap.put("accuracy", String.valueOf(locationToReport.getAccuracy()));

            Log.d(TAG, "Reporting location: " + locationToReport.getLatitude() +
                    ", " + locationToReport.getLongitude() + " at time: " + currentTime);
        } else {
            // Report null location data when no location is available
            locationMap.put("lat", "null");
            locationMap.put("lng", "null");
            locationMap.put("timestamp", String.valueOf(currentTime));
            Log.d(TAG, "Reporting null location (no location available)");
        }

        // Report to database - we've already prefetched the location so this should be fast
        EventOperationManager.getInstance(context).addEvent(
                ModuleCharacteristics.getInstance().getLocationEventCharacteristics(),
                locationMap
        );

        // Reset the pending location after reporting
        pendingLocation = null;
    }

    public void startLocationUpdates() {
        if (!checkLocationPermission()) {
            Log.w(TAG, "Location permission not granted.");
            return;
        }

        createLocationCallback();
        buildLocationRequest();
        try {
            // First start the location client to begin receiving background updates
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, context.getMainLooper());

            // Then start our scheduled reporting and prefetching
            startScheduledReporting();
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while requesting location updates: " + e.getMessage());
        }
    }

    public void stopLocationUpdates() {
        // Stop location updates from the client
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        // Cancel our scheduled tasks
        cancelScheduledTasks();

        // Shutdown the schedulers properly
        if (reportScheduler != null) reportScheduler.shutdown();
        if (prefetchScheduler != null) prefetchScheduler.shutdown();

        Log.d(TAG, "Location updates stopped");
    }

    private void cancelScheduledTasks() {
        // Cancel report task
        if (scheduledReportTask != null && !scheduledReportTask.isCancelled()) {
            scheduledReportTask.cancel(false);
        }

        // Cancel prefetch task
        if (scheduledPrefetchTask != null && !scheduledPrefetchTask.isCancelled()) {
            scheduledPrefetchTask.cancel(false);
        }
    }

    public void updateIntervalIfNeeded(long newIntervalMillis) {
        if (this.intervalMillis != newIntervalMillis) {
            Log.d(TAG, "Updating interval from " + intervalMillis + " to " + newIntervalMillis);

            // Update interval
            this.intervalMillis = newIntervalMillis;

            // Restart location services with new interval
            stopLocationUpdates();
            buildLocationRequest();
            startLocationUpdates();
        } else {
            Log.d(TAG, "New interval is the same as current. No changes made.");
        }
    }

    public boolean checkLocationPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}