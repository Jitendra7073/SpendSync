package com.example.spendsync.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHashingTest {

    @Test
    fun `hashPin is deterministic for the same pin and salt`() {
        assertEquals(hashPin("1234", "abc"), hashPin("1234", "abc"))
    }

    @Test
    fun `different pins produce different hashes for the same salt`() {
        assertNotEquals(hashPin("1234", "abc"), hashPin("5678", "abc"))
    }

    @Test
    fun `different salts produce different hashes for the same pin`() {
        assertNotEquals(hashPin("1234", "abc"), hashPin("1234", "xyz"))
    }

    @Test
    fun `verifyPin returns true for the correct pin`() {
        val salt = generateSalt()
        val hash = hashPin("4321", salt)
        assertTrue(verifyPin("4321", salt, hash))
    }

    @Test
    fun `verifyPin returns false for the wrong pin`() {
        val salt = generateSalt()
        val hash = hashPin("4321", salt)
        assertTrue(!verifyPin("0000", salt, hash))
    }

    @Test
    fun `generateSalt produces different non-empty values each call`() {
        val a = generateSalt()
        val b = generateSalt()
        assertTrue(a.isNotEmpty())
        assertTrue(b.isNotEmpty())
        assertNotEquals(a, b)
    }
}
