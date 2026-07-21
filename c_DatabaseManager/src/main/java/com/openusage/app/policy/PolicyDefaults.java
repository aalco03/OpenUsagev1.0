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
            "insurance member", "member id", "biopsy", "mri", "prognosis"
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
                .addDomains(DOMAINS)
                .addSuppressPackages(SUPPRESS_PACKAGES)
                .addMediaPackages(MEDIA_PACKAGES)
                .suppressScore(SUPPRESS_SCORE)
                .redactScore(REDACT_SCORE)
                .policyEnabled(true)
                .ocrEscalationEnabled(false)
                .fallbackMode("inline")
                .build();
    }
}
