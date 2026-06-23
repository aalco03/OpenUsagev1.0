package com.openusage.app.modulemanager;


/**
 * May 7, 2025
 * This class is used to turn on and off modules
 * For example if we set ENABLE_LOCATIONS = false then the locations module will not be used and true means it will be used
 *
 */

public class ModuleController {
    public static boolean ENABLE_SCREENSHOTS = true;
    public static boolean ENABLE_APPS = true;
    public static boolean ENABLE_INTERACTIONS = true;
    public static boolean ENABLE_ACTIVITIES = true;
    public static boolean ENABLE_LOCATIONS = true;
    public static boolean ENABLE_BATTERY = true;
    public static boolean ENABLE_POWER = true;
    public static boolean ENABLE_NETWORK = true;
}
// SPECS module is always activated.
