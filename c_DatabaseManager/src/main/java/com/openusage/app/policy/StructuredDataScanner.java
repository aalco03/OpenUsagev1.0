package com.openusage.app.policy;

import java.util.ArrayList;
import java.util.List;

/**
 * StructuredDataScanner - detects sensitive data by mathematical/structural properties
 * rather than a word bank.
 *
 * <p>Detectors (checksums and shape state machines) generalize to data never seen before
 * and carry the majority of the suppression weight in {@link SensitiveContentPolicy}:
 * <ul>
 *   <li>Card numbers: 13-19 digit runs (allowing space/dash separators) validated with Luhn</li>
 *   <li>Routing numbers: 9-digit runs validated with the ABA weighted mod-10 checksum</li>
 *   <li>SSN shapes: {@code \d{3}-\d{2}-\d{4}} via a lightweight state machine</li>
 *   <li>IBAN candidates: 2-letter country code + alphanumeric body validated with mod-97</li>
 *   <li>Dosage patterns: number + medical unit ("mg", "ml", "mcg", "units")</li>
 * </ul>
 *
 * <p>Single pass, no regex engine. Pure Java (no Android dependencies), unit-testable on a
 * plain JVM. Category names match {@link PolicyCategories}.
 */
public final class StructuredDataScanner {

    /** A structural finding: category, the kind of detector that fired, and its span. */
    public static final class Finding {
        public final String category;
        public final String kind; // "card", "routing", "ssn", "iban", "dosage"
        public final int start;   // inclusive
        public final int end;     // exclusive

        Finding(String category, String kind, int start, int end) {
            this.category = category;
            this.kind = kind;
            this.start = start;
            this.end = end;
        }
    }

    private static final String[] DOSAGE_UNITS = {"mg", "mcg", "ml", "units", "iu"};

    /**
     * Scans {@code text} for structured sensitive data. {@code text} may be raw (not
     * lowercased); dosage-unit matching is case-insensitive internally.
     */
    public List<Finding> scan(String text) {
        List<Finding> findings = new ArrayList<>();
        if (text == null || text.isEmpty()) return findings;

        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);

            if (Character.isDigit(c)) {
                // Consume a "digit group" allowing internal single space/dash separators.
                int start = i;
                StringBuilder digits = new StringBuilder();
                int j = i;
                while (j < n) {
                    char cj = text.charAt(j);
                    if (Character.isDigit(cj)) {
                        digits.append(cj);
                        j++;
                    } else if ((cj == ' ' || cj == '-') && j + 1 < n && Character.isDigit(text.charAt(j + 1))) {
                        // Separator between digits - keep scanning but don't record it.
                        j++;
                    } else {
                        break;
                    }
                }
                int end = j;
                String digitStr = digits.toString();
                int len = digitStr.length();

                boolean matched = false;

                // SSN: exact 9 digits formatted as XXX-XX-XXXX (dashes required to reduce FP).
                if (isSsnShape(text, start, end)) {
                    findings.add(new Finding(PolicyCategories.FINANCIAL, "ssn", start, end));
                    matched = true;
                }

                // Card: 13-19 digits passing Luhn.
                if (!matched && len >= 13 && len <= 19 && luhnValid(digitStr)) {
                    findings.add(new Finding(PolicyCategories.FINANCIAL, "card", start, end));
                    matched = true;
                }

                // Routing: exactly 9 digits passing ABA checksum.
                if (!matched && len == 9 && abaValid(digitStr)) {
                    findings.add(new Finding(PolicyCategories.FINANCIAL, "routing", start, end));
                    matched = true;
                }

                // Dosage: digits immediately followed by a medical unit.
                if (!matched && dosageUnitFollows(text, end)) {
                    findings.add(new Finding(PolicyCategories.HEALTH, "dosage",
                            start, unitEnd(text, end)));
                    matched = true;
                }

                i = Math.max(end, i + 1);
                continue;
            }

            // IBAN: two letters + alphanumeric body (15-34 chars total) passing mod-97.
            if (Character.isLetter(c) && i + 1 < n && Character.isLetter(text.charAt(i + 1))) {
                int ibanEnd = ibanCandidateEnd(text, i);
                if (ibanEnd > i) {
                    String candidate = stripSeparators(text, i, ibanEnd);
                    if (candidate.length() >= 15 && candidate.length() <= 34 && ibanValid(candidate)) {
                        findings.add(new Finding(PolicyCategories.FINANCIAL, "iban", i, ibanEnd));
                        i = ibanEnd;
                        continue;
                    }
                }
            }

