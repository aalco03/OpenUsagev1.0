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

    private static boolean isStrongKind(String kind) {
        return "card".equals(kind) || "ssn".equals(kind) || "iban".equals(kind);
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
        PolicyRules r = rules;
        if (r == null || !r.policyEnabled) return PolicyVerdict.allow();

        // Package-level unconditional suppression (Tier 1).
        if (packageName != null && r.suppressPackages.contains(packageName)) {
            Set<String> cats = new LinkedHashSet<>();
            cats.add("package");
            return PolicyVerdict.suppress(r.suppressScore, cats, "suppress_package:" + packageName);
        }

        if (text == null) text = "";

        // Structural pass (on raw text - checksums are digit-based, dashes matter for SSN).
        List<StructuredDataScanner.Finding> findings = scanner.scan(text);

        // Keyword pass (on a single lowercased copy).
        String lower = text.toLowerCase();
        List<AhoCorasickMatcher.Match> keywordMatches = r.matcher.findMatches(lower);

        // Aggregate.
        Set<String> structuralCats = new LinkedHashSet<>();
        boolean hasStrongStructural = false;
        boolean hasWeakStructural = false;
        for (StructuredDataScanner.Finding f : findings) {
            structuralCats.add(f.category);
            if (isStrongKind(f.kind)) hasStrongStructural = true;
            else hasWeakStructural = true;
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
        }

        boolean passwordField = ctx != null && ctx.hasPasswordField;

        // Score.
        double score = 0.0;
        Set<String> firedCats = new LinkedHashSet<>();

        if (hasStrongStructural) {
            score += W_STRUCTURED_STRONG; // near-deterministic; suppresses on its own
        }
        if (hasWeakStructural) {
            score += W_STRUCTURED_WEAK; // partial; needs corroboration to suppress
        }
        if (!findings.isEmpty()) {
            firedCats.addAll(structuralCats);
        }
        // Distinct keyword contributions.
        int distinctKeywordCount = distinctKeywords.size();
        if (distinctKeywordCount > 0) {
            score += W_KEYWORD * distinctKeywordCount;
            firedCats.addAll(keywordCats);
        }
        if (domainHit) {
            score += W_DOMAIN;
            firedCats.add(PolicyCategories.DOMAIN);
        }
        if (passwordField) {
            score += W_PASSWORD_FIELD;
            firedCats.add(PolicyCategories.CREDENTIALS);
        }
        // Co-occurrence bonus: keyword + structural in the same category.
        for (String cat : keywordCats) {
            if (structuralCats.contains(cat)) {
                score += W_COOCCURRENCE;
                break;
            }
        }

        // Decide.
        if (score >= r.suppressScore) {
            return PolicyVerdict.suppress(score, firedCats, "suppress:" + reasonOf(firedCats));
        }
        if (score >= r.redactScore) {
            List<PolicyVerdict.Span> spans = buildRedactionSpans(findings, keywordMatches);
            return PolicyVerdict.redact(score, firedCats, spans);
        }
        return PolicyVerdict.allow();
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
