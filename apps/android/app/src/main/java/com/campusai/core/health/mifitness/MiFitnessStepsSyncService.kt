package com.campusai.core.health.mifitness

import android.content.Context
import com.campusai.core.health.HealthPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

interface MiFitnessStepsTransport {
    suspend fun exchangePassToken(credential: MiFitnessCredential): MiFitnessSession

    suspend fun fetchSteps(
        session: MiFitnessSession,
        startEpochSeconds: Long,
        endEpochSeconds: Long,
        nextKey: String,
    ): String
}

class MiFitnessReadOnlyTransportAdapter(
    private val client: MiFitnessReadOnlyClient = MiFitnessReadOnlyClient(),
) : MiFitnessStepsTransport {
    override suspend fun exchangePassToken(credential: MiFitnessCredential): MiFitnessSession =
        client.exchangePassToken(credential.userId, credential.passToken)

    override suspend fun fetchSteps(
        session: MiFitnessSession,
        startEpochSeconds: Long,
        endEpochSeconds: Long,
        nextKey: String,
    ): String = client.fetchSteps(session, startEpochSeconds, endEpochSeconds, nextKey)
}

class MiFitnessStepsSyncException(
    val code: String,
    message: String,
) : IllegalStateException(message) {
    override fun toString(): String = "MiFitnessStepsSyncException(code=$code)"
}

internal data class MiFitnessCnDayWindow(
    val localDate: LocalDate,
    val period: HealthPeriod,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
)

internal class MiFitnessStepsSyncOutcome(
    val summary: MiFitnessStepsSummary,
    val refreshedPassToken: String?,
) {
    override fun toString(): String = "MiFitnessStepsSyncOutcome(<redacted>)"
}

