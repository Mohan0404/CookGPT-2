package com.example.cookgpt.data

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApiService {

    /**
     * Legacy single-result search — kept so RecipeDetailsActivity still compiles.
     * New code should use searchVideos() below.
     */
    @GET("youtube/v3/search")
    fun searchVideo(
        @Query("q")          query: String,
        @Query("key")        apiKey: String,
        @Query("part")       part: String = "snippet",
        @Query("type")       type: String = "video",
        @Query("maxResults") maxResults: Int = 1
    ): Call<YouTubeSearchResponse>

    /**
     * Multi-result search for the Discover screen.
     * videoDuration=medium  → excludes Shorts (<4 min) at the API level
     * videoEmbeddable=true  → guarantees the player can load in-app
     * safeSearch=strict     → filters inappropriate content
     * maxResults=15         → wider pool to survive client-side Shorts filter
     */
    @GET("youtube/v3/search")
    fun searchVideos(
        @Query("q")               query: String,
        @Query("key")             apiKey: String,
        @Query("part")            part: String = "snippet",
        @Query("type")            type: String = "video",
        @Query("maxResults")      maxResults: Int = 15,
        @Query("safeSearch")      safeSearch: String = "strict",
        @Query("videoEmbeddable") videoEmbeddable: String = "true",
        @Query("videoDuration")   videoDuration: String = "any"
    ): Call<YouTubeSearchResponse>

    companion object {
        private const val BASE_URL = "https://www.googleapis.com/"

        fun create(): YouTubeApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(YouTubeApiService::class.java)
        }
    }

    /**
     * Videos API — authoritative embeddability + duration check.
     * Called as a SECOND pass after the Search API.
     *
     * @param ids Comma-separated list of video IDs (max 50).
     * @param part Must include "status" (for embeddable) and "contentDetails" (for duration).
     */
    @GET("youtube/v3/videos")
    fun getVideoDetails(
        @Query("id")   ids: String,
        @Query("key")  apiKey: String,
        @Query("part") part: String = "status,contentDetails"
    ): Call<YouTubeVideoListResponse>
}
