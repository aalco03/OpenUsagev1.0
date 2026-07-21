package com.openusage.app.policy;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.openusage.app.FirebaseSettings.UtilsForFirebaseSettings;

/**
 * PolicyConfigManager - Firestore-backed source of {@link PolicyRules} for the policy engine.
 *
 * <p>{@code SettingsManager} only stores integer settings, so sensitive-content rules (string
 * lists, thresholds, flags) travel through this parallel channel:
 * <ol>
 *   <li>On startup, load bundled {@link PolicyDefaults}, then overlay the last cached remote
 *       rules from SharedPreferences (offline-safe).</li>
 *   <li>Attach a Firestore snapshot listener on the group's policy document; on change, parse,
 *       cache to SharedPreferences, and rebuild the shared {@link SensitiveContentPolicy}'s
 *       rules atomically.</li>
 * </ol>
 *
 * <p>Firestore path: {@code settings_profiles/{GROUP}/policy/sensitive_content} with a
 * {@code settings_profiles/_default_/policy/sensitive_content} fallback.
 *
 * <p>Expected document shape (all fields optional):
 * <pre>
 * {
 *   "policy_enabled": true,
 *   "ocr_escalation_enabled": false,
 *   "fallback_mode": "inline",
 *   "suppress_score": 1.0,
 *   "redact_score": 0.5,
 *   "keywords": { "financial": [...], "health": [...], "credentials": [...] },
 *   "domains": [...],
 *   "suppress_packages": [...],
 *   "media_packages": [...]
 * }
 * </pre>
 */
public final class PolicyConfigManager {

    private static final String TAG = "PolicyConfigManager";
    private static final String PREFS = "policy_config";
    private static final String KEY_CACHED_JSON = "cached_rules_json";

    private static final String COL_PROFILES = "settings_profiles";
    private static final String SUBCOL_POLICY = "policy";
    private static final String DOC_SENSITIVE = "sensitive_content";
    private static final String DEFAULT_GROUP = "_default_";

    private static volatile PolicyConfigManager instance;

    private final Context appContext;
    private final SensitiveContentPolicy policy;
    private final Gson gson = new Gson();
    private com.google.firebase.firestore.ListenerRegistration registration;

    private PolicyConfigManager(Context context) {
        this.appContext = context.getApplicationContext();
        // Start from defaults + any cached remote rules so the policy is usable immediately.
        PolicyRules initial = loadInitialRules();
        this.policy = new SensitiveContentPolicy(initial);
    }

    public static PolicyConfigManager getInstance(Context context) {
        if (instance == null) {
            synchronized (PolicyConfigManager.class) {
                if (instance == null) {
                    instance = new PolicyConfigManager(context);
                }
            }
        }
        return instance;
    }

    /** The shared decider. Safe to call from any thread. */
    public SensitiveContentPolicy getPolicy() {
        return policy;
    }

    // ── Startup load ─────────────────────────────────────────

    private PolicyRules loadInitialRules() {
        try {
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String cached = prefs.getString(KEY_CACHED_JSON, null);
            if (!TextUtils.isEmpty(cached)) {
                PolicyRules parsed = parseRules(cached);
                if (parsed != null) {
                    Log.i(TAG, "Loaded policy rules from cache");
                    return parsed;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load cached policy rules, using defaults: " + e.getMessage());
        }
        Log.i(TAG, "Using bundled default policy rules");
        return PolicyDefaults.buildDefaultRules();
    }

    // ── Remote load ──────────────────────────────────────────

    /** Attaches a live listener on the group policy doc, falling back to {@code _default_}. */
    public void startListening() {
        String group = UtilsForFirebaseSettings.getGroupCode(appContext);
        if (TextUtils.isEmpty(group)) group = DEFAULT_GROUP;
        listenTo(group);
    }

    private void listenTo(final String group) {
        try {
            if (registration != null) registration.remove();
            registration = FirebaseFirestore.getInstance()
                    .collection(COL_PROFILES).document(group)
                    .collection(SUBCOL_POLICY).document(DOC_SENSITIVE)
                    .addSnapshotListener((snapshot, error) -> onSnapshot(group, snapshot, error));
        } catch (Exception e) {
            Log.e(TAG, "Error attaching policy listener: " + e.getMessage());
        }
    }

    private void onSnapshot(String group, @Nullable DocumentSnapshot snapshot,
                            @Nullable FirebaseFirestoreException error) {
        if (error != null) {
            Log.e(TAG, "Policy listener error: " + error.getMessage());
            return;
        }
        if (snapshot == null || !snapshot.exists() || snapshot.getData() == null) {
            // No group-specific policy; fall back to _default_ once.
            if (!DEFAULT_GROUP.equals(group)) {
                Log.d(TAG, "No policy for group " + group + "; falling back to " + DEFAULT_GROUP);
                listenTo(DEFAULT_GROUP);
            } else {
                Log.d(TAG, "No default policy document found; keeping current rules");
            }
            return;
        }
        try {
            String json = gson.toJson(snapshot.getData());
            PolicyRules parsed = parseRules(json);
            if (parsed != null) {
                policy.setRules(parsed);
                cacheJson(json);
                Log.i(TAG, "Applied remote policy rules for group " + group);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply remote policy rules: " + e.getMessage());
        }
    }

    private void cacheJson(String json) {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_CACHED_JSON, json).apply();
    }

    // ── Parsing ──────────────────────────────────────────────

    /** Parses a policy JSON document into {@link PolicyRules}. Returns null on hard failure. */
    PolicyRules parseRules(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            PolicyRules.Builder b = new PolicyRules.Builder();

            if (root.has("keywords") && root.get("keywords").isJsonObject()) {
                JsonObject kw = root.getAsJsonObject("keywords");
                for (Map.Entry<String, com.google.gson.JsonElement> e : kw.entrySet()) {
                    b.addKeywords(e.getKey(), toStringList(e.getValue()));
                }
            }
            if (root.has("domains")) b.addDomains(toStringList(root.get("domains")));
            if (root.has("suppress_packages")) b.addSuppressPackages(toStringList(root.get("suppress_packages")));
            if (root.has("media_packages")) b.addMediaPackages(toStringList(root.get("media_packages")));

            if (root.has("suppress_score")) b.suppressScore(root.get("suppress_score").getAsDouble());
            else b.suppressScore(PolicyDefaults.SUPPRESS_SCORE);
            if (root.has("redact_score")) b.redactScore(root.get("redact_score").getAsDouble());
            else b.redactScore(PolicyDefaults.REDACT_SCORE);

            if (root.has("policy_enabled")) b.policyEnabled(root.get("policy_enabled").getAsBoolean());
            if (root.has("ocr_escalation_enabled")) b.ocrEscalationEnabled(root.get("ocr_escalation_enabled").getAsBoolean());
            if (root.has("fallback_mode")) b.fallbackMode(root.get("fallback_mode").getAsString());

            return b.build();
        } catch (Exception e) {
            Log.e(TAG, "parseRules failed: " + e.getMessage());
            return null;
        }
    }

    private static List<String> toStringList(com.google.gson.JsonElement el) {
        List<String> out = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (com.google.gson.JsonElement item : el.getAsJsonArray()) {
                try {
                    out.add(item.getAsString());
                } catch (Exception ignored) { }
            }
        }
        return out;
    }

    public void stop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