            i++;
        }
        return findings;
    }

    // ── SSN ──────────────────────────────────────────────────

    /** True if the span looks exactly like XXX-XX-XXXX (dashes required). */
    private static boolean isSsnShape(String text, int start, int end) {
        // Reconstruct the raw substring and check the dash pattern precisely.
        String s = text.substring(start, Math.min(end, text.length()));
        if (s.length() != 11) return false;
        for (int k = 0; k < 11; k++) {
            char ch = s.charAt(k);
            if (k == 3 || k == 6) {
                if (ch != '-') return false;
            } else if (!Character.isDigit(ch)) {
                return false;
            }
        }
        return true;
    }

    // ── Luhn (card) ──────────────────────────────────────────

    /** Standard Luhn mod-10 checksum. */
    public static boolean luhnValid(String digits) {
        int sum = 0;
        boolean alt = false;
        for (int k = digits.length() - 1; k >= 0; k--) {
            int d = digits.charAt(k) - '0';
            if (alt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            alt = !alt;
        }
        return (sum % 10) == 0;
    }

    // ── ABA (routing) ────────────────────────────────────────

    /** ABA routing checksum: weighted 3,7,1 mod-10 over 9 digits. */
    public static boolean abaValid(String digits) {
        if (digits.length() != 9) return false;
        int[] w = {3, 7, 1, 3, 7, 1, 3, 7, 1};
        int sum = 0;
        for (int k = 0; k < 9; k++) {
            sum += (digits.charAt(k) - '0') * w[k];
        }
        // Reject all-zeros which passes trivially but is never a real routing number.
        if (sum == 0) return false;
        return (sum % 10) == 0;
    }

    // ── IBAN (mod-97) ────────────────────────────────────────

    private static int ibanCandidateEnd(String text, int start) {
        int j = start;
        int n = text.length();
        int alnum = 0;
        while (j < n) {
            char c = text.charAt(j);
            if (Character.isLetterOrDigit(c)) {
                alnum++;
                j++;
            } else if ((c == ' ') && j + 1 < n && Character.isLetterOrDigit(text.charAt(j + 1))) {
                j++;
            } else {
                break;
            }
            if (alnum >= 34) break;
        }
        return j;
    }

    private static String stripSeparators(String text, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int k = start; k < end && k < text.length(); k++) {
            char c = text.charAt(k);
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    /** ISO 13616 IBAN mod-97 validation. */
    public static boolean ibanValid(String iban) {
        String s = iban.toUpperCase();
        // First two chars must be letters (country), next two digits (check digits).
        if (s.length() < 4) return false;
        if (!Character.isLetter(s.charAt(0)) || !Character.isLetter(s.charAt(1))) return false;
        if (!Character.isDigit(s.charAt(2)) || !Character.isDigit(s.charAt(3))) return false;

        // Move first four chars to the end.
        String rearranged = s.substring(4) + s.substring(0, 4);

        // Convert letters to numbers (A=10 .. Z=35) and compute mod 97 incrementally.
        int remainder = 0;
        for (int k = 0; k < rearranged.length(); k++) {
            char c = rearranged.charAt(k);
            int value;
            if (Character.isDigit(c)) {
                value = c - '0';
                remainder = (remainder * 10 + value) % 97;
            } else if (Character.isLetter(c)) {
                value = c - 'A' + 10; // two-digit number
                remainder = (remainder * 100 + value) % 97;
            } else {
                return false;
            }
        }
        return remainder == 1;
    }

    // ── Dosage ───────────────────────────────────────────────

    /** True if a medical unit token immediately follows position {@code pos} (skipping one optional space). */
    private static boolean dosageUnitFollows(String text, int pos) {
        return unitEnd(text, pos) > pos;
    }

    /**
     * Returns the end index (exclusive) of a medical unit token starting at/after {@code pos}
     * (allowing a single leading space), or {@code pos} if none matches.
     */
    private static int unitEnd(String text, int pos) {
        int n = text.length();
        int p = pos;
        if (p < n && text.charAt(p) == ' ') p++;
        for (String unit : DOSAGE_UNITS) {
            int end = p + unit.length();
            if (end <= n && text.regionMatches(true, p, unit, 0, unit.length())) {
                // Ensure the unit isn't part of a longer word (e.g. "mgomething").
                if (end == n || !Character.isLetter(text.charAt(end))) {
                    return end;
                }
            }
        }
        return pos;
    }
}
