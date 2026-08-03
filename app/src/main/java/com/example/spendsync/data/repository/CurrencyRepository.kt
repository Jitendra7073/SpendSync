package com.example.spendsync.data.repository

import com.example.spendsync.data.remote.ExchangeRateApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory repository for live currency exchange rates sourced from open.er-api.com.
 *
 * Intended to be created with `remember` in the Compose UI layer (not a singleton) so
 * that its lifetime is tied to the composition that owns it.
 *
 * Key behaviour:
 * - Rates are fetched from the free INR-base endpoint; no authentication required.
 * - A 1-hour in-memory TTL prevents redundant network calls.
 * - Re-fetch is also triggered automatically when the target currency changes.
 * - On fetch failure [ratesUnavailable] is set to `true` so callers can display a banner.
 * - On fetch success [ratesUnavailable] is reset to `false`.
 * - When the target currency is "INR" (or the rate is missing) amounts are returned as-is.
 */
class CurrencyRepository {

    // ── Public state ────────────────────────────────────────────────────────────

    private val _rates = MutableStateFlow<Map<String, Double>>(emptyMap())
    /** Live INR-base exchange rates keyed by ISO 4217 currency code (e.g. "USD", "JPY"). */
    val rates: StateFlow<Map<String, Double>> = _rates.asStateFlow()

    private val _ratesUnavailable = MutableStateFlow(false)
    /** `true` when the last fetch attempt failed and no cached rates are available. */
    val ratesUnavailable: StateFlow<Boolean> = _ratesUnavailable.asStateFlow()

    // ── TTL tracking ────────────────────────────────────────────────────────────

    /** Epoch-millisecond timestamp of the last successful fetch. -1 means never fetched. */
    private var lastFetchTime: Long = -1L

    /** The currency that was used for the last successful fetch. */
    private var lastFetchedCurrency: String = ""

    private val ttlMillis: Long = 60 * 60 * 1_000L // 1 hour

    // ── Public API ───────────────────────────────────────────────────────────────

    /**
     * Fetches exchange rates for [currency] if the cached data is stale (>= 1 hour old)
     * or if the target currency has changed since the last fetch.
     *
     * On success: updates [rates] and resets [ratesUnavailable] to `false`.
     * On failure: sets [ratesUnavailable] to `true` (existing cached rates are preserved).
     */
    suspend fun fetchRates(currency: String) {
        val now = System.currentTimeMillis()
        val cacheExpired = lastFetchTime < 0 || (now - lastFetchTime) >= ttlMillis
        val currencyChanged = currency != lastFetchedCurrency

        if (!cacheExpired && !currencyChanged) return

        try {
            val response = ExchangeRateApiClient.ratesApi.getRates()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    _rates.value = body.rates
                    _ratesUnavailable.value = false
                    lastFetchTime = now
                    lastFetchedCurrency = currency
                } else {
                    _ratesUnavailable.value = true
                }
            } else {
                _ratesUnavailable.value = true
            }
        } catch (e: Exception) {
            _ratesUnavailable.value = true
        }
    }

    /**
     * Converts [amountInr] from INR to [toCurrency] using the cached rates.
     *
     * Returns the original [amountInr] unchanged when:
     * - [toCurrency] is "INR"
     * - The rate for [toCurrency] is not present in the cached map
     */
    fun convert(amountInr: Double, toCurrency: String): Double {
        if (toCurrency == "INR") return amountInr
        val rate = _rates.value[toCurrency] ?: return amountInr
        return amountInr * rate
    }

    /**
     * Converts [amountInr] to [toCurrency] and formats the result as a human-readable string
     * prefixed with [symbol].
     *
     * Formatting rules:
     * - JPY: no decimal places  → `"%,.0f"`
     * - All others: 2 decimal places → `"%,.2f"`
     *
     * Example outputs: `"$1,234.56"`, `"¥1,235"`, `"₹1,234.56"`
     */
    fun formatAmount(amountInr: Double, toCurrency: String, symbol: String): String {
        val converted = convert(amountInr, toCurrency)
        val formatted = if (toCurrency == "JPY") {
            "%,.0f".format(converted)
        } else {
            "%,.2f".format(converted)
        }
        return "$symbol$formatted"
    }
}
