package com.example.spendsync.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerErrorTest {

    @Test
    fun readsBetterAuthShape() {
        val (message, code) = serverErrorParts("""{"message":"Invalid email or password","code":"INVALID_EMAIL_OR_PASSWORD"}""")
        assertEquals("Invalid email or password", message)
        assertEquals("INVALID_EMAIL_OR_PASSWORD", code)
    }

    @Test
    fun readsOurOwnNestedShape_theResetCodeErrors() {
        // This is what /api/password/verify and /reset send. It used to show "unexpected error" in the app.
        val (message, code) = serverErrorParts("""{"success":false,"error":{"message":"That code is not correct.","code":"INVALID_CODE"}}""")
        assertEquals("That code is not correct.", message)
        assertEquals("INVALID_CODE", code)
        assertEquals("CODE_EXPIRED", serverErrorParts("""{"success":false,"error":{"message":"x","code":"CODE_EXPIRED"}}""").second)
        assertEquals("TOO_MANY_ATTEMPTS", serverErrorParts("""{"success":false,"error":{"code":"TOO_MANY_ATTEMPTS"}}""").second)
    }

    @Test
    fun toleratesPlainStringErrorsAndGarbage() {
        assertEquals("boom" to null, serverErrorParts("""{"error":"boom"}"""))
        assertEquals(null to null, serverErrorParts(null))
        assertEquals(null to null, serverErrorParts(""))
        assertEquals(null to null, serverErrorParts("<html>502 Bad Gateway</html>"))
        assertNull(serverErrorParts("""{"unrelated":1}""").first)
    }
}
