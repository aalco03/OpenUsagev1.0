package com.openusage.app.TextBasedEventData;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.database.SQLException;
import java.util.List;

public class EventDatabaseHelper extends SQLiteOpenHelper implements ISessionRepository {
    private static final String TAG = "EventDatabaseHelper";
    private static final String DATABASE_NAME = "UserEvents.db";
    private static final int DATABASE_VERSION = 6; // Added step_history (Health Connect)
    
    // Existing events table
    private static final String TABLE_NAME = "events";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_EVENT_NAME = "event_name";
    private static final String COLUMN_EVENT_DATA = "event_data";
    
    // New session-based tables
    private static final String TABLE_USERS = "users";
    private static final String TABLE_USER_SESSIONS = "user_sessions";
    private static final String TABLE_APP_SESSIONS = "app_sessions";
    private static final String TABLE_UI_TEXT_EXTRACTIONS = "ui_text_extractions";
    private static final String TABLE_SESSION_EVENTS = "session_events";
    private static final String TABLE_SCREENSHOTS = "screenshots";
    private static final String TABLE_USER_INTERACTIONS = "user_interactions";
    private static final String TABLE_HISTORICAL_USAGE = "historical_usage_snapshot";
    private static final String TABLE_STEP_HISTORY = "step_history";

    private static volatile EventDatabaseHelper instance;
    private SQLiteDatabase database;
    private Context context;

    // DAOs
    private final HistoricalUsageDao historicalUsageDao;
    private final StepHistoryDao stepHistoryDao;
    private final SyncDao syncDao;
    private final UserDao userDao;
    private final SessionDao sessionDao;

