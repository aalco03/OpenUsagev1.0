package com.openusage.app.policy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The outcome of a policy evaluation.
 *
 * <ul>
 *   <li>{@link Decision#ALLOW} - no sensitive content detected; collect normally.</li>
 *   <li>{@link Decision#REDACT} - store text with matched spans replaced; for screenshots
 *       this is treated as SUPPRESS (pixels can't be cheaply redacted).</li>
 *   <li>{@link Decision#SUPPRESS} - do not store/capture at all.</li>
 * </ul>
 *
 * <p>Carries the score, the categories that fired, and (for REDACT) the character spans to
 * redact. Never carries the matched sensitive content itself, so it is safe to log the
 * {@link #reason} and {@link #categories} in audit events.
 */
public final class PolicyVerdict {

    public enum Decision { ALLOW, REDACT, SUPPRESS }

    /** A half-open character span [start, end) to redact. */
    public static final class Span {
        public final int start;
        public final int end;
        public final String category;

        public Span(int start, int end, String category) {
            this.start = start;
            this.end = end;
            this.category = category;
        }
    }

    public final Decision decision;
    public final double score;
    public final String reason;
    public final Set<String> categories;
    public final List<Span> spans;

    private PolicyVerdict(Decision decision, double score, String reason,
                          Set<String> categories, List<Span> spans) {
        this.decision = decision;
        this.score = score;
        this.reason = reason;
        this.categories = categories;
        this.spans = spans;
    }

    public static PolicyVerdict allow() {
        return new PolicyVerdict(Decision.ALLOW, 0.0, "clean",
                Collections.<String>emptySet(), Collections.<Span>emptyList());
    }

    public static PolicyVerdict redact(double score, Set<String> categories, List<Span> spans) {
        return new PolicyVerdict(Decision.REDACT, score, "redact:" + join(categories),
                categories, spans != null ? spans : new ArrayList<Span>());
    }

    public static PolicyVerdict suppress(double score, Set<String> categories, String reason) {
        return new PolicyVerdict(Decision.SUPPRESS, score,
                reason != null ? reason : "suppress:" + join(categories),
                categories, Collections.<Span>emptyList());
    }

    public boolean isAllow() { return decision == Decision.ALLOW; }
    public boolean isRedact() { return decision == Decision.REDACT; }
    public boolean isSuppress() { return decision == Decision.SUPPRESS; }

    private static String join(Set<String> cats) {
        if (cats == null || cats.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (String c : new LinkedHashSet<>(cats)) {
            if (sb.length() > 0) sb.append('+');
            sb.append(c);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "PolicyVerdict{" + decision + ", score=" + score
                + ", reason=" + reason + ", categories=" + categories + "}";
    }
}
