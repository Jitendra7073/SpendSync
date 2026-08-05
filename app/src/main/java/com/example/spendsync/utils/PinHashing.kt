package com.example.spendsync.utils

import java.security.MessageDigest
import java.security.SecureRandom

internal fun generateSalt(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

internal fun hashPin(pin: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest((salt + pin).toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}

internal fun verifyPin(pin: String, salt: String, expectedHash: String): Boolean =
    hashPin(pin, salt) == expectedHash
