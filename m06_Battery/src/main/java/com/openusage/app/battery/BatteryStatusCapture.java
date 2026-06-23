package com.openusage.app.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import java.util.HashMap;

import com.openusage.app.TextBasedEventData.EventOperationManager;
import com.openusage.app.TextBasedEventData.HashMapPool;
import com.openusage.app.modulemanager.ModuleCharacteristics;

public class BatteryStatusCapture extends BroadcastReceiver {



    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();


        if (action == null) {
            return;
        }


        HashMap<String, String> batteryMap = HashMapPool.getMap();


        switch (action) {

            case Intent.ACTION_BATTERY_LOW:

                batteryMap.put("percentage", String.valueOf(getBatteryPercentage(context)));
                batteryMap.put("action", "low");

                EventOperationManager.getInstance(context).addEvent(ModuleCharacteristics.getInstance().getBatteryStateEventCharacteristics(), batteryMap);


//                HashMapPool.releaseMap(batteryMap);
                Log.d("BatteryReceiver", "Battery is low.");
                // Handle battery low case
                break;

            case Intent.ACTION_BATTERY_OKAY:
                batteryMap.put("percentage", String.valueOf(getBatteryPercentage(context)));
                batteryMap.put("action", "okay");
                EventOperationManager.getInstance(context).addEvent(ModuleCharacteristics.getInstance().getBatteryStateEventCharacteristics(), batteryMap);
//                HashMapPool.releaseMap(batteryMap);

                Log.d("BatteryReceiver", "Battery is okay.");
                // Handle battery okay case
                break;

            case Intent.ACTION_POWER_CONNECTED:
                batteryMap.put("percentage", String.valueOf(getBatteryPercentage(context)));
                batteryMap.put("charging", "yes");
                EventOperationManager.getInstance(context).addEvent(ModuleCharacteristics.getInstance().getBatteryChargingEventCharacteristics(), batteryMap);
//                HashMapPool.releaseMap(batteryMap);

                Log.d("BatteryReceiver", "Power is connected.");
                // Handle power connected case
                break;

            case Intent.ACTION_POWER_DISCONNECTED:
                batteryMap.put("percentage", String.valueOf(getBatteryPercentage(context)));
                batteryMap.put("charging", "no");
                EventOperationManager.getInstance(context).addEvent(ModuleCharacteristics.getInstance().getBatteryChargingEventCharacteristics(), batteryMap);
//                HashMapPool.releaseMap(batteryMap);

                Log.d("BatteryReceiver", "Power is disconnected.");
                // Handle power disconnected case
                break;

            default:
                break;
        }
    }


    public static int getBatteryPercentage(Context context) {
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            float percent = (level * 100.f) / scale;
            return (int) percent;
        }

        return -1;
    }
}

