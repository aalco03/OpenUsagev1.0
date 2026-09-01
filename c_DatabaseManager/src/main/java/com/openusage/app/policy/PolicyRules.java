package com.openusage.app.policy;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of the policy configuration used by {@link SensitiveContentPolicy}.
 *
 * <p>Built from {@code PolicyConfigManager} (Firestore-backed) or from bundled defaults.
 * Holds the keyword dictionary (category -> keywords), domain list, package tier lists,
 * scoring thresholds, and behavior flags. A prebuilt {@link AhoCorasickMatcher} is created
 * once here so evaluation never pays automaton-build cost on the hot path.
 */
public final class PolicyRules {

    // Category -> keywords (contextual evidence for scoring).
    public final Map<String, Set<String>> keywords;
    // Sensitive domains (matched as keywords under the DOMAIN category).
    public final Set<String> domains;

    // Tier 1: unconditionally suppress capture for these packages.
    public final Set<String> suppressPackages;
    // Photo galleries / image viewers: block ALL collection (text + capture) at the early gates.
    public final Set<String> blockPackages;
    // Tier 2: media/entertainment packages that skip OCR (capture-direct).
    public final Set<String> mediaPackages;

    // Scoring thresholds.
    public final double suppressScore;
    public final double redactScore;

    // Behavior flags.
    public final boolean policyEnabled;
    public final boolean ocrEscalationEnabled;
    public final String fallbackMode; // "inline" | "quarantine"

    // Prebuilt keyword automaton (financial/health/credentials + domains).
    public final AhoCorasickMatcher matcher;

    private PolicyRules(Builder b) {
        this.keywords = Collections.unmodifiableMap(b.keywords);
        this.domains = Collections.unmodifiableSet(b.domains);
        this.suppressPackages = Collections.unmodifiableSet(b.suppressPackages);
        this.blockPackages = Collections.unmodifiableSet(b.blockPackages);
        this.mediaPackages = Collections.unmodifiableSet(b.mediaPackages);
        this.suppressScore = b.suppressScore;
        this.redactScore = b.redactScore;
        this.policyEnabled = b.policyEnabled;
        this.ocrEscalationEnabled = b.ocrEscalationEnabled;
        this.fallbackMode = b.fallbackMode;

        AhoCorasickMatcher m = new AhoCorasickMatcher();
        for (Map.Entry<String, Set<String>> e : b.keywords.entrySet()) {
            for (String kw : e.getValue()) {
                m.addKeyword(e.getKey(), kw);
            }
        }
        for (String d : b.domains) {
            m.addKeyword(PolicyCategories.DOMAIN, d);
        }
        m.build();
        this.matcher = m;
    }

    /** True if there are no rules to act on (empty dictionary and no package lists). */
    public boolean isEmpty() {
        return matcher.isEmpty() && suppressPackages.isEmpty()
                && blockPackages.isEmpty() && mediaPackages.isEmpty();
    }

    public static final class Builder {
        private final Map<String, Set<String>> keywords = new LinkedHashMap<>();
        private final Set<String> domains = new HashSet<>();
        private final Set<String> suppressPackages = new HashSet<>();
        private final Set<String> blockPackages = new HashSet<>();
        private final Set<String> mediaPackages = new HashSet<>();
        private double suppressScore = 1.0;
        private double redactScore = 0.5;
        private boolean policyEnabled = true;
        private boolean ocrEscalationEnabled = false;
        private String fallbackMode = "inline";

        public Builder addKeywords(String category, List<String> kws) {
            if (kws == null) return this;
            Set<String> set = keywords.get(category);
            if (set == null) {
                set = new HashSet<>();
                keywords.put(category, set);
            }
            for (String kw : kws) {
                if (kw != null && !kw.trim().isEmpty()) set.add(kw.toLowerCase().trim());
            }
            return this;
        }

        public Builder addDomains(List<String> ds) {
            if (ds == null) return this;
            for (String d : ds) {
                if (d != null && !d.trim().isEmpty()) domains.add(d.toLowerCase().trim());
            }
            return this;
        }

        public Builder addSuppressPackages(List<String> pkgs) {
            if (pkgs != null) for (String p : pkgs) if (p != null && !p.trim().isEmpty()) suppressPackages.add(p.trim());
            return this;
        }

        public Builder addBlockPackages(List<String> pkgs) {
            if (pkgs != null) for (String p : pkgs) if (p != null && !p.trim().isEmpty()) blockPackages.add(p.trim());
            return this;
        }

        public Builder addMediaPackages(List<String> pkgs) {
            if (pkgs != null) for (String p : pkgs) if (p != null && !p.trim().isEmpty()) mediaPackages.add(p.trim());
            return this;
        }

        public Builder suppressScore(double v) { this.suppressScore = v; return this; }
        public Builder redactScore(double v) { this.redactScore = v; return this; }
        public Builder policyEnabled(boolean v) { this.policyEnabled = v; return this; }
        public Builder ocrEscalationEnabled(boolean v) { this.ocrEscalationEnabled = v; return this; }
        public Builder fallbackMode(String v) { if (v != null) this.fallbackMode = v; return this; }

        public PolicyRules build() { return new PolicyRules(this); }
    }
}
