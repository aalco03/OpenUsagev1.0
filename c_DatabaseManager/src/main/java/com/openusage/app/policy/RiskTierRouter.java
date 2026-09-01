package com.openusage.app.policy;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.concurrent.ConcurrentHashMap;

/**
 * RiskTierRouter - decides, at capture time, how much scrutiny a fallback screenshot needs.
 *
 * <p>Rationale: fallback screenshots fire continuously on media apps (video/reels produce
 * near-zero accessibility text by design). OCRing every such frame is wasted cost on the
 * lowest-risk content. This router routes by risk tier instead of a blanket policy:
 *
 * <ul>
 *   <li><b>TIER_1_SUPPRESS</b> - banking/health/password-manager packages: never captured
 *       (also enforced at Gate 2). Listed for completeness.</li>
 *   <li><b>TIER_2_CAPTURE_DIRECT</b> - media/entertainment: skip OCR, persist immediately.
 *       Membership = {@link ApplicationInfo#category} in {VIDEO, AUDIO, GAME} (free local
 *       lookup, cached) OR remote {@code media_packages}. Demotes to Tier 3 if the trigger
 *       context carried any sensitive signal.</li>
 *   <li><b>TIER_3_OCR</b> - browsers, galleries, file managers, and all unknown packages:
 *       OCR before persist. Unknown =&gt; Tier 3, so miscategorization fails toward more
 *       scrutiny.</li>
 * </ul>
 *
 * <p>Category lookups are cached per package to keep the per-capture cost at ~0.
 */
public final class RiskTierRouter {

    public enum Tier {
        TIER_1_SUPPRESS,
        TIER_2_CAPTURE_DIRECT,
        TIER_3_OCR
    }

    private final Context appContext;
    private final SensitiveContentPolicy policy;
    // Cache: package -> ApplicationInfo.category (or a sentinel for uncategorized).
    private final ConcurrentHashMap<String, Integer> categoryCache = new ConcurrentHashMap<>();

    private static final int CATEGORY_UNCACHED = Integer.MIN_VALUE;

    public RiskTierRouter(Context context, SensitiveContentPolicy policy) {
        this.appContext = context.getApplicationContext();
        this.policy = policy;
    }

    /**
     * @param packageName the foreground package for this capture
     * @param hadSensitiveSignal true if the triggering text/node context had any lexical or
     *                           metadata hit (forces Tier 3 even for media apps)
     */
    public Tier route(String packageName, boolean hadSensitiveSignal) {
        PolicyRules rules = policy != null ? policy.getRules() : null;

        if (packageName == null || packageName.isEmpty()) {
            return Tier.TIER_3_OCR; // unknown => most scrutiny
        }

        // Tier 1: unconditional suppress list (banking/health + gallery block list).
        if (rules != null && (rules.suppressPackages.contains(packageName)
                || rules.blockPackages.contains(packageName))) {
            return Tier.TIER_1_SUPPRESS;
        }

        // Demotion rule: any sensitive signal from the trigger context forces Tier 3.
        if (hadSensitiveSignal) {
            return Tier.TIER_3_OCR;
        }

        // Tier 2: media/entertainment (remote list OR system category).
        boolean isMediaByList = rules != null && rules.mediaPackages.contains(packageName);
        if (isMediaByList || isMediaByCategory(packageName)) {
            return Tier.TIER_2_CAPTURE_DIRECT;
        }

        // Default: Tier 3 (browsers, galleries, unknown).
        return Tier.TIER_3_OCR;
    }

    private boolean isMediaByCategory(String packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false; // ApplicationInfo.category unavailable pre-API 26
        }
        int category = lookupCategory(packageName);
        return category == ApplicationInfo.CATEGORY_VIDEO
                || category == ApplicationInfo.CATEGORY_AUDIO
                || category == ApplicationInfo.CATEGORY_GAME;
    }

    private int lookupCategory(String packageName) {
        Integer cached = categoryCache.get(packageName);
        if (cached != null && cached != CATEGORY_UNCACHED) {
            return cached;
        }
        int category = ApplicationInfo.CATEGORY_UNDEFINED;
        try {
            PackageManager pm = appContext.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                category = info.category;
            }
        } catch (Exception ignored) {
            // Unknown package -> leave as undefined (will route to Tier 3).
        }
        categoryCache.put(packageName, category);
        return category;
    }

    /** Clears the per-package category cache (e.g. after app install/uninstall). */
    public void clearCache() {
        categoryCache.clear();
    }
}
