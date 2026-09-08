package com.openusage.app.policy;

/**
 * Canonical category names shared across the policy engine (keyword dictionary,
 * structured-data findings, scoring, and audit events).
 */
public final class PolicyCategories {
    public static final String FINANCIAL = "financial";
    public static final String HEALTH = "health";
    public static final String CREDENTIALS = "credentials";
    public static final String DOMAIN = "domain";
    // Government / identity documents: passport, visa, driver license, national/state IDs.
    public static final String IDENTITY = "identity";

    private PolicyCategories() {}
}
