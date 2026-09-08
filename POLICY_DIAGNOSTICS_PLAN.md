# On-Device Policy Diagnostics (JSONL Trace)

Add a debug-only, on-device policy diagnostic that runs the real compiled policy and writes one
JSON line per evaluation (raw input, full per-signal trace, every score component,
decision-conditioned output) to a file you `adb pull`, driven primarily by a fast adb-triggered
self-test harness and secondarily by a targeted passive recorder.

> Revised from the original plan after verifying every claim against the current code. Changes are
> marked **[R1]**–**[R6]** and summarized at the bottom.

---

## Approach (locked decisions)

- **Self-test harness (primary):** a bundled, concatenation-aware synthetic pack is fed through the
  real on-device policy on demand via `adb shell am broadcast`; writes `policy_diag/selftest.jsonl`
  in seconds. No emails/accounts needed, fully repeatable. Lives in a debug-only source set so it
  never ships.
- **Targeted passive recorder (secondary):** debug-only recorder of real accessibility extractions
  and OCR-gate outcomes, gated by `PASSIVE_ENABLED` plus an optional `PASSIVE_PACKAGES` allowlist so
  you can scope capture to your highest-risk app(s) (e.g. patient portal / EHR); writes
  `policy_diag/diagnostics.jsonl`.
- Full per-signal trace; no runbook file (run steps live in this doc / chat).

### Why this scope is sufficient — corrected failure-mode analysis **[R1]**

The original plan argued there were two failure modes and that only one could leak. Verification
against the code shows there are **three**, and the original "extraction misses are fail-safe" claim
is false.

| Mode | Description | Leak-capable? | Covered by |
|---|---|---|---|
| **A** | Accessibility extraction misses the PHI (WebView, canvas, no accessible node) | **YES — via pixels** | Gate 3 OCR (partially); targeted passive |
| **B** | Text *is* extracted, policy fails to suppress it | YES | Self-test (directly) |
| **C** | Gate 3 OCR gate fails open or is bypassed | YES | New `gate:"ocr"` records (this plan) |

**Mode A is not fail-safe.** The fallback-screenshot veto keys off the *text* verdict:

```java
// ScreenomicsAccessService.java:679
if (lastPolicyVerdict != null && !lastPolicyVerdict.isAllow()) { /* veto */ }
```

If extraction misses the PHI, `lastPolicyVerdict == ALLOW`, no veto fires, and a **screenshot of the
visible PHI is captured.** A Mode-A miss does not shrink the storage surface — it moves the PHI from
the text path to the pixel path.

**Mode B** lives entirely inside `evaluateText`, which the self-test drives directly with the same
compiled bytecode on the device. Because extraction is a deterministic `node text + "\n\n"`
concatenation (`ScreenomicsAccessService.java:440-445`), the pack reproduces real formatting.

**Mode C** is the backstop for Mode A, and it has holes. Tier-3 frames route to
`OcrClassifier.classify` (`ScreenshotCapture.java:589`), which re-runs the same policy — so the
*decision* logic is covered by the self-test. But the wrapper fails open in four distinct ways, none
of which the self-test can see:

1. Empty/whitespace OCR result → `allow()` (`OcrClassifier.java:62-63`)
2. Timeout at 5 s → exception → `allow()` (`OcrClassifier.java:28`, `:66-69`)
3. Any other exception → `allow()` (`OcrClassifier.java:66-69`)
4. `ocrEscalationEnabled == false`, or Tier-2 media routing → OCR skipped entirely, frame persists
   (`ScreenshotCapture.java:571-572`, `:579`, `:599`)

Suppression remains defense-in-depth atop already-HIPAA-compliant storage — but the targeted passive
spot-check is **not optional**: it is the only way to learn empirically whether a given high-risk app
exposes PHI in accessible nodes (Mode A) and whether its frames survive Gate 3 (Mode C).

---

## What you get

Append-only JSONL under `.../files/policy_diag/` (never uploaded, debug builds only):
`selftest.jsonl` (harness) and `diagnostics.jsonl` (passive). Each line is one evaluation. Output is
conditioned on decision: `SUPPRESS` → `null`, `REDACT` → the redacted text, `ALLOW` → `"APPROVED"`
(input not repeated).

