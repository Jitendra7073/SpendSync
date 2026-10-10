package com.example.spendsync.data.bills

/** A bill API result that tells the upload queue whether trying again can help. */
sealed class BillCall<out T> {
    data class Ok<T>(val data: T) : BillCall<T>()
    data class Fail(val message: String, val permanent: Boolean) : BillCall<Nothing>()

    companion object {
        /** 4xx means "this request will never work" — except a timeout (408) or a rate limit (429). */
        fun isPermanent(httpCode: Int): Boolean = httpCode in 400..499 && httpCode != 408 && httpCode != 429
    }
}
