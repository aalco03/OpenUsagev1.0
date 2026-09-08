package com.openusage.app.policy;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Runs the full synthetic corpus through {@link SensitiveContentPolicy}, builds a confusion
 * matrix, and asserts precision/recall floors. This is the regression gate: any rules or code
 * change that drops precision or recall below the thresholds fails the build.
 *
 * <p>Definitions (positive = "should be flagged", i.e. verdict != ALLOW):
 * <ul>
 *   <li>TP: expected flagged AND flagged</li>
 *   <li>FP: expected clean BUT flagged (over-collection risk on benign content)</li>
 *   <li>FN: expected flagged BUT allowed (leaked sensitive content)</li>
 * </ul>
 */
public class PolicyEvaluationTest {

    // Tunable floors (initial proposal from the plan).
    private static final double MIN_RECALL = 0.95;
    private static final double MIN_PRECISION = 0.90;

    @Test
    public void meetsPrecisionAndRecallFloors() {
        SensitiveContentPolicy policy = new SensitiveContentPolicy(PolicyDefaults.buildDefaultRules());
        List<PolicyCorpusGenerator.Case> corpus = PolicyCorpusGenerator.generate();

        int tp = 0, fp = 0, fn = 0, tn = 0;
        StringBuilder failures = new StringBuilder();

        for (PolicyCorpusGenerator.Case c : corpus) {
            PolicyVerdict v = policy.evaluateText(c.text, c.pkg);
            boolean flagged = !v.isAllow();

            if (c.expectFlagged && flagged) {
                tp++;
            } else if (!c.expectFlagged && flagged) {
                fp++;
                failures.append("  FALSE POSITIVE [").append(c.label).append("]: ")
                        .append(truncate(c.text)).append(" -> ").append(v.decision).append('\n');
            } else if (c.expectFlagged && !flagged) {
                fn++;
                failures.append("  FALSE NEGATIVE [").append(c.label).append("]: ")
                        .append(truncate(c.text)).append('\n');
            } else {
                tn++;
            }
        }

        double recall = tp + fn == 0 ? 1.0 : (double) tp / (tp + fn);
        double precision = tp + fp == 0 ? 1.0 : (double) tp / (tp + fp);
        double f1 = (precision + recall == 0) ? 0 : 2 * precision * recall / (precision + recall);

        System.out.println("=== Policy Evaluation ===");
        System.out.printf("Cases=%d  TP=%d FP=%d FN=%d TN=%d%n", corpus.size(), tp, fp, fn, tn);
        System.out.printf("Precision=%.3f  Recall=%.3f  F1=%.3f%n", precision, recall, f1);
        if (failures.length() > 0) {
            System.out.println("Misclassifications:\n" + failures);
        }

        assertTrue("Recall " + recall + " below floor " + MIN_RECALL + "\n" + failures,
                recall >= MIN_RECALL);
        assertTrue("Precision " + precision + " below floor " + MIN_PRECISION + "\n" + failures,
                precision >= MIN_PRECISION);
    }

    private static String truncate(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }
}