### Record schema (one JSON object per line, compact)

```jsonc
{
  "ts": 1690000000000,
  "package": "com.google.android.gm",
  "gate": "text",                         // text | trigger | capture | ocr
  "source": "accessibility",              // accessibility | ocr | synthetic   [R1]
  "extraction": { "text": "<raw>", "length": 214,
                  "password_field": false, "extra_tokens": ["res:pwd"] },
  "processing": {
    "structural_findings": [
      {"kind":"govid","category":"identity","start":17,"end":25,
       "context_required":true,"counted":true,
       "drop_reason":null,"nearest_keyword_gap":3}
    ],
    "rejected_candidates": [                                            // [R2]
      {"kind":"routing","start":88,"end":97,"reason":"aba_checksum_failed"}
    ],
    "keyword_matches": [
      {"keyword":"passport number","category":"identity","start":0,"end":15}
    ],
    "domain_hit": false,
    "categories_fired": ["identity"]
  },
  "scores": { "structural_strong":0.0, "structural_weak":0.4, "keyword":0.5,
              "domain":0.0, "password":0.0, "cooccurrence":0.75,
              "total":1.65, "suppress_threshold":1.0, "redact_threshold":0.5,
              "proximity_window":40 },                                  // [R3]
  "decision": "SUPPRESS",
  "reason": "suppress:identity",
  "output": null,
  "error": null,
  "case_id": "passport_kw", "expected": "SUPPRESS",   // self-test lines only
  "status": "PASS",                                   // PASS | FAIL
  "failure_kind": null,                               // FALSE_NEGATIVE | FALSE_POSITIVE | WRONG_BAND | ERROR | null
  "known_limitation": false                           // [R5]
}
```

**Removed:** the `validated` field from structural findings. `StructuredDataScanner.Finding` carries
only `category, kind, start, end, contextRequired` (`StructuredDataScanner.java:26-44`);
checksum-failed candidates are never emitted, so `validated` would be a constant `true`. **[R4]**

**Added: `rejected_candidates`.** The scanner gains an optional diagnostic sink recording shapes it
found and *rejected* — a 9-digit run that failed the ABA checksum, a 13–19 digit run that failed
Luhn, an IBAN that failed mod-97, an MRZ that failed to parse. For debugging a false negative, "we
saw the shape and the checksum rejected it" vs. "we never saw the shape" is the single most valuable
distinction, and it is currently invisible. Populated only when a trace is attached; `null` in
production. **[R2]**

Each context-required finding carries `counted`, and when dropped a `drop_reason` (e.g.
`"no_nearby_identity_keyword"`) plus `nearest_keyword_gap` (char distance to the nearest
same-category keyword), compared against `scores.proximity_window`. Score components sum to `total`,
so the math is auditable line-by-line — verified additive at `SensitiveContentPolicy.java:144-176`.
Package short-circuits (`suppress_package` / `block_package`) are recorded with the relevant category
and zeroed score components.

### OCR-gate records (`gate:"ocr"`) **[R1]**

Emitted from `ScreenshotCapture` at the Gate-3 decision point, covering Mode C:

```jsonc
{"ts":..., "package":"com.example.portal", "gate":"ocr", "source":"ocr",
 "tier":"TIER_3_OCR",              // TIER_1_SUPPRESS | TIER_2_CAPTURE_DIRECT | TIER_3_OCR
 "ocr_enabled":true,               // rules.ocrEscalationEnabled
 "fallback_mode":"inline",         // inline | quarantine
 "ocr_ran":true, "ocr_text_length":412, "ocr_failure":null,  // empty_result | timeout | exception
 "had_sensitive_signal":false,     // the trigger-context flag that forces Tier 3
 "outcome":"persisted",            // persisted | suppressed | quarantined | blank_frame
 "decision":"ALLOW", "reason":null}
```

`ocr_failure` distinguishes the four fail-open paths. `outcome:"persisted"` with
`ocr_ran:false` is the signature of a Mode-C leak and should be greppable in one line.

### Run-summary line (first line of the self-test file)

