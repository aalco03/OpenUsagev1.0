package com.openusage.app.TextBasedEventData;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import com.openusage.app.FirebaseSettings.SettingsManager;

public class NetworkUtils {



    // Memory management New function

    public static boolean isInternetAvailable(Context context) {

            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (connectivityManager != null) {
                Network activeNetwork = connectivityManager.getActiveNetwork();
                if (activeNetwork == null) return false; // No active network

                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
                return capabilities != null &&
                        (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
            }



        return false; // No connectivity manager available
    }

        public static boolean IsOnlyUploadTextOnWifi(Context context){

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null){

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

            if (activeNetwork != null)
            {
                // If we're only allowed to upload over wifi, check that.
                if (SettingsManager.val("data-text-upload-wifi-only") == 1 && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {

                    return true;

                }
                else if (SettingsManager.val("data-text-upload-wifi-only") == 0 && activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE){

                    return false;

                }else {
                    return false;
                }
            }
        }

        return false;


    }

}

