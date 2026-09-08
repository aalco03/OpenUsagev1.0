package com.openusage.app.policy;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;

import com.openusage.app.TextBasedEventData.EventOperationManager;
import com.openusage.app.modulemanager.ModuleCharacteristics;

/**
 * Emits audit events whenever the policy engine redacts or suppresses data.
 *
 * <p>Records ONLY non-sensitive metadata (decision, reason, categories, package, gate) and
 * NEVER the matched content. This trail is the IRB-facing evidence that the app actively
 * avoids collecting sensitive data.
 */
public final class PolicyAuditLogger {

    private static final String TAG = "PolicyAudit";

    private PolicyAuditLogger() {}

    /**
     * @param gate one of "text", "trigger", "capture", "bitmap", "quarantine"
     */
    public static void log(Context context, String gate, String packageName, PolicyVerdict verdict) {
        if (context == null || verdict == null || verdict.isAllow()) return;
        try {
            HashMap<String, String> map = new HashMap<>();
            map.put("gate", gate != null ? gate : "unknown");
            map.put("decision", verdict.decision.name());
            map.put("reason", verdict.reason != null ? verdict.reason : "");
            map.put("categories", verdict.categories != null ? verdict.categories.toString() : "[]");
            map.put("score", String.format(java.util.Locale.US, "%.2f", verdict.score));
            map.put("package", packageName != null ? packageName : "");

            EventOperationManager.getInstance(context).addEvent(
                    ModuleCharacteristics.getInstance().getPolicySuppressionCharacteristics(),
                    map);

            Log.i(TAG, "policy " + verdict.decision + " @" + gate
                    + " pkg=" + packageName + " reason=" + verdict.reason);
        } catch (Exception e) {
            Log.e(TAG, "Failed to log policy audit event: " + e.getMessage());
        }
    }
}
