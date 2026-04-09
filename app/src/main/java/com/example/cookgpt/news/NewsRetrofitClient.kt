package com.example.cookgpt.news

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NewsRetrofitClient { 
    private const val BASE_URL = "https://newsapi.org/" 
    val instance: NewsApiService by lazy { 
        Retrofit.Builder() 
            .baseUrl(BASE_URL) 
            .addConverterFactory(GsonConverterFactory.create()) 
            .build() 
            .create(NewsApiService::class.java) 
    } 
}
