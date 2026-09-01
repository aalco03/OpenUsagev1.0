package com.openusage.app.policy;

import java.util.Arrays;
import java.util.List;

/**
 * Bundled default policy rules. Used on first run (before Firestore config is fetched) and
 * as a fallback if remote config is unavailable. Remote config, when present, replaces these.
 *
 * <p>These are intentionally conservative seed lists; the study team tunes them remotely via
 * Firestore without shipping an app release.
 */
public final class PolicyDefaults {

    private PolicyDefaults() {}

    public static final List<String> FINANCIAL_KEYWORDS = Arrays.asList(
            "account balance", "available balance", "routing number", "account number",
            "card number", "card ending", "credit limit", "cvv", "security code",
            "wire transfer", "iban", "swift", "sort code", "billing address"
    );

    public static final List<String> HEALTH_KEYWORDS = Arrays.asList(
            "diagnosis", "prescription", "medication", "dosage", "lab results",
            "test results", "blood pressure", "medical record", "patient portal",
            "insurance member", "member id", "biopsy", "mri", "prognosis",
            // PHI / HIPAA identifiers (DRA: Health Information incl. PHI; insurance policy IDs).
            "protected health information", "phi", "medical record number", "mrn",
            "health plan", "subscriber id", "group number", "plan id", "policy id",
            "npi", "icd-10", "cpt code", "copay"
    );

    // Government / identity documents (DRA: passport & visa, driver license, SSN). These
    // corroborate the context-required gov-ID and bare-SSN shapes from StructuredDataScanner;
    // a shape only suppresses when one of these keywords occurs nearby.
    public static final List<String> IDENTITY_KEYWORDS = Arrays.asList(
            "passport", "passport number", "passport no", "visa number",
            "driver license", "driver's license", "drivers license", "license number",
            "dl number", "dl no", "state id", "national id", "government id",
            "ssn", "social security", "social security number", "dmv"
    );

    public static final List<String> CREDENTIAL_KEYWORDS = Arrays.asList(
            "password", "passcode", "one-time code", "verification code", "2fa",
            "authentication code", "security question", "pin number"
    );

    public static final List<String> DOMAINS = Arrays.asList(
            "chase.com", "bankofamerica.com", "wellsfargo.com", "citibank.com",
            "capitalone.com", "paypal.com", "venmo.com", "mychart", "myhealth",
            "healthcare.gov", "medicare.gov"
    );

    public static final List<String> SUPPRESS_PACKAGES = Arrays.asList(
            // Password managers
            "com.lastpass.lpandroid", "com.agilebits.onepassword", "com.dashlane",
            "com.bitwarden.authenticator", "com.google.android.apps.authenticator2",
            // Common banking apps
            "com.chase.sig.android", "com.infonow.bofa", "com.wf.wellsfargomobile",
            "com.konylabs.capitalone", "com.paypal.android.p2pmobile", "com.venmo"
    );

    // Photo galleries / image viewers. Blocked entirely (text + capture) at Gates 1 & 2 so no
    // data is collected while the participant browses stored photos (IDs, documents, etc.).
    public static final List<String> GALLERY_PACKAGES = Arrays.asList(
            "com.google.android.apps.photos", "com.google.android.apps.photosgo",
            "com.sec.android.gallery3d", "com.samsung.android.gallery.app",
            "com.android.gallery3d", "com.miui.gallery", "com.oneplus.gallery",
            "com.coloros.gallery3d", "com.oppo.gallery3d", "com.huawei.photos",
            "com.motorola.gallery"
    );

    public static final List<String> MEDIA_PACKAGES = Arrays.asList(
            "com.google.android.youtube", "com.netflix.mediaclient", "com.instagram.android",
            "com.zhiliaoapp.musically", "com.spotify.music", "com.hulu.plus",
            "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient", "com.google.android.apps.youtube.music"
    );

    public static final double SUPPRESS_SCORE = 1.0;
    public static final double REDACT_SCORE = 0.5;

    /** Builds the default {@link PolicyRules} snapshot. */
    public static PolicyRules buildDefaultRules() {
        return new PolicyRules.Builder()
                .addKeywords(PolicyCategories.FINANCIAL, FINANCIAL_KEYWORDS)
                .addKeywords(PolicyCategories.HEALTH, HEALTH_KEYWORDS)
                .addKeywords(PolicyCategories.CREDENTIALS, CREDENTIAL_KEYWORDS)
                .addKeywords(PolicyCategories.IDENTITY, IDENTITY_KEYWORDS)
                .addDomains(DOMAINS)
                .addSuppressPackages(SUPPRESS_PACKAGES)
                .addBlockPackages(GALLERY_PACKAGES)
                .addMediaPackages(MEDIA_PACKAGES)
                .suppressScore(SUPPRESS_SCORE)
                .redactScore(REDACT_SCORE)
                .policyEnabled(true)
                .ocrEscalationEnabled(false)
                .fallbackMode("inline")
                .build();
    }
}
