package com.openusage.app.policy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PolicyEvaluationTrace - a write-only diagnostic sink for one policy evaluation.
 *
 * <p>Attach an instance to {@link SensitiveContentPolicy#evaluateText(String, String,
 * SensitiveContentPolicy.EvalContext, PolicyEvaluationTrace)} to capture every intermediate
 * signal the decider used: which structural shapes fired, which were rejected by a checksum,
 * which keywords matched, why a context-required shape was or was not counted, and how each
 * score component summed to the total.
 *
 * <p>Production callers pass {@code null} and pay nothing - every field here is populated only
 * behind a null check. Debug-only consumers ({@code PolicySelfTestReceiver},
 * {@code PolicyDiagnosticWriter}) serialize it to JSONL.
 *
 * <p>Pure Java (no Android), so it is exercisable from plain JVM unit tests.
 *
 * <p>Not thread-safe: one instance per evaluation.
 */
public final class PolicyEvaluationTrace {

    /** Sentinel for "no same-category keyword was found anywhere in the text". */
    public static final int NO_KEYWORD = Integer.MAX_VALUE;

    /** A structural finding plus why it did or did not count toward the score. */
    public static final class FindingTrace {
        public String kind;
        public String category;
        public int start;
        public int end;
        public boolean contextRequired;
        /** False when a context-required shape had no same-category keyword in range. */
        public boolean counted;
        /** Non-null only when {@code counted == false}, e.g. "no_nearby_identity_keyword". */
        public String dropReason;
        /** Char distance to the nearest same-category keyword, or {@link #NO_KEYWORD}. */
        public int nearestKeywordGap = NO_KEYWORD;
    }

    /**
     * A shape the scanner found and then rejected. This is the highest-value signal when
     * debugging a false negative: it separates "we never saw the shape" from "we saw it and
     * the checksum threw it out".
     */
    public static final class RejectedCandidate {
        public String kind;
        public int start;
        public int end;
        /** e.g. "luhn_failed", "aba_checksum_failed", "iban_mod97_failed", "mrz_no_name_separator". */
        public String reason;

        public RejectedCandidate(String kind, int start, int end, String reason) {
            this.kind = kind;
            this.start = start;
            this.end = end;
            this.reason = reason;
        }
    }

    /** A keyword hit from the Aho-Corasick pass. */
    public static final class KeywordTrace {
        public String keyword;
        public String category;
        public int start;
        public int end;

        public KeywordTrace(String keyword, String category, int start, int end) {
            this.keyword = keyword;
            this.category = category;
            this.start = start;
            this.end = end;
        }
    }

    // ── Input ────────────────────────────────────────────────
    /** The exact text the policy saw (raw, pre-redaction). */
    public String text;
    public int length;
    public boolean passwordField;
    public List<String> extraTokens = new ArrayList<>();

    // ── Processing ───────────────────────────────────────────
    public final List<FindingTrace> findings = new ArrayList<>();
    public final List<RejectedCandidate> rejectedCandidates = new ArrayList<>();
    public final List<KeywordTrace> keywordMatches = new ArrayList<>();
    public boolean domainHit;
    public final Set<String> categoriesFired = new LinkedHashSet<>();

    // ── Scores (components sum to total) ─────────────────────
    public double structuralStrong;
    public double structuralWeak;
    public double keywordScore;
    public double domainScore;
    public double passwordScore;
    public double cooccurrenceScore;
    public double total;
    public double suppressThreshold;
    public double redactThreshold;
    /** The proximity window context-required findings were judged against. */
    public int proximityWindow;

    // ── Outcome ──────────────────────────────────────────────
    /** Non-null when the evaluation short-circuited: "suppress_package" or "block_package". */
    public String shortCircuit;
    public String decision;
    public String reason;
    /** Exception message if the evaluation threw, else null. */
    public String error;

    // ── Self-test only (null/false for passive records) ──────
    public String caseId;
    public String expected;
    /** "PASS" or "FAIL". */
    public String status;
    /** FALSE_NEGATIVE | FALSE_POSITIVE | WRONG_BAND | ERROR | null. */
    public String failureKind;
    /** True for cases that document an accepted gap; these are expected to pass. */
    public boolean knownLimitation;

    /** Records a rejected structural candidate. No-op guard lives at the call site. */
    public void addRejected(String kind, int start, int end, String reason) {
        rejectedCandidates.add(new RejectedCandidate(kind, start, end, reason));
    }

    /** Sum of the individual score components, for asserting the math is auditable. */
    public double componentSum() {
        return structuralStrong + structuralWeak + keywordScore
                + domainScore + passwordScore + cooccurrenceScore;
    }
}
