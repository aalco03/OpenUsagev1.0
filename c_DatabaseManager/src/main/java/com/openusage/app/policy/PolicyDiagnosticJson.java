package com.openusage.app.policy;

import java.util.List;

/**
 * PolicyDiagnosticJson - serializes a {@link PolicyEvaluationTrace} to one compact JSON line.
 *
 * <p>Hand-rolled rather than using {@code org.json} so it stays pure Java: the same serializer
 * runs in plain JVM unit tests (where Android's {@code org.json} is a throwing stub) and on
 * device. No external dependency.
 *
 * <p>Output is one object per line, append-friendly (JSONL).
 */
public final class PolicyDiagnosticJson {

    private PolicyDiagnosticJson() {}

    /**
     * Builds one record line.
     *
     * @param gate      "text" | "trigger" | "capture" | "ocr"
     * @param source    "accessibility" | "ocr" | "synthetic"
     * @param verdict   the decision that was returned (may be null if the eval threw)
     * @param trace     the populated trace
     * @param timestamp epoch millis
     */
    public static String record(long timestamp, String packageName, String gate, String source,
                                PolicyVerdict verdict, PolicyEvaluationTrace trace) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        num(sb, "ts", timestamp).append(',');
        str(sb, "package", packageName).append(',');
        str(sb, "gate", gate).append(',');
        str(sb, "source", source).append(',');

        // ── extraction ───────────────────────────────────────
        sb.append("\"extraction\":{");
        str(sb, "text", trace.text).append(',');
        num(sb, "length", trace.length).append(',');
        bool(sb, "password_field", trace.passwordField).append(',');
        sb.append("\"extra_tokens\":");
        strArray(sb, trace.extraTokens);
        sb.append("},");

        // ── processing ───────────────────────────────────────
        sb.append("\"processing\":{");
        sb.append("\"structural_findings\":[");
        for (int i = 0; i < trace.findings.size(); i++) {
            if (i > 0) sb.append(',');
            PolicyEvaluationTrace.FindingTrace f = trace.findings.get(i);
            sb.append('{');
            str(sb, "kind", f.kind).append(',');
            str(sb, "category", f.category).append(',');
            num(sb, "start", f.start).append(',');
            num(sb, "end", f.end).append(',');
            bool(sb, "context_required", f.contextRequired).append(',');
            bool(sb, "counted", f.counted).append(',');
            str(sb, "drop_reason", f.dropReason).append(',');
            sb.append("\"nearest_keyword_gap\":");
            if (f.nearestKeywordGap == PolicyEvaluationTrace.NO_KEYWORD) sb.append("null");
            else sb.append(f.nearestKeywordGap);
            sb.append('}');
        }
        sb.append("],");

        sb.append("\"rejected_candidates\":[");
        for (int i = 0; i < trace.rejectedCandidates.size(); i++) {
            if (i > 0) sb.append(',');
            PolicyEvaluationTrace.RejectedCandidate rc = trace.rejectedCandidates.get(i);
            sb.append('{');
            str(sb, "kind", rc.kind).append(',');
            num(sb, "start", rc.start).append(',');
            num(sb, "end", rc.end).append(',');
            str(sb, "reason", rc.reason);
            sb.append('}');
        }
        sb.append("],");

        sb.append("\"keyword_matches\":[");
        for (int i = 0; i < trace.keywordMatches.size(); i++) {
            if (i > 0) sb.append(',');
            PolicyEvaluationTrace.KeywordTrace k = trace.keywordMatches.get(i);
            sb.append('{');
            str(sb, "keyword", k.keyword).append(',');
            str(sb, "category", k.category).append(',');
            num(sb, "start", k.start).append(',');
            num(sb, "end", k.end);
            sb.append('}');
        }
        sb.append("],");

        bool(sb, "domain_hit", trace.domainHit).append(',');
        sb.append("\"categories_fired\":");
        strArray(sb, trace.categoriesFired);
        sb.append("},");

        // ── scores ───────────────────────────────────────────
        sb.append("\"scores\":{");
        num(sb, "structural_strong", trace.structuralStrong).append(',');
        num(sb, "structural_weak", trace.structuralWeak).append(',');
        num(sb, "keyword", trace.keywordScore).append(',');
        num(sb, "domain", trace.domainScore).append(',');
        num(sb, "password", trace.passwordScore).append(',');
        num(sb, "cooccurrence", trace.cooccurrenceScore).append(',');
        num(sb, "total", trace.total).append(',');
        num(sb, "suppress_threshold", trace.suppressThreshold).append(',');
        num(sb, "redact_threshold", trace.redactThreshold).append(',');
        num(sb, "proximity_window", trace.proximityWindow);
        sb.append("},");

        // ── outcome ──────────────────────────────────────────
        str(sb, "short_circuit", trace.shortCircuit).append(',');
        str(sb, "decision", trace.decision).append(',');
        str(sb, "reason", trace.reason).append(',');

        sb.append("\"output\":");
        sb.append(outputFor(verdict, trace.text));
        sb.append(',');

        str(sb, "error", trace.error);

        // ── self-test fields (omitted for passive records) ───
        if (trace.caseId != null) {
            sb.append(',');
            str(sb, "case_id", trace.caseId).append(',');
            str(sb, "expected", trace.expected).append(',');
            str(sb, "status", trace.status).append(',');
            str(sb, "failure_kind", trace.failureKind).append(',');
            bool(sb, "known_limitation", trace.knownLimitation);
        }

