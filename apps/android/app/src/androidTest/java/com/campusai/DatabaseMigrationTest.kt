package com.campusai

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.campusai.core.database.CampusDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val databaseName = "campus-migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CampusDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationThreeToFourPreservesOfflineRowsAndAddsSyncIdentity() {
        helper.createDatabase(databaseName, 3).apply {
            execSQL("INSERT INTO time_records(title,category,startTime,endTime,durationMinutes,remark,userId) VALUES('复习','学习',1000,61000,1,'旧记录','local_user')")
            execSQL("INSERT INTO course_schedules(name,weekday,startMinute,endMinute,location,teacher,weeks,sourceHash) VALUES('数据结构',1,480,580,'教学楼','','每周','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')")
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 4, true, CampusDatabase.MIGRATION_3_4).use { database ->
            database.query("SELECT clientId,syncState,deletedAt FROM time_records").use { cursor ->
                cursor.moveToFirst()
                assertEquals(36, cursor.getString(0).length)
                assertEquals("pending", cursor.getString(1))
                assertEquals(true, cursor.isNull(2))
            }
            database.query("SELECT clientId,syncState FROM course_schedules").use { cursor ->
                cursor.moveToFirst()
                assertEquals(36, cursor.getString(0).length)
                assertEquals("pending", cursor.getString(1))
            }
        }
    }

    @Test
    fun migrationFourToFivePreservesHistoryAndAddsStableConversationMetadata() {
        helper.createDatabase(databaseName, 4).apply {
            execSQL("INSERT INTO ai_reports(id,mode,title,summary,messagesJson,createdAt) VALUES('session-1','FAST','旧会话','旧摘要','[]',1234)")
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 5, true, CampusDatabase.MIGRATION_4_5).use { database ->
            database.query("SELECT id,provider,model,createdAt,updatedAt FROM ai_reports WHERE id='session-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("session-1", cursor.getString(0))
                assertEquals("AUTO", cursor.getString(1))
                assertEquals("", cursor.getString(2))
                assertEquals(1234L, cursor.getLong(3))
                assertEquals(1234L, cursor.getLong(4))
            }
            database.query("SELECT COUNT(*) FROM daily_greetings").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrationFiveToSixAddsAgentGovernanceTablesWithoutLosingHistory() {
        helper.createDatabase(databaseName, 5).apply {
            execSQL("INSERT INTO ai_reports(id,provider,mode,model,title,summary,messagesJson,createdAt,updatedAt) VALUES('session-6','LOCAL','FAST','Qwen','会话','摘要','[]',100,200)")
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 6, true, CampusDatabase.MIGRATION_5_6).use { database ->
            database.query("SELECT COUNT(*) FROM ai_reports WHERE id='session-6'").use { cursor -> cursor.moveToFirst(); assertEquals(1, cursor.getInt(0)) }
            listOf("agent_memories", "agent_traces", "agent_actions", "health_summary_cache").forEach { table ->
                database.query("SELECT COUNT(*) FROM $table").use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
            }
        }
    }
}
