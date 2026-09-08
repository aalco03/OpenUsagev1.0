package com.openusage.app.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the self-test pack through the trace overload on a plain JVM - no device, no emulator.
 *
 * <p>This is the fast inner loop and the format gate: it writes the exact JSONL the on-device
 * harness produces to {@code build/reports/policy/selftest-preview.jsonl}, so the debug output
 * format is confirmed before any Android wiring is touched.
 */
public class PolicyDiagnosticTraceTest {

    private SensitiveContentPolicy policy() {
        return new SensitiveContentPolicy(PolicyDefaults.buildDefaultRules());
    }

    /** Every case must land on its expected decision. */
    @Test
    public void packMatchesExpectedDecisions() {
        SensitiveContentPolicy p = policy();
        List<String> mismatches = new ArrayList<>();

        for (PolicySelfTestCases.Case tc : PolicySelfTestCases.all()) {
            PolicyEvaluationTrace trace = new PolicyEvaluationTrace();
            SensitiveContentPolicy.EvalContext ctx = tc.passwordField
                    ? new SensitiveContentPolicy.EvalContext().password(true) : null;
            PolicyVerdict v = p.evaluateText(tc.text, tc.packageName, ctx, trace);
            if (v.decision != tc.expected) {
                mismatches.add(tc.id + ": expected " + tc.expected + " but got " + v.decision
                        + " (total=" + trace.total + ", "
                        + PolicySelfTestRunner.classify(tc.expected, v.decision) + ")");
            }
        }

        assertTrue("Pack decisions diverged:\n  " + String.join("\n  ", mismatches),
                mismatches.isEmpty());
    }

    /** Score components must sum to the total, so the math in each line is auditable. */
    @Test
    public void scoreComponentsSumToTotal() {
        SensitiveContentPolicy p = policy();
        for (PolicySelfTestCases.Case tc : PolicySelfTestCases.all()) {
            PolicyEvaluationTrace trace = new PolicyEvaluationTrace();
            SensitiveContentPolicy.EvalContext ctx = tc.passwordField
                    ? new SensitiveContentPolicy.EvalContext().password(true) : null;
            p.evaluateText(tc.text, tc.packageName, ctx, trace);

            if (trace.shortCircuit != null) continue; // package short-circuits skip scoring
            assertEquals("component sum != total for " + tc.id,
                    trace.total, trace.componentSum(), 1e-9);
        }
    }

    /** A dropped context-required finding must explain itself without logcat. */
    @Test
    public void droppedFindingsCarryReasonAndGap() {
        SensitiveContentPolicy p = policy();
        PolicyEvaluationTrace trace = new PolicyEvaluationTrace();
        // Gov-ID shape with no identity keyword anywhere: must be found, then dropped.
        p.evaluateText("Reference code X1234567 for your order", "com.example.testapp", null, trace);

        PolicyEvaluationTrace.FindingTrace govid = null;
        for (PolicyEvaluationTrace.FindingTrace f : trace.findings) {
            if ("govid".equals(f.kind)) govid = f;
        }
        assertNotNull("expected a govid finding to be detected then dropped", govid);
        assertTrue("govid should not be counted without a nearby keyword", !govid.counted);
        assertNotNull("dropped finding must carry a drop_reason", govid.dropReason);
        assertEquals(PolicyEvaluationTrace.NO_KEYWORD, govid.nearestKeywordGap);
    }

    /** A near-miss must report the actual gap, not just "dropped". */
    @Test
    public void nearMissReportsMeasuredGap() {
        SensitiveContentPolicy p = policy();
        PolicyEvaluationTrace trace = new PolicyEvaluationTrace();
        // "passport" keyword present but pushed well beyond the 40-char window.
        String filler = new String(new char[60]).replace('\0', '.');
        p.evaluateText("passport" + filler + "X1234567", "com.example.testapp", null, trace);

        PolicyEvaluationTrace.FindingTrace govid = null;
        for (PolicyEvaluationTrace.FindingTrace f : trace.findings) {
            if ("govid".equals(f.kind)) govid = f;
        }
        assertNotNull(govid);
        assertTrue("gap should be measured, not NO_KEYWORD",
                govid.nearestKeywordGap != PolicyEvaluationTrace.NO_KEYWORD);
        assertTrue("gap should exceed the proximity window",
                govid.nearestKeywordGap > SensitiveContentPolicy.contextProximityChars());
        assertTrue(!govid.counted);
    }

    /** Checksum rejections must be visible, separating "not seen" from "seen and rejected". */
    @Test
    public void rejectedCandidatesAreRecorded() {
        SensitiveContentPolicy p = policy();
        PolicyEvaluationTrace trace = new PolicyEvaluationTrace();
        p.evaluateText("Order number 4111111111111112 confirmed", "com.example.testapp", null, trace);

        boolean sawLuhnRejection = false;
        for (PolicyEvaluationTrace.RejectedCandidate rc : trace.rejectedCandidates) {
            if ("card".equals(rc.kind) && "luhn_failed".equals(rc.reason)) sawLuhnRejection = true;
        }
        assertTrue("a Luhn-failing 16-digit run should be reported as a rejected candidate",
                sawLuhnRejection);
    }

    /** The trace overload must not change what production (null-trace) callers get. */
    @Test
    public void traceOverloadDoesNotAlterDecisions() {
        SensitiveContentPolicy p = policy();
        for (PolicySelfTestCases.Case tc : PolicySelfTestCases.all()) {
            SensitiveContentPolicy.EvalContext ctxA = tc.passwordField
                    ? new SensitiveContentPolicy.EvalContext().password(true) : null;
            SensitiveContentPolicy.EvalContext ctxB = tc.passwordField
                    ? new SensitiveContentPolicy.EvalContext().password(true) : null;
            PolicyVerdict untraced = p.evaluateText(tc.text, tc.packageName, ctxA);
            PolicyVerdict traced = p.evaluateText(tc.text, tc.packageName, ctxB,
                    new PolicyEvaluationTrace());
            assertEquals("traced vs untraced diverged for " + tc.id,
                    untraced.decision, traced.decision);
            assertEquals("score diverged for " + tc.id, untraced.score, traced.score, 1e-9);
        }
    }

    /** Emitted JSON must be well-formed even with newlines and quotes in the extracted text. */
    @Test
    public void jsonEscapesControlCharacters() {
        String ctl = String.valueOf((char) 1);
        String input = "a\"b\\c" + "\n" + "d\te" + ctl + "f";
        String expected = "\"a\\\"b\\\\c\\nd\\te" + "\\u0001" + "f\"";
        assertEquals(expected, PolicyDiagnosticJson.quote(input));
    }

    /**
     * Writes the full run (summary + one line per case) so the exact on-device format can be
     * reviewed before touching any Android code.
     */
    @Test
    public void writesPreviewJsonl() throws Exception {
        PolicySelfTestRunner.Result r = PolicySelfTestRunner.run(
                policy(), "defaults", 1690000000000L, "1.0", "jvm-preview");

        File dir = new File("build/reports/policy");
        assertTrue(dir.exists() || dir.mkdirs());
        File out = new File(dir, "selftest-preview.jsonl");
        try (FileWriter fw = new FileWriter(out, false)) {
            for (String line : r.lines) {
                fw.write(line);
                fw.write("\n");
            }
        }

        assertEquals("summary + one line per case",
                PolicySelfTestCases.all().size() + 1, r.lines.size());
        assertEquals("all cases should pass; known limitations are encoded as expected behavior",
                0, r.failed);
    }
}
