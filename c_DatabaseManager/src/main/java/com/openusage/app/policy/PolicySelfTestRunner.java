package com.openusage.app.policy;

import java.util.ArrayList;
import java.util.List;

/**
 * PolicySelfTestRunner - drives {@link PolicySelfTestCases} through a real
 * {@link SensitiveContentPolicy} and produces the JSONL lines (summary first, then one per case).
 *
 * <p>Pure Java on purpose: the identical runner backs both the JVM preview test and the on-device
 * debug receiver, so the format you confirm on your laptop is byte-for-byte what the device emits.
 * The Android side only has to write the returned lines to a file.
 *
 * <p>Each case is evaluated inside a try/catch; a thrown exception becomes
 * {@code error} + {@code failure_kind:"ERROR"} on that record rather than aborting the run or
 * vanishing into logcat.
 */
public final class PolicySelfTestRunner {

    private PolicySelfTestRunner() {}

    /** Outcome of a run: the lines to write plus the tallies the caller may want to log. */
    public static final class Result {
        public final List<String> lines = new ArrayList<>();
        public int total;
        public int passed;
        public int failed;
        public final List<String> failingCaseIds = new ArrayList<>();
        public final List<String> knownLimitationIds = new ArrayList<>();
    }

    /**
     * Runs the full pack.
     *
     * @param policy      the policy under test (built from defaults or from live remote rules)
     * @param rulesSource "defaults" or "remote", recorded in the summary
     * @param timestamp   epoch millis stamped on every line
     */
    public static Result run(SensitiveContentPolicy policy, String rulesSource,
                             long timestamp, String appVersion, String gitCommit) {
        Result result = new Result();
        List<PolicySelfTestCases.Case> cases = PolicySelfTestCases.all();
        result.total = cases.size();

        List<String> caseLines = new ArrayList<>(cases.size());

        for (PolicySelfTestCases.Case tc : cases) {
            PolicyEvaluationTrace trace = new PolicyEvaluationTrace();
            trace.caseId = tc.id;
            trace.expected = tc.expected.name();
            trace.knownLimitation = tc.knownLimitation;

            PolicyVerdict verdict = null;
            try {
                SensitiveContentPolicy.EvalContext ctx = null;
                if (tc.passwordField) {
                    ctx = new SensitiveContentPolicy.EvalContext().password(true);
                }
                verdict = policy.evaluateText(tc.text, tc.packageName, ctx, trace);
                trace.status = verdict.decision == tc.expected ? "PASS" : "FAIL";
                if (!"PASS".equals(trace.status)) {
                    trace.failureKind = classify(tc.expected, verdict.decision);
                }
            } catch (Exception e) {
                trace.error = e.getClass().getSimpleName() + ": " + e.getMessage();
                trace.status = "FAIL";
                trace.failureKind = "ERROR";
                if (trace.decision == null) trace.decision = "ERROR";
            }

            if ("PASS".equals(trace.status)) {
                result.passed++;
            } else {
                result.failed++;
                result.failingCaseIds.add(tc.id);
            }
            if (tc.knownLimitation) {
                result.knownLimitationIds.add(tc.id);
            }

            caseLines.add(PolicyDiagnosticJson.record(
                    timestamp, tc.packageName, "text", "synthetic", verdict, trace));
        }

        PolicyRules rules = policy.getRules();
        result.lines.add(PolicyDiagnosticJson.summary(
                timestamp, result.total, result.passed, result.failed,
                result.failingCaseIds, result.knownLimitationIds,
                result.total, caseLines.size(),
                rulesSource, PolicyDiagnosticJson.fingerprint(rules),
                rules, appVersion, gitCommit));
        result.lines.addAll(caseLines);
        return result;
    }

    /**
     * Classifies a mismatch so the failure type is explicit rather than inferred by eye.
     *
     * <ul>
     *   <li>FALSE_NEGATIVE - should have acted, allowed instead (the leak-capable direction)</li>
     *   <li>FALSE_POSITIVE - should have allowed, acted instead (over-collection loss)</li>
     *   <li>WRONG_BAND - acted, but redacted vs suppressed the wrong way</li>
     * </ul>
     */
    static String classify(PolicyVerdict.Decision expected, PolicyVerdict.Decision actual) {
        boolean expectedAction = expected != PolicyVerdict.Decision.ALLOW;
        boolean actualAction = actual != PolicyVerdict.Decision.ALLOW;
        if (expectedAction && !actualAction) return "FALSE_NEGATIVE";
        if (!expectedAction && actualAction) return "FALSE_POSITIVE";
        return "WRONG_BAND";
    }
}
