package com.openusage.app.dao;

import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openusage.app.TextBasedEventData.EventDatabaseHelper;
import com.openusage.app.TextBasedEventData.SessionDao;
import com.openusage.app.TextBasedEventData.UserDao;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SessionDaoTest {

    private EventDatabaseHelper dbHelper;
    private SessionDao sessionDao;
    private UserDao userDao;

    @Before
    public void setUp() {
        dbHelper = EventDatabaseHelper.getInstance(ApplicationProvider.getApplicationContext());
        sessionDao = dbHelper.getSessionDao();
        userDao = dbHelper.getUserDao();

        // Insert a test user
        userDao.insert("test_user", "firebase_uid", "test@test.com",
                "TEST_CODE", "001", "install_001", "Test Device");
    }

    @After
    public void tearDown() {
        dbHelper.deleteDatabase(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void startUserSession_returnsNonNullId() {
        String sessionId = sessionDao.startUserSession("test_user", "Pixel 7", "1.0");
        assertNotNull("User session ID should not be null", sessionId);
        assertTrue("Session ID should start with user_session_", sessionId.startsWith("user_session_"));
    }

    @Test
    public void endUserSession_setsInactive() {
        String sessionId = sessionDao.startUserSession("test_user", "Pixel 7", "1.0");
        sessionDao.endUserSession(sessionId, System.currentTimeMillis());

        Cursor cursor = sessionDao.getActiveUserSession("test_user");
        assertFalse("Should have no active sessions after ending", cursor.moveToFirst());
        cursor.close();
    }

    @Test
    public void insertAppSession_insertsCorrectly() {
        String userSessionId = sessionDao.startUserSession("test_user", "Pixel 7", "1.0");
        long result = sessionDao.insertAppSession("app_session_1", userSessionId, "test_user",
                "com.example.app", "Example App", System.currentTimeMillis(),
                "portrait", 85, "wifi");

        assertTrue("Insert should return positive ID", result > 0);
    }

    @Test
    public void getActiveSession_returnsCorrectApp() {
        String userSessionId = sessionDao.startUserSession("test_user", "Pixel 7", "1.0");
        sessionDao.insertAppSession("app_session_1", userSessionId, "test_user",
                "com.example.app", "Example App", System.currentTimeMillis(),
                "portrait", 85, "wifi");

        Cursor cursor = sessionDao.getActiveSession("com.example.app");
        assertTrue("Should find active session for app", cursor.moveToFirst());
        assertEquals("com.example.app",
                cursor.getString(cursor.getColumnIndexOrThrow("app_package_name")));
        cursor.close();
    }

    @Test
    public void endAppSession_setsInactive() {
        String userSessionId = sessionDao.startUserSession("test_user", "Pixel 7", "1.0");
        sessionDao.insertAppSession("app_session_1", userSessionId, "test_user",
                "com.example.app", "Example App", System.currentTimeMillis(),
                "portrait", 85, "wifi");

        sessionDao.endAppSession("app_session_1", System.currentTimeMillis(), 5000);

        Cursor cursor = sessionDao.getActiveSession("com.example.app");
        assertFalse("Should have no active sessions after ending", cursor.moveToFirst());
        cursor.close();
    }

    @Test
    public void getAllSessions_returnsInsertedSessions() {
        String userSessionId = sessionDao.startUserSession("test_user", "Pixel 7", "1.0");
        sessionDao.insertAppSession("app_session_1", userSessionId, "test_user",
                "com.example.app1", "App 1", System.currentTimeMillis(),
                "portrait", 85, "wifi");
        sessionDao.insertAppSession("app_session_2", userSessionId, "test_user",
                "com.example.app2", "App 2", System.currentTimeMillis(),
                "landscape", 70, "mobile");

        Cursor cursor = sessionDao.getAllSessions();
        assertEquals("Should have 2 sessions", 2, cursor.getCount());
        cursor.close();
    }
}
