package com.example.cookgpt

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.cookgpt.data.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class NutritionAnalysisActivity : AppCompatActivity() {

    private val apiService = ApiService.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_nutrition_analysis)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nutrition_analysis_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Onboarding navigation — unchanged
        findViewById<Button>(R.id.btn_next).setOnClickListener {
            startActivity(Intent(this, GroceryShoppingActivity::class.java))
        }
        findViewById<TextView>(R.id.btn_skip).setOnClickListener {
            proceedToNext()
        }

        // TASK 11: New nutrition lookup section
        val etFood       = findViewById<EditText>(R.id.et_food_search)
        val btnSearch    = findViewById<Button>(R.id.btn_search_nutrition)
        val progressBar  = findViewById<ProgressBar>(R.id.pb_nutrition)
        val cardResult   = findViewById<View>(R.id.card_nutrition_result)
        val ivFood       = findViewById<android.widget.ImageView>(R.id.iv_food_image)
        val tvFoodName   = findViewById<TextView>(R.id.tv_food_name)
        val tvCalories   = findViewById<TextView>(R.id.tv_calories)
        val tvProtein    = findViewById<TextView>(R.id.tv_protein)
        val tvCarbs      = findViewById<TextView>(R.id.tv_carbs)
        val tvFat        = findViewById<TextView>(R.id.tv_fat)
        val tvEmpty      = findViewById<TextView>(R.id.tv_nutrition_empty)

        btnSearch.setOnClickListener {
            val food = etFood.text.toString().trim()
            if (food.isEmpty()) {
                etFood.error = "Enter a food item"
                return@setOnClickListener
            }
            progressBar.visibility = View.VISIBLE
            cardResult.visibility  = View.GONE
            tvEmpty.visibility     = View.GONE

            // Step 1: Search for ingredient ID
            apiService.searchIngredient(food, Constants.API_KEY).enqueue(object : Callback<IngredientSearchResponse> {
                override fun onResponse(call: Call<IngredientSearchResponse>, response: Response<IngredientSearchResponse>) {
                    val result = response.body()?.results?.firstOrNull()
                    if (result == null) {
                        progressBar.visibility = View.GONE
                        tvEmpty.text = "No results found for \"$food\""
                        tvEmpty.visibility = View.VISIBLE
                        Log.d("Nutrition", "No ingredient found for: $food")
                        return
                    }
                    Log.d("Nutrition", "Found ingredient: ${result.name} id=${result.id}")

                    // Step 2: Get nutritional info per 100g
                    apiService.getIngredientInfo(result.id, Constants.API_KEY)
                        .enqueue(object : Callback<IngredientInfo> {
                            override fun onResponse(call: Call<IngredientInfo>, response: Response<IngredientInfo>) {
                                progressBar.visibility = View.GONE
                                val info = response.body()
                                if (info == null) {
                                    tvEmpty.text = "Could not fetch nutrition data"
                                    tvEmpty.visibility = View.VISIBLE
                                    return
                                }

                                val nutrients = info.nutrition?.nutrients ?: emptyList()
                                fun nutrient(name: String) = nutrients
                                    .firstOrNull { it.name.equals(name, ignoreCase = true) }
                                    ?.let { "${String.format("%.1f", it.amount)} ${it.unit}" } ?: "—"

                                tvFoodName.text = info.name.replaceFirstChar { it.uppercase() }
                                tvCalories.text = "Calories: ${nutrient("Calories")}"
                                tvProtein.text  = "Protein: ${nutrient("Protein")}"
                                tvCarbs.text    = "Carbs: ${nutrient("Carbohydrates")}"
                                tvFat.text      = "Fat: ${nutrient("Fat")}"

                                Glide.with(this@NutritionAnalysisActivity)
                                    .load("https://spoonacular.com/cdn/ingredients_100x100/${info.image}")
                                    .placeholder(R.drawable.ic_chef_hat)
                                    .into(ivFood)

                                cardResult.visibility = View.VISIBLE
                                Log.d("Nutrition", "Nutrients loaded for ${info.name}")
                            }

                            override fun onFailure(call: Call<IngredientInfo>, t: Throwable) {
                                progressBar.visibility = View.GONE
                                tvEmpty.text = "Network error — check connection"
                                tvEmpty.visibility = View.VISIBLE
                                Log.e("Nutrition", "Info fetch failed: ${t.localizedMessage}")
                            }
                        })
                }

                override fun onFailure(call: Call<IngredientSearchResponse>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    tvEmpty.text = "Network error — check connection"
                    tvEmpty.visibility = View.VISIBLE
                    Log.e("Nutrition", "Search failed: ${t.localizedMessage}")
                }
            })
        }
    }

    private fun proceedToNext() {
        val intent = if (SessionManager.isLoggedIn(this)) {
            Intent(this, HealthProfileActivity::class.java)
        } else {
            Intent(this, RegisterActivity::class.java)
        }
        startActivity(intent)
        finish()
    }
}
