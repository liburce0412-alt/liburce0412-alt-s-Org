package com.campusai.app

import com.campusai.core.health.HealthFreshness
import com.campusai.core.health.HealthMetrics
import com.campusai.core.health.HealthPeriod
import com.campusai.core.health.HealthSnapshot
import com.campusai.features.ai.CaesarHealthUiState
import com.campusai.features.ai.MiFitnessUiStatus
import com.campusai.features.ai.afterMiFitnessNoDataCredentialSave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class MiFitnessSettingsPresentationTest {
    @Test
    fun `settings status text is fixed and never includes raw provider errors`() {
        assertEquals("尚未配置。", miFitnessStatusText(MiFitnessSettingsStatus.IDLE, configured = false))
        assertEquals("已配置，等待手动刷新。", miFitnessStatusText(MiFitnessSettingsStatus.IDLE, configured = true))
        assertEquals("正在验证并保存到系统安全存储。", miFitnessStatusText(MiFitnessSettingsStatus.VALIDATING, false))
        assertEquals("正在读取 Mi Fitness 中国区的今日步数。", miFitnessStatusText(MiFitnessSettingsStatus.REFRESHING, true))
        assertEquals("正在删除本机凭据与步数缓存。", miFitnessStatusText(MiFitnessSettingsStatus.DELETING, true))
        assertEquals("最近一次操作已完成。", miFitnessStatusText(MiFitnessSettingsStatus.SUCCESS, true))
        assertEquals(
            "Mi Fitness 云端暂未返回今天的步数记录；不会把空记录当成 0 步。",
            miFitnessStatusText(MiFitnessSettingsStatus.NO_DATA, true),
        )
        assertEquals("验证失败，请检查 userId 与 passToken。", miFitnessStatusText(MiFitnessSettingsStatus.AUTH_ERROR, true))
        assertEquals("网络或云端响应异常，请稍后重试。", miFitnessStatusText(MiFitnessSettingsStatus.NETWORK_ERROR, true))
        assertEquals("系统安全存储暂不可用，请稍后重试。", miFitnessStatusText(MiFitnessSettingsStatus.STORAGE_ERROR, true))
    }

    @Test
    fun `last sync time handles missing values and uses the local month day minute format`() {
        assertEquals("尚未刷新", formatMiFitnessLastSync(null))
        assertEquals("尚未刷新", formatMiFitnessLastSync(0L))
        assertEquals("尚未刷新", formatMiFitnessLastSync(-1L))

        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            val timestamp = Instant.parse("2026-08-27T03:04:00Z").toEpochMilli()
            assertEquals("8月27日 11:04", formatMiFitnessLastSync(timestamp))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `saving a different Mi Fitness account with no data clears the previous account snapshot`() {
        val previousAccountSnapshot = HealthSnapshot(
            originPackages = setOf("campusai.mifitness.cloud"),
            period = HealthPeriod(0L, 10_000L, "today"),
            observedAt = 10_000L,
            lastSyncAt = 9_000L,
            freshness = HealthFreshness.FRESH,
            metrics = HealthMetrics(steps = 42L),
            missingFields = emptySet(),
            confidence = 1.0,
        )
        val message = "Mi Fitness 凭据已安全保存；云端暂无今日步数记录。"

        val updated = CaesarHealthUiState(
            miFitnessConfigured = true,
            miFitnessSyncing = true,
            miFitnessStatus = MiFitnessUiStatus.VALIDATING,
            miFitnessLastSyncAt = 9_000L,
            miFitnessFormResetKey = 7L,
            snapshot = previousAccountSnapshot,
        ).afterMiFitnessNoDataCredentialSave(message)

        assertTrue(updated.miFitnessConfigured)
        assertEquals(MiFitnessUiStatus.NO_DATA, updated.miFitnessStatus)
        assertEquals(8L, updated.miFitnessFormResetKey)
        assertNull(updated.miFitnessLastSyncAt)
        assertNull(updated.snapshot)
        assertEquals(message, updated.actionMessage)
    }
}
