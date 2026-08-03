package com.example.spendsync.data.remote.model

import com.google.gson.annotations.SerializedName

data class ExchangeRateResponse(
    @SerializedName("rates") val rates: Map<String, Double>,
    @SerializedName("result") val result: String
)
