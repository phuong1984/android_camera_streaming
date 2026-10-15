package com.bapi.ipcamera

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionTest {
    @Test
    fun prefixesVersionNameWithV() {
        assertEquals("v1.0", AppVersion.label("1.0"))
    }

    @Test
    fun nullNameBecomesBareV() {
        assertEquals("v", AppVersion.label(null))
    }

    @Test
    fun blankNameBecomesBareV() {
        assertEquals("v", AppVersion.label(""))
    }
}
