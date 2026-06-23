package com.openusage.app.modulemanager;

import java.util.Map;

/**
 *
 * This file contains all the characteristics of the modules. For example if we want location
 * module to to not update ticker then just set updateticker in getLocationEventCharacteristics to 0.
 */

public class ModuleCharacteristics {
    private static volatile ModuleCharacteristics instance;

    private ModuleCharacteristics() {
        // Private constructor to prevent instantiation
    }

    public static ModuleCharacteristics getInstance() {
        if (instance == null) {
            synchronized (ModuleCharacteristics.class) {
                if (instance == null) {
                    instance = new ModuleCharacteristics();
                }
            }
        }
        return instance;
    }

    public Map<String, String> getLocationEventCharacteristics() {
        return new ModuleCharacteristicsData("GPSLocationEvent", "location", "1").toMap();
    }

    public Map<String, String> getPowerScreenOnOffCharacteristics() {
        return new ModuleCharacteristicsData("ScreenOnOffEvent", "screen-on-off", "1").toMap();
    }

    public Map<String, String> getInteractionEventCharacteristics() {
        return new ModuleCharacteristicsData("InteractionEvent", "interaction", "1").toMap();
    }

    public Map<String, String> getNetworkEventCharacteristics() {
        return new ModuleCharacteristicsData("InternetEvent", "internet", "1").toMap();
    }

    public Map<String, String> getStepCountEventCharacteristics() {
        return new ModuleCharacteristicsData("StepCountEvent", "step-count", "1").toMap();
    }

    public Map<String, String> getBatteryStateEventCharacteristics() {
        return new ModuleCharacteristicsData("BatteryStateEvent", "battery-state", "1").toMap();
    }

    public Map<String, String> getBatteryChargingEventCharacteristics() {
        return new ModuleCharacteristicsData("BatteryChargingEvent", "battery-charging", "1").toMap();
    }


    public Map<String, String> getAndroidSpecsEventCharacteristics() {
        return new ModuleCharacteristicsData("SpecsEvent", "android-specs", "0").toMap();
    }

    public Map<String, String> getScreenshotFailureCharacteristics() {
        return new ModuleCharacteristicsData("ScreenshotFailureEvent", "screenshot-failure", "0").toMap();
    }

    public Map<String, String> getScreenshotEventCharacteristics() {
        return new ModuleCharacteristicsData("ScreenshotEvent", "screenshot", "1").toMap();
    }

    public Map<String, String> getScreenshotUploadEventCharacteristics() {
        return new ModuleCharacteristicsData("ScreenshotUploadEvent", "screenshot-upload", "1").toMap();
    }

    public Map<String, String> getForegroundAppModuleCharacteristics() {
        return new ModuleCharacteristicsData("NewForegroundAppEvent", "new-foreground-app", "1").toMap();
    }

    public Map<String, String> getCaptureStartupCharacteristics() {
        return new ModuleCharacteristicsData("CaptureStartupEvent", "capture-startup", "1").toMap();
    }

    public Map<String, String> getLowMemoryEventCharacteristics() {
        return new ModuleCharacteristicsData("LowMemoryEvent", "low-memory", "0").toMap();
    }

    public Map<String, String> getSystemPowerEventCharacteristics() {
        return new ModuleCharacteristicsData("SystemPowerEvent", "system-power", "1").toMap();
    }

    public Map<String, String> getAlarmManagerCharacteristics() {
        return new ModuleCharacteristicsData("AlarmManagerNotificationEvent", "alarm-manager-notification", "1").toMap();
    }

    public Map<String, String> getTrackingToggleCharacteristics() {
        return new ModuleCharacteristicsData("TrackingToggleEvent", "tracking-toggle", "1").toMap();
    }
}
