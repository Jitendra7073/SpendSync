package com.example.spendsync.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthLogicTest {

    @Test
    fun resetCodeIsCleanedUpWhileTyping() {
        assertEquals("K7M2QX", normalizeResetCode(" k7m-2qx "))
        assertEquals("K7M2QX", normalizeResetCode("k7m2qxEXTRA")) // a pasted code with junk is capped
        assertEquals("", normalizeResetCode("--  "))
        assertEquals("AB", normalizeResetCode("ab"))
    }

    @Test
    fun passwordStrengthGoesUpWithLengthAndVariety() {
        assertEquals(0, passwordStrength(""))
        assertEquals(1, passwordStrength("short1"))        // too short
        assertEquals(1, passwordStrength("onlyletters"))   // no digit
        assertEquals(1, passwordStrength("12345678"))      // no letter
        assertEquals(2, passwordStrength("abcd1234"))      // okay
        assertEquals(3, passwordStrength("abcd1234!"))     // symbol
        assertEquals(3, passwordStrength("abcdefgh1234"))  // long
    }
}