    private EventDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
        this.historicalUsageDao = new HistoricalUsageDao(this);
        this.stepHistoryDao = new StepHistoryDao(this);
        this.syncDao = new SyncDao(this);
        this.userDao = new UserDao(this);
        this.sessionDao = new SessionDao(this, userDao, context);
    }

    public HistoricalUsageDao getHistoricalUsageDao() { return historicalUsageDao; }
    public StepHistoryDao getStepHistoryDao() { return stepHistoryDao; }
    public SyncDao getSyncDao() { return syncDao; }
    public UserDao getUserDao() { return userDao; }
    public SessionDao getSessionDao() { return sessionDao; }


    // Sql New Function

    public synchronized void openDatabase() {
        if (database == null || !database.isOpen()) {
            database = this.getWritableDatabase();
        }
    }



    public static EventDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (EventDatabaseHelper.class) {
                if (instance == null) {
                    instance = new EventDatabaseHelper(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create existing events table
        String createEventsTable = "CREATE TABLE " + TABLE_NAME + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_EVENT_NAME + " TEXT UNIQUE, "
                + COLUMN_EVENT_DATA + " TEXT)";
        db.execSQL(createEventsTable);
        
        // Create new session-based tables
        createSessionTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
        
        if (oldVersion < 2) {
            // Add session-based tables for version 2
            createSessionTables(db);
        }
        
        if (oldVersion < 3) {
            // Upgrade to version 3: Add UsageStats-based enhancements
            upgradeToUsageStatsSchema(db);
        }
        
        if (oldVersion < 4) {
            // Upgrade to version 4: Add screenshot Firestore sync support
            upgradeToScreenshotSyncSchema(db);
        }
        
        if (oldVersion < 5) {
            // Upgrade to version 5: Add historical usage snapshot table
            createHistoricalUsageTable(db);
        }

        if (oldVersion < 6) {
            // Upgrade to version 6: Add Health Connect step history table
            createStepHistoryTable(db);
        }
    }
    
    /**
     * Upgrade existing tables to include UsageStats-based features
     */
    private void upgradeToUsageStatsSchema(SQLiteDatabase db) {
        Log.i(TAG, "Upgrading to UsageStats-based schema (version 3)...");
        
        try {
            // Add new columns to users table
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN study_id TEXT");
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN device_id TEXT");
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN registration_date INTEGER");
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN last_active_date INTEGER");
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN study_group TEXT");
            
            // Add new columns to user_sessions table
            db.execSQL("ALTER TABLE " + TABLE_USER_SESSIONS + " ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_USER_SESSIONS + " ADD COLUMN updated_at INTEGER DEFAULT 0");
            
            // Add new columns to app_sessions table
            db.execSQL("ALTER TABLE " + TABLE_APP_SESSIONS + " ADD COLUMN interaction_count INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_APP_SESSIONS + " ADD COLUMN app_category TEXT");
            db.execSQL("ALTER TABLE " + TABLE_APP_SESSIONS + " ADD COLUMN launch_count INTEGER DEFAULT 1");
            db.execSQL("ALTER TABLE " + TABLE_APP_SESSIONS + " ADD COLUMN total_time_foreground INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_APP_SESSIONS + " ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_APP_SESSIONS + " ADD COLUMN updated_at INTEGER DEFAULT 0");
            
            // Add new columns to ui_text_extractions table
            db.execSQL("ALTER TABLE " + TABLE_UI_TEXT_EXTRACTIONS + " ADD COLUMN user_session_id TEXT");
            db.execSQL("ALTER TABLE " + TABLE_UI_TEXT_EXTRACTIONS + " ADD COLUMN user_id TEXT");
            db.execSQL("ALTER TABLE " + TABLE_UI_TEXT_EXTRACTIONS + " ADD COLUMN app_package_name TEXT");
            db.execSQL("ALTER TABLE " + TABLE_UI_TEXT_EXTRACTIONS + " ADD COLUMN is_processed INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_UI_TEXT_EXTRACTIONS + " ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_UI_TEXT_EXTRACTIONS + " ADD COLUMN should_delete INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_UI_TEXT_EXTRACTIONS + " ADD COLUMN delete_after_days INTEGER NOT NULL DEFAULT 30");
            db.execSQL("ALTER TABLE " + TABLE_UI_TEXT_EXTRACTIONS + " ADD COLUMN created_at INTEGER DEFAULT 0");
            
            // Create new indexes for enhanced functionality
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_text_user_session ON " + TABLE_UI_TEXT_EXTRACTIONS + "(user_session_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_text_user_id ON " + TABLE_UI_TEXT_EXTRACTIONS + "(user_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_text_app_package ON " + TABLE_UI_TEXT_EXTRACTIONS + "(app_package_name)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_text_synced ON " + TABLE_UI_TEXT_EXTRACTIONS + "(is_synced)");
            
            // Update timestamp values for existing records
            updateTimestampDefaults(db);
            
            Log.i(TAG, "Successfully upgraded to UsageStats-based schema");
            
        } catch (SQLException e) {
            Log.e(TAG, "Error upgrading to UsageStats schema", e);
            throw e;
        }
    }
    
    /**
     * Update timestamp defaults for existing records after schema upgrade
     */
    private void updateTimestampDefaults(SQLiteDatabase db) {
        try {
            long currentTime = System.currentTimeMillis();
            
            // Update user_sessions table
            db.execSQL("UPDATE " + TABLE_USER_SESSIONS + " SET updated_at = ? WHERE updated_at = 0", 
                      new String[]{String.valueOf(currentTime)});
            
            // Update app_sessions table  
            db.execSQL("UPDATE " + TABLE_APP_SESSIONS + " SET updated_at = ? WHERE updated_at = 0",
                      new String[]{String.valueOf(currentTime)});
            
            // Update ui_text_extractions table
            db.execSQL("UPDATE " + TABLE_UI_TEXT_EXTRACTIONS + " SET created_at = ? WHERE created_at = 0",
                      new String[]{String.valueOf(currentTime)});
            
            Log.i(TAG, "Updated timestamp defaults for existing records");
            
        } catch (SQLException e) {
            Log.w(TAG, "Error updating timestamp defaults (non-critical): " + e.getMessage());
        }
    }
    
    /**
     * Upgrade to version 4: Add screenshot Firestore sync support
     */
    private void upgradeToScreenshotSyncSchema(SQLiteDatabase db) {
        Log.i(TAG, "Upgrading to screenshot sync schema (version 4)...");
        
        try {
            // Add is_synced column to screenshots table for Firestore sync tracking
            db.execSQL("ALTER TABLE " + TABLE_SCREENSHOTS + " ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_SCREENSHOTS + " ADD COLUMN created_at INTEGER DEFAULT 0");
            
            // Create index for efficient querying of unsynced screenshots
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_screenshots_synced ON " + TABLE_SCREENSHOTS + "(is_synced)");
            
            // Remove screenshot_filename column from ui_text_extractions (no longer needed)
            // SQLite doesn't support DROP COLUMN directly, so we'll leave it as deprecated
            // New code will not use it, and it will be NULL for all records
            Log.i(TAG, "Note: screenshot_filename column in ui_text_extractions is now deprecated and unused");
            
            // Update created_at for existing screenshots
            long currentTime = System.currentTimeMillis();
            db.execSQL("UPDATE " + TABLE_SCREENSHOTS + " SET created_at = ? WHERE created_at = 0",
                      new String[]{String.valueOf(currentTime)});
            
            Log.i(TAG, "Successfully upgraded to screenshot sync schema");
            
        } catch (SQLException e) {
            Log.e(TAG, "Error upgrading to screenshot sync schema", e);
            throw e;
        }
    }

    public synchronized void closeDatabase() {
        if (database != null && database.isOpen()) {
            database.close();
            database = null;
        }
    }


    // Sql New Function

    public Cursor getLimitedEvents(int limit) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM "+TABLE_NAME+" ORDER BY id ASC LIMIT ?", new String[]{String.valueOf(limit)});
    }

    // Sql New Function

    public int getTotalEventCount() {
        int count = 0;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NAME, null);
            if (cursor != null && cursor.moveToFirst()) {
                count = cursor.getInt(0); // Get the count from the first column
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching total event count: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close(); // Close database after operation
        }

        return count;
    }



    public void insertEvent(String eventName, String eventData) {
        try {
            openDatabase();
            ContentValues contentValues = new ContentValues();
            contentValues.put(COLUMN_EVENT_NAME, eventName);
            contentValues.put(COLUMN_EVENT_DATA, eventData);
            database.insertWithOnConflict(TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLException e) {
            Log.e(TAG, "Error inserting event: ", e);
        }
    }


    public void insertEventsBatch(List<EventEntity> events) {
        try {
            openDatabase();
            database.beginTransaction();
            for (EventEntity event : events) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(COLUMN_EVENT_NAME, event.getEventName());
                contentValues.put(COLUMN_EVENT_DATA, event.getEventData());
                database.insertWithOnConflict(TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
            }
            database.setTransactionSuccessful();
        } catch (SQLException e) {
            Log.e(TAG, "Error inserting batch events: ", e);
        } finally {
            database.endTransaction();
        }
    }

    public void deleteEvent(int id) {

        Log.e(TAG, "Delete event called");


        try {
            openDatabase();

            int i = database.delete(TABLE_NAME, COLUMN_ID + "=?", new String[]{String.valueOf(id)});

            Log.e(TAG, "trying deleting event: "  + String.valueOf(i));

        } catch (SQLException e) {
            Log.e(TAG, "Error deleting event: ", e);
        }
    }

    public Cursor getAllEvents() {
        openDatabase();
        return database.rawQuery("SELECT * FROM " + TABLE_NAME + " ORDER BY id ASC LIMIT 10", null);

    }

    public void deleteDatabase(Context context) {
        closeDatabase();
        context.deleteDatabase(DATABASE_NAME);
        Log.d(TAG, "Database deleted.");
    }
    
    // ========== SESSION-BASED SCHEMA METHODS ==========
    
    /**
     * Create all session-based tables with hierarchical structure:
     * Users -> User Sessions -> App Sessions -> UI Text Extractions
     */
    private void createSessionTables(SQLiteDatabase db) {
        Log.i(TAG, "Creating session-based tables with hierarchical structure...");
        
        // 1. Users Table (Top Level) - Enhanced with UsageStats features
        String createUsersTable = "CREATE TABLE " + TABLE_USERS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "user_id TEXT UNIQUE NOT NULL, "
                + "firebase_uid TEXT UNIQUE, "
                + "email TEXT NOT NULL, "
                + "study_code TEXT NOT NULL, "
                + "study_id TEXT, " // For dashboard integration (tenantId)
                + "participant_number TEXT NOT NULL, "
                + "install_code TEXT UNIQUE, "
                + "device_id TEXT NOT NULL, " // Device identifier for dashboard
                + "registration_date INTEGER NOT NULL, " // UsageStats compatibility
                + "created_at INTEGER NOT NULL, "
                + "last_login INTEGER, "
                + "last_active_date INTEGER, " // UsageStats compatibility
                + "is_active INTEGER DEFAULT 1, "
                + "consent_given INTEGER DEFAULT 0, "
                + "study_start_date INTEGER, "
                + "study_end_date INTEGER, "
                + "device_info TEXT, "
                + "study_group TEXT " // For study management
                + ")";
        db.execSQL(createUsersTable);
        
        // Create indexes for users
        db.execSQL("CREATE INDEX idx_users_user_id ON " + TABLE_USERS + "(user_id)");
        db.execSQL("CREATE INDEX idx_users_study_code ON " + TABLE_USERS + "(study_code)");
        db.execSQL("CREATE INDEX idx_users_active ON " + TABLE_USERS + "(is_active)");
        
        // 2. User Sessions Table (Second Level - User Login Sessions) - Enhanced with sync features
        String createUserSessionsTable = "CREATE TABLE " + TABLE_USER_SESSIONS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "user_session_id TEXT UNIQUE NOT NULL, "
                + "user_id TEXT NOT NULL, "
                + "login_time INTEGER NOT NULL, "
                + "logout_time INTEGER, "
                + "session_duration INTEGER DEFAULT 0, "
                + "is_active INTEGER DEFAULT 1, "
                + "device_info TEXT, "
                + "app_version TEXT, "
                + "is_synced INTEGER NOT NULL DEFAULT 0, " // Dashboard sync tracking
                + "created_at INTEGER DEFAULT (strftime('%s','now') * 1000), "
                + "updated_at INTEGER DEFAULT (strftime('%s','now') * 1000), "
                + "FOREIGN KEY (user_id) REFERENCES " + TABLE_USERS + "(user_id) ON DELETE CASCADE"
                + ")";
        db.execSQL(createUserSessionsTable);
        
        // Create indexes for user_sessions
        db.execSQL("CREATE INDEX idx_user_sessions_user_id ON " + TABLE_USER_SESSIONS + "(user_id)");
        db.execSQL("CREATE INDEX idx_user_sessions_active ON " + TABLE_USER_SESSIONS + "(is_active)");
        db.execSQL("CREATE INDEX idx_user_sessions_login_time ON " + TABLE_USER_SESSIONS + "(login_time)");
        
        // 3. App Sessions Table (Third Level - Individual App Usage Sessions) - Enhanced with UsageStats features
        String createAppSessionsTable = "CREATE TABLE " + TABLE_APP_SESSIONS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "session_id TEXT UNIQUE NOT NULL, "
                + "user_session_id TEXT NOT NULL, "
                + "user_id TEXT NOT NULL, "
                + "app_package_name TEXT NOT NULL, "
                + "app_name TEXT, "
                + "start_time INTEGER NOT NULL, "
                + "end_time INTEGER, "
                + "duration INTEGER DEFAULT 0, "
                + "interaction_count INTEGER DEFAULT 0, " // UsageStats compatibility
                + "is_active INTEGER DEFAULT 1, "
                + "device_orientation TEXT DEFAULT 'portrait', "
                + "battery_level INTEGER, "
                + "network_type TEXT, "
                + "app_category TEXT, " // App category for dashboard
                + "launch_count INTEGER DEFAULT 1, " // UsageStats compatibility
                + "total_time_foreground INTEGER DEFAULT 0, " // UsageStats compatibility
                + "is_synced INTEGER NOT NULL DEFAULT 0, " // Dashboard sync tracking
                + "created_at INTEGER DEFAULT (strftime('%s','now') * 1000), "
                + "updated_at INTEGER DEFAULT (strftime('%s','now') * 1000), "
                + "FOREIGN KEY (user_session_id) REFERENCES " + TABLE_USER_SESSIONS + "(user_session_id) ON DELETE CASCADE, "
                + "FOREIGN KEY (user_id) REFERENCES " + TABLE_USERS + "(user_id) ON DELETE CASCADE"
                + ")";
        db.execSQL(createAppSessionsTable);
        
        // Create indexes for app_sessions
        db.execSQL("CREATE INDEX idx_sessions_package ON " + TABLE_APP_SESSIONS + "(app_package_name)");
        db.execSQL("CREATE INDEX idx_sessions_start_time ON " + TABLE_APP_SESSIONS + "(start_time)");
        db.execSQL("CREATE INDEX idx_sessions_active ON " + TABLE_APP_SESSIONS + "(is_active)");
        db.execSQL("CREATE INDEX idx_sessions_user_session ON " + TABLE_APP_SESSIONS + "(user_session_id)");
        db.execSQL("CREATE INDEX idx_sessions_user_id ON " + TABLE_APP_SESSIONS + "(user_id)");
        
        // 4. UI Text Extractions Table (Fourth Level - Text extracted during app sessions) - Enhanced with UsageStats features
        String createUITextTable = "CREATE TABLE " + TABLE_UI_TEXT_EXTRACTIONS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "session_id TEXT NOT NULL, "
                + "user_session_id TEXT NOT NULL, " // Direct link to user session
                + "user_id TEXT NOT NULL, " // Direct link to user
                + "screenshot_filename TEXT, "
                + "extracted_text TEXT NOT NULL, "
                + "extraction_timestamp INTEGER NOT NULL, "
                + "has_valid_content INTEGER DEFAULT 1, "
                + "text_length INTEGER, "
                + "window_title TEXT, "
                + "app_package_name TEXT, " // UsageStats compatibility
                + "is_processed INTEGER NOT NULL DEFAULT 0, " // Processing status
                + "is_synced INTEGER NOT NULL DEFAULT 0, " // Dashboard sync tracking
                + "should_delete INTEGER NOT NULL DEFAULT 0, " // Cleanup management
                + "delete_after_days INTEGER NOT NULL DEFAULT 30, " // Data retention
                + "created_at INTEGER DEFAULT (strftime('%s','now') * 1000), "
                + "FOREIGN KEY (session_id) REFERENCES " + TABLE_APP_SESSIONS + "(session_id) ON DELETE CASCADE, "
                + "FOREIGN KEY (user_session_id) REFERENCES " + TABLE_USER_SESSIONS + "(user_session_id) ON DELETE CASCADE, "
                + "FOREIGN KEY (user_id) REFERENCES " + TABLE_USERS + "(user_id) ON DELETE CASCADE"
                + ")";
        db.execSQL(createUITextTable);
        
        // Create indexes for ui_text_extractions
        db.execSQL("CREATE INDEX idx_text_session ON " + TABLE_UI_TEXT_EXTRACTIONS + "(session_id)");
        db.execSQL("CREATE INDEX idx_text_timestamp ON " + TABLE_UI_TEXT_EXTRACTIONS + "(extraction_timestamp)");
        db.execSQL("CREATE INDEX idx_text_valid ON " + TABLE_UI_TEXT_EXTRACTIONS + "(has_valid_content)");
        db.execSQL("CREATE INDEX idx_text_user_session ON " + TABLE_UI_TEXT_EXTRACTIONS + "(user_session_id)");
        db.execSQL("CREATE INDEX idx_text_user_id ON " + TABLE_UI_TEXT_EXTRACTIONS + "(user_id)");
        db.execSQL("CREATE INDEX idx_text_app_package ON " + TABLE_UI_TEXT_EXTRACTIONS + "(app_package_name)");
        db.execSQL("CREATE INDEX idx_text_synced ON " + TABLE_UI_TEXT_EXTRACTIONS + "(is_synced)");
        
        // 3. Session Events Table (enhanced events linked to sessions)
        String createSessionEventsTable = "CREATE TABLE " + TABLE_SESSION_EVENTS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "session_id TEXT, "
                + "event_type TEXT NOT NULL, "
                + "event_data TEXT NOT NULL, "
                + "timestamp INTEGER NOT NULL, "
                + "event_name TEXT UNIQUE, "
                + "FOREIGN KEY (session_id) REFERENCES " + TABLE_APP_SESSIONS + "(session_id) ON DELETE SET NULL"
                + ")";
        db.execSQL(createSessionEventsTable);
        
        // Create indexes for session_events
        db.execSQL("CREATE INDEX idx_events_session ON " + TABLE_SESSION_EVENTS + "(session_id)");
        db.execSQL("CREATE INDEX idx_events_type ON " + TABLE_SESSION_EVENTS + "(event_type)");
        db.execSQL("CREATE INDEX idx_events_timestamp ON " + TABLE_SESSION_EVENTS + "(timestamp)");
        
        // 4. Screenshots Table
        String createScreenshotsTable = "CREATE TABLE " + TABLE_SCREENSHOTS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "session_id TEXT, "
                + "filename TEXT NOT NULL, "
                + "file_path TEXT NOT NULL, "
                + "capture_timestamp INTEGER NOT NULL, "
                + "file_size INTEGER, "
                + "is_uploaded INTEGER DEFAULT 0, "
                + "is_synced INTEGER NOT NULL DEFAULT 0, "  // NEW: Firestore sync tracking
                + "created_at INTEGER DEFAULT (strftime('%s','now') * 1000), "  // NEW: Creation timestamp
                + "FOREIGN KEY (session_id) REFERENCES " + TABLE_APP_SESSIONS + "(session_id) ON DELETE SET NULL"
                + ")";
        db.execSQL(createScreenshotsTable);
        
        // Create indexes for screenshots
        db.execSQL("CREATE INDEX idx_screenshots_session ON " + TABLE_SCREENSHOTS + "(session_id)");
        db.execSQL("CREATE INDEX idx_screenshots_timestamp ON " + TABLE_SCREENSHOTS + "(capture_timestamp)");
        db.execSQL("CREATE INDEX idx_screenshots_uploaded ON " + TABLE_SCREENSHOTS + "(is_uploaded)");
        
        // 5. User Interactions Table
        String createInteractionsTable = "CREATE TABLE " + TABLE_USER_INTERACTIONS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "session_id TEXT NOT NULL, "
                + "interaction_type TEXT NOT NULL, "
                + "target_element TEXT, "
                + "x_coordinate INTEGER, "
                + "y_coordinate INTEGER, "
                + "timestamp INTEGER NOT NULL, "
                + "FOREIGN KEY (session_id) REFERENCES " + TABLE_APP_SESSIONS + "(session_id) ON DELETE CASCADE"
                + ")";
        db.execSQL(createInteractionsTable);
        
        // Create indexes for user_interactions
        db.execSQL("CREATE INDEX idx_interactions_session ON " + TABLE_USER_INTERACTIONS + "(session_id)");
        db.execSQL("CREATE INDEX idx_interactions_type ON " + TABLE_USER_INTERACTIONS + "(interaction_type)");
        db.execSQL("CREATE INDEX idx_interactions_timestamp ON " + TABLE_USER_INTERACTIONS + "(timestamp)");
        
        // 7. Historical Usage Snapshot Table
        createHistoricalUsageTable(db);

        // 8. Health Connect Step History Table
        createStepHistoryTable(db);
        
        Log.i(TAG, "Session-based tables created successfully");
    }

    /**
     * Create the step history table for Health Connect step data (PASSIVE group).
     * Stores daily and hourly step-count buckets. UNIQUE constraint makes re-syncs idempotent.
     */
    private void createStepHistoryTable(SQLiteDatabase db) {
        Log.i(TAG, "Creating step history table...");
        try {
            String createTable = "CREATE TABLE IF NOT EXISTS " + TABLE_STEP_HISTORY + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "user_id TEXT NOT NULL, "
                    + "granularity TEXT NOT NULL, "          // 'daily' | 'hourly'
                    + "period_label TEXT NOT NULL, "          // '2026-06-15' | '2026-06-15T13'
                    + "period_start_ms INTEGER NOT NULL, "
                    + "period_end_ms INTEGER NOT NULL, "
                    + "step_count INTEGER DEFAULT 0, "
                    + "data_origin TEXT, "
                    + "captured_at INTEGER NOT NULL, "
                    + "is_synced INTEGER DEFAULT 0, "
                    + "UNIQUE(user_id, granularity, period_label)"
                    + ")";
            db.execSQL(createTable);

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_stephist_user ON " + TABLE_STEP_HISTORY + "(user_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_stephist_gran ON " + TABLE_STEP_HISTORY + "(granularity)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_stephist_synced ON " + TABLE_STEP_HISTORY + "(is_synced)");

            Log.i(TAG, "Step history table created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error creating step history table: " + e.getMessage());
        }
    }
    
    /**
     * Create the historical usage snapshot table for pre-study baseline data.
     * Stores per-app usage stats captured from UsageStatsManager on first login.
     */
    private void createHistoricalUsageTable(SQLiteDatabase db) {
        Log.i(TAG, "Creating historical usage snapshot table...");
        try {
            String createTable = "CREATE TABLE IF NOT EXISTS " + TABLE_HISTORICAL_USAGE + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "user_id TEXT NOT NULL, "
                    + "interval_type TEXT NOT NULL, "          // 'daily' | 'monthly'
                    + "period_label TEXT NOT NULL, "            // '2026-03-23' | '2026-03'
                    + "period_start_ms INTEGER NOT NULL, "
                    + "period_end_ms INTEGER NOT NULL, "
                    + "app_package TEXT NOT NULL, "
                    + "foreground_time_ms INTEGER DEFAULT 0, "
                    + "visible_time_ms INTEGER DEFAULT 0, "
                    + "last_used_ms INTEGER DEFAULT 0, "
                    + "captured_at INTEGER NOT NULL, "
                    + "is_synced INTEGER DEFAULT 0, "
                    + "UNIQUE(user_id, interval_type, period_label, app_package)"
                    + ")";
            db.execSQL(createTable);
            
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_hist_user ON " + TABLE_HISTORICAL_USAGE + "(user_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_hist_interval ON " + TABLE_HISTORICAL_USAGE + "(interval_type)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_hist_synced ON " + TABLE_HISTORICAL_USAGE + "(is_synced)");
            
            Log.i(TAG, "Historical usage snapshot table created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error creating historical usage table: " + e.getMessage());
        }
    }
    
    // User and Session methods delegated to UserDao and SessionDao. Kept for backward compatibility.

    public long insertUser(String userId, String firebaseUid, String email, String studyCode,
                          String participantNumber, String installCode, String deviceInfo) {
        return userDao.insert(userId, firebaseUid, email, studyCode, participantNumber, installCode, deviceInfo);
    }
    public void updateUserLastLogin(String userId, long loginTime) { userDao.updateLastLogin(userId, loginTime); }
    public Cursor getUser(String userId) { return userDao.getById(userId); }

    public String startUserSession(String userId, String deviceInfo, String appVersion) {
        return sessionDao.startUserSession(userId, deviceInfo, appVersion);
    }
    public void endUserSession(String userSessionId, long logoutTime) { sessionDao.endUserSession(userSessionId, logoutTime); }
    public Cursor getActiveUserSession(String userId) { return sessionDao.getActiveUserSession(userId); }

    public long insertAppSession(String sessionId, String userSessionId, String userId,
                                String appPackageName, String appName, long startTime,
                                String deviceOrientation, Integer batteryLevel, String networkType) {
        return sessionDao.insertAppSession(sessionId, userSessionId, userId, appPackageName, appName,
                startTime, deviceOrientation, batteryLevel, networkType);
    }
    public long insertAppSession(String sessionId, String appPackageName, String appName,
                                long startTime, String deviceOrientation, Integer batteryLevel, String networkType) {
        return sessionDao.insertAppSession(sessionId, appPackageName, appName, startTime,
                deviceOrientation, batteryLevel, networkType);
    }
    public void endAppSession(String sessionId, long endTime, long duration) { sessionDao.endAppSession(sessionId, endTime, duration); }
    
    /**
     * Insert UI text extraction linked to session
     */
    public long insertUITextExtraction(String sessionId, String screenshotFilename, 
                                      String extractedText, long timestamp, boolean hasValidContent, String windowTitle) {
        try {
            openDatabase();
            ContentValues values = new ContentValues();
            values.put("session_id", sessionId);
            values.put("screenshot_filename", screenshotFilename);
            values.put("extracted_text", extractedText);
            values.put("extraction_timestamp", timestamp);
            values.put("has_valid_content", hasValidContent ? 1 : 0);
            values.put("text_length", extractedText.length());
            values.put("window_title", windowTitle);
            
            // Get app package name, user_session_id, and user_id from the app session
            Cursor cursor = database.query(TABLE_APP_SESSIONS, 
                new String[]{"app_package_name", "user_session_id", "user_id"}, 
                "session_id = ?", 
                new String[]{sessionId}, 
                null, null, null);
            
            if (cursor.moveToFirst()) {
                String appPackageName = cursor.getString(0);
                String userSessionId = cursor.getString(1);
                String userId = cursor.getString(2);
                
                values.put("app_package_name", appPackageName);
                values.put("user_session_id", userSessionId);
                values.put("user_id", userId);
                
                cursor.close();
            } else {
                cursor.close();
                Log.e(TAG, "Cannot insert text extraction: app session not found: " + sessionId);
                return -1;
            }
            
            long id = database.insert(TABLE_UI_TEXT_EXTRACTIONS, null, values);
            Log.d(TAG, "Inserted UI text extraction for session: " + sessionId);
            return id;
        } catch (SQLException e) {
            Log.e(TAG, "Error inserting UI text extraction: ", e);
            return -1;
        }
    }
    


    /**
     * Insert session event (enhanced event linked to session)
     */
    public long insertSessionEvent(String sessionId, String eventType, String eventData, 
                                  long timestamp, String eventName) {
        try {
            openDatabase();
            ContentValues values = new ContentValues();
            values.put("session_id", sessionId);
            values.put("event_type", eventType);
            values.put("event_data", eventData);
            values.put("timestamp", timestamp);
            values.put("event_name", eventName);
            
            long id = database.insert(TABLE_SESSION_EVENTS, null, values);
            Log.d(TAG, "Inserted session event: " + eventType + " for session: " + sessionId);
            return id;
        } catch (SQLException e) {
            Log.e(TAG, "Error inserting session event: ", e);
            return -1;
        }
    }
    
    /**
     * Insert screenshot record linked to session
     */
    public long insertScreenshot(String sessionId, String filename, String filePath, 
                                long timestamp, long fileSize) {
        try {
            openDatabase();
            ContentValues values = new ContentValues();
            values.put("session_id", sessionId);
            values.put("filename", filename);
            values.put("file_path", filePath);
            values.put("capture_timestamp", timestamp);
            values.put("file_size", fileSize);
            values.put("is_uploaded", 0);
            
            long id = database.insert(TABLE_SCREENSHOTS, null, values);
            Log.d(TAG, "Inserted screenshot record for session: " + sessionId);
            return id;
        } catch (SQLException e) {
            Log.e(TAG, "Error inserting screenshot: ", e);
            return -1;
        }
    }
    
    public Cursor getActiveSession(String appPackageName) { return sessionDao.getActiveSession(appPackageName); }
    public Cursor getAllSessions() { return sessionDao.getAllSessions(); }
    
    /**
     * Get UI text extractions for a session
     */
    public Cursor getUITextForSession(String sessionId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_UI_TEXT_EXTRACTIONS + 
                " WHERE session_id = ? ORDER BY extraction_timestamp ASC", 
                new String[]{sessionId});
    }

    // Sync methods delegated to SyncDao. Kept for backward compatibility.

    public Cursor getUnsyncedUserSessions(int limit) { return syncDao.getUnsyncedUserSessions(limit); }
    public Cursor getUnsyncedAppSessions(int limit) { return syncDao.getUnsyncedAppSessions(limit); }
    public Cursor getUnsyncedTextExtractions(int limit) { return syncDao.getUnsyncedTextExtractions(limit); }
    public Cursor getUnsyncedScreenshots(int limit) { return syncDao.getUnsyncedScreenshots(limit); }
    public void markUserSessionSynced(String userSessionId) { syncDao.markUserSessionSynced(userSessionId); }
    public void markAppSessionSynced(String sessionId) { syncDao.markAppSessionSynced(sessionId); }
    public void markTextExtractionSynced(long id) { syncDao.markTextExtractionSynced(id); }
    public void markScreenshotSynced(long id) { syncDao.markScreenshotSynced(id); }

    // Session query methods delegated to SessionDao.
    public Cursor getAppSessionsForUserSession(String userSessionId) { return sessionDao.getAppSessionsForUserSession(userSessionId); }
    public Cursor getAppSessionsForUser(String userId) { return sessionDao.getAppSessionsForUser(userId); }
    public Cursor getUserSessions(String userId) { return sessionDao.getUserSessions(userId); }
    
    /**
     * Get hierarchical session data: User -> User Session -> App Sessions -> Text Extractions
     */
    public Cursor getHierarchicalSessionData(String userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " +
                "u.user_id, u.email, u.study_code, " +
                "us.user_session_id, us.login_time, us.logout_time, us.session_duration as user_session_duration, " +
                "aps.session_id, aps.app_package_name, aps.app_name, aps.start_time, aps.end_time, aps.duration as app_session_duration, " +
                "COUNT(uit.id) as text_extractions_count " +
                "FROM " + TABLE_USERS + " u " +
                "LEFT JOIN " + TABLE_USER_SESSIONS + " us ON u.user_id = us.user_id " +
                "LEFT JOIN " + TABLE_APP_SESSIONS + " aps ON us.user_session_id = aps.user_session_id " +
                "LEFT JOIN " + TABLE_UI_TEXT_EXTRACTIONS + " uit ON aps.session_id = uit.session_id " +
                "WHERE u.user_id = ? " +
                "GROUP BY u.user_id, us.user_session_id, aps.session_id " +
                "ORDER BY us.login_time DESC, aps.start_time DESC";
        
        return db.rawQuery(query, new String[]{userId});
    }
    
    /**
     * Get session analytics for a user
     */
    public Cursor getUserSessionAnalytics(String userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " +
                "COUNT(DISTINCT us.user_session_id) as total_user_sessions, " +
                "COUNT(DISTINCT aps.session_id) as total_app_sessions, " +
                "COUNT(DISTINCT aps.app_package_name) as unique_apps_used, " +
                "SUM(aps.duration) as total_app_usage_time, " +
                "AVG(aps.duration) as avg_app_session_duration, " +
                "COUNT(uit.id) as total_text_extractions, " +
                "SUM(CASE WHEN uit.has_valid_content = 1 THEN 1 ELSE 0 END) as valid_text_extractions " +
                "FROM " + TABLE_USERS + " u " +
                "LEFT JOIN " + TABLE_USER_SESSIONS + " us ON u.user_id = us.user_id " +
                "LEFT JOIN " + TABLE_APP_SESSIONS + " aps ON us.user_session_id = aps.user_session_id " +
                "LEFT JOIN " + TABLE_UI_TEXT_EXTRACTIONS + " uit ON aps.session_id = uit.session_id " +
                "WHERE u.user_id = ?";
        
        return db.rawQuery(query, new String[]{userId});
    }
    
    // ========== HISTORICAL USAGE SNAPSHOT METHODS ==========
    // Delegated to HistoricalUsageDao. These methods are kept for backward compatibility.

    public long insertHistoricalUsage(String userId, String intervalType, String periodLabel,
                                       long periodStartMs, long periodEndMs, String appPackage,
                                       long foregroundTimeMs, long visibleTimeMs, long lastUsedMs) {
        return historicalUsageDao.insert(userId, intervalType, periodLabel, periodStartMs, periodEndMs,
                appPackage, foregroundTimeMs, visibleTimeMs, lastUsedMs);
    }

    public Cursor getUnsyncedHistoricalUsage(int limit) {
        return historicalUsageDao.getUnsynced(limit);
    }

    public void markHistoricalUsageSynced(long id) {
        historicalUsageDao.markSynced(id);
    }

    // ========== STEP HISTORY METHODS (Health Connect) ==========
    // Delegated to StepHistoryDao.

    public long insertStepHistory(String userId, String granularity, String periodLabel,
                                  long periodStartMs, long periodEndMs, long stepCount, String dataOrigin) {
        return stepHistoryDao.insert(userId, granularity, periodLabel, periodStartMs, periodEndMs,
                stepCount, dataOrigin);
    }

    public Cursor getUnsyncedStepHistory(int limit) {
        return stepHistoryDao.getUnsynced(limit);
    }

    public void markStepHistorySynced(long id) {
        stepHistoryDao.markSynced(id);
    }

    public int getHistoricalUsageCount(String userId) {
        return historicalUsageDao.getCount(userId);
    }
}


// Sql New Function class


class EventEntity {
    private String eventName;
    private String eventData;

    public EventEntity(String eventName, String eventData) {
        this.eventName = eventName;
        this.eventData = eventData;
    }

    public String getEventName() {
        return eventName;
    }

    public String getEventData() {
        return eventData;
    }
}
