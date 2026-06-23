package com.openusage.app.power;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.HashMap;
import java.util.Objects;
import com.openusage.app.FirebaseSettings.UtilsForFirebaseSettings;
import com.openusage.app.TextBasedEventData.EventOperationManager;
import com.openusage.app.TextBasedEventData.HashMapPool;
import com.openusage.app.modulemanager.ModuleCharacteristics;

/**
 * May 7, 2025
 * Receiver that starts Screenomics when the phone boots.
 */

/**
 * May 7, 2025
 * Receiver that starts Screenomics when the phone boots.
 */
public class AutostartReceiver extends BroadcastReceiver
{
    public static final String TAG = "AutostartReceiver";
    public static final String QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON";

    ModuleCharacteristics moduleCharacteristics;
    EventOperationManager eventOperationManager;

    public void onReceive(Context context, Intent intent)
    {

        eventOperationManager = EventOperationManager.getInstance(context);
        try{
            if (Objects.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)
                    || Objects.equals(intent.getAction(), QUICKBOOT_POWERON))
            {
                if (!UtilsForFirebaseSettings.getSubjectId(context).isEmpty())
                {

                    HashMap<String, String> map = HashMapPool.getMap(); // Get from pool
                    map.put("power", "on");

                    EventOperationManager.getInstance(context).addEvent(moduleCharacteristics.getSystemPowerEventCharacteristics(), map);


                    HashMapPool.releaseMap(map); // Return to pool after use

                }
            }
            else
            {
                Log.e(TAG, "Autostart received wrong action: " + intent.getAction());
            }
        }catch (Exception e){

        }

    }

}