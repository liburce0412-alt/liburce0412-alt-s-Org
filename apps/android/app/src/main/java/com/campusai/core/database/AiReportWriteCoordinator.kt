package com.campusai.core.database

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes read/merge/write updates to AI reports within the app process. */
object AiReportWriteCoordinator {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