```jsonc
{"summary":true, "ts":..., "total":22, "passed":22, "failed":0,
 "failing_case_ids":[],
 "known_limitation_cases":["ssn_split_nodes"],     // pass, but documented gaps  [R5]
 "expected_cases":22, "written_cases":22,
 "rules_source":"defaults",                        // defaults | remote  [R6]
 "rules_fingerprint":"<hash of keywords+packages+thresholds>",           // [R6]
 "suppress_threshold":1.0, "redact_threshold":0.5, "proximity_window":40,
 "weights":{"strong":1.0,"weak":0.4,"keyword":0.25,"domain":0.5,"password":1.0,"cooccurrence":0.75},
 "app_version":"1.0", "git_commit":"<if available>"}
```

Hand over the JSONL and failures are diagnosable without logcat.

---

## Components to build

1. **`PolicyEvaluationTrace`** (new, pure Java, `c_DatabaseManager/.../policy`). Holder for:
   structural findings (kind, category, start, end, contextRequired, counted, drop_reason,
   nearest_keyword_gap), **rejected candidates (kind, span, reason) [R2]**, keyword matches,
   domain/password flags, categories fired, each score component, total, thresholds, **proximity
   window [R3]**, short-circuit label, decision/reason, error, and optional self-test fields
   (case_id, expected, status, failure_kind, known_limitation).

2. **`SensitiveContentPolicy` diagnostic path.** Add
   `evaluateText(text, pkg, ctx, PolicyEvaluationTrace out)`; the existing 3-arg overload delegates
   with `null`. **One implementation of the decision logic — no duplication.** When `out != null`,
   populate every field, including package short-circuit branches. Production callers pass `null` →
   zero overhead, behavior unchanged.

3. **Expose scoring constants. [R3]** `W_STRUCTURED_STRONG/WEAK`, `W_KEYWORD`, `W_PASSWORD_FIELD`,
   `W_DOMAIN`, `W_COOCCURRENCE` and — critically — **`CONTEXT_PROXIMITY_CHARS`**
   (`SensitiveContentPolicy.java:41-51`, all currently `private static final`). `nearest_keyword_gap`
   is meaningless without the window it is compared against. Thresholds come from
   `policy.getRules().suppressScore / .redactScore`, not from `PolicyDefaults`. Single source of
   truth — the harness must not hard-code duplicates.

4. **Refactor `hasNearbyKeyword`. [R3]** Currently returns `boolean` and discards the distance
   (`SensitiveContentPolicy.java:194-205`). Change to return the minimum same-category gap (or
   `Integer.MAX_VALUE` when none), so the caller derives both the boolean and
   `nearest_keyword_gap` in the same pass.

5. **`StructuredDataScanner` rejection sink. [R2]** Add an overload
   `scan(String text, PolicyEvaluationTrace out)` that records checksum/parse rejections. Existing
   `scan(String)` delegates with `null`. No behavior change.

6. **`PolicyDiagnosticWriter`** (new, Android, alongside `PolicyDumpWriter`). `PASSIVE_ENABLED` flag
   (default `false`) + optional `PASSIVE_PACKAGES` allowlist (empty = all) + `shouldRecord(pkg)`.
   `record(ctx, fileName, gate, pkg, rawText, verdict, trace)` builds the JSON (conditioned output
   via `applyRedaction` for `REDACT`) and appends one line; `reset(ctx, fileName)` clears a file.
   Constants `SELFTEST_FILE` / `PASSIVE_FILE`, dir `policy_diag`.

7. **Self-test harness (debug source set).** `app/src/debug/java/.../policy/PolicySelfTestReceiver`
   (a `BroadcastReceiver`) + `app/src/debug/AndroidManifest.xml` registering it with
   `android:exported="true"` for action `com.openusage.app.POLICY_SELFTEST`. Note: **`app/src/debug/`
   does not exist yet** (only `main`, `test`, `androidTest`) — it must be created. On trigger it
   resolves the rules (see [R6] below), resets `selftest.jsonl`, writes the run-summary line first,
   runs every pack case through the trace overload wrapped in try/catch (exceptions → `error` +
   `failure_kind:ERROR`), computes `status`/`failure_kind` per case, and writes one record per case.

