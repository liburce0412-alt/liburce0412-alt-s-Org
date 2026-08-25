package com.campusai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.campusai.core.agent.CaesarMemoryStore
import com.campusai.core.database.CampusDatabase
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
class CaesarMemoryStoreTest {
    private lateinit var database: CampusDatabase
    private lateinit var store: CaesarMemoryStore

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CampusDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = CaesarMemoryStore(database.campusDao())
    }

    @After fun tearDown() = database.close()

    @Test fun `memory is not context until confirmed and remains editable`() = runTest {
        val proposal = store.propose("preference", "晚上八点提醒我运动", "user turn", .9)
        assertTrue(store.context().isEmpty())
        assertTrue(store.updateContent(proposal.id, "晚上九点提醒我运动"))
        assertTrue(store.confirm(proposal.id))
        assertEquals("晚上九点提醒我运动", store.context().single().content)

        store.forget(proposal.id)
        assertTrue(store.context().isEmpty())
        assertFalse(store.updateContent(proposal.id, "不应该成功"))
    }

    @Test fun `expired memory never enters context`() = runTest {
        val proposal = store.propose("goal", "本周完成五次运动", "user turn", .8, expiresAt = 100)
        assertTrue(store.confirm(proposal.id))
        assertTrue(store.context(now = 101).isEmpty())
    }
}
