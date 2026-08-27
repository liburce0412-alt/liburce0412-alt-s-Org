package com.campusai.app

import org.junit.Assert.assertEquals
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
}
