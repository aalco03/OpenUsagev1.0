package com.openusage.app.policy;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug-only capture of real accessibility extractions for the offline replay harness.
 *
 * <p>When {@link #ENABLED} is true (flip manually on a researcher's own device), each extraction
 * is appended as one JSON object per line ({@code {text, package, timestamp}}) to a local file
 * under the app's external files dir. Files are NEVER uploaded; pull them with {@code adb pull}
 * and drop them into {@code c_DatabaseManager/src/test/resources/replay/} to replay through the
 * decider via {@code PolicyReplayTest}.
 *
 * <p>This lets us evaluate the policy against real-world app formatting (using the researcher's
 * own accounts, no participants) without shipping any behavior change: {@link #ENABLED} defaults
 * to false and callers additionally gate on {@code BuildConfig.DEBUG}.
 */
public final class PolicyDumpWriter {

    private static final String TAG = "PolicyDumpWriter";

    /** Flip to true on a debug device to record extractions for offline evaluation. */
    public static final boolean ENABLED = false;

    private static final String DUMP_DIR = "policy_dumps";
    private static final String DUMP_FILE = "extractions.jsonl";

    private PolicyDumpWriter() {}

    /** Appends one extraction record. No-op unless {@link #ENABLED}. */
    public static void append(Context context, String packageName, String text) {
        if (!ENABLED || context == null || text == null) return;
        try {
            File dir = new File(context.getExternalFilesDir(null), DUMP_DIR);
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, DUMP_FILE);

            JSONObject obj = new JSONObject();
            obj.put("package", packageName != null ? packageName : "");
            obj.put("timestamp", System.currentTimeMillis());
            obj.put("text", text);

            try (FileWriter fw = new FileWriter(out, true)) {
                fw.write(obj.toString());
                fw.write("\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "append failed: " + e.getMessage());
        }
    }
}
