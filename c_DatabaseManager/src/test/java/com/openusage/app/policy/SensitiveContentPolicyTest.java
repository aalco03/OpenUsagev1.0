package com.openusage.app.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Behavior tests for {@link SensitiveContentPolicy}: scoring thresholds, co-occurrence,
 * redaction span math, package suppression, and precision on benign text.
 */
public class SensitiveContentPolicyTest {

    private SensitiveContentPolicy policy;

    @Before
    public void setup() {
        policy = new SensitiveContentPolicy(PolicyDefaults.buildDefaultRules());
    }

    @Test
    public void suppresses_luhnCard() {
        PolicyVerdict v = policy.evaluateText("Card 4111 1111 1111 1111 on file", "com.example.shop");
        assertTrue(v.isSuppress());
        assertTrue(v.categories.contains(PolicyCategories.FINANCIAL));
    }

    @Test
    public void suppresses_passwordField() {
        SensitiveContentPolicy.EvalContext ctx = new SensitiveContentPolicy.EvalContext().password(true);
        PolicyVerdict v = policy.evaluateText("Enter your credentials", "com.example.app", ctx);
        assertTrue(v.isSuppress());
        assertTrue(v.categories.contains(PolicyCategories.CREDENTIALS));
    }

    @Test
    public void suppresses_suppressListPackage() {
        PolicyVerdict v = policy.evaluateText("anything", "com.chase.sig.android");
        assertTrue(v.isSuppress());
    }

    @Test
    public void blocks_galleryPackage_text() {
        // Gallery/photo viewers must be blocked entirely at Gate 1, regardless of content.
        PolicyVerdict v = policy.evaluateText("just some photo captions", "com.google.android.apps.photos");
        assertTrue(v.isSuppress());
        assertTrue(v.reason.startsWith("block_package:"));
        assertTrue(v.categories.contains("gallery"));
    }

    @Test
    public void blocks_galleryPackage_capture() {
        // Gate 2 capture check must also suppress gallery packages (no screenshot).
        PolicyVerdict v = policy.evaluateCapture("com.sec.android.gallery3d");
        assertTrue(v.isSuppress());
        assertTrue(v.reason.startsWith("block_package:"));
    }

    @Test
    public void allows_nonGalleryPackage_capture() {
        // A non-blocked, non-suppress package passes the capture gate (content judged later).
        PolicyVerdict v = policy.evaluateCapture("com.example.reader");
        assertTrue(v.isAllow());
    }

    @Test
    public void allows_benignNewsArticleAboutCreditCards() {
        // A news article mentioning "credit limit" once, no structural data => should not suppress.
        String article = "The bank announced changes to how a credit limit is calculated for new customers.";
        PolicyVerdict v = policy.evaluateText(article, "com.google.android.apps.magazines");
        assertFalse("benign article should not be suppressed", v.isSuppress());
    }

    @Test
    public void allows_plainText() {
        PolicyVerdict v = policy.evaluateText("The quick brown fox jumps over the lazy dog", "com.example.reader");
        assertTrue(v.isAllow());
    }

    @Test
    public void redaction_replacesStructuralSpan() {
        // Force a REDACT by using a structural SSN hit (score == suppress via structural=1.0).
        // Lower suppress threshold scenario: build custom rules where structural alone redacts.
        PolicyRules rules = new PolicyRules.Builder()
                .addKeywords(PolicyCategories.FINANCIAL, PolicyDefaults.FINANCIAL_KEYWORDS)
                .suppressScore(2.0)  // require more to suppress
                .redactScore(0.5)    // structural (1.0) lands in REDACT band
                .build();
        SensitiveContentPolicy p = new SensitiveContentPolicy(rules);
        String text = "SSN 123-45-6789 recorded";
        PolicyVerdict v = p.evaluateText(text, "com.example.app");
        assertTrue(v.isRedact());
        String redacted = SensitiveContentPolicy.applyRedaction(text, v.spans);
        assertFalse(redacted.contains("123-45-6789"));
        assertTrue(redacted.contains("[REDACTED:financial]"));
    }

    // ── DRA identifier expansion ──────────────────────────

    @Test
    public void suppresses_passportNumberWithKeyword() {
        PolicyVerdict v = policy.evaluateText(
                "Passport number: X1234567 issued 2020", "com.example.travel");
        assertTrue(v.isSuppress());
        assertTrue(v.categories.contains(PolicyCategories.IDENTITY));
    }

    @Test
    public void allows_govIdTokenWithoutKeyword() {
        // A gov-ID-shaped token with no nearby identity keyword must not suppress.
        PolicyVerdict v = policy.evaluateText("Order X1234567 shipped today", "com.example.shop");
        assertTrue(v.isAllow());
    }

    @Test
    public void suppresses_spaceSeparatedSsn() {
        PolicyVerdict v = policy.evaluateText("Employee SSN 123 45 6789 on file", "com.example.hr");
        assertTrue(v.isSuppress());
    }

    @Test
    public void suppresses_bareSsnWithKeyword() {
        PolicyVerdict v = policy.evaluateText(
                "Social security number 123456789 verified", "com.example.hr");
        assertTrue(v.isSuppress());
    }

    @Test
    public void allows_bareNineDigitsWithoutKeyword() {
        PolicyVerdict v = policy.evaluateText("Reference id 123456789 processed", "com.example.app");
        assertTrue(v.isAllow());
    }

    @Test
    public void phiIdentifiers_crossRedactBand() {
        // Multiple PHI/health keywords should at least redact (keyword-only, no structural hit).
        PolicyVerdict v = policy.evaluateText(
                "Patient diagnosis noted; medical record number assigned", "com.example.health");
        assertFalse("PHI identifiers should not be allowed outright", v.isAllow());
    }

    @Test
    public void coOccurrence_boostsScore() {
        // Keyword + structural in the same category should suppress via the co-occurrence bonus.
        PolicyVerdict v = policy.evaluateText(
                "Your account number 4111111111111111 and balance", "com.example.bank");
        assertTrue(v.isSuppress());
    }

    @Test
    public void disabledPolicy_allowsEverything() {
        PolicyRules disabled = new PolicyRules.Builder()
                .addKeywords(PolicyCategories.FINANCIAL, PolicyDefaults.FINANCIAL_KEYWORDS)
                .policyEnabled(false)
                .build();
        SensitiveContentPolicy p = new SensitiveContentPolicy(disabled);
        PolicyVerdict v = p.evaluateText("Card 4111 1111 1111 1111", "com.chase.sig.android");
        assertTrue(v.isAllow());
    }
}
