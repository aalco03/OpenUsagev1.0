package com.openusage.app.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Component tests for {@link StructuredDataScanner}: checksum validators against known vectors
 * and end-to-end shape detection with false-positive traps.
 */
public class StructuredDataScannerTest {

    private final StructuredDataScanner scanner = new StructuredDataScanner();

    // ── Luhn ──────────────────────────────────────────────

    @Test
    public void luhn_validCards() {
        assertTrue(StructuredDataScanner.luhnValid("4111111111111111")); // Visa test
        assertTrue(StructuredDataScanner.luhnValid("5500005555555559")); // Mastercard test
        assertTrue(StructuredDataScanner.luhnValid("378282246310005"));  // Amex test
    }

    @Test
    public void luhn_invalidNumbers() {
        assertFalse(StructuredDataScanner.luhnValid("4111111111111112"));
        assertFalse(StructuredDataScanner.luhnValid("1234567812345678"));
    }

    // ── ABA routing ───────────────────────────────────────

    @Test
    public void aba_validRouting() {
        // Known valid ABA routing numbers.
        assertTrue(StructuredDataScanner.abaValid("021000021")); // JPMorgan Chase
        assertTrue(StructuredDataScanner.abaValid("011401533"));
    }

    @Test
    public void aba_invalidRouting() {
        assertFalse(StructuredDataScanner.abaValid("123456789"));
        assertFalse(StructuredDataScanner.abaValid("000000000"));
        assertFalse(StructuredDataScanner.abaValid("02100002"));  // too short
    }

    // ── IBAN mod-97 ───────────────────────────────────────

    @Test
    public void iban_valid() {
        assertTrue(StructuredDataScanner.ibanValid("GB82WEST12345698765432"));
        assertTrue(StructuredDataScanner.ibanValid("DE89370400440532013000"));
    }

    @Test
    public void iban_invalid() {
        assertFalse(StructuredDataScanner.ibanValid("GB82WEST12345698765433"));
        assertFalse(StructuredDataScanner.ibanValid("XX00"));
    }

    // ── End-to-end shape detection ────────────────────────

    @Test
    public void detects_cardInText() {
        List<StructuredDataScanner.Finding> f = scanner.scan("Your card 4111 1111 1111 1111 was charged");
        assertTrue(hasKind(f, "card"));
    }

    @Test
    public void detects_ssnShape() {
        List<StructuredDataScanner.Finding> f = scanner.scan("SSN: 123-45-6789 on file");
        assertTrue(hasKind(f, "ssn"));
    }

    @Test
    public void detects_dosage() {
        List<StructuredDataScanner.Finding> f = scanner.scan("Take 500 mg twice daily");
        assertTrue(hasKind(f, "dosage"));
    }

    // ── New DRA identifier detectors ──────────────────────

    @Test
    public void detects_spaceSeparatedSsn() {
        List<StructuredDataScanner.Finding> f = scanner.scan("SSN 123 45 6789 filed");
        assertTrue(hasKind(f, "ssn"));
    }

    @Test
    public void detects_passportMrz() {
        List<StructuredDataScanner.Finding> f =
                scanner.scan("P<USASMITH<<JOHN<<<<<<<<<<<<<<<<<<<<<<<<<<<");
        assertTrue(hasKind(f, "mrz"));
    }

    @Test
    public void detects_govIdToken_asContextRequired() {
        List<StructuredDataScanner.Finding> f = scanner.scan("License X1234567 issued");
        assertTrue(hasKind(f, "govid"));
        for (StructuredDataScanner.Finding fd : f) {
            if (fd.kind.equals("govid")) assertTrue(fd.contextRequired);
        }
    }

    @Test
    public void bareNineDigits_emitContextRequiredSsn() {
        List<StructuredDataScanner.Finding> f = scanner.scan("ref 123456789 end");
        assertTrue(hasKind(f, "ssn_bare"));
    }

    @Test
    public void ignores_pureWordAsGovId() {
        List<StructuredDataScanner.Finding> f = scanner.scan("hello world message");
        assertFalse(hasKind(f, "govid"));
    }

    @Test
    public void ignores_nonLuhnTrackingNumber() {
        // UPS-style 18-digit tracking number that is NOT Luhn-valid must not be flagged as a card.
        List<StructuredDataScanner.Finding> f = scanner.scan("Tracking 1Z999AA10123456784 shipped");
        assertFalse(hasKind(f, "card"));
    }

    @Test
    public void ignores_phoneNumber() {
        List<StructuredDataScanner.Finding> f = scanner.scan("Call us at 415-555-0132 today");
        assertFalse(hasKind(f, "ssn"));
        assertFalse(hasKind(f, "card"));
    }

    private static boolean hasKind(List<StructuredDataScanner.Finding> findings, String kind) {
        for (StructuredDataScanner.Finding f : findings) {
            if (f.kind.equals(kind)) return true;
        }
        return false;
    }
}