class MiFitnessStepsSyncService internal constructor(
    private val credentialStore: MiFitnessCredentialStore,
    private val transport: MiFitnessStepsTransport,
    private val cache: MiFitnessStepsCache,
    private val clock: Clock,
) {
    constructor(context: Context) : this(
        credentialStore = MiFitnessCredentialStore(context),
        transport = MiFitnessReadOnlyTransportAdapter(),
        cache = MiFitnessStepsCache(context),
        clock = Clock.systemUTC(),
    )

    suspend fun syncToday(): Result<MiFitnessStepsSummary> = serialized {
        val credential = try {
            credentialStore.read()
        } catch (_: Exception) {
            null
        } ?: return@serialized failure("credentials_missing", "尚未保存小米运动健康凭据。")
        val window = todayWindow()
        val oldSummary = try {
            cache.read(window.period, window.localDate, credential.accountScope)
        } catch (_: Exception) {
            return@serialized failure("sync_failed", "小米运动健康步数同步失败。")
        }
        val outcome = syncTodayOutcomeLocked(credential, window).getOrElse { error ->
            return@serialized Result.failure(error)
        }
        val refreshedPassToken = outcome.refreshedPassToken
        if (refreshedPassToken != null) {
            val saved = try {
                credentialStore.save(credential.userId, refreshedPassToken).isSuccess
            } catch (_: Exception) {
                false
            }
            if (!saved) {
                restoreCache(oldSummary)
                return@serialized failure("credential_write_failed", "系统安全存储不可用，刷新凭据未保存。")
            }
        }
        Result.success(outcome.summary)
    }

    internal fun todayWindow(): MiFitnessCnDayWindow {
        val localDate = clock.instant().atOffset(CN_OFFSET).toLocalDate()
        val startEpochSeconds = localDate.atStartOfDay().toEpochSecond(CN_OFFSET)
        val endEpochSeconds = localDate.plusDays(1).atStartOfDay().toEpochSecond(CN_OFFSET) - 1L
        return MiFitnessCnDayWindow(
            localDate = localDate,
            period = HealthPeriod(
                startEpochMillis = startEpochSeconds * 1_000L,
                endEpochMillis = (endEpochSeconds + 1L) * 1_000L - 1L,
                key = "today",
            ),
            startEpochSeconds = startEpochSeconds,
            endEpochSeconds = endEpochSeconds,
        )
    }

    internal suspend fun syncTodayOutcomeLocked(
        credential: MiFitnessCredential,
        window: MiFitnessCnDayWindow,
    ): Result<MiFitnessStepsSyncOutcome> = try {
        withTimeout(MAX_SYNC_DURATION_MILLIS) {
            val session = transport.exchangePassToken(credential)
            val records = ArrayList<MiFitnessStepRecord>()
            val seenCursors = mutableSetOf("")
            var cursor = ""
            var pageCount = 0
            var complete = false

            while (pageCount < MAX_PAGES) {
                val rawPage = transport.fetchSteps(
                    session = session,
                    startEpochSeconds = window.startEpochSeconds,
                    endEpochSeconds = window.endEpochSeconds,
                    nextKey = cursor,
                )
                val page = MiFitnessStepsParser.parse(rawPage).getOrElse {
                    throw error("response_invalid", "小米运动健康步数响应格式无效。")
                }
                pageCount += 1
                if (page.records.any { it.epochSeconds !in window.startEpochSeconds..window.endEpochSeconds }) {
                    throw error("record_out_of_window", "小米运动健康返回了日期范围外的步数。")
                }
                if (records.size + page.records.size > MAX_RECORDS) {
                    throw error("record_limit", "本次步数记录超出安全上限。")
                }
                records += page.records

                if (!page.hasMore) {
                    complete = true
                    break
                }
                val nextCursor = page.nextKey
                    ?: throw error("cursor_missing", "小米运动健康分页响应缺少游标。")
                if (nextCursor.length > MAX_CURSOR_CHARS) {
                    throw error("cursor_limit", "小米运动健康分页游标超出安全上限。")
                }
                if (!seenCursors.add(nextCursor)) {
                    throw error("cursor_repeated", "小米运动健康分页游标重复。")
                }
                cursor = nextCursor
            }
            if (!complete) {
                throw error("page_limit", "本次步数分页超出安全上限。")
            }

            val aggregate = MiFitnessStepsAggregator.sumIncremental(records).getOrElse {
                throw error("aggregation_invalid", "小米运动健康步数无法安全聚合。")
            }
            val syncedAt = clock.millis()
            val summary = MiFitnessStepsSummary(
                period = window.period,
                localDate = window.localDate,
                accountScope = credential.accountScope,
                steps = aggregate.steps,
                recordCount = aggregate.recordCount,
                observedAt = syncedAt,
                lastSyncAt = syncedAt,
            )
            if (cache.save(summary).isFailure) {
                throw error("cache_write_failed", "系统安全存储不可用，步数摘要未缓存。")
            }
            Result.success(MiFitnessStepsSyncOutcome(summary, session.refreshedPassToken))
        }
    } catch (_: TimeoutCancellationException) {
        failure("network_failed", "小米运动健康网络请求超时。")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (exception: MiFitnessStepsSyncException) {
        Result.failure(exception)
    } catch (_: MiFitnessAuthenticationException) {
        failure("authentication_failed", "小米运动健康身份验证失败。")
    } catch (_: MiFitnessNetworkException) {
        failure("network_failed", "小米运动健康网络请求失败。")
    } catch (_: MiFitnessProtocolException) {
        failure("response_invalid", "小米运动健康步数响应格式无效。")
    } catch (_: Exception) {
        failure("sync_failed", "小米运动健康步数同步失败。")
    }

    private fun restoreCache(summary: MiFitnessStepsSummary?) {
        try {
            if (summary == null) cache.delete() else cache.save(summary)
        } catch (_: Exception) {
            // Keep the surfaced failure generic; cache payloads never enter exceptions.
        }
    }

    companion object {
        private val CN_OFFSET = ZoneOffset.ofHours(8)
        private const val MAX_PAGES = 10
        private const val MAX_RECORDS = 10_000
        private const val MAX_CURSOR_CHARS = 4_096
        private const val MAX_SYNC_DURATION_MILLIS = 90_000L
        private val syncMutex = Mutex()

        internal suspend fun <T> serialized(block: suspend () -> T): T {
            syncMutex.lock()
            try {
                return block()
            } finally {
                syncMutex.unlock()
            }
        }

        private fun error(code: String, message: String) = MiFitnessStepsSyncException(code, message)

        private fun <T> failure(code: String, message: String): Result<T> =
            Result.failure(error(code, message))
    }
}
