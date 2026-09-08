package com.openusage.app.Services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import com.openusage.app.screenshots.ScreenshotCapture;

/**
 * Receives fallback screenshot requests from the AccessibilityService
 * when text extraction quality is insufficient.
 * Extracted from CaptureUploadService to follow Single Responsibility Principle.
 */
public class ScreenshotFallbackReceiver {

    private static final String TAG = "FallbackReceiver";
    public static final String ACTION_CAPTURE = "edu.stanford.communication.screenomics.CAPTURE_SCREENSHOT";

    /**
     * Callback interface for when the receiver needs actions from the service.
     */
    public interface FallbackCallback {
        void onLaunchPermissionActivity();
    }

    /**
     * Supplier interface to get the current ScreenshotCapture instance.
     * This avoids capturing a stale reference at registration time,
     * since screenshotCapture may be initialized after the receiver is registered.
     */
    public interface ScreenshotCaptureSupplier {
        ScreenshotCapture get();
    }

    private BroadcastReceiver receiver;
    private final Context context;
    private final FallbackCallback callback;

    public ScreenshotFallbackReceiver(Context context, FallbackCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    /**
     * Register the broadcast receiver.
     * @param captureSupplier supplier that returns the current ScreenshotCapture instance
     */
    public void register(final ScreenshotCaptureSupplier captureSupplier) {
        try {
            receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    String action = intent.getAction();
                    if (action != null && action.equals(ACTION_CAPTURE)) {
                        handleFallbackRequest(intent, captureSupplier.get());
                    }
                }
            };

            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_CAPTURE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }

            Log.d(TAG, "Screenshot fallback receiver registered");
        } catch (Exception e) {
            Log.e(TAG, "Error starting screenshot fallback receiver", e);
        }
    }

    /**
     * Unregister the broadcast receiver.
     */
    public void unregister() {
        try {
            if (receiver != null) {
                context.unregisterReceiver(receiver);
                receiver = null;
                Log.d(TAG, "Screenshot fallback receiver unregistered");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping screenshot fallback receiver", e);
        }
    }

    private void handleFallbackRequest(Intent intent, ScreenshotCapture screenshotCapture) {
        String fallbackReason = intent.getStringExtra("fallback_reason");
        String appPackage = intent.getStringExtra("app_package");
        String sessionId = intent.getStringExtra("session_id");

        Log.d(TAG, "Screenshot fallback request: reason=" + fallbackReason + ", app=" + appPackage);

        if (screenshotCapture != null && screenshotCapture.screenAwake) {
            if (screenshotCapture.mediaProjection == null) {
                Log.e(TAG, "Fallback failed: MediaProjection is null");
                if (callback != null) {
                    callback.onLaunchPermissionActivity();
                }
                return;
            }

            // Set session context for screenshot database linkage
            screenshotCapture.currentSessionId = sessionId;
            screenshotCapture.isFallbackScreenshot = true;
            // Provide the triggering package so Gate 2/Gate 3 can re-check it at capture time.
            screenshotCapture.currentPackage = appPackage;
            screenshotCapture.lastTriggerHadSensitiveSignal =
                    intent.getBooleanExtra("had_sensitive_signal", false);

            // Force immediate screenshot capture
            if (screenshotCapture.handler != null && screenshotCapture.screengrabber != null) {
                screenshotCapture.handler.removeCallbacks(screenshotCapture.screengrabber);
                screenshotCapture.handler.post(screenshotCapture.screengrabber);
                Log.d(TAG, "Screenshot fallback capture triggered for session: " + sessionId);
            } else {
                Log.w(TAG, "Screenshot handler/grabber not ready");
            }
        } else {
            Log.w(TAG, "Screenshot capture not available (capture=" + (screenshotCapture != null) +
                    ", awake=" + (screenshotCapture != null ? screenshotCapture.screenAwake : false) + ")");
        }
    }
}
