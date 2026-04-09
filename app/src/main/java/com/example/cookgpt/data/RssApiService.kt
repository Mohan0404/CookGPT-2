package com.example.cookgpt.data

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface RssApiService {
    @GET
    suspend fun fetchRss(@Url url: String): Response<String>
}
