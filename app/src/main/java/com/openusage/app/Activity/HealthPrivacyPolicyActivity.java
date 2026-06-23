package com.openusage.app.Activity;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * HealthPrivacyPolicyActivity
 *
 * Mandatory rationale/privacy-policy screen for Health Connect. Health Connect will not
 * grant health permissions unless the app declares an activity that handles the rationale
 * intent (Android 13 and lower) or the permission-usage intent (Android 14+). This screen
 * explains how the study uses step data.
 *
 * Both intent filters are declared in AndroidManifest.xml and point here.
 */
public class HealthPrivacyPolicyActivity extends AppCompatActivity {

    private static final String POLICY_TEXT =
            "Open Usage — Health Data Privacy\n\n" +
            "As part of the Open Usage research study, this app may read your step-count " +
            "data from Android Health Connect.\n\n" +
            "What we read:\n" +
            "  • Daily and hourly step counts.\n\n" +
            "Why we read it:\n" +
            "  • To understand physical-activity patterns alongside your device-usage data " +
            "for research purposes.\n\n" +
            "How it is used:\n" +
            "  • Step data is associated with your study Participant ID and securely uploaded " +
            "to the study's research database.\n" +
            "  • It is never sold or used for advertising.\n\n" +
            "Your control:\n" +
            "  • Reading step data is optional. You can grant or revoke this permission at any " +
            "time in Health Connect settings, and the rest of the study is unaffected.\n\n" +
            "Questions? Contact support@openusage.app";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        scrollView.setPadding(pad, pad, pad, pad);

        TextView textView = new TextView(this);
        textView.setText(POLICY_TEXT);
        textView.setTextSize(16f);
        textView.setGravity(Gravity.START);
        textView.setMovementMethod(new ScrollingMovementMethod());

        scrollView.addView(textView);
        setContentView(scrollView);

        setTitle("Health Data Privacy");
    }
}