8. **Live-rules mode. [R6]** The receiver accepts `--es rules live|defaults` (default `defaults`).
   `live` pulls `PolicyConfigManager.getInstance(ctx).getPolicy()` — the rules actually deployed
   (`ScreenomicsAccessService.java:137-139`) — instead of `PolicyDefaults`. A green run against
   defaults says nothing about what is on participants' phones. The summary records `rules_source`
   and a `rules_fingerprint` so a result is self-contained.

9. **`PolicySelfTestCases`** (new, pure Java). The synthetic pack as embedded
   `Case{id, description, text, package, expected, knownLimitation}` data — no asset plumbing.

10. **Targeted passive wiring in `ScreenomicsAccessService`.** At the Gate-1 text eval
    (`ScreenomicsAccessService.java:275`): if `BuildConfig.DEBUG && PolicyDiagnosticWriter.shouldRecord(pkg)`,
    create a trace, call the trace overload (else `null`), record to `diagnostics.jsonl`. Minimal
    records at the fallback-trigger veto (`gate:"trigger"`, `:679`) and `evaluateCapture`
    (`gate:"capture"`, `:684`). Live suppress/redact/store behavior untouched.

11. **Gate-3 OCR wiring in `ScreenshotCapture`. [R1]** At `ScreenshotCapture.java:560-599`, emit a
    `gate:"ocr"` record covering all branches: blank frame (`:561`), Tier-1 suppress (`:574`),
    Tier-2 fall-through (`:599`), Tier-3 quarantine (`:581`), Tier-3 inline OCR (`:589`), and
    OCR-disabled fall-through (`:571-572`). Classify the fail-open reason (`empty_result` / `timeout`
    / `exception`) — requires `OcrClassifier.classify` to surface *why* it returned `allow()`, via an
    optional trace out-param rather than swallowing it into logcat (`OcrClassifier.java:62-69`).

12. **JVM trace test** (`PolicyDiagnosticTraceTest`, no device). Runs the pack through the trace
    overload, asserts each decision == expected, each score-component sum == total (skipping
    short-circuit lines), and that `drop_reason` / `nearest_keyword_gap` are populated for dropped
    context findings; writes the full JSONL (summary + per-case) to
    `build/reports/policy/selftest-preview.jsonl` so the exact debug format is confirmed before any
    device work.

---

## Synthetic pack

Embedded in `PolicySelfTestCases`. Cases mirror real extraction where relevant.

**Accessibility-shaped (nodes joined by `\n\n`):** card (spaces), SSN (dashed / spaced /
label+value in separate nodes / split-value across nodes), bare-9-digit ±SSN keyword, passport +
keyword (inline and node-split), gov-ID token no keyword, passport MRZ, driver license + keyword,
routing + account, PHI identifiers (inline and MRN node-split), banking package, gallery package,
benign news, IBAN, benign email.

**OCR-shaped **[R1]**:** ML Kit's `Text.getText()` joins blocks with a **single `\n`**, not `\n\n`.
The concatenation-aware reasoning in the original plan targeted only the accessibility source. Add
`\n`-joined variants of the label/value and split-value cases, plus a couple of realistic OCR
confusions (`O`↔`0`, `l`↔`1`, dropped separators) so the Gate-3 path is exercised at the same
fidelity as Gate 1.

### Known limitations vs. failures **[R5]**

A case that documents an accepted gap (e.g. `ssn_split_nodes` — a value split as `123\n\n45\n\n6789`
does not form an SSN shape, since the scanner looks for `-`/space separators) must be encoded as
`expected: ALLOW` with `known_limitation: true`, so it **PASSES**. The original plan listed it under
`failing_case_ids`, which would leave every clean run permanently red and drain `FAIL` of meaning.
Known limitations are reported separately in `known_limitation_cases`. `FAIL` means regression, and
nothing else.

---

## Run steps

**Self-test (defaults):**
```bash
./gradlew :app:installDebug
adb shell am broadcast -a com.openusage.app.POLICY_SELFTEST \
  -n com.openusage.app/com.openusage.app.policy.PolicySelfTestReceiver
adb pull /sdcard/Android/data/com.openusage.app/files/policy_diag/selftest.jsonl .
```

