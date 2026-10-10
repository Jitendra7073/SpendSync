package com.example.spendsync.data.bills

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface UploadOutcome {
    data class Done(val uploaded: Uploaded) : UploadOutcome
    /** Signature/timestamp refused: ask our API to sign again. */
    data object Resign : UploadOutcome
    data class Retry(val message: String) : UploadOutcome
    data class Rejected(val message: String) : UploadOutcome
}

/** Sends a bill straight to Cloudinary. Own client: never carries our session token, never logs bodies. */
object CloudinaryUploader {
    private val gson = Gson()
    private fun json(text: String): JsonObject = gson.fromJson(text, JsonObject::class.java)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private class ProgressBody(private val file: File, private val type: MediaType?, private val onProgress: (Float) -> Unit) : RequestBody() {
        override fun contentType() = type
        override fun contentLength() = file.length()
        override fun writeTo(sink: BufferedSink) {
            val total = contentLength().coerceAtLeast(1)
            var sent = 0L
            file.source().use { src ->
                while (true) {
                    val read = src.read(sink.buffer, 8_192)
                    if (read == -1L) break
                    sent += read
                    sink.flush()
                    onProgress(sent.toFloat() / total)
                }
            }
        }
    }

    suspend fun upload(file: File, mime: String, url: String, params: Map<String, String>, onProgress: (Float) -> Unit): UploadOutcome =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                params.forEach { (k, v) -> addFormDataPart(k, v) }
                addFormDataPart("file", file.name, ProgressBody(file, mime.toMediaTypeOrNull(), onProgress))
            }.build()
            try {
                client.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    when {
                        r.isSuccessful -> {
                            val j = json(text)
                            UploadOutcome.Done(
                                Uploaded(
                                    publicId = j["public_id"].asString,
                                    version = j["version"].asLong,
                                    signature = j["signature"].asString,
                                    format = j["format"].asString,
                                    bytes = j["bytes"].asLong,
                                    width = j["width"]?.takeIf { !it.isJsonNull }?.asInt,
                                    height = j["height"]?.takeIf { !it.isJsonNull }?.asInt,
                                    pages = j["pages"]?.takeIf { !it.isJsonNull }?.asInt,
                                ),
                            )
                        }
                        r.code == 401 || (r.code == 400 && text.contains("Stale request", ignoreCase = true)) -> UploadOutcome.Resign
                        r.code == 420 || r.code == 429 || r.code >= 500 -> UploadOutcome.Retry("Cloudinary ${r.code}")
                        else -> UploadOutcome.Rejected(runCatching { json(text)["error"].asJsonObject["message"].asString }.getOrDefault("Upload refused (${r.code})"))
                    }
                }
            } catch (e: IOException) {
                UploadOutcome.Retry(e.message ?: "network")
            }
        }
}
