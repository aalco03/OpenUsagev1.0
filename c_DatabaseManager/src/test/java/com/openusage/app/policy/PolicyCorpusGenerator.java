package com.openusage.app.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Test-only generator that emits a deterministic (fixed-seed) labeled corpus for evaluating
 * {@link SensitiveContentPolicy}. Positives are screens that SHOULD be suppressed (or at least
 * flagged); negatives are benign screens and adversarial false-positive traps that must pass.
 *
 * <p>Each case carries the expected "flagged" label (verdict != ALLOW). This drives the
 * precision/recall metrics in {@code PolicyEvaluationTest}.
 */
public final class PolicyCorpusGenerator {

    public static final class Case {
        public final String text;
        public final String pkg;
        public final boolean expectFlagged; // true => policy should REDACT or SUPPRESS
        public final String label;          // human-readable category for debugging

        Case(String text, String pkg, boolean expectFlagged, String label) {
            this.text = text;
            this.pkg = pkg;
            this.expectFlagged = expectFlagged;
            this.label = label;
        }
    }

    // Luhn-valid card numbers for realistic positives.
    private static final String[] VALID_CARDS = {
            "4111111111111111", "5500005555555559", "378282246310005", "6011000990139424"
    };
    // Valid ABA routing numbers.
    private static final String[] VALID_ROUTING = {"021000021", "011401533", "121000248"};
    // NON-Luhn digit sequences (tracking/order numbers) — must NOT be flagged as cards.
    // (Verified non-Luhn; note "1111222233334444" IS Luhn-valid and is intentionally excluded.)
    private static final String[] TRACKING_NUMBERS = {
            "1234567812345670000", "9400111899223817200000", "1111222233334441"
    };

    public static List<Case> generate() {
        List<Case> cases = new ArrayList<>();
        Random rnd = new Random(42); // fixed seed for determinism

        // ── POSITIVES ──────────────────────────────────────
        for (String card : VALID_CARDS) {
            cases.add(new Case("Payment card " + spaced(card) + " charged $" + (rnd.nextInt(400) + 10),
                    "com.example.shop", true, "card"));
        }
        for (String routing : VALID_ROUTING) {
            cases.add(new Case("Routing number " + routing + " account balance $"
                    + (rnd.nextInt(9000) + 100), "com.example.bank", true, "routing+kw"));
        }
        cases.add(new Case("SSN 123-45-6789 verified for enrollment", "com.example.gov", true, "ssn"));
        cases.add(new Case("Diagnosis: hypertension. Prescription: take 50 mg daily",
                "com.example.health", true, "health"));
        cases.add(new Case("Your account balance is $4,210.55 and available credit limit is $10,000",
                "com.example.bank", true, "financial-kw-cluster"));
        // Package-based positives (unconditional suppress list).
        cases.add(new Case("Home screen", "com.chase.sig.android", true, "suppress-pkg"));
        cases.add(new Case("Vault", "com.lastpass.lpandroid", true, "suppress-pkg"));

        // ── NEGATIVES (benign) ─────────────────────────────
        cases.add(new Case("The quick brown fox jumps over the lazy dog near the riverbank",
                "com.example.reader", false, "plain"));
        cases.add(new Case("Breaking: local team wins championship 4 to 2 in overtime thriller",
                "com.example.news", false, "sports"));
        cases.add(new Case("Recipe: combine 2 cups flour, 1 egg, and 200 ml milk; bake 30 minutes",
                "com.example.food", false, "recipe"));
        cases.add(new Case("Weather today: high of 72 and low of 55 with light winds",
                "com.example.weather", false, "weather"));
        cases.add(new Case("Now playing: track 3 of 12, 4:35 remaining",
                "com.google.android.youtube", false, "media"));

        // ── ADVERSARIAL (false-positive traps) ─────────────
        for (String tn : TRACKING_NUMBERS) {
            cases.add(new Case("Your package with tracking " + tn + " is out for delivery",
                    "com.example.shipping", false, "tracking-number"));
        }
        cases.add(new Case("Call customer service at 415-555-0132 for questions",
                "com.example.support", false, "phone"));
        cases.add(new Case("Order confirmation number 100045567 has shipped",
                "com.example.shop", false, "order-number"));
        // News article that MENTIONS financial terms but has no structural data.
        cases.add(new Case("Analysts discussed how a rising credit limit can affect a household budget",
                "com.example.news", false, "finance-article"));
        cases.add(new Case("The museum exhibit on medical history covers early diagnosis methods",
                "com.example.news", false, "health-article"));

        return cases;
    }

    /** Formats a card number with spaces every 4 digits (realistic rendering). */
    private static String spaced(String digits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append(' ');
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    private PolicyCorpusGenerator() {}
}
