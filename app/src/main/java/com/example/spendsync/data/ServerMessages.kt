package com.example.spendsync.data

import com.example.spendsync.R
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.example.spendsync.ui.i18n.tr

/**
 * The API replies in English. Known replies are swapped for the same sentence in the user's
 * language (matched on the error code first, then on the text); anything unknown is shown as the
 * server sent it rather than hidden.
 */
/**
 * The API reports errors in two shapes: Better Auth sends `{ "message", "code" }`, our own routes send
 * `{ "success": false, "error": { "message", "code" } }`. Returns (message, code), either may be null.
 * Pure on purpose, so it can be unit-tested without Android.
 */
fun serverErrorParts(errorBody: String?): Pair<String?, String?> {
    if (errorBody.isNullOrBlank()) return null to null
    return try {
        val root = JsonParser().parse(errorBody).asJsonObject
        fun JsonObject.text(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString
        val nested = root.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
        val message = root.text("message") ?: nested?.text("message") ?: root.text("error")
        val code = root.text("code") ?: nested?.text("code")
        message to code
    } catch (e: Exception) {
        null to null
    }
}

object ServerMessages {
    /** Turns a raw error response body into one sentence in the user's language. */
    fun fromBody(errorBody: String?): String {
        val (message, code) = serverErrorParts(errorBody)
        return localize(message, code)
    }

    fun localize(message: String?, code: String? = null): String {
        val m = message.orEmpty().lowercase()
        val c = code.orEmpty().uppercase()
        return when {
            c == "INVALID_EMAIL_OR_PASSWORD" || "invalid email or password" in m -> tr(R.string.err_invalid_credentials)
            c == "USER_ALREADY_EXISTS" || "user already exists" in m -> tr(R.string.err_user_exists)
            c == "INVALID_CODE" -> tr(R.string.auth_err_code_invalid)
            c == "CODE_EXPIRED" -> tr(R.string.auth_err_code_expired)
            c == "TOO_MANY_ATTEMPTS" -> tr(R.string.auth_err_too_many)
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
