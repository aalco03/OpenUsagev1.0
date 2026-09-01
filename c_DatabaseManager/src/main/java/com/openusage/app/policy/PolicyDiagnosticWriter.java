package com.openusage.app.policy;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PolicyDiagnosticWriter - appends policy diagnostic records to app-private JSONL files.
 *
 * <p>Two files share one schema under {@code files/policy_diag/}:
 * <ul>
 *   <li>{@link #SELFTEST_FILE} - written by the debug self-test harness (synthetic data only)</li>
 *   <li>{@link #PASSIVE_FILE} - written by the live gates when {@link #PASSIVE_ENABLED} is on</li>
 * </ul>
 *
 * <p><b>Passive capture records raw extracted text, which on a real device means real content.</b>
 * It is off by default, gated additionally on {@code BuildConfig.DEBUG} at every call site, and
 * scoped by {@link #PASSIVE_PACKAGES}. Use a synthetic test account with fabricated PHI - never a
 * real patient record - and delete the file after each session:
 *
 * <pre>adb shell rm /sdcard/Android/data/com.openusage.app/files/policy_diag/diagnostics.jsonl</pre>
 *
 * <p>Files are NEVER uploaded; pull them with {@code adb pull}.
 */
public final class PolicyDiagnosticWriter {

    private static final String TAG = "PolicyDiagWriter";

    private static final String DIAG_DIR = "policy_diag";
    public static final String SELFTEST_FILE = "selftest.jsonl";
    public static final String PASSIVE_FILE = "diagnostics.jsonl";

    /**
     * Flip to true on a researcher's own debug device to record live evaluations.
     * Leave false for anything resembling a participant build.
     */
    public static final boolean PASSIVE_ENABLED = false;

    /**
     * Optional allowlist scoping passive capture to specific packages (empty = all packages).
     * Prefer scoping to the one or two highest-risk apps rather than recording everything.
     */
    public static final Set<String> PASSIVE_PACKAGES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.<String>asList(
                    // e.g. "com.example.patientportal"
            )));

    private PolicyDiagnosticWriter() {}

    /** True if passive recording should run for {@code packageName}. */
    public static boolean shouldRecord(String packageName) {
        if (!PASSIVE_ENABLED) return false;
        if (PASSIVE_PACKAGES.isEmpty()) return true;
        return packageName != null && PASSIVE_PACKAGES.contains(packageName);
    }

    /**
     * Appends one record. Callers must have already checked {@code BuildConfig.DEBUG} and, for
     * passive capture, {@link #shouldRecord(String)}.
     */
    public static void record(Context context, String fileName, String gate, String source,
                              String packageName, PolicyVerdict verdict,
                              PolicyEvaluationTrace trace) {
        if (context == null || trace == null) return;
        try {
            String line = PolicyDiagnosticJson.record(
                    System.currentTimeMillis(), packageName, gate, source, verdict, trace);
            appendLine(context, fileName, line);
        } catch (Exception e) {
            Log.e(TAG, "record failed: " + e.getMessage());
        }
    }

    /** Appends a batch of pre-built lines (used by the self-test harness). */
    public static void appendLines(Context context, String fileName, List<String> lines) {
        if (context == null || lines == null || lines.isEmpty()) return;
        try {
            File out = fileIn(context, fileName);
            try (FileWriter fw = new FileWriter(out, true)) {
                for (String line : lines) {
                    fw.write(line);
                    fw.write("\n");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "appendLines failed: " + e.getMessage());
        }
    }

    /** Truncates a diagnostic file so each run starts clean. */
    public static void reset(Context context, String fileName) {
        if (context == null) return;
        try {
            File out = fileIn(context, fileName);
            try (FileWriter fw = new FileWriter(out, false)) {
                fw.write("");
            }
        } catch (Exception e) {
            Log.e(TAG, "reset failed: " + e.getMessage());
        }
    }

    /** Absolute path of a diagnostic file, for logging the adb pull target. */
    public static String pathOf(Context context, String fileName) {
        try {
            return fileIn(context, fileName).getAbsolutePath();
        } catch (Exception e) {
            return "(unavailable)";
        }
    }

    private static void appendLine(Context context, String fileName, String line) throws Exception {
        File out = fileIn(context, fileName);
        try (FileWriter fw = new FileWriter(out, true)) {
            fw.write(line);
            fw.write("\n");
        }
    }

    private static File fileIn(Context context, String fileName) {
        File dir = new File(context.getExternalFilesDir(null), DIAG_DIR);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, fileName);
    }
}
