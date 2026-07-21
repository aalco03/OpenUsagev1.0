package com.openusage.app.policy;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Micro-benchmark for the decider hot path. Prints per-evaluation latency over a realistic
 * text size so the IRB writeup can cite a hard number for the always-on cost. Not a strict
 * assertion (CI machines vary), but guards against gross regressions with a generous ceiling.
 */
public class PolicyBenchmarkTest {

    @Test
    public void deciderLatencyIsSmall() {
        SensitiveContentPolicy policy = new SensitiveContentPolicy(PolicyDefaults.buildDefaultRules());

        // Build a ~5 KB representative extraction with a couple of embedded signals.
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 5000) {
            sb.append("The quick brown fox jumps over the lazy dog near the account balance area. ");
        }
        sb.append(" card 4111 1111 1111 1111 ");
        String text = sb.toString();

        int warmup = 2000;
        for (int i = 0; i < warmup; i++) policy.evaluateText(text, "com.example.app");

        int iters = 20000;
        long start = System.nanoTime();
        for (int i = 0; i < iters; i++) policy.evaluateText(text, "com.example.app");
        long elapsed = System.nanoTime() - start;

        double usPerOp = (elapsed / (double) iters) / 1000.0;
        System.out.printf("=== Policy Benchmark ===%n");
        System.out.printf("Text size=%d chars  iters=%d  avg=%.2f us/op%n", text.length(), iters, usPerOp);

        // Generous ceiling: even on slow CI, a single O(n) pass over 5 KB should be well under 5 ms.
        assertTrue("decider too slow: " + usPerOp + " us/op", usPerOp < 5000.0);
    }
}
