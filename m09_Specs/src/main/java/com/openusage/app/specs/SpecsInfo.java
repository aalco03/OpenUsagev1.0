package com.openusage.app.specs;

import android.os.Build;

import java.util.HashMap;
import java.util.Map;

public class SpecsInfo {




    public static Map<String, String> createPhoneSpecMap()
    {


        HashMap<String, String> map = new HashMap<>();
        map.put("fingerprint", Build.FINGERPRINT);
        map.put("manufacturer", Build.MANUFACTURER);
        map.put("brand", Build.BRAND);
        map.put("model", Build.MODEL);
        map.put("product", Build.PRODUCT);
        map.put("display-id", Build.DISPLAY);
        return map;
    }

}
