package com.example.cookgpt

import com.example.cookgpt.data.YouTubeApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Dedicated Retrofit instance for YouTube Data API v3.
 * MUST remain separate from the Spoonacular ApiService instance.
 */
object YouTubeRetrofitClient {
    private const val BASE_URL = "https://www.googleapis.com/"

    val instance: YouTubeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(YouTubeApiService::class.java)
    }
}
