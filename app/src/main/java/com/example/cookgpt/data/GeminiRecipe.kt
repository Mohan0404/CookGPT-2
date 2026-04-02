package com.example.cookgpt.data

import com.google.gson.annotations.SerializedName

data class GeminiRecipe(
    @SerializedName("title") val title: String,
    @SerializedName("ingredients") val ingredients: List<String>,
    @SerializedName("steps") val steps: List<String>,
    @SerializedName("nutrition") val nutrition: NutritionInfo? = null
)

data class NutritionInfo(
    @SerializedName("calories") val calories: String,
    @SerializedName("protein") val protein: String,
    @SerializedName("carbs") val carbs: String,
    @SerializedName("fat") val fat: String
)
