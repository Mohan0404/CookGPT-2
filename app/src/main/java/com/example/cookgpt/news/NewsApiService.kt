package com.example.cookgpt.news

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface NewsApiService { 
    @GET("v2/everything") 
    fun getNews( 
        @Query("q") query: String, 
        @Query("language") language: String = "en", 
        @Query("sortBy") sortBy: String = "publishedAt", 
        @Query("pageSize") pageSize: Int = 20, 
        @Query("apiKey") apiKey: String 
    ): Call<NewsResponse> 
}
