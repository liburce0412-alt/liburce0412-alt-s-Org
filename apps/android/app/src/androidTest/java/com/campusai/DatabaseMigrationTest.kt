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
}
