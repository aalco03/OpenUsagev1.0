package com.openusage.app;

import android.os.SystemClock;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class EventTimestamp implements Serializable
{
    public static final String TAG = "EventTimestamp";
    private static final String TIMESTAMP_PATTERN = "yyyyMMddHHmmssSSS";
    private static final String TIMESTAMP_PATTERN_FRIENDLY = "yyyy-MM-dd HH:mm:ss";

    // Static values for tracking real-world time. Server time is milliseconds since Epoch UTC.
    private static long referenceServerTime = 0;
    private static long referenceElapsedRealtime = 0;

    // Final instance variables for the time of this timestamp, set upon creation.
    private final long elapsedTime;
    private final String systemClockTimestamp;

    // Derived real-world time. This field allows serialization across boot sessions.
    private long time;

    public EventTimestamp()
    {
        // Set stuff that doesn't depend on having the server time.
        elapsedTime = SystemClock.elapsedRealtime();
        SimpleDateFormat sdf = new SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        systemClockTimestamp = sdf.format(Calendar.getInstance().getTime());

        // If we have the server time, we can derive the real-world time since epoch.
        time = 0;
        refresh();
    }

    /**
     * Assuming a server time is known, anchors this timestamp to real-world time. It's good
     * to call this method before serialization (i.e. if the timestamp is being written to disk),
     * because otherwise the time could change if the device is rebooted.
     */
    public void refresh()
    {
        if (time == 0 && isServerTimeSet()) {
            time = referenceServerTime + (elapsedTime - referenceElapsedRealtime);
        }
    }

    /**
     * Gets a timestamp string for this time with the best available method.
     * @return Server time if set, otherwise system clock time.
     */
    public String getTimestring() {
        return isServerTimeSet() ? getRealTimestring() : getSystemClockTimestring();
    }


    // GMT New function


    public String getGMTTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }

//    public String getTimestringFriendly() {
//        return isServerTimeSet() ? getRealTimestringFriendly() : getSystemClockTimestring();
//    }


    public String getTimestringFriendly() {
        // Define the time zone for Los Angeles
        TimeZone losAngelesTimeZone = TimeZone.getTimeZone("America/Los_Angeles");

        // Get the current date and time
        Date now = new Date();

        // Set the date and time format
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss '(PT)'");
        dateFormat.setTimeZone(losAngelesTimeZone);

        // Format the current date and time in Los Angeles time zone
        return dateFormat.format(now);
    }


    /**
     * Gets a string representing the date/time of this timestamp based on the system clock.
     * This will be in the device's time zone.
     * @return Local timestamp
     */
    public String getSystemClockTimestring() {
//        return systemClockTimestamp;
        return new SimpleDateFormat("yyyyMMddHHmmssSSS").format(Calendar
                .getInstance().getTime());
    }

    /**
     * Gets a string representing the date/time of this timestamp in real-world time, which is
     * robust to changes in the system clock. This is adjusted to GMT Time.
     * @return Real world timestamp
     */
    public String getRealTimestring() {
        SimpleDateFormat sdf = new SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(getRealTimeMillis()));
    }

    public String getRealTimestringFriendly() {
        SimpleDateFormat sdf = new SimpleDateFormat(TIMESTAMP_PATTERN_FRIENDLY, Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
        return sdf.format(new Date(getRealTimeMillis())) + " (PT)";
    }

    /**
     * Gets the real-world time of this timestamp in milliseconds.
     * @return milliseconds since epoch UTC
     */
    public long getRealTimeMillis()
    {
        if (time != 0) return time;

        if (isServerTimeSet())
        {
            time = referenceServerTime + (elapsedTime - referenceElapsedRealtime);
            return time;
        }

        return 0;
    }

    public static boolean isServerTimeSet()
    {
        return referenceServerTime != 0;
    }

    @NonNull
    @Override
    public String toString() {
        return getTimestring();
    }
}
