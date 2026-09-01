package com.openusage.app.policy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SensitiveContentPolicy - the decider. A pure function {@code (text, package) -> verdict}
 * combining structural detection (checksums/shapes) with contextual keyword evidence via
 * co-occurrence scoring.
 *
 * <p>Detection philosophy: structural findings (Luhn-valid card, ABA routing, SSN shape,
 * IBAN, dosage) are near-deterministic and carry most of the weight. Keywords are mostly
 * contextual - a single keyword rarely suppresses on its own; it must co-occur with a
 * structural hit or reach a category-count threshold. This keeps precision high (a news
 * article mentioning "credit card" won't trip suppression).
 *
 * <p>Extra signals (e.g. an {@code isPassword} node observed during accessibility traversal)
 * can be injected via {@link #evaluateText(String, String, EvalContext)}.
 *
 * <p>Pure Java (no Android). Thread-safe: holds an immutable {@link PolicyRules} snapshot;
 * swap rules atomically with {@link #setRules(PolicyRules)}.
 */
public final class SensitiveContentPolicy {

    /** Optional extra signals gathered by the caller (e.g. accessibility node metadata). */
    public static final class EvalContext {
        public boolean hasPasswordField = false;
        public List<String> extraTokens = new ArrayList<>(); // e.g. resource ids, window title

        public EvalContext password(boolean v) { this.hasPasswordField = v; return this; }
        public EvalContext addToken(String t) { if (t != null) extraTokens.add(t); return this; }
    }

    // Scoring weights (structural >> keyword, deliberately).
    // Strong structural signals (card via Luhn, SSN shape, IBAN mod-97) are near-deterministic
    // and suppress on their own. Weak structural signals (bare 9-digit ABA routing, dosage
    // units) accidentally match common IDs/recipes, so they only contribute partial score and
    // require corroborating context (keyword co-occurrence) to cross the suppress threshold.
    private static final double W_STRUCTURED_STRONG = 1.0;
    private static final double W_STRUCTURED_WEAK = 0.4;
    private static final double W_KEYWORD = 0.25;      // per distinct keyword hit
    private static final double W_PASSWORD_FIELD = 1.0;
    private static final double W_DOMAIN = 0.5;
    // Co-occurrence bonus when a keyword and a structural hit share a category.
    private static final double W_COOCCURRENCE = 0.75;

    // Max character gap between a context-required shape (e.g. a gov-ID token) and a same-category
    // keyword for the shape to count. Keeps false positives on ordinary numbers/tokens near zero.
    private static final int CONTEXT_PROXIMITY_CHARS = 40;

    // ── Diagnostic accessors ─────────────────────────────────
    // Single source of truth for the debug harness: it must never hard-code duplicates of these,
    // or a reported run would not reflect the build that produced it.

    public static double weightStructuralStrong() { return W_STRUCTURED_STRONG; }
    public static double weightStructuralWeak() { return W_STRUCTURED_WEAK; }
    public static double weightKeyword() { return W_KEYWORD; }
    public static double weightPasswordField() { return W_PASSWORD_FIELD; }
    public static double weightDomain() { return W_DOMAIN; }
    public static double weightCooccurrence() { return W_COOCCURRENCE; }

    /** The proximity window a context-required shape is judged against. */
    public static int contextProximityChars() { return CONTEXT_PROXIMITY_CHARS; }

    private static boolean isStrongKind(String kind) {
        return "card".equals(kind) || "ssn".equals(kind) || "iban".equals(kind)
                || "mrz".equals(kind);
    }

    private final StructuredDataScanner scanner = new StructuredDataScanner();
    private volatile PolicyRules rules;

    public SensitiveContentPolicy(PolicyRules rules) {
        this.rules = rules;
    }

    public void setRules(PolicyRules rules) {
        this.rules = rules;
    }

    public PolicyRules getRules() {
        return rules;
    }

    /** Evaluate text with no extra context. */
    public PolicyVerdict evaluateText(String text, String packageName) {
        return evaluateText(text, packageName, null);
    }

    /**
     * Core evaluation. {@code text} may be raw; structural scan handles casing internally,
     * keyword matching lowercases a single normalized copy.
     */
    public PolicyVerdict evaluateText(String text, String packageName, EvalContext ctx) {
        return evaluateText(text, packageName, ctx, null);
    }

    /**
     * Diagnostic overload: identical decision logic, but when {@code out} is non-null every
     * intermediate signal is recorded into it (structural findings and why each did or did not
     * count, rejected checksum candidates, keyword hits, each score component, thresholds).
     *
     * <p>There is exactly one implementation of the decision logic - the other overloads delegate
     * here with {@code null} - so a traced run and a production run can never diverge. Production
     * callers pass {@code null} and pay only a handful of null checks.
     */
    public PolicyVerdict evaluateText(String text, String packageName, EvalContext ctx,
                                      PolicyEvaluationTrace out) {
        PolicyRules r = rules;
        if (out != null) {
            out.text = text != null ? text : "";
            out.length = out.text.length();
            out.proximityWindow = CONTEXT_PROXIMITY_CHARS;
            if (r != null) {
                out.suppressThreshold = r.suppressScore;
                out.redactThreshold = r.redactScore;
            }
            if (ctx != null) {
                out.passwordField = ctx.hasPasswordField;
                if (ctx.extraTokens != null) out.extraTokens.addAll(ctx.extraTokens);
            }
        }

        if (r == null || !r.policyEnabled) {
            if (out != null) { out.decision = "ALLOW"; out.reason = "policy_disabled"; }
            return PolicyVerdict.allow();
        }

        // Package-level unconditional suppression (Tier 1).
        if (packageName != null && r.suppressPackages.contains(packageName)) {
            Set<String> cats = new LinkedHashSet<>();
            cats.add("package");
            PolicyVerdict v = PolicyVerdict.suppress(
                    r.suppressScore, cats, "suppress_package:" + packageName);
            if (out != null) {
                out.shortCircuit = "suppress_package";
                out.categoriesFired.addAll(cats);
                out.total = r.suppressScore; // score components stay zeroed: no content was scored
                out.decision = "SUPPRESS";
                out.reason = v.reason;
            }
            return v;
        }

        // Gallery / image-viewer block: no collection at all while browsing stored photos.
        if (packageName != null && r.blockPackages.contains(packageName)) {
            Set<String> cats = new LinkedHashSet<>();
            cats.add("gallery");
            PolicyVerdict v = PolicyVerdict.suppress(
                    r.suppressScore, cats, "block_package:" + packageName);
            if (out != null) {
                out.shortCircuit = "block_package";
                out.categoriesFired.addAll(cats);
                out.total = r.suppressScore;
                out.decision = "SUPPRESS";
                out.reason = v.reason;
            }
            return v;
        }

        if (text == null) text = "";

        // Structural pass (on raw text - checksums are digit-based, dashes matter for SSN).
        List<StructuredDataScanner.Finding> findings = scanner.scan(text, out);

        // Keyword pass (on a single lowercased copy).
        String lower = text.toLowerCase();
        List<AhoCorasickMatcher.Match> keywordMatches = r.matcher.findMatches(lower);

        // Aggregate. Context-required findings (ambiguous shapes like bare gov-ID tokens) only
        // count when a same-category keyword occurs within the proximity window; unvalidated
        // candidates are dropped so they contribute neither score nor redaction spans.
        List<StructuredDataScanner.Finding> countedFindings = new ArrayList<>();
        Set<String> structuralCats = new LinkedHashSet<>();
        boolean hasStrongStructural = false;
        boolean hasWeakStructural = false;
        for (StructuredDataScanner.Finding f : findings) {
            PolicyEvaluationTrace.FindingTrace ft = null;
            if (out != null) {
                ft = new PolicyEvaluationTrace.FindingTrace();
                ft.kind = f.kind;
                ft.category = f.category;
                ft.start = f.start;
                ft.end = f.end;
                ft.contextRequired = f.contextRequired;
                out.findings.add(ft);
            }

            if (f.contextRequired) {
                int gap = nearestKeywordGap(f, keywordMatches);
                if (ft != null) ft.nearestKeywordGap = gap;
                if (gap > CONTEXT_PROXIMITY_CHARS) {
                    if (ft != null) {
                        ft.counted = false;
                        ft.dropReason = gap == PolicyEvaluationTrace.NO_KEYWORD
                                ? "no_" + f.category + "_keyword_in_text"
                                : "no_nearby_" + f.category + "_keyword";
                    }
                    continue;
                }
                hasWeakStructural = true;
            } else if (isStrongKind(f.kind)) {
                hasStrongStructural = true;
            } else {
                hasWeakStructural = true;
            }
            if (ft != null) ft.counted = true;
            countedFindings.add(f);
            structuralCats.add(f.category);
        }

        Set<String> keywordCats = new LinkedHashSet<>();
        Set<String> distinctKeywords = new LinkedHashSet<>();
        boolean domainHit = false;
        for (AhoCorasickMatcher.Match m : keywordMatches) {
            if (PolicyCategories.DOMAIN.equals(m.category)) {
                domainHit = true;
            } else {
                keywordCats.add(m.category);
            }
            distinctKeywords.add(m.category + ":" + m.keyword);
            if (out != null) {
                out.keywordMatches.add(new PolicyEvaluationTrace.KeywordTrace(
                        m.keyword, m.category, m.start, m.end));
            }
        }

        boolean passwordField = ctx != null && ctx.hasPasswordField;

        // Score.
        double score = 0.0;
        Set<String> firedCats = new LinkedHashSet<>();

        if (hasStrongStructural) {
            score += W_STRUCTURED_STRONG; // near-deterministic; suppresses on its own
            if (out != null) out.structuralStrong = W_STRUCTURED_STRONG;
        }
        if (hasWeakStructural) {
            score += W_STRUCTURED_WEAK; // partial; needs corroboration to suppress
            if (out != null) out.structuralWeak = W_STRUCTURED_WEAK;
        }
        if (!countedFindings.isEmpty()) {
            firedCats.addAll(structuralCats);
        }
        // Distinct keyword contributions.
        int distinctKeywordCount = distinctKeywords.size();
        if (distinctKeywordCount > 0) {
            score += W_KEYWORD * distinctKeywordCount;
            firedCats.addAll(keywordCats);
            if (out != null) out.keywordScore = W_KEYWORD * distinctKeywordCount;
        }
        if (domainHit) {
            score += W_DOMAIN;
            firedCats.add(PolicyCategories.DOMAIN);
            if (out != null) { out.domainScore = W_DOMAIN; out.domainHit = true; }
        }
        if (passwordField) {
            score += W_PASSWORD_FIELD;
            firedCats.add(PolicyCategories.CREDENTIALS);
            if (out != null) out.passwordScore = W_PASSWORD_FIELD;
        }
        // Co-occurrence bonus: keyword + structural in the same category.
        for (String cat : keywordCats) {
            if (structuralCats.contains(cat)) {
                score += W_COOCCURRENCE;
                if (out != null) out.cooccurrenceScore = W_COOCCURRENCE;
                break;
            }
        }

        if (out != null) {
            out.total = score;
            out.categoriesFired.addAll(firedCats);
        }

        // Decide.
        if (score >= r.suppressScore) {
            PolicyVerdict v = PolicyVerdict.suppress(score, firedCats, "suppress:" + reasonOf(firedCats));
            if (out != null) { out.decision = "SUPPRESS"; out.reason = v.reason; }
            return v;
        }
        if (score >= r.redactScore) {
            List<PolicyVerdict.Span> spans = buildRedactionSpans(countedFindings, keywordMatches);
            PolicyVerdict v = PolicyVerdict.redact(score, firedCats, spans);
            if (out != null) { out.decision = "REDACT"; out.reason = v.reason; }
            return v;
        }
        if (out != null) { out.decision = "ALLOW"; out.reason = "clean"; }
        return PolicyVerdict.allow();
    }

    /**
     * Character distance from {@code f} to the nearest same-category keyword match, or
     * {@link PolicyEvaluationTrace#NO_KEYWORD} when the category never appears. Keyword match
     * spans are in the lowercased copy, whose indices align 1:1 with the raw text used for
     * findings.
     *
     * <p>Returns the distance rather than a boolean so the diagnostic trace can report
     * <em>how far</em> a dropped shape was from corroboration, not just that it was dropped.
     */
    private static int nearestKeywordGap(StructuredDataScanner.Finding f,
                                         List<AhoCorasickMatcher.Match> keywordMatches) {
        int best = PolicyEvaluationTrace.NO_KEYWORD;
        for (AhoCorasickMatcher.Match m : keywordMatches) {
            if (!m.category.equals(f.category)) continue;
            int gap;
            if (m.end <= f.start) gap = f.start - m.end;
            else if (f.end <= m.start) gap = m.start - f.end;
            else gap = 0; // overlapping
            if (gap < best) best = gap;
            if (best == 0) break;
        }
        return best;
    }

    /** True if a same-category keyword lies within {@link #CONTEXT_PROXIMITY_CHARS} of {@code f}. */
    private static boolean hasNearbyKeyword(StructuredDataScanner.Finding f,
                                            List<AhoCorasickMatcher.Match> keywordMatches) {
        return nearestKeywordGap(f, keywordMatches) <= CONTEXT_PROXIMITY_CHARS;
    }

    /**
     * Capture-side (pre-pixel) evaluation. Only the package is known at this point.
     * Returns SUPPRESS for Tier-1 packages, otherwise ALLOW (content is judged later).
     */
    public PolicyVerdict evaluateCapture(String packageName) {
        PolicyRules r = rules;
        if (r == null || !r.policyEnabled) return PolicyVerdict.allow();
        if (packageName != null && r.suppressPackages.contains(packageName)) {
            Set<String> cats = new LinkedHashSet<>();
            cats.add("package");
            return PolicyVerdict.suppress(r.suppressScore, cats, "suppress_package:" + packageName);
        }
        if (packageName != null && r.blockPackages.contains(packageName)) {
            Set<String> cats = new LinkedHashSet<>();
            cats.add("gallery");
            return PolicyVerdict.suppress(r.suppressScore, cats, "block_package:" + packageName);
        }
        return PolicyVerdict.allow();
    }

    /** Applies redaction spans to text, replacing each with {@code [REDACTED:category]}. */
    public static String applyRedaction(String text, List<PolicyVerdict.Span> spans) {
        if (text == null || spans == null || spans.isEmpty()) return text;
        // Sort by start descending so earlier indices remain valid while we splice.
        List<PolicyVerdict.Span> sorted = new ArrayList<>(spans);
        sorted.sort((a, b) -> Integer.compare(b.start, a.start));
        StringBuilder sb = new StringBuilder(text);
        int lastStart = Integer.MAX_VALUE;
        for (PolicyVerdict.Span s : sorted) {
            int start = Math.max(0, s.start);
            int end = Math.min(sb.length(), s.end);
            if (end <= start || start >= lastStart) continue; // skip overlaps
            sb.replace(start, end, "[REDACTED:" + s.category + "]");
            lastStart = start;
        }
        return sb.toString();
    }

    private static List<PolicyVerdict.Span> buildRedactionSpans(
            List<StructuredDataScanner.Finding> findings,
            List<AhoCorasickMatcher.Match> keywordMatches) {
        List<PolicyVerdict.Span> spans = new ArrayList<>();
        for (StructuredDataScanner.Finding f : findings) {
            spans.add(new PolicyVerdict.Span(f.start, f.end, f.category));
        }
        // Only redact structural spans (concrete sensitive tokens). Keyword spans are
        // contextual words like "balance" and are not themselves sensitive to redact.
        return spans;
    }

    private static String reasonOf(Set<String> cats) {
        if (cats == null || cats.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (String c : cats) {
            if (sb.length() > 0) sb.append('+');
            sb.append(c);
        }
        return sb.toString();
    }
}
