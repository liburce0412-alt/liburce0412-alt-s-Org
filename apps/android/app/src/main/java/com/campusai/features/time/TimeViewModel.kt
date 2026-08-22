package com.campusai.features.time

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.campusai.core.database.CampusDao
import com.campusai.core.database.TimeRecordEntity
import com.campusai.core.model.TimeRecord
import com.campusai.core.model.CourseSchedule
import com.campusai.core.database.CourseScheduleEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import com.campusai.core.sync.CampusSyncScheduler

@OptIn(ExperimentalCoroutinesApi::class)
class TimeViewModel(private val dao: CampusDao, private val appContext: Context, initialUserId: String?) : ViewModel() {

    private val activeUser = MutableStateFlow(initialUserId?.takeIf { it.isNotBlank() } ?: "local_user")

    // Reactive complete record list mapped to domain layer
    val timeRecords: StateFlow<List<TimeRecord>> = activeUser.flatMapLatest { userId -> dao.getAllTimeRecordsFlow(userId, userId != "local_user") }
        .map { list -> list.map { it.toDomain() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val courses: StateFlow<List<CourseSchedule>> = activeUser.flatMapLatest { userId -> dao.getCourseSchedulesFlow(userId, userId != "local_user") }
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isInserting = MutableStateFlow(false)
    val isInserting: StateFlow<Boolean> = _isInserting.asStateFlow()

    fun setActiveUser(userId: String?) {
        activeUser.value = userId?.takeIf { it.isNotBlank() } ?: "local_user"
    }

    // Add record
    fun addTimeRecord(
        title: String,
        category: String,
        startTime: Long,
        endTime: Long,
        remark: String
    ) {
        viewModelScope.launch {
            _isInserting.value = true
            val durationMin = if (endTime > startTime) {
                (endTime - startTime) / (1000 * 60)
            } else {
                0L
            }
            val record = TimeRecord(
                title = title,
                category = category,
                startTime = startTime,
                endTime = endTime,
                durationMinutes = durationMin,
                remark = remark,
                userId = activeUser.value,
            )
            dao.insertTimeRecord(TimeRecordEntity.fromDomain(record))
            CampusSyncScheduler.enqueue(appContext)
            _isInserting.value = false
        }
    }

    // Delete record
    fun deleteTimeRecord(id: Int) {
        viewModelScope.launch {
            dao.softDeleteTimeRecord(id)
        }
    }

    fun confirmDeleteTimeRecord() = CampusSyncScheduler.enqueue(appContext)

    fun undoDeleteTimeRecord(id: Int) {
        viewModelScope.launch {
            dao.undoDeleteTimeRecord(id)
            CampusSyncScheduler.enqueue(appContext)
        }
    }

    fun editTimeRecord(id: Int, title: String, category: String, startTime: Long, endTime: Long, remark: String) {
        viewModelScope.launch {
            val duration = ((endTime - startTime) / 60_000L).coerceAtLeast(0)
            dao.editTimeRecord(id, title, category, startTime, endTime, duration, remark)
            CampusSyncScheduler.enqueue(appContext)
        }
    }

    fun importCourses(courses: List<CourseSchedule>, onComplete: (inserted: Int, duplicates: Int) -> Unit) {
        viewModelScope.launch {
            val owner = activeUser.value
            val results = dao.insertCourseSchedules(courses.map { CourseScheduleEntity.fromDomain(it, owner) })
            val inserted = results.count { it != -1L }
            if (inserted > 0) CampusSyncScheduler.enqueue(appContext)
            onComplete(inserted, results.size - inserted)
        }
    }

    // Helper functions for stats
    fun getStatsToday(records: List<TimeRecord>): Long {
        val todayStart = getStartOfToday()
        return records.filter { it.startTime >= todayStart }.sumOf { it.durationMinutes }
    }

    fun getStatsThisWeek(records: List<TimeRecord>): Long {
        val weekStart = getStartOfWeek()
        return records.filter { it.startTime >= weekStart }.sumOf { it.durationMinutes }
    }

    fun getStatsThisMonth(records: List<TimeRecord>): Long {
        val monthStart = getStartOfMonth()
        return records.filter { it.startTime >= monthStart }.sumOf { it.durationMinutes }
    }

    fun getStreakDays(records: List<TimeRecord>): Int {
        if (records.isEmpty()) return 0
        val uniqueDays = records.map {
            val cal = Calendar.getInstance()
            cal.timeInMillis = it.startTime
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }.distinct().sortedDescending()

        if (uniqueDays.isEmpty()) return 0

        var streak = 0
        var currentDayCal = Calendar.getInstance()
        currentDayCal.set(Calendar.HOUR_OF_DAY, 0)
        currentDayCal.set(Calendar.MINUTE, 0)
        currentDayCal.set(Calendar.SECOND, 0)
        currentDayCal.set(Calendar.MILLISECOND, 0)
        var checkTimestamp = currentDayCal.timeInMillis

        // If today or yesterday is present, start checking streak
        val hasToday = uniqueDays.contains(checkTimestamp)
        val hasYesterday = uniqueDays.contains(checkTimestamp - 86400000L)

        if (!hasToday && !hasYesterday) return 0

        if (hasToday) {
            streak++
            var prevDay = checkTimestamp - 86400000L
            while (uniqueDays.contains(prevDay)) {
                streak++
                prevDay -= 86400000L
            }
        } else {
            // Yesterday is the start
            streak++
            var prevDay = checkTimestamp - 2 * 86400000L
            while (uniqueDays.contains(prevDay)) {
                streak++
                prevDay -= 86400000L
            }
        }
        return streak
    }

    private fun getStartOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getStartOfWeek(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        return cal.timeInMillis
    }

    private fun getStartOfMonth(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }
}

class TimeViewModelFactory(private val dao: CampusDao, private val appContext: Context, private val initialUserId: String?) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimeViewModel::class.java)) {
            return TimeViewModel(dao, appContext.applicationContext, initialUserId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
