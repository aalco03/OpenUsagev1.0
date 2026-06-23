package com.openusage.app.TextBasedEventData;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.openusage.app.FirebaseSettings.FirebaseManagerSingleton;
import com.openusage.app.FirebaseSettings.UtilsForFirebaseSettings;

public class SessionDataUploaderToFireStore {

    private static final String TAG = "SessionDataUploader";
    private static SessionDataUploaderToFireStore instance;

    private final Context context;
    private final EventDatabaseHelper dbHelper;
    private final FirebaseFirestore firestore;

    private static final int MAX_BATCH_SIZE = 200;

    private SessionDataUploaderToFireStore(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = EventDatabaseHelper.getInstance(this.context);
        this.firestore = FirebaseManagerSingleton.getFirestore();
    }

    public static synchronized SessionDataUploaderToFireStore getInstance(Context context) {
        if (instance == null) {
            instance = new SessionDataUploaderToFireStore(context);
        }
        return instance;
    }

    public void uploadUnsyncedSessions(boolean isUserTryingToLogout) {

        if (!NetworkUtils.isInternetAvailable(context)) {
            return;
        }

        String subjectId = UtilsForFirebaseSettings.getCodeAndNumber(context);
        if (subjectId == null || subjectId.isEmpty()) {
            Log.d(TAG, "Subject ID is empty. Cannot upload session data.");
            return;
        }

        try {
            uploadUserSessions(subjectId);
            uploadAppSessions(subjectId);
            uploadTextExtractions(subjectId);
            uploadScreenshots(subjectId);
            uploadHistoricalUsage(subjectId);
            uploadStepHistory(subjectId);
        } catch (Exception e) {
            Log.e(TAG, "Error uploading session data to Firestore", e);
        }
    }

