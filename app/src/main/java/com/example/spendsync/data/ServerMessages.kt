package com.example.spendsync.data

import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr

/**
 * The API replies in English. Known replies are swapped for the same sentence in the user's
 * language (matched on the error code first, then on the text); anything unknown is shown as the
 * server sent it rather than hidden.
 */
object ServerMessages {
    fun localize(message: String?, code: String? = null): String {
        val m = message.orEmpty().lowercase()
        val c = code.orEmpty().uppercase()
        return when {
            c == "INVALID_EMAIL_OR_PASSWORD" || "invalid email or password" in m -> tr(R.string.err_invalid_credentials)
            c == "USER_ALREADY_EXISTS" || "user already exists" in m -> tr(R.string.err_user_exists)
            c == "PASSWORD_TOO_SHORT" || "password too short" in m -> tr(R.string.password_must_be_at_least_8)
            c == "RATE_LIMIT_EXCEEDED" || "too many requests" in m -> tr(R.string.err_rate_limit)
            c == "VALIDATION_ERROR" || "validation failed" in m -> tr(R.string.err_validation)
            c == "INTERNAL_ERROR" || "internal server error" in m -> tr(R.string.err_server)
            "invalid or expired session" in m || m == "unauthorized" -> tr(R.string.err_session_expired)
            "transaction not found" in m -> tr(R.string.err_tx_not_found)
            "hold not found" in m -> tr(R.string.err_hold_not_found)
            "budget not found" in m -> tr(R.string.err_budget_not_found)
            "category rule not found" in m -> tr(R.string.err_rule_not_found)
            "budget already exists" in m -> tr(R.string.err_budget_exists)
            "keyword already exists" in m -> tr(R.string.err_rule_exists)
            message.isNullOrBlank() -> tr(R.string.an_unexpected_error_occurred)
            else -> message
        }
    }
}
