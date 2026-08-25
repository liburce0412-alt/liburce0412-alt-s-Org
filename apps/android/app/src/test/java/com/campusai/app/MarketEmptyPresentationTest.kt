package com.campusai.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketEmptyPresentationTest {
    @Test
    fun firstRefreshDoesNotClaimTheRemoteCatalogIsEmpty() {
        val copy = marketEmptyPresentation(refreshing = true, hasSynced = false, hasError = false)

        assertEquals("正在确认心愿墙", copy.title)
        assertEquals("正在确认", copy.status)
    }

    @Test
    fun firstFailureExplainsThatTheEmptyScreenIsNotAConfirmedEmptyCatalog() {
        val copy = marketEmptyPresentation(refreshing = false, hasSynced = false, hasError = true)

        assertEquals("暂时无法确认心愿墙", copy.title)
        assertEquals("尚未取得远端结果；当前空白不代表没有心愿卡。", copy.detail)
    }

    @Test
    fun successfulEmptyResultUsesTheRealEmptyState() {
        val copy = marketEmptyPresentation(refreshing = false, hasSynced = true, hasError = false)

        assertEquals("心愿墙还很安静", copy.title)
        assertEquals("本次同步结果为空；新心愿提交后会先进入审核。", copy.detail)
    }

    @Test
    fun refreshAndFailureAfterAConfirmedEmptyResultKeepTheirProvenance() {
        val refreshing = marketEmptyPresentation(refreshing = true, hasSynced = true, hasError = false)
        val failed = marketEmptyPresentation(refreshing = false, hasSynced = true, hasError = true)

        assertEquals("上次同步时心愿墙是空的", refreshing.title)
        assertEquals("正在更新", refreshing.status)
        assertEquals("上次同步时心愿墙是空的", failed.title)
        assertEquals("刷新未完成", failed.status)
    }
}