    private void uploadUserSessions(String subjectId) {
        Cursor cursor = null;
        try {
            cursor = dbHelper.getUnsyncedUserSessions(MAX_BATCH_SIZE);
            if (cursor == null || !cursor.moveToFirst()) {
                Log.d(TAG, "No unsynced user sessions to upload");
                return;
            }

            CollectionReference collection = firestore
                    .collection("users")
                    .document(subjectId)
                    .collection("user_sessions");

            List<String> successfulIds = new ArrayList<>();
            List<String> failedIds = new ArrayList<>();
            int totalCount = 0;

            do {
                String userSessionId = cursor.getString(cursor.getColumnIndexOrThrow("user_session_id"));
                totalCount++;

                Map<String, Object> data = new HashMap<>();
                data.put("user_session_id", userSessionId);
                data.put("user_id", cursor.getString(cursor.getColumnIndexOrThrow("user_id")));
                data.put("login_time", cursor.getLong(cursor.getColumnIndexOrThrow("login_time")));
                data.put("logout_time", cursor.getLong(cursor.getColumnIndexOrThrow("logout_time")));
                data.put("session_duration", cursor.getLong(cursor.getColumnIndexOrThrow("session_duration")));
                data.put("is_active", cursor.getInt(cursor.getColumnIndexOrThrow("is_active")));
                data.put("device_info", cursor.getString(cursor.getColumnIndexOrThrow("device_info")));
                data.put("app_version", cursor.getString(cursor.getColumnIndexOrThrow("app_version")));
                data.put("created_at", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
                data.put("updated_at", cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")));

                try {
                    // SYNCHRONOUS write - wait for Firestore to confirm success
                    com.google.android.gms.tasks.Tasks.await(collection.document(userSessionId).set(data));
                    successfulIds.add(userSessionId);
                    Log.d(TAG, "Successfully uploaded user session: " + userSessionId);
                } catch (Exception e) {
                    failedIds.add(userSessionId);
                    Log.e(TAG, "Failed to upload user session: " + userSessionId + " - " + e.getMessage(), e);
                }
            } while (cursor.moveToNext());

            // Only mark successfully uploaded items as synced
            for (String id : successfulIds) {
                dbHelper.markUserSessionSynced(id);
            }

            Log.i(TAG, String.format("User session sync complete: %d total, %d succeeded, %d failed",
                    totalCount, successfulIds.size(), failedIds.size()));

            if (!failedIds.isEmpty()) {
                Log.w(TAG, "Failed user session IDs: " + failedIds.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error uploading user sessions", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void uploadAppSessions(String subjectId) {
        Cursor cursor = null;
        try {
            cursor = dbHelper.getUnsyncedAppSessions(MAX_BATCH_SIZE);
            if (cursor == null || !cursor.moveToFirst()) {
                Log.d(TAG, "No unsynced app sessions to upload");
                return;
            }

            CollectionReference collection = firestore
                    .collection("users")
                    .document(subjectId)
                    .collection("app_sessions");

            List<String> successfulIds = new ArrayList<>();
            List<String> failedIds = new ArrayList<>();
            int totalCount = 0;

            do {
                String sessionId = cursor.getString(cursor.getColumnIndexOrThrow("session_id"));
                totalCount++;

                Map<String, Object> data = new HashMap<>();
                data.put("session_id", sessionId);
                data.put("user_session_id", cursor.getString(cursor.getColumnIndexOrThrow("user_session_id")));
                data.put("user_id", cursor.getString(cursor.getColumnIndexOrThrow("user_id")));
                data.put("app_package_name", cursor.getString(cursor.getColumnIndexOrThrow("app_package_name")));
                data.put("app_name", cursor.getString(cursor.getColumnIndexOrThrow("app_name")));
                data.put("start_time", cursor.getLong(cursor.getColumnIndexOrThrow("start_time")));
                data.put("end_time", cursor.getLong(cursor.getColumnIndexOrThrow("end_time")));
                data.put("duration", cursor.getLong(cursor.getColumnIndexOrThrow("duration")));
                data.put("interaction_count", cursor.getInt(cursor.getColumnIndexOrThrow("interaction_count")));
                data.put("is_active", cursor.getInt(cursor.getColumnIndexOrThrow("is_active")));
                data.put("device_orientation", cursor.getString(cursor.getColumnIndexOrThrow("device_orientation")));
                data.put("battery_level", cursor.getInt(cursor.getColumnIndexOrThrow("battery_level")));
                data.put("network_type", cursor.getString(cursor.getColumnIndexOrThrow("network_type")));
                data.put("app_category", cursor.getString(cursor.getColumnIndexOrThrow("app_category")));
                data.put("launch_count", cursor.getInt(cursor.getColumnIndexOrThrow("launch_count")));
                data.put("total_time_foreground", cursor.getLong(cursor.getColumnIndexOrThrow("total_time_foreground")));
                data.put("created_at", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
                data.put("updated_at", cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")));

                try {
                    // SYNCHRONOUS write - wait for Firestore to confirm success
                    com.google.android.gms.tasks.Tasks.await(collection.document(sessionId).set(data));
                    successfulIds.add(sessionId);
                    Log.d(TAG, "Successfully uploaded app session: " + sessionId + " (" + data.get("app_package_name") + ")");
                } catch (Exception e) {
                    failedIds.add(sessionId);
                    Log.e(TAG, "Failed to upload app session: " + sessionId + " - " + e.getMessage(), e);
                }
            } while (cursor.moveToNext());

            // Only mark successfully uploaded items as synced
            for (String id : successfulIds) {
                dbHelper.markAppSessionSynced(id);
            }

            Log.i(TAG, String.format("App session sync complete: %d total, %d succeeded, %d failed",
                    totalCount, successfulIds.size(), failedIds.size()));

            if (!failedIds.isEmpty()) {
                Log.w(TAG, "Failed app session IDs: " + failedIds.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error uploading app sessions", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void uploadTextExtractions(String subjectId) {
        Cursor cursor = null;
        try {
            cursor = dbHelper.getUnsyncedTextExtractions(MAX_BATCH_SIZE);
            if (cursor == null || !cursor.moveToFirst()) {
                Log.d(TAG, "No unsynced text extractions to upload");
                return;
            }

            CollectionReference collection = firestore
                    .collection("users")
                    .document(subjectId)
                    .collection("ui_text_extractions");

            List<Long> successfulIds = new ArrayList<>();
            List<Long> failedIds = new ArrayList<>();
            int totalCount = 0;

            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                totalCount++;

                Map<String, Object> data = new HashMap<>();
                data.put("id", id);
                data.put("session_id", cursor.getString(cursor.getColumnIndexOrThrow("session_id")));
                data.put("user_session_id", cursor.getString(cursor.getColumnIndexOrThrow("user_session_id")));
                data.put("user_id", cursor.getString(cursor.getColumnIndexOrThrow("user_id")));
                data.put("screenshot_filename", cursor.getString(cursor.getColumnIndexOrThrow("screenshot_filename")));
                data.put("extracted_text", cursor.getString(cursor.getColumnIndexOrThrow("extracted_text")));
                data.put("extraction_timestamp", cursor.getLong(cursor.getColumnIndexOrThrow("extraction_timestamp")));
                data.put("has_valid_content", cursor.getInt(cursor.getColumnIndexOrThrow("has_valid_content")));
                data.put("text_length", cursor.getInt(cursor.getColumnIndexOrThrow("text_length")));
                data.put("window_title", cursor.getString(cursor.getColumnIndexOrThrow("window_title")));
                data.put("app_package_name", cursor.getString(cursor.getColumnIndexOrThrow("app_package_name")));
                data.put("created_at", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));

                try {
                    // SYNCHRONOUS write - wait for Firestore to confirm success
                    com.google.android.gms.tasks.Tasks.await(collection.document(String.valueOf(id)).set(data));
                    successfulIds.add(id);
                    Log.d(TAG, "Successfully uploaded text extraction ID: " + id + " (app: " + data.get("app_package_name") + ")");
                } catch (Exception e) {
                    failedIds.add(id);
                    Log.e(TAG, "Failed to upload text extraction ID: " + id + " - " + e.getMessage(), e);
                }
            } while (cursor.moveToNext());

            // Only mark successfully uploaded items as synced
            for (Long id : successfulIds) {
                dbHelper.markTextExtractionSynced(id);
            }

            Log.i(TAG, String.format("Text extraction sync complete: %d total, %d succeeded, %d failed",
                    totalCount, successfulIds.size(), failedIds.size()));

            if (!failedIds.isEmpty()) {
                Log.w(TAG, "Failed extraction IDs: " + failedIds.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error uploading text extractions", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void uploadScreenshots(String subjectId) {
        Cursor cursor = null;
        try {
            cursor = dbHelper.getUnsyncedScreenshots(MAX_BATCH_SIZE);
            if (cursor == null || !cursor.moveToFirst()) {
                Log.d(TAG, "No unsynced screenshots to upload");
                return;
            }

            CollectionReference collection = firestore
                    .collection("users")
                    .document(subjectId)
                    .collection("screenshots");

            List<Long> successfulIds = new ArrayList<>();
            List<Long> failedIds = new ArrayList<>();
            int totalCount = 0;

            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                totalCount++;

                Map<String, Object> data = new HashMap<>();
                data.put("id", id);
                data.put("session_id", cursor.getString(cursor.getColumnIndexOrThrow("session_id")));
                data.put("filename", cursor.getString(cursor.getColumnIndexOrThrow("filename")));
                data.put("file_path", cursor.getString(cursor.getColumnIndexOrThrow("file_path")));
                
                // Generate Firebase Storage URL
                String filename = cursor.getString(cursor.getColumnIndexOrThrow("filename"));
                String storageUrl = "gs://gdp-b-participant-logs.firebasestorage.app/" + subjectId + "/" + filename;
                data.put("storage_url", storageUrl);
                
                data.put("capture_timestamp", cursor.getLong(cursor.getColumnIndexOrThrow("capture_timestamp")));
                data.put("file_size", cursor.getLong(cursor.getColumnIndexOrThrow("file_size")));
                data.put("is_uploaded", cursor.getInt(cursor.getColumnIndexOrThrow("is_uploaded")));
                data.put("created_at", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));

                try {
                    // SYNCHRONOUS write - wait for Firestore to confirm success
                    com.google.android.gms.tasks.Tasks.await(collection.document(String.valueOf(id)).set(data));
                    successfulIds.add(id);
                    Log.d(TAG, "Successfully uploaded screenshot ID: " + id + " (" + filename + ")");
                } catch (Exception e) {
                    failedIds.add(id);
                    Log.e(TAG, "Failed to upload screenshot ID: " + id + " - " + e.getMessage(), e);
                }
            } while (cursor.moveToNext());

            // Only mark successfully uploaded items as synced
            for (Long id : successfulIds) {
                dbHelper.markScreenshotSynced(id);
            }

            Log.i(TAG, String.format("Screenshot sync complete: %d total, %d succeeded, %d failed",
                    totalCount, successfulIds.size(), failedIds.size()));

            if (!failedIds.isEmpty()) {
                Log.w(TAG, "Failed screenshot IDs: " + failedIds.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error uploading screenshots", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void uploadHistoricalUsage(String subjectId) {
        Cursor cursor = null;
        try {
            cursor = dbHelper.getUnsyncedHistoricalUsage(MAX_BATCH_SIZE);
            if (cursor == null || !cursor.moveToFirst()) {
                Log.d(TAG, "No unsynced historical usage to upload");
                return;
            }

            CollectionReference collection = firestore
                    .collection("users")
                    .document(subjectId)
                    .collection("historical_usage");

            List<Long> successfulIds = new ArrayList<>();
            List<Long> failedIds = new ArrayList<>();
            int totalCount = 0;

            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                totalCount++;

                Map<String, Object> data = new HashMap<>();
                data.put("user_id", cursor.getString(cursor.getColumnIndexOrThrow("user_id")));
                data.put("interval_type", cursor.getString(cursor.getColumnIndexOrThrow("interval_type")));
                data.put("period_label", cursor.getString(cursor.getColumnIndexOrThrow("period_label")));
                data.put("period_start_ms", cursor.getLong(cursor.getColumnIndexOrThrow("period_start_ms")));
                data.put("period_end_ms", cursor.getLong(cursor.getColumnIndexOrThrow("period_end_ms")));
                data.put("app_package", cursor.getString(cursor.getColumnIndexOrThrow("app_package")));
                data.put("foreground_time_ms", cursor.getLong(cursor.getColumnIndexOrThrow("foreground_time_ms")));
                data.put("visible_time_ms", cursor.getLong(cursor.getColumnIndexOrThrow("visible_time_ms")));
                data.put("last_used_ms", cursor.getLong(cursor.getColumnIndexOrThrow("last_used_ms")));
                data.put("captured_at", cursor.getLong(cursor.getColumnIndexOrThrow("captured_at")));

                // Document ID: intervalType_periodLabel_appPackage
                String intervalType = cursor.getString(cursor.getColumnIndexOrThrow("interval_type"));
                String periodLabel = cursor.getString(cursor.getColumnIndexOrThrow("period_label"));
                String appPackage = cursor.getString(cursor.getColumnIndexOrThrow("app_package"));
                String docId = intervalType + "_" + periodLabel + "_" + appPackage.replace(".", "_");

                try {
                    com.google.android.gms.tasks.Tasks.await(collection.document(docId).set(data));
                    successfulIds.add(id);
                    Log.d(TAG, "Uploaded historical usage: " + docId);
                } catch (Exception e) {
                    failedIds.add(id);
                    Log.e(TAG, "Failed to upload historical usage: " + docId + " - " + e.getMessage(), e);
                }
            } while (cursor.moveToNext());

            for (Long id : successfulIds) {
                dbHelper.markHistoricalUsageSynced(id);
            }

            Log.i(TAG, String.format("Historical usage sync: %d total, %d succeeded, %d failed",
                    totalCount, successfulIds.size(), failedIds.size()));

        } catch (Exception e) {
            Log.e(TAG, "Error uploading historical usage", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Upload Health Connect step-history buckets (PASSIVE group) to Firestore.
     * Uses batched writes with deterministic, idempotent document IDs
     * (granularity_periodLabel) so re-syncing overlapping ranges upserts rather than
     * duplicating. Loops through all unsynced records in a single call so the manual
     * PASSIVE sync uploads everything in one pass.
     */
    private void uploadStepHistory(String subjectId) {
        try {
            CollectionReference collection = firestore
                    .collection("users")
                    .document(subjectId)
                    .collection("step_history");

            int grandTotal = 0;
            int grandSuccess = 0;

            while (true) {
                Cursor cursor = null;
                int batchCount = 0;
                List<Long> ids = new ArrayList<>();
                WriteBatch batch = firestore.batch();
                try {
                    cursor = dbHelper.getUnsyncedStepHistory(MAX_BATCH_SIZE);
                    if (cursor == null || !cursor.moveToFirst()) {
                        break;
                    }

                    do {
                        long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                        String granularity = cursor.getString(cursor.getColumnIndexOrThrow("granularity"));
                        String periodLabel = cursor.getString(cursor.getColumnIndexOrThrow("period_label"));

                        Map<String, Object> data = new HashMap<>();
                        data.put("user_id", cursor.getString(cursor.getColumnIndexOrThrow("user_id")));
                        data.put("granularity", granularity);
                        data.put("period_label", periodLabel);
                        data.put("period_start_ms", cursor.getLong(cursor.getColumnIndexOrThrow("period_start_ms")));
                        data.put("period_end_ms", cursor.getLong(cursor.getColumnIndexOrThrow("period_end_ms")));
                        data.put("step_count", cursor.getLong(cursor.getColumnIndexOrThrow("step_count")));
                        data.put("data_origin", cursor.getString(cursor.getColumnIndexOrThrow("data_origin")));
                        data.put("captured_at", cursor.getLong(cursor.getColumnIndexOrThrow("captured_at")));

                        // Deterministic doc ID for idempotent upserts.
                        String docId = granularity + "_" + periodLabel;
                        batch.set(collection.document(docId), data);
                        ids.add(id);
                        batchCount++;
                    } while (cursor.moveToNext());
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }

                if (batchCount == 0) {
                    break;
                }

                try {
                    com.google.android.gms.tasks.Tasks.await(batch.commit());
                    for (Long id : ids) {
                        dbHelper.markStepHistorySynced(id);
                    }
                    grandSuccess += ids.size();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to commit step history batch: " + e.getMessage(), e);
                    break; // avoid an infinite loop on persistent failure
                }

                grandTotal += batchCount;
                if (batchCount < MAX_BATCH_SIZE) {
                    break; // last partial batch processed
                }
            }

            Log.i(TAG, String.format("Step history sync: %d uploaded of %d processed",
                    grandSuccess, grandTotal));

        } catch (Exception e) {
            Log.e(TAG, "Error uploading step history", e);
        }
    }
}
