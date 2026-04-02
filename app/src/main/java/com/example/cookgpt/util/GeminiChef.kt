package com.example.cookgpt.util

import android.util.Log
import com.example.cookgpt.BuildConfig
import com.example.cookgpt.UserData
import com.example.cookgpt.data.GeminiRecipe
import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiChef {

    // Using gemini-pro which is the most stable/compatible for current v1beta endpoints
    private val generativeModel = GenerativeModel(
        modelName = "gemini-pro",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    suspend fun generateRecipe(userData: UserData, mealType: String): GeminiRecipe? = withContext(Dispatchers.IO) {
        val key = BuildConfig.GEMINI_API_KEY
        
        if (key.isNullOrEmpty() || key == "null") {
            Log.e("GeminiChef", "CRITICAL ERROR: API Key is missing. Sync Gradle with the elephant icon.")
            return@withContext null
        }

        val prompt = """
            Generate a personalized ${userData.fitnessGoal} recipe for $mealType.
            User Profile:
            - Age: ${userData.age}
            - Weight: ${userData.weight}kg
            - Height: ${userData.height}cm
            - Allergies: ${userData.allergies.joinToString()}
            - Dietary Restrictions: ${userData.restrictions.joinToString()}
            
            Provide the response strictly in JSON format with the following structure:
            {
              "title": "Recipe Name",
              "ingredients": ["ingredient 1", "ingredient 2"],
              "steps": ["step 1", "step 2"],
              "nutrition": {
                "calories": "xxx kcal",
                "protein": "xx g",
                "carbs": "xx g",
                "fat": "xx g"
              }
            }
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            val text = response.text ?: return@withContext null
            
            // Extract JSON from markdown blocks if present
            val jsonString = text.replace("```json", "").replace("```", "").trim()
            
            return@withContext Gson().fromJson(jsonString, GeminiRecipe::class.java)
        } catch (e: Exception) {
            Log.e("GeminiChef", "Generation Error: ${e.message}")
            
            // Try fallback to gemini-1.5-flash-latest if gemini-pro is not supported
            if (e.message?.contains("404") == true) {
                return@withContext tryFlashLatest(key, userData, mealType)
            }
            return@withContext null
        }
    }

    private suspend fun tryFlashLatest(key: String, userData: UserData, mealType: String): GeminiRecipe? {
        return try {
            val fallbackModel = GenerativeModel(modelName = "gemini-1.5-flash-latest", apiKey = key)
            val prompt = "Generate a $mealType recipe for someone with ${userData.fitnessGoal}. Return JSON."
            val response = fallbackModel.generateContent(prompt)
            val text = response.text ?: return null
            val jsonString = text.replace("```json", "").replace("```", "").trim()
            Gson().fromJson(jsonString, GeminiRecipe::class.java)
        } catch (e: Exception) {
            Log.e("GeminiChef", "Fallback also failed: ${e.message}")
            null
        }
    }
}
