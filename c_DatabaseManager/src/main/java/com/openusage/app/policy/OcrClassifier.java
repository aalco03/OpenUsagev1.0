package com.openusage.app.policy;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.concurrent.TimeUnit;

/**
 * OcrClassifier - runs on-device OCR (Google ML Kit) over a Tier-3 fallback bitmap and feeds
 * the recognized text through {@link SensitiveContentPolicy}.
 *
 * <p>Used ONLY for Tier-3 frames (browsers, galleries, unknown apps) as decided by
 * {@link RiskTierRouter}, so its ~100-250 ms cost is paid at low volume. The recognizer is
 * created lazily and can be released via {@link #close()}.
 *
 * <p>This wrapper never persists the OCR text; it only derives a {@link PolicyVerdict}.
 */
public final class OcrClassifier {

    private static final String TAG = "OcrClassifier";
    private static final long OCR_TIMEOUT_MS = 5000;

    private final SensitiveContentPolicy policy;
    private volatile TextRecognizer recognizer;

    public OcrClassifier(SensitiveContentPolicy policy) {
        this.policy = policy;
    }

    private TextRecognizer getRecognizer() {
        if (recognizer == null) {
            synchronized (this) {
                if (recognizer == null) {
                    recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                }
            }
        }
        return recognizer;
    }

    /**
     * Runs OCR synchronously on {@code bitmap}, then evaluates the recognized text against the
     * policy for {@code packageName}. Returns {@link PolicyVerdict#allow()} if OCR yields no
     * text or fails (fail-open; the failure is logged).
     *
     * <p>Must NOT be called on the main thread (blocks on the OCR task).
     */
    public PolicyVerdict classify(Bitmap bitmap, String packageName) {
        if (bitmap == null || policy == null) return PolicyVerdict.allow();
        try {
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            Text result = Tasks.await(getRecognizer().process(image),
                    OCR_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            String text = result != null ? result.getText() : null;
            if (text == null || text.trim().isEmpty()) {
                return PolicyVerdict.allow();
            }
            return policy.evaluateText(text, packageName);
        } catch (Exception e) {
            Log.e(TAG, "OCR classification failed (fail-open): " + e.getMessage());
            return PolicyVerdict.allow();
        }
    }

    public void close() {
        try {
            if (recognizer != null) {
                recognizer.close();
                recognizer = null;
            }
        } catch (Exception ignored) { }
    }
}
