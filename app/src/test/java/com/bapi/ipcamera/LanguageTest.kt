package com.bapi.ipcamera

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageTest {
    @Test
    fun defaultIsVietnamese() {
        assertEquals("vi", Language.DEFAULT)
    }

    @Test
    fun togglesFromVietnameseToEnglish() {
        assertEquals("en", Language.next("vi"))
    }

    @Test
    fun togglesFromEnglishToVietnamese() {
        assertEquals("vi", Language.next("en"))
    }

    @Test
    fun unknownLanguageTogglesToEnglish() {
        assertEquals("en", Language.next(""))
        assertEquals("en", Language.next("fr"))
    }

    @Test
    fun flagResPointsToTheTargetLanguageFlag() {
        assertEquals(R.drawable.flag_uk, Language.flagRes("vi"))
        assertEquals(R.drawable.flag_vietnam, Language.flagRes("en"))
    }
}
