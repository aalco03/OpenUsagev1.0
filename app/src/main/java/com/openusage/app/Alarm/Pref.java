package com.openusage.app.Alarm;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;


/**
 * May 7, 2025 this file is to save the small chages which are responsible for taking decesion for other stuff
 *
 */

public class Pref {

    private final String TimeLocale = "TimeLocale";
    private final String CurrentlyActiveType = "CurrentlyActiveType";
    private final String Time = "Time";
    private final String StepCount = "StepCount";
    private final String DATA_PLAN = "DATA_PLAN";
    private final String SHOW_CONSENT_DIALOG = "SHOW_CONSENT_DIALOG";
    private final String RESET_STEP_COUNT = "RESET_STEP_COUNT";

    private Context context;

    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;

    private final String Shared_prefrence = "screenomics_settings";

    public Pref(Context context) {
        this.context = context;
    }



    public void Put_SHOW_CONSENT_DIALOG(boolean IsShowed){

        editor = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE).edit();
        editor.putBoolean(SHOW_CONSENT_DIALOG,IsShowed);
        editor.apply();
    }


    public boolean GetShowConsentDialog(){
        prefs = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE);
        return prefs.getBoolean(SHOW_CONSENT_DIALOG,false);
    }


}
