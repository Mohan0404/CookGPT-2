package com.example.cookgpt.data

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("recipes/complexSearch")
    fun searchRecipes(
        @Query("query") query: String,
        @Query("apiKey") apiKey: String,
        @Query("intolerances") intolerances: String = "",
        @Query("diet") diet: String = "",
        @Query("number") number: Int = 20
    ): Call<RecipeResponse>

    @GET("recipes/complexSearch")
    fun searchDiscoverRecipes(
        @Query("query") query: String,
        @Query("apiKey") apiKey: String,
        @Query("intolerances") intolerances: String = "",
        @Query("diet") diet: String = "",
        @Query("number") number: Int = 20,
        @Query("addRecipeNutrition") addRecipeNutrition: Boolean = true
    ): Call<RecipeResponse>

    @GET("recipes/{id}/information")
    fun getRecipeInformation(
        @Path("id") id: Int,
        @Query("apiKey") apiKey: String,
        @Query("includeNutrition") includeNutrition: Boolean = true
    ): Call<RecipeDetail>

    @GET("recipes/{id}/analyzedInstructions")
    fun getAnalyzedInstructions(
        @Path("id") id: Int,
        @Query("apiKey") apiKey: String
    ): Call<List<AnalyzedInstruction>>

    @GET("food/ingredients/search")
    fun searchIngredient(
        @Query("query") query: String,
        @Query("apiKey") apiKey: String,
        @Query("number") number: Int = 1,
        @Query("metaInformation") metaInformation: Boolean = true
    ): Call<IngredientSearchResponse>

    @GET("food/ingredients/{id}/information")
    fun getIngredientInfo(
        @Path("id") id: Int,
        @Query("apiKey") apiKey: String,
        @Query("amount") amount: Int = 100,
        @Query("unit") unit: String = "grams"
    ): Call<IngredientInfo>

    companion object {
        private const val BASE_URL = "https://api.spoonacular.com/"

        fun create(): ApiService {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(ApiService::class.java)
        }
    }
}
