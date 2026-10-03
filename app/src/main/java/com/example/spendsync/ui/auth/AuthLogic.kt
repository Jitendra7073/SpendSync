package com.example.spendsync.ui.auth

const val RESET_CODE_LENGTH = 6
const val RESEND_COOLDOWN_SECONDS = 60

/** What people type or paste ("k7m 2qx") -> the code as sent ("K7M2QX"), capped at the code length. */
fun normalizeResetCode(input: String): String =
    input.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(RESET_CODE_LENGTH)

/** 0 = nothing typed, 1 = weak, 2 = okay, 3 = strong. Simple on purpose: length first, then variety. */
fun passwordStrength(password: String): Int {
    if (password.isEmpty()) return 0
    val hasLetter = password.any { it.isLetter() }
    val hasDigit = password.any { it.isDigit() }
    val hasOther = password.any { !it.isLetterOrDigit() }
    return when {
        password.length < 8 || !(hasLetter && hasDigit) -> 1
        password.length >= 12 || hasOther -> 3
        else -> 2
    }
}
