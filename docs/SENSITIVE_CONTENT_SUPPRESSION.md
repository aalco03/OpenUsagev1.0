# Sensitive Content Suppression

## 1. Purpose and Scope

What sensitive information does the app actively avoid collecting, and why?

## 2. Design Principles

On-device processing is deterministic and contains no machine learning. Real-time evaluation. Fail toward suppression.

## 3. The Multi-Gate Architecture

Overview of where and when suppression decisions happen.

## 4. On-Device Layer 1: Keyword Detection

Aho-Corasick automaton for lexical pattern matching.

## 5. On-Device Layer 2: Structural Detection

Mathematical validation: Luhn (card numbers), ABA (routing numbers), SSN shapes, IBAN, medication dosages, passport/MRZ patterns.

## 6. On-Device Layer 3: The Scoring Engine

Weighted scoring, thresholds, category co-occurrence, proximity analysis.

## 7. Gate 1: UI Text Evaluation

Where accessibility text is evaluated before storage. Redaction of sensitive spans. Password-field detection.

## 8. Gate 2: Screenshot Package Re-Check

Banking, password managers, and photo galleries blocked before capture.

## 9. Off-Device VLM Content Abstraction

[User to write: Firebase-side vision-language model processing, image deletion, text-only retention.]

## 10. The Audit Trail

Metadata-only logging. What gets recorded and what never does.

## 11. Configuration and Tunability

Remote policy configuration via Firestore. Bundled defaults. Keyword lists, package lists, thresholds.

## 12. Limitations and Fail-Safe Behavior

Disabled on-device OCR scaffolding. What happens when policy evaluation fails.

