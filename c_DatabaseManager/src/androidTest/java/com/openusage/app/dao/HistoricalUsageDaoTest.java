package com.openusage.app.dao;

import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openusage.app.TextBasedEventData.EventDatabaseHelper;
import com.openusage.app.TextBasedEventData.HistoricalUsageDao;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HistoricalUsageDaoTest {

    private EventDatabaseHelper dbHelper;
    private HistoricalUsageDao dao;

    @Before
    public void setUp() {
        dbHelper = EventDatabaseHelper.getInstance(ApplicationProvider.getApplicationContext());
        dao = dbHelper.getHistoricalUsageDao();
    }

    @After
    public void tearDown() {
        dbHelper.deleteDatabase(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void insert_returnsPositiveId() {
        long id = dao.insert("test_user", "daily", "2026-04-07",
                1000L, 2000L, "com.example.app",
                60000L, 30000L, 1500L);
        assertTrue("Insert should return positive ID", id > 0);
    }

    @Test
    public void getCount_returnsCorrectCount() {
        dao.insert("test_user", "daily", "2026-04-07",
                1000L, 2000L, "com.example.app1",
                60000L, 30000L, 1500L);
        dao.insert("test_user", "daily", "2026-04-07",
                1000L, 2000L, "com.example.app2",
                45000L, 20000L, 1400L);

        assertEquals("Should have 2 records", 2, dao.getCount("test_user"));
    }

    @Test
    public void getCount_filtersByUser() {
        dao.insert("user_a", "daily", "2026-04-07",
                1000L, 2000L, "com.example.app",
                60000L, 30000L, 1500L);
        dao.insert("user_b", "daily", "2026-04-07",
                1000L, 2000L, "com.example.app",
                45000L, 20000L, 1400L);

        assertEquals("user_a should have 1 record", 1, dao.getCount("user_a"));
        assertEquals("user_b should have 1 record", 1, dao.getCount("user_b"));
    }

    @Test
    public void getUnsynced_returnsNewRecords() {
        dao.insert("test_user", "daily", "2026-04-07",
                1000L, 2000L, "com.example.app",
                60000L, 30000L, 1500L);

        Cursor cursor = dao.getUnsynced(10);
        assertTrue("Should have unsynced records", cursor.moveToFirst());
        assertEquals(1, cursor.getCount());
        cursor.close();
    }

    @Test
    public void markSynced_removesFromUnsynced() {
        long id = dao.insert("test_user", "daily", "2026-04-07",
                1000L, 2000L, "com.example.app",
                60000L, 30000L, 1500L);

        dao.markSynced(id);

        Cursor cursor = dao.getUnsynced(10);
        assertFalse("Should have no unsynced records after marking", cursor.moveToFirst());
        cursor.close();
    }

    @Test
    public void insert_replacesOnDuplicate() {
        dao.insert("test_user", "daily", "2026-04-07",
                1000L, 2000L, "com.example.app",
                60000L, 30000L, 1500L);
        dao.insert("test_user", "daily", "2026-04-07",
                1000L, 2000L, "com.example.app",
                90000L, 50000L, 1600L);

        assertEquals("Duplicate should be replaced, count stays 1", 1, dao.getCount("test_user"));
    }
}
