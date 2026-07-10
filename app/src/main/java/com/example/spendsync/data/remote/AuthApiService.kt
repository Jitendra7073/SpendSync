package com.example.spendsync.data.remote

import com.example.spendsync.data.remote.model.SessionResponse
import com.example.spendsync.data.remote.model.SignInRequest
import com.example.spendsync.data.remote.model.SignInResponse
import com.example.spendsync.data.remote.model.SignUpRequest
import com.example.spendsync.data.remote.model.SignUpResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Better Auth endpoints exposed through the Express backend.
 *
 * Base path: /api/auth
 * Docs: https://www.better-auth.com/docs/concepts/api
 */
interface AuthApiService {

    /** Sign in with email + password. Returns a session cookie + token. */
    @POST("api/auth/sign-in/email")
    suspend fun signIn(@Body body: SignInRequest): Response<SignInResponse>

    /** Register a new account. */
    @POST("api/auth/sign-up/email")
    suspend fun signUp(@Body body: SignUpRequest): Response<SignUpResponse>

    /** Verify that the current session token is still valid. */
    @GET("api/auth/get-session")
    suspend fun getSession(@Header("Authorization") token: String): Response<SessionResponse>

    /** Sign out — invalidates the server-side session. */
    @POST("api/auth/sign-out")
    suspend fun signOut(@Header("Authorization") token: String): Response<Unit>
}
