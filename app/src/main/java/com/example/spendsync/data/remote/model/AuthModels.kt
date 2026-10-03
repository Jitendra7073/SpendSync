package com.example.spendsync.data.remote.model

import com.google.gson.annotations.SerializedName

// ── Forgot password ──────────────────────────────────────────────────────────

data class ForgotPasswordRequest(
    @SerializedName("email")    val email: String,
    @SerializedName("language") val language: String,
)

data class VerifyCodeRequest(
    @SerializedName("email") val email: String,
    @SerializedName("code")  val code: String,
)

data class ResetPasswordRequest(
    @SerializedName("email")       val email: String,
    @SerializedName("code")        val code: String,
    @SerializedName("newPassword") val newPassword: String,
)

// ── Sign-In ──────────────────────────────────────────────────────────────────

data class SignInRequest(
    @SerializedName("email")    val email: String,
    @SerializedName("password") val password: String,
)

data class SignInResponse(
    @SerializedName("token")   val token: String?,
    @SerializedName("user")    val user: UserDto?,
    @SerializedName("session") val session: SessionDto?,
)

// ── Sign-Up ──────────────────────────────────────────────────────────────────

data class SignUpRequest(
    @SerializedName("email")    val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("name")     val name: String,
)

data class SignUpResponse(
    @SerializedName("token")   val token: String?,
    @SerializedName("user")    val user: UserDto?,
    @SerializedName("session") val session: SessionDto?,
)

// ── Session ───────────────────────────────────────────────────────────────────

data class SessionResponse(
    @SerializedName("session") val session: SessionDto?,
    @SerializedName("user")    val user: UserDto?,
)

// ── Shared DTOs ───────────────────────────────────────────────────────────────

data class UserDto(
    @SerializedName("id")            val id: String,
    @SerializedName("email")         val email: String,
    @SerializedName("name")          val name: String?,
    @SerializedName("emailVerified") val emailVerified: Boolean,
    @SerializedName("createdAt")     val createdAt: String?,
)

data class SessionDto(
    @SerializedName("id")        val id: String,
    @SerializedName("expiresAt") val expiresAt: String?,
    @SerializedName("token")     val token: String?,
)

// ── Error ─────────────────────────────────────────────────────────────────────

data class ApiError(
    @SerializedName("error")   val error: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("code")    val code: String?,
)
