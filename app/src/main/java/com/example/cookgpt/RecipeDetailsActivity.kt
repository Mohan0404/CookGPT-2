package com.example.cookgpt

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.cookgpt.data.*

import com.google.android.material.button.MaterialButton
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import kotlin.math.roundToInt

class RecipeDetailsActivity : AppCompatActivity() {


    private lateinit var ivDetailImage:      ImageView
    private lateinit var tvDetailTitle:      TextView
    private lateinit var tvIngredients:      TextView
    private lateinit var tvInstructions:     TextView
    private lateinit var btnSaveRecipe:      MaterialButton
    private lateinit var progressBar:        ProgressBar

    // Nutrition
    private lateinit var llNutritionRow: LinearLayout
    private lateinit var tvCalories:     TextView
    private lateinit var tvProtein:      TextView
    private lateinit var tvCarbs:        TextView

    private val apiService = ApiService.create()


    private val youtubeApiKey: String by lazy { BuildConfig.YOUTUBE_API_KEY }

    private lateinit var dbHelper: RecipeDatabaseHelper
    private var currentRecipe: RecipeDetail? = null
    private var isSaved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_details)


        ivDetailImage     = findViewById(R.id.ivDetailImage)
        tvDetailTitle     = findViewById(R.id.tvDetailTitle)
        tvIngredients     = findViewById(R.id.tvIngredients)
        tvInstructions    = findViewById(R.id.tvInstructions)
        btnSaveRecipe     = findViewById(R.id.btnSaveRecipe)
        progressBar       = findViewById(R.id.progressBarDetail)

        llNutritionRow    = findViewById(R.id.llNutritionRow)
        tvCalories        = findViewById(R.id.tvCalories)
        tvProtein         = findViewById(R.id.tvProtein)
        tvCarbs           = findViewById(R.id.tvCarbs)



        dbHelper = RecipeDatabaseHelper(this)

        val recipe = intent.getSerializableExtra("RECIPE") as? Recipe
        Log.d("Navigation", "RecipeDetailsActivity intent received. Data: $recipe")

        if (recipe == null) {
            Log.e("Navigation", "ERROR: Recipe object is null! Not finishing to show fallback UI.")
            // Keep empty fallback UI, don't finish
        } else {
            val recipeId = recipe.id
            checkIfSaved(recipeId)
            fetchRecipeDetails(recipeId)
            fetchRecipeInstructions(recipeId)
        }



        btnSaveRecipe.setOnClickListener { toggleSaveStatus() }
    }

    private fun checkIfSaved(id: Int) {
        val userId = SessionManager.getUserId(this)
        if (userId.isNotEmpty()) {
            isSaved = dbHelper.isRecipeSaved(id, userId)
        }
        updateSaveButtonUI()
    }

    private fun updateSaveButtonUI() {
        if (isSaved) {
            btnSaveRecipe.text = "Saved ✅"
            btnSaveRecipe.setIconResource(R.drawable.ic_saved)
        } else {
            btnSaveRecipe.text = "Save Recipe ❤️"
            btnSaveRecipe.setIconResource(R.drawable.ic_saved)
        }
    }

    private fun toggleSaveStatus() {
        currentRecipe?.let { recipe ->
            lifecycleScope.launch {
                val userId = SessionManager.getUserId(this@RecipeDetailsActivity)
                if (userId.isEmpty()) {
                    Toast.makeText(this@RecipeDetailsActivity, "Please login to save recipes", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (isSaved) {
                    dbHelper.deleteRecipe(recipe.id, userId)
                    removeFromFirebase(userId, recipe.id)
                    isSaved = false
                    Toast.makeText(this@RecipeDetailsActivity, "Removed from favourites", Toast.LENGTH_SHORT).show()
                } else {
                    val ingredientsString = recipe.extendedIngredients.joinToString("\n") { "• ${it.original}" }
                    val savedRecipe = SavedRecipe(
                        recipe.id,
                        recipe.title,
                        recipe.image,
                        ingredientsString,
                        recipe.instructions ?: ""
                    )
                    dbHelper.insertRecipe(savedRecipe, userId)
                    saveToFirebase(userId, savedRecipe)
                    isSaved = true
                    Toast.makeText(this@RecipeDetailsActivity, "Recipe Saved ✅", Toast.LENGTH_SHORT).show()
                }
                updateSaveButtonUI()
            }
        }
    }

    private fun saveToFirebase(userId: String, recipe: SavedRecipe) {
        try {
            FirebaseDatabase.getInstance().reference
                .child("users").child(userId)
                .child("saved_recipes").child(recipe.id.toString())
                .setValue(recipe)
        } catch (e: Exception) {
            Log.e("FIREBASE", "saveToFirebase exception", e)
        }
    }

    private fun removeFromFirebase(userId: String, recipeId: Int) {
        try {
            FirebaseDatabase.getInstance().reference
                .child("users").child(userId)
                .child("saved_recipes").child(recipeId.toString())
                .removeValue()
        } catch (e: Exception) {
            Log.e("FIREBASE", "removeFromFirebase exception", e)
        }
    }

    private fun fetchRecipeDetails(id: Int) {
        progressBar.visibility = View.VISIBLE
        apiService.getRecipeInformation(id, Constants.API_KEY).enqueue(object : Callback<RecipeDetail> {
            override fun onResponse(call: Call<RecipeDetail>, response: Response<RecipeDetail>) {
                progressBar.visibility = View.GONE
                if (response.isSuccessful) {
                    currentRecipe = response.body()
                    displayRecipe(currentRecipe)

                } else {
                    Toast.makeText(this@RecipeDetailsActivity, "Error loading details", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<RecipeDetail>, t: Throwable) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@RecipeDetailsActivity, "Network Error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchRecipeInstructions(id: Int) {
        apiService.getAnalyzedInstructions(id, Constants.API_KEY).enqueue(object : Callback<List<AnalyzedInstruction>> {
            override fun onResponse(call: Call<List<AnalyzedInstruction>>, response: Response<List<AnalyzedInstruction>>) {
                if (response.isSuccessful && response.body() != null) {
                    val instructions = response.body()!!
                    if (instructions.isNotEmpty() && instructions[0].steps.isNotEmpty()) {
                        val stepsText = instructions[0].steps.joinToString("\n") { "${it.number}. ${it.step}" }
                        tvInstructions.text = stepsText
                    } else {
                        tvInstructions.text = "No instructions available for this recipe."
                    }
                }
            }
            override fun onFailure(call: Call<List<AnalyzedInstruction>>, t: Throwable) {
                Log.e("Details", "Failed to load instructions", t)
            }
        })
    }
    
    // Video loading is handled by VideoPlayerManager + VideoRepository — no direct player code here.

    private fun displayRecipe(recipe: RecipeDetail?) {
        recipe?.let {
            tvDetailTitle.text = it.title
            tvIngredients.text = it.extendedIngredients.joinToString("\n") { ing -> "• ${ing.original}" }
            
            // Populate Nutrition
            it.nutrition?.nutrients?.let { nutrients ->
                llNutritionRow.visibility = View.VISIBLE
                val formatNutrient = { name: String ->
                    val nut = nutrients.find { n -> n.name.equals(name, ignoreCase = true) }
                    if (nut != null) "${nut.amount.roundToInt()}${nut.unit}" else "—"
                }

                tvCalories.text = formatNutrient("Calories")
                tvProtein.text = formatNutrient("Protein")
                tvCarbs.text = formatNutrient("Carbohydrates")
            }
            
            // Only load glide image if the youtube player isn't active
            Glide.with(this).load(it.image).into(ivDetailImage)
        }
    }
}
