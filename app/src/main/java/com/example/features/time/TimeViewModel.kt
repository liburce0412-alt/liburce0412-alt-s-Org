package com.example.features.time

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.database.CampusDao
import com.example.core.database.TimeRecordEntity
import com.example.core.model.TimeRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class TimeViewModel(private val dao: CampusDao) : ViewModel() {

    // Reactive complete record list mapped to domain layer
    val timeRecords: StateFlow<List<TimeRecord>> = dao.getAllTimeRecordsFlow()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isInserting = MutableStateFlow(false)
    val isInserting: StateFlow<Boolean> = _isInserting.asStateFlow()

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
                remark = remark
            )
            dao.insertTimeRecord(TimeRecordEntity.fromDomain(record))
            _isInserting.value = false
        }
    }

    // Delete record
    fun deleteTimeRecord(id: Int) {
        viewModelScope.launch {
            dao.deleteTimeRecordById(id)
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

class TimeViewModelFactory(private val dao: CampusDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimeViewModel::class.java)) {
            return TimeViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
