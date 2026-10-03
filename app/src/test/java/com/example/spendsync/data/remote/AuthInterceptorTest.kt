package com.example.spendsync.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthInterceptorTest {
    @Test
    fun aWrongPasswordOrCodeIsNotASessionExpiry() {
        assertTrue(isCredentialAttempt("/api/auth/sign-in/email"))
        assertTrue(isCredentialAttempt("/api/auth/sign-up/email"))
        assertTrue(isCredentialAttempt("/api/password/verify"))
        assertTrue(isCredentialAttempt("/api/password/reset"))
    }

    @Test
    fun everythingElseStillMeansTheSessionExpired() {
        assertFalse(isCredentialAttempt("/api/transactions"))
        assertFalse(isCredentialAttempt("/api/settings"))
        assertFalse(isCredentialAttempt("/api/auth/get-session"))
        assertFalse(isCredentialAttempt("/api/assistant/chat"))
    }
}