**Self-test (live deployed rules) [R6]:** append `--es rules live` to the broadcast.

Read the first line's `passed`/`failed`/`failing_case_ids`, or `grep '"status":"FAIL"'`. Each FAIL
line carries `failure_kind` + the full trace (dropped-finding `drop_reason` /
`nearest_keyword_gap`, plus `rejected_candidates`) — send the file and it can be diagnosed without
logcat.

**Targeted passive:** set `PASSIVE_ENABLED=true`, add your app id to `PASSIVE_PACKAGES`, build +
install debug, enable accessibility, use the target app, then pull `diagnostics.jsonl`. Check for
`gate:"ocr"` lines with `outcome:"persisted"` and `ocr_ran:false` — the Mode-C signature.

---

## Privacy / safety

Records raw extraction text (needed for "what did the policy actually see"), like the existing
`PolicyDumpWriter`. Debug-only, opt-in, app-private, never uploaded. Not shipped to participants
(debug source set + default-off flags).

**Passive recorder constraint:** it writes raw PHI in plaintext to `getExternalFilesDir`, which is
adb-readable. Use a **synthetic test account with fabricated PHI only — never a real patient
record**, and delete the file after each session:

```bash
adb shell rm /sdcard/Android/data/com.openusage.app/files/policy_diag/diagnostics.jsonl
```

Self-test data is synthetic by construction.

---

## Caveats (with examples)

- **Extraction timing/dedup (passive only).** 10 s interval + 0.90 similarity de-dup: opening the
  passport email 3 s after the SSN email means the passport screen is never evaluated. Dwell >10 s
  and vary screens. Self-test is immune — it feeds text directly.
- **WebView rendering (passive only).** Gmail may expose only the subject, so an SSN in the body
  never reaches the policy; `extraction.text` shows exactly what the policy saw, distinguishing
  "extraction missed" from "detection missed." **This is Mode A — and per [R1] it is not benign, as
  the screenshot path may still capture the PHI.**
- **Node-split values.** Extraction joins nodes with `\n\n`; a value split as `123\n\n45\n\n6789`
  won't match the SSN shape — encoded as a `known_limitation` case, not a failure. Contiguous values
  and adjacent label/value (`SSN\n\n123-45-6789`) are unaffected: the 2-char separator is well inside
  the 40-char proximity window.
- **Trigger/capture gates have no text;** those records carry empty `extraction.text` with only the
  package decision.
- **OCR fail-open is silent by design** (`OcrClassifier` logs and returns `allow()`); the
  `gate:"ocr"` record is what makes it visible.

---

## Revision summary

| # | Change | Severity |
|---|---|---|
| **R1** | Corrected the HIPAA argument: Mode A is not fail-safe (text miss → screenshot still captures PHI, `ScreenomicsAccessService.java:679`). Added Mode C (OCR fail-open, 4 paths) with new `gate:"ocr"` records, Gate-3 wiring, and OCR-shaped (`\n`-joined) pack cases. | **High** |
| **R2** | Replaced the non-existent `validated` field with `rejected_candidates` — checksum/parse rejections, the highest-value missing signal for debugging false negatives. | Medium |
| **R3** | Expose `CONTEXT_PROXIMITY_CHARS` alongside the weights; emit it as `scores.proximity_window`. Refactor `hasNearbyKeyword` to return the gap. | Low |
| **R4** | Dropped `validated` from the finding schema — `Finding` has no such field (`StructuredDataScanner.java:26-44`). | Low |
| **R5** | `known_limitation` flag; documented gaps are `expected: ALLOW` and PASS, reported in `known_limitation_cases`. `FAIL` now means regression only. | Low |
| **R6** | `--es rules live` mode running the deployed `PolicyConfigManager` rules, plus `rules_fingerprint` in the summary. | Medium |

---

## Notes

- Files live in the app-external files dir (same as `PolicyDumpWriter`).
- Supersedes the earlier "offline verification report" idea; the embedded pack doubles as the JVM
  test input.
- Original plan + chat history preserved verbatim in `PlanMDAndChatHistory.rtf` (untouched).
