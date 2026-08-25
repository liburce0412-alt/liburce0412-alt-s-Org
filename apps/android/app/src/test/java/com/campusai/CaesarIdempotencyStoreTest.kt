package com.campusai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.campusai.core.agent.CaesarIdempotencyStore
import com.campusai.core.database.CampusDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaesarIdempotencyStoreTest {
    private lateinit var database: CampusDatabase
    private lateinit var store: CaesarIdempotencyStore

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CampusDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = CaesarIdempotencyStore(database.campusDao())
    }

    @After fun tearDown() = database.close()

    @Test fun `only one concurrent begin acquires an action`() = runTest {
        val results = coroutineScope {
            List(8) { async { store.begin("same", "time.create_record", "{\"a\":1}") } }.awaitAll()
        }
        assertEquals(1, results.count { it })
        assertFalse(store.begin("same", "time.create_record", "{\"a\":1}"))
    }

    @Test fun `completed actions replay result and failed actions may retry`() = runTest {
        assertTrue(store.begin("complete", "test.write", "{}"))
        store.complete("complete", "test.write", "{}", "{\"ok\":true}")
        assertEquals("{\"ok\":true}", store.completed("complete"))
        assertFalse(store.begin("complete", "test.write", "{}"))

        assertTrue(store.begin("retry", "test.write", "{}"))
        store.fail("retry", "test.write", "{}")
        assertTrue(store.begin("retry", "test.write", "{}"))
    }
}
