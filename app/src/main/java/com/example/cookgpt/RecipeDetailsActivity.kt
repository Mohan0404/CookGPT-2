package com.example.cookgpt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.cookgpt.data.*

import com.google.android.material.button.MaterialButton
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

    private lateinit var cvNutrition:    com.google.android.material.card.MaterialCardView
    private lateinit var tvCalories:     TextView
    private lateinit var tvProtein:      TextView
    private lateinit var tvCarbs:        TextView
    private lateinit var tvPrepTime:     TextView
    private lateinit var tvServings:     TextView

    private val apiService = ApiService.create()
    private lateinit var dbHelper: RecipeDatabaseHelper
    private var currentRecipe: RecipeDetail? = null
    private var isSaved = false
    private var pendingRecipeToNotify: SavedRecipe? = null

    companion object {
        private const val SMS_PERMISSION_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_details)

        ivDetailImage     = findViewById(R.id.ivDetailImage)
        tvDetailTitle     = findViewById(R.id.tvDetailTitle)
        tvIngredients     = findViewById(R.id.tvIngredients)
        tvInstructions    = findViewById(R.id.tvInstructions)
        btnSaveRecipe     = findViewById(R.id.btnSaveRecipe)
        progressBar       = findViewById(R.id.progressBarDetail)

        cvNutrition       = findViewById(R.id.cvNutrition)
        tvCalories        = findViewById(R.id.tvCalories)
        tvProtein         = findViewById(R.id.tvProtein)
        tvCarbs           = findViewById(R.id.tvCarbs)
        tvPrepTime        = findViewById(R.id.tvPrepTime)
        tvServings        = findViewById(R.id.tvServings)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationIcon(R.drawable.ic_arrow_back)
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        dbHelper = RecipeDatabaseHelper(this)

        val recipe = intent.getSerializableExtra("RECIPE") as? Recipe
        if (recipe == null) {
            finish()
            return
        }

        checkIfSaved(recipe.id)
        loadAllData(recipe.id) // FIX: Consolidated data loading

        btnSaveRecipe.setOnClickListener { toggleSaveStatus() }
    }

    private fun loadAllData(recipeId: Int) {
        progressBar.visibility = View.VISIBLE
        // FIX: Sequential fetch ensures currentRecipe has instructions before save is possible
        fetchRecipeDetails(recipeId) {
            fetchRecipeInstructions(recipeId)
        }
    }

    private fun checkIfSaved(id: Int) {
        val userId = SessionManager.getUserId(this)
        if (userId.isNotEmpty()) {
            isSaved = dbHelper.isRecipeSaved(id, userId)
        }
        updateSaveButtonUI()
    }

    private fun updateSaveButtonUI() {
        btnSaveRecipe.text = if (isSaved) "Saved ✅" else "Save Recipe ❤️"
    }

    private fun toggleSaveStatus() {
        val recipe = currentRecipe ?: return
        lifecycleScope.launch {
            val userId = SessionManager.getUserId(this@RecipeDetailsActivity)
            if (userId.isEmpty()) {
                Toast.makeText(this@RecipeDetailsActivity, "Please login", Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (isSaved) {
                dbHelper.deleteRecipe(recipe.id, userId)
                removeFromFirebase(userId, recipe.id)
                isSaved = false
                Toast.makeText(this@RecipeDetailsActivity, "Removed", Toast.LENGTH_SHORT).show()
            } else {
                val ingredientsString = recipe.extendedIngredients.joinToString("\n") { "• ${it.original}" }
                // FIX: currentRecipe now guaranteed to have instructions from sequential fetch
                val savedRecipe = SavedRecipe(
                    recipe.id,
                    recipe.title,
                    recipe.image,
                    ingredientsString,
                    tvInstructions.text.toString() // FIX: Use the actual formatted text from UI
                )
                
                dbHelper.insertRecipe(savedRecipe, userId)
                saveToFirebase(userId, savedRecipe)
                isSaved = true
                Toast.makeText(this@RecipeDetailsActivity, "Saved ✅", Toast.LENGTH_SHORT).show()
                
                pendingRecipeToNotify = savedRecipe
                checkSmsPermissionAndNotify()
            }
            updateSaveButtonUI()
        }
    }

    private fun fetchRecipeDetails(id: Int, onComplete: () -> Unit) {
        // // FIX: Removed Constants.API_KEY as it is now handled by the interceptor
        apiService.getRecipeInformation(id).enqueue(object : Callback<RecipeDetail> {
            override fun onResponse(call: Call<RecipeDetail>, response: Response<RecipeDetail>) {
                if (response.isSuccessful) {
                    currentRecipe = response.body()
                    displayRecipe(currentRecipe)
                    onComplete()
                } else {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@RecipeDetailsActivity, "Details Error", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<RecipeDetail>, t: Throwable) {
                progressBar.visibility = View.GONE
            }
        })
    }

    private fun fetchRecipeInstructions(id: Int) {
        // // FIX: Removed Constants.API_KEY as it is now handled by the interceptor
        apiService.getAnalyzedInstructions(id).enqueue(object : Callback<List<AnalyzedInstruction>> {
            override fun onResponse(call: Call<List<AnalyzedInstruction>>, response: Response<List<AnalyzedInstruction>>) {
                progressBar.visibility = View.GONE
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    val stepsText = response.body()!![0].steps.joinToString("\n") { "${it.number}. ${it.step}" }
                    tvInstructions.text = stepsText
                } else {
                    tvInstructions.text = "No instructions available."
                }
            }
            override fun onFailure(call: Call<List<AnalyzedInstruction>>, t: Throwable) {
                progressBar.visibility = View.GONE
            }
        })
    }
    
    private fun displayRecipe(recipe: RecipeDetail?) {
        recipe?.let {
            tvDetailTitle.text = it.title
            tvIngredients.text = it.extendedIngredients.joinToString("\n") { ing -> "• ${ing.original}" }
            
            it.nutrition?.nutrients?.let { nutrients ->
                cvNutrition.visibility = View.VISIBLE
                tvCalories.text = nutrients.find { n -> n.name == "Calories" }?.let { "${it.amount.roundToInt()}kcal" } ?: "—"
                tvProtein.text = nutrients.find { n -> n.name == "Protein" }?.let { "${it.amount.roundToInt()}g" } ?: "—"
                tvCarbs.text = nutrients.find { n -> n.name == "Carbohydrates" }?.let { "${it.amount.roundToInt()}g" } ?: "—"
            }
            
            tvPrepTime.text = "${it.readyInMinutes} mins"
            tvServings.text = "${it.servings} servings"

            Glide.with(this).load(it.image).placeholder(R.drawable.ic_chef_hat).into(ivDetailImage)
        }
    }

    private fun checkSmsPermissionAndNotify() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_CODE)
        } else {
            pendingRecipeToNotify?.let { lifecycleScope.launch { sendRecipeNotifications(it) } }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE && grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {
            pendingRecipeToNotify?.let { lifecycleScope.launch { sendRecipeNotifications(it) } }
        }
    }

    private suspend fun getTargetPhoneNumber(): String {
        val db = AppDatabase.getDatabase(this)
        val user = withContext(Dispatchers.IO) { db.userDao().getUser() }
        val sp = getSharedPreferences("user_data", Context.MODE_PRIVATE).getString("phone", "") ?: ""
        return user?.phoneNumber ?: sp
    }

    private suspend fun sendRecipeNotifications(recipe: SavedRecipe) {
        val phoneNumber = getTargetPhoneNumber()
        if (phoneNumber.isEmpty()) return
        
        val message = "Recipe: ${recipe.title}\nIngredients: ${recipe.ingredients.take(100)}..."
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                this.getSystemService(SmsManager::class.java)
            } else { SmsManager.getDefault() }
            smsManager?.sendMultipartTextMessage(phoneNumber, null, smsManager.divideMessage(message), null, null)
        } catch (e: Exception) { Log.e("SMS", "Failed", e) }
        
        withContext(Dispatchers.Main) { openWhatsApp(recipe, phoneNumber) }
    }

    private fun openWhatsApp(recipe: SavedRecipe, phone: String) {
        val url = "https://api.whatsapp.com/send?phone=${phone.replace(Regex("[^0-9]"), "")}&text=${Uri.encode("Recipe: ${recipe.title}")}"
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.whatsapp")) }
        catch (e: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun saveToFirebase(userId: String, recipe: SavedRecipe) {
        FirebaseDatabase.getInstance().reference.child("users").child(userId).child("saved_recipes").child(recipe.id.toString()).setValue(recipe)
    }

    private fun removeFromFirebase(userId: String, recipeId: Int) {
        FirebaseDatabase.getInstance().reference.child("users").child(userId).child("saved_recipes").child(recipeId.toString()).removeValue()
    }
}