        sb.append('}');
        return sb.toString();
    }

    /**
     * Decision-conditioned output: SUPPRESS -> null (proof nothing was kept), REDACT -> the
     * redacted string (so you see exactly what would be stored), ALLOW -> "APPROVED" (no need to
     * repeat text already present under extraction).
     */
    private static String outputFor(PolicyVerdict verdict, String text) {
        if (verdict == null) return "null";
        switch (verdict.decision) {
            case SUPPRESS:
                return "null";
            case REDACT:
                return quote(SensitiveContentPolicy.applyRedaction(text, verdict.spans));
            case ALLOW:
            default:
                return "\"APPROVED\"";
        }
    }

    /** Builds the run-summary line that heads a self-test file. */
    public static String summary(long timestamp, int total, int passed, int failed,
                                 List<String> failingCaseIds, List<String> knownLimitationIds,
                                 int expectedCases, int writtenCases,
                                 String rulesSource, String rulesFingerprint,
                                 PolicyRules rules, String appVersion, String gitCommit) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        sb.append("\"summary\":true,");
        num(sb, "ts", timestamp).append(',');
        num(sb, "total", total).append(',');
        num(sb, "passed", passed).append(',');
        num(sb, "failed", failed).append(',');
        sb.append("\"failing_case_ids\":");
        strArray(sb, failingCaseIds);
        sb.append(',');
        sb.append("\"known_limitation_cases\":");
        strArray(sb, knownLimitationIds);
        sb.append(',');
        num(sb, "expected_cases", expectedCases).append(',');
        num(sb, "written_cases", writtenCases).append(',');
        str(sb, "rules_source", rulesSource).append(',');
        str(sb, "rules_fingerprint", rulesFingerprint).append(',');
        num(sb, "suppress_threshold", rules != null ? rules.suppressScore : 0).append(',');
        num(sb, "redact_threshold", rules != null ? rules.redactScore : 0).append(',');
        num(sb, "proximity_window", SensitiveContentPolicy.contextProximityChars()).append(',');
        bool(sb, "ocr_escalation_enabled", rules != null && rules.ocrEscalationEnabled).append(',');
        sb.append("\"weights\":{");
        num(sb, "strong", SensitiveContentPolicy.weightStructuralStrong()).append(',');
        num(sb, "weak", SensitiveContentPolicy.weightStructuralWeak()).append(',');
        num(sb, "keyword", SensitiveContentPolicy.weightKeyword()).append(',');
        num(sb, "domain", SensitiveContentPolicy.weightDomain()).append(',');
        num(sb, "password", SensitiveContentPolicy.weightPasswordField()).append(',');
        num(sb, "cooccurrence", SensitiveContentPolicy.weightCooccurrence());
        sb.append("},");
        str(sb, "app_version", appVersion).append(',');
        str(sb, "git_commit", gitCommit);
        sb.append('}');
        return sb.toString();
    }

    /**
     * Stable fingerprint of the rules that produced a run, so a result is self-contained: two
     * runs with the same fingerprint were judged by the same dictionary and thresholds.
     */
    public static String fingerprint(PolicyRules r) {
        if (r == null) return "none";
        long h = 17;
        h = mix(h, r.suppressScore);
        h = mix(h, r.redactScore);
        h = mix(h, r.policyEnabled ? 1 : 0);
        h = mix(h, r.ocrEscalationEnabled ? 1 : 0);
        for (String cat : sorted(r.keywords.keySet())) {
            h = mix(h, cat.hashCode());
            for (String kw : sorted(r.keywords.get(cat))) h = mix(h, kw.hashCode());
        }
        for (String d : sorted(r.domains)) h = mix(h, d.hashCode());
        for (String p : sorted(r.suppressPackages)) h = mix(h, p.hashCode());
        for (String p : sorted(r.blockPackages)) h = mix(h, p.hashCode());
        for (String p : sorted(r.mediaPackages)) h = mix(h, p.hashCode());
        return Long.toHexString(h & 0xFFFFFFFFL);
    }

    private static List<String> sorted(java.util.Collection<String> in) {
        List<String> out = new java.util.ArrayList<>(in);
        java.util.Collections.sort(out);
        return out;
    }

    private static long mix(long h, double v) { return mix(h, Double.hashCode(v)); }

    private static long mix(long h, long v) { return h * 31 + v; }

    // ── primitives ───────────────────────────────────────────

    private static StringBuilder str(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":");
        if (value == null) sb.append("null");
        else sb.append(quote(value));
        return sb;
    }

    private static StringBuilder num(StringBuilder sb, String key, double value) {
        sb.append('"').append(key).append("\":").append(trim(value));
        return sb;
    }

    private static StringBuilder num(StringBuilder sb, String key, long value) {
        sb.append('"').append(key).append("\":").append(value);
        return sb;
    }

    private static StringBuilder bool(StringBuilder sb, String key, boolean value) {
        sb.append('"').append(key).append("\":").append(value);
        return sb;
    }

    private static StringBuilder strArray(StringBuilder sb, java.util.Collection<String> values) {
        sb.append('[');
        if (values != null) {
            boolean first = true;
            for (String v : values) {
                if (!first) sb.append(',');
                sb.append(quote(v));
                first = false;
            }
        }
        sb.append(']');
        return sb;
    }

    /** Renders a double without a trailing ".0" for whole numbers, keeping lines readable. */
    private static String trim(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        // Avoid binary-float noise like 1.6500000000000001 in the score fields.
        return String.valueOf(Math.round(v * 1e6) / 1e6);
    }

    /** JSON string literal with full escaping (quotes, backslash, control chars). */
    static String quote(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
