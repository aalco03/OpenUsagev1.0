package com.openusage.app.policy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PolicySelfTestCases - the embedded synthetic corpus driven through the real policy by the
 * debug self-test harness and by {@code PolicyDiagnosticTraceTest} on the JVM.
 *
 * <p><b>Concatenation-aware by design.</b> Accessibility extraction joins node text with
 * {@code "\n\n"} ({@code ScreenomicsAccessService#writeNodeComponent}), while ML Kit OCR
 * ({@code Text#getText()}) joins blocks with a single {@code "\n"}. Both text sources reach the
 * same {@code evaluateText}, so the pack mirrors both separator shapes rather than only feeding
 * clean single-line strings.
 *
 * <p><b>Known limitations vs. failures.</b> A case that documents an accepted gap carries
 * {@code knownLimitation = true} and an {@code expected} matching current behavior, so it
 * PASSES. That keeps FAIL meaning "regression" and nothing else. Known limitations are listed
 * separately in the run summary.
 *
 * <p>All data is synthetic. Card/IBAN/routing values are published test vectors, not real
 * instruments.
 */
public final class PolicySelfTestCases {

    private PolicySelfTestCases() {}

    /** One self-test case. */
    public static final class Case {
        public final String id;
        public final String description;
        public final String text;
        public final String packageName;
        public final PolicyVerdict.Decision expected;
        public final boolean knownLimitation;
        public final boolean passwordField;

        Case(String id, String description, String text, String packageName,
             PolicyVerdict.Decision expected, boolean knownLimitation, boolean passwordField) {
            this.id = id;
            this.description = description;
            this.text = text;
            this.packageName = packageName;
            this.expected = expected;
            this.knownLimitation = knownLimitation;
            this.passwordField = passwordField;
        }
    }

    private static final String NEUTRAL_PKG = "com.example.testapp";

    // Accessibility node separator and OCR line separator.
    private static final String AX = "\n\n";
    private static final String OCR = "\n";

    private static final List<Case> CASES;

    static {
        List<Case> c = new ArrayList<>();

        // ── Financial: strong structural ─────────────────────
        add(c, "card_spaces", "Luhn-valid card with space separators",
                "Card number: 4111 1111 1111 1111", PolicyVerdict.Decision.SUPPRESS);

        add(c, "iban_spaced", "Valid IBAN (mod-97) with spaces + keyword",
                "IBAN GB82 WEST 1234 5698 7654 32", PolicyVerdict.Decision.SUPPRESS);

        add(c, "routing_and_account", "Valid ABA routing + 9-digit account (ABA-invalid)",
                "Routing number 021000021 account number 123456789",
                PolicyVerdict.Decision.SUPPRESS);

        // ── SSN shapes ───────────────────────────────────────
        add(c, "ssn_dashed", "Canonical dashed SSN",
                "SSN: 123-45-6789", PolicyVerdict.Decision.SUPPRESS);

        add(c, "ssn_spaced", "SSN with space separators",
                "SSN: 123 45 6789", PolicyVerdict.Decision.SUPPRESS);

        add(c, "ssn_label_node_split", "Label and value in separate accessibility nodes",
                "SSN" + AX + "123-45-6789", PolicyVerdict.Decision.SUPPRESS);

        add(c, "ocr_ssn_label", "Label and value on separate OCR lines",
                "SSN" + OCR + "123-45-6789", PolicyVerdict.Decision.SUPPRESS);

        add(c, "ssn_bare_with_kw", "Bare 9-digit SSN corroborated by a nearby keyword",
                "Social security number 123456789", PolicyVerdict.Decision.SUPPRESS);

        add(c, "ssn_bare_no_kw", "Bare 9-digit run with no identity keyword - must not fire",
                "Order confirmation 123456789 shipped today",
                PolicyVerdict.Decision.ALLOW);

        // ── Identity documents ───────────────────────────────
        add(c, "passport_kw", "Gov-ID token corroborated by a passport keyword",
                "Passport number: X1234567", PolicyVerdict.Decision.SUPPRESS);

        add(c, "passport_node_split", "Passport label and value in separate nodes",
                "Passport Number" + AX + "X1234567", PolicyVerdict.Decision.SUPPRESS);

        add(c, "ocr_passport", "Passport label and value on separate OCR lines",
                "Passport Number" + OCR + "X1234567", PolicyVerdict.Decision.SUPPRESS);

        add(c, "passport_mrz", "Passport machine-readable zone",
                "P<USADOE<<JOHN<ROBERT<<<<<<<<<<<<<<<<<<<<<<<",
                PolicyVerdict.Decision.SUPPRESS);

        add(c, "driver_license_kw", "Driver license number with keyword",
                "Driver license: D1234567", PolicyVerdict.Decision.SUPPRESS);

        add(c, "govid_no_kw", "Bare alphanumeric token with no identity keyword - must not fire",
                "Reference code X1234567 for your order", PolicyVerdict.Decision.ALLOW);

        // ── Health / PHI ─────────────────────────────────────
        add(c, "phi_mrn", "Medical record number keywords, no structural shape",
                "Medical record number: 4478291", PolicyVerdict.Decision.REDACT);

        add(c, "phi_mrn_node_split", "MRN label and value in separate nodes",
                "Medical Record Number" + AX + "4478291", PolicyVerdict.Decision.REDACT);

        add(c, "dosage_with_kw", "Dosage shape corroborated by a health keyword",
                "Prescription: metformin 500mg twice daily",
                PolicyVerdict.Decision.SUPPRESS);

        // ── Credentials ──────────────────────────────────────
        add(c, "password_field", "Password node metadata plus keyword",
                "Enter your password to continue", NEUTRAL_PKG,
                PolicyVerdict.Decision.SUPPRESS, false, true);

        // ── Package short-circuits ───────────────────────────
        add(c, "banking_package", "Tier-1 banking package suppresses regardless of text",
                "Welcome back", "com.chase.sig.android",
                PolicyVerdict.Decision.SUPPRESS, false, false);

        add(c, "gallery_package", "Gallery package blocks all collection",
                "Camera roll", "com.google.android.apps.photos",
                PolicyVerdict.Decision.SUPPRESS, false, false);

        // ── Benign controls (false-positive guards) ──────────
        add(c, "benign_news", "Financial news article must not suppress",
                "The Federal Reserve raised interest rates today. Analysts expect "
                        + "markets to react through the quarter.",
                PolicyVerdict.Decision.ALLOW);

        add(c, "benign_email", "Ordinary email with incidental numbers",
                "Hi team, the meeting is at 3pm tomorrow in room 214. Please bring notes.",
                PolicyVerdict.Decision.ALLOW);

        add(c, "card_luhn_fail", "16-digit run failing Luhn - rejected, not a finding",
                "Order number 4111111111111112 confirmed", PolicyVerdict.Decision.ALLOW);

        // ── Documented gaps (expected to pass as-is) ─────────
        addKnownLimitation(c, "ssn_split_nodes",
                "SSN digits split across three nodes: no separator the scanner recognizes, "
                        + "so no SSN shape forms. Uncommon in practice (most apps render a full "
                        + "SSN in one field) but real.",
                "SSN" + AX + "123" + AX + "45" + AX + "6789",
                PolicyVerdict.Decision.ALLOW);

        CASES = Collections.unmodifiableList(c);
    }

    private static void add(List<Case> c, String id, String desc, String text,
                            PolicyVerdict.Decision expected) {
        c.add(new Case(id, desc, text, NEUTRAL_PKG, expected, false, false));
    }

    private static void add(List<Case> c, String id, String desc, String text, String pkg,
                            PolicyVerdict.Decision expected, boolean known, boolean pwd) {
        c.add(new Case(id, desc, text, pkg, expected, known, pwd));
    }

    private static void addKnownLimitation(List<Case> c, String id, String desc, String text,
                                           PolicyVerdict.Decision expected) {
        c.add(new Case(id, desc, text, NEUTRAL_PKG, expected, true, false));
    }

    /** The full pack, in declaration order. */
    public static List<Case> all() {
        return CASES;
    }
}
