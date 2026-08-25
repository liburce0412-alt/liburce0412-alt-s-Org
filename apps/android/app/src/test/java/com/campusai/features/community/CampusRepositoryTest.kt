package com.campusai.features.community

import org.junit.Assert.assertEquals
import org.junit.Test

class CampusRepositoryTest {
    @Test
    fun nullableString_mapsJsonNullAndLiteralNullToEmpty() {
        assertEquals("", normalizeNullableString(isNull = true, value = null))
        assertEquals("", normalizeNullableString(isNull = false, value = "null"))
        assertEquals("", normalizeNullableString(isNull = false, value = null))
    }

    @Test
    fun nullableString_preservesRealText() {
        assertEquals("学习", normalizeNullableString(isNull = false, value = "学习"))
    }
}
