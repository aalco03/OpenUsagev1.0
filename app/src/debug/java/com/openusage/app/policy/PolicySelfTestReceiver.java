package com.openusage.app.policy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * PolicySelfTestReceiver - adb-triggerable on-device policy self-test. <b>Debug builds only</b>
 * (this file lives in {@code src/debug}, so it is not compiled into a release APK).
 *
 * <p>Runs the embedded {@link PolicySelfTestCases} pack through the real compiled policy on the
 * device and writes {@code policy_diag/selftest.jsonl} - a run-summary line followed by one line
 * per case. No accounts, emails, or manual app navigation needed; the whole run takes seconds and
 * is fully repeatable.
 *
 * <pre>
 * adb shell am broadcast -a com.openusage.app.POLICY_SELFTEST \
 *   -n com.openusage.app/com.openusage.app.policy.PolicySelfTestReceiver
 *
 * # against the live remote rules rather than the bundled defaults:
 * adb shell am broadcast -a com.openusage.app.POLICY_SELFTEST --es rules live \
 *   -n com.openusage.app/com.openusage.app.policy.PolicySelfTestReceiver
 *
 * adb pull /sdcard/Android/data/com.openusage.app/files/policy_diag/selftest.jsonl .
 * </pre>
 *
 * <p>A run against {@code defaults} validates the bundled seed rules. A run against {@code live}
 * validates what is actually deployed to devices via Firestore - a green defaults run says
 * nothing about the rules participants are really running, so use {@code live} before trusting a
 * result operationally. The summary line records which was used plus a rules fingerprint.
 */
public class PolicySelfTestReceiver extends BroadcastReceiver {

    private static final String TAG = "PolicySelfTest";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();

        String rulesArg = intent != null ? intent.getStringExtra("rules") : null;
        boolean useLive = "live".equalsIgnoreCase(rulesArg);

        SensitiveContentPolicy policy;
        String rulesSource;
        try {
            if (useLive) {
                policy = PolicyConfigManager.getInstance(appContext).getPolicy();
                rulesSource = "remote";
                if (policy == null) {
                    Log.w(TAG, "live rules unavailable; falling back to defaults");
                    policy = new SensitiveContentPolicy(PolicyDefaults.buildDefaultRules());
                    rulesSource = "defaults_fallback";
                }
            } else {
                policy = new SensitiveContentPolicy(PolicyDefaults.buildDefaultRules());
                rulesSource = "defaults";
            }
        } catch (Exception e) {
            Log.e(TAG, "failed to resolve rules: " + e.getMessage());
            policy = new SensitiveContentPolicy(PolicyDefaults.buildDefaultRules());
            rulesSource = "defaults_fallback";
        }

        try {
            PolicySelfTestRunner.Result result = PolicySelfTestRunner.run(
                    policy, rulesSource, System.currentTimeMillis(),
                    com.openusage.app.BuildConfig.VERSION_NAME, null);

            PolicyDiagnosticWriter.reset(appContext, PolicyDiagnosticWriter.SELFTEST_FILE);
            PolicyDiagnosticWriter.appendLines(
                    appContext, PolicyDiagnosticWriter.SELFTEST_FILE, result.lines);

            Log.i(TAG, "self-test complete: " + result.passed + "/" + result.total
                    + " passed, " + result.failed + " failed"
                    + (result.failingCaseIds.isEmpty() ? "" : " " + result.failingCaseIds)
                    + " [rules=" + rulesSource + "]");
            Log.i(TAG, "wrote " + PolicyDiagnosticWriter.pathOf(
                    appContext, PolicyDiagnosticWriter.SELFTEST_FILE));
        } catch (Exception e) {
            // The runner already catches per-case failures; this covers a catastrophic failure
            // before or during file writing, which is the one genuine logcat dependency.
            Log.e(TAG, "self-test run failed: " + e, e);
        }
    }
}
