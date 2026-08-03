package com.example.spendsync.data.remote

import com.example.spendsync.data.remote.model.ExchangeRateResponse
import retrofit2.Response
import retrofit2.http.GET

interface ExchangeRateApiService {

    @GET("v6/latest/INR")
    suspend fun getRates(): Response<ExchangeRateResponse>
}
