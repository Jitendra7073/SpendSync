package com.example.spendsync.data.planify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartCategoriesTest {
    @Test
    fun onlyRealMissingCategoriesAreListed() {
        val known = listOf("Food", "Transport", "Trip fund")
        val m = SmartCategories.missing(listOf("Eating out", "food", " Emergency fund ", "Other", "trip fund", "Eating out", ""), known)
        assertEquals(listOf("Eating out", "Emergency fund"), m)
    }

    @Test
    fun iconsComeFromWordsInTheName() {
        assertEquals("mdi:silverware-fork-knife", SmartCategories.iconFor("Eating out"))
        assertEquals("mdi:shield-check", SmartCategories.iconFor("Emergency fund")) // emergency beats fund
        assertNull(SmartCategories.iconFor("Zzz"))
    }

    @Test
    fun matchKeysAreNormalisedLikeTheServer() {
        assertEquals("eating out", PlanMath.norm("  Eating-Out!  "))
        assertEquals("zomato", PlanMath.norm("ZOMATO"))
    }
}
