package com.example.spendsync.data.remote

import com.example.spendsync.data.remote.model.ForgotPasswordRequest
import com.example.spendsync.data.remote.model.SignInRequest
import com.example.spendsync.data.remote.model.SignUpRequest
import com.example.spendsync.data.remote.model.VerifyCodeRequest
import com.example.spendsync.data.serverErrorParts
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The app's REAL networking layer (same Retrofit interface and request/response classes the app uses) against a
 * running backend. Skipped unless AUTH_E2E_URL is set, e.g.  AUTH_E2E_URL=http://localhost:3113 ./gradlew testDebugUnitTest
 * Creates one throwaway account and deletes it afterwards.
 */
class AuthApiLiveTest {
    private val base = System.getenv("AUTH_E2E_URL")
    private val email = "app-live-${System.currentTimeMillis()}@example.com"
    private val password = "Probe-pass-12345"
    private var token: String? = null
    private lateinit var api: AuthApiService

    @Before
    fun setUp() {
        assumeTrue("AUTH_E2E_URL not set, skipping", !base.isNullOrBlank())
        api = Retrofit.Builder().baseUrl(base!!.trimEnd('/') + "/").addConverterFactory(GsonConverterFactory.create())
            .build().create(AuthApiService::class.java)
    }

    @After
    fun cleanUp() {
        val t = token ?: return
        OkHttpClient().newCall(Request.Builder().url(base!!.trimEnd('/') + "/api/account").delete().header("Authorization", "Bearer $t").build()).execute().close()
    }

    @Test
    fun theWholeAppSideOfLoginAndReset() = runBlocking {
        // sign up: the app reads the token from the set-auth-token header
        val up = api.signUp(SignUpRequest(email = email, password = password, name = "App Live"))
        assertTrue("sign-up HTTP ${up.code()}", up.isSuccessful)
        token = up.headers()["set-auth-token"]
        assertNotNull("sign-up must return the set-auth-token header", token)
        assertEquals(email, up.body()?.user?.email)

        // sign in: same
        val inn = api.signIn(SignInRequest(email = email, password = password))
        assertTrue(inn.isSuccessful)
        val signedInToken = inn.headers()["set-auth-token"] ?: inn.body()?.session?.token ?: inn.body()?.token
        assertNotNull(signedInToken)
        token = signedInToken
        assertEquals(email, inn.body()?.user?.email)

        // the session check the app does on start
        val session = api.getSession("Bearer $signedInToken")
        assertTrue(session.isSuccessful && session.body()?.session != null)

        // wrong password: the app must be able to turn this into its translated message
        val bad = api.signIn(SignInRequest(email = email, password = "Wrong-pass-0000"))
        assertEquals(401, bad.code())
        assertEquals("INVALID_EMAIL_OR_PASSWORD", serverErrorParts(bad.errorBody()?.string()).second)

        // forgot password answers success; a wrong code comes back in OUR error shape and must be understood
        assertTrue(api.forgotPassword(ForgotPasswordRequest(email = email, language = "English")).isSuccessful)
        val wrongCode = api.verifyResetCode(VerifyCodeRequest(email = email, code = "ZZZZZZ"))
        assertEquals(400, wrongCode.code())
        assertEquals("INVALID_CODE", serverErrorParts(wrongCode.errorBody()?.string()).second)

        // an unknown email gets the same answer from forgot (no enumeration) and the same error from verify
        assertTrue(api.forgotPassword(ForgotPasswordRequest(email = "nobody-$email", language = "English")).isSuccessful)
        assertEquals("INVALID_CODE", serverErrorParts(api.verifyResetCode(VerifyCodeRequest("nobody-$email", "ZZZZZZ")).errorBody()?.string()).second)
    }
}
