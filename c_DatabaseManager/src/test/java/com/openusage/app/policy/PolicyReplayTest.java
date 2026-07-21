package com.openusage.app.policy;

import org.json.JSONObject;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replays real accessibility extractions (captured on a researcher's own device via
 * {@link PolicyDumpWriter}, pulled with {@code adb pull}) through the decider and reports the
 * verdict distribution plus a sample of flagged snippets for manual labeling.
 *
 * <p>Place {@code .jsonl} dump files (one JSON object per line: {text, package, timestamp}) in
 * {@code c_DatabaseManager/src/test/resources/replay/}. If that directory is absent or empty,
 * this test is a no-op so it never breaks CI when no dumps are provided.
 */
public class PolicyReplayTest {

    @Test
    public void replayRealDumps() throws Exception {
        File replayDir = resolveReplayDir();
        if (replayDir == null || !replayDir.isDirectory()) {
            System.out.println("[replay] No replay dir found; skipping (add .jsonl dumps to enable).");
            return;
        }
        File[] files = replayDir.listFiles((d, n) -> n.endsWith(".jsonl") || n.endsWith(".json"));
        if (files == null || files.length == 0) {
            System.out.println("[replay] No dump files; skipping.");
            return;
        }

        SensitiveContentPolicy policy = new SensitiveContentPolicy(PolicyDefaults.buildDefaultRules());

        int total = 0, allow = 0, redact = 0, suppress = 0;
        Map<String, Integer> byPackage = new LinkedHashMap<>();
        List<String> flaggedSamples = new ArrayList<>();

        for (File f : files) {
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    JSONObject obj = new JSONObject(line);
                    String text = obj.optString("text", "");
                    String pkg = obj.optString("package", "");
                    if (text.isEmpty()) continue;

                    total++;
                    PolicyVerdict v = policy.evaluateText(text, pkg);
                    switch (v.decision) {
                        case ALLOW: allow++; break;
                        case REDACT: redact++; break;
                        case SUPPRESS: suppress++; break;
                    }
                    if (!v.isAllow()) {
                        byPackage.merge(pkg, 1, Integer::sum);
                        if (flaggedSamples.size() < 20) {
                            flaggedSamples.add(v.decision + " [" + pkg + "] " + v.reason
                                    + " :: " + truncate(text));
                        }
                    }
                }
            }
        }

        System.out.println("=== Policy Replay (real dumps) ===");
        System.out.printf("Extractions=%d  ALLOW=%d REDACT=%d SUPPRESS=%d  (flag rate=%.1f%%)%n",
                total, allow, redact, suppress,
                total == 0 ? 0.0 : 100.0 * (redact + suppress) / total);
        System.out.println("Flagged by package: " + byPackage);
        System.out.println("Flagged samples (for manual labeling):");
        for (String s : flaggedSamples) System.out.println("  " + s);
        // Informational only: no assertions (real dumps are unlabeled).
    }

    private static File resolveReplayDir() {
        // Prefer classpath resource dir, then fall back to the source path.
        URL url = PolicyReplayTest.class.getClassLoader().getResource("replay");
        if (url != null) {
            return new File(url.getFile());
        }
        File src = new File("src/test/resources/replay");
        return src.exists() ? src : null;
    }

    private static String truncate(String s) {
        s = s.replaceAll("\\s+", " ");
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
}
