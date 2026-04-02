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

    // Nutrition
    private lateinit var llNutritionRow: LinearLayout
    private lateinit var tvCalories:     TextView
    private lateinit var tvProtein:      TextView
    private lateinit var tvCarbs:        TextView

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

        llNutritionRow    = findViewById(R.id.llNutritionRow)
        tvCalories        = findViewById(R.id.tvCalories)
        tvProtein         = findViewById(R.id.tvProtein)
        tvCarbs           = findViewById(R.id.tvCarbs)



        dbHelper = RecipeDatabaseHelper(this)

        val recipe = intent.getSerializableExtra("RECIPE") as? Recipe
        Log.d("Navigation", "RecipeDetailsActivity intent received. Data: $recipe")

        if (recipe == null) {
            Log.e("Navigation", "ERROR: Recipe object is null! Not finishing to show fallback UI.")
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
                    
                    // Request permission and then send notification
                    pendingRecipeToNotify = savedRecipe
                    checkSmsPermissionAndNotify()
                }
                updateSaveButtonUI()
            }
        }
    }

    private fun checkSmsPermissionAndNotify() {
        Log.d("RecipeDetails", "checkSmsPermissionAndNotify triggered")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.d("RecipeDetails", "SMS permission NOT granted, requesting now...")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_CODE)
        } else {
            Log.d("RecipeDetails", "SMS permission already GRANTED")
            pendingRecipeToNotify?.let {
                lifecycleScope.launch { sendRecipeNotifications(it) }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("RecipeDetails", "SMS permission GRANTED by user")
                pendingRecipeToNotify?.let {
                    lifecycleScope.launch { sendRecipeNotifications(it) }
                }
            } else {
                Log.d("RecipeDetails", "SMS permission DENIED by user")
                Toast.makeText(this, "SMS permission denied. Falling back to WhatsApp.", Toast.LENGTH_SHORT).show()
                pendingRecipeToNotify?.let {
                    lifecycleScope.launch { openWhatsApp(it) }
                }
            }
        }
    }

    private suspend fun getTargetPhoneNumber(): String {
        val db = AppDatabase.getDatabase(this)
        val user = withContext(Dispatchers.IO) { db.userDao().getUser() }
        val sqlitePhone = user?.phoneNumber ?: ""
        
        val spPhone = getSharedPreferences("user_data", Context.MODE_PRIVATE).getString("phone", "") ?: ""
        
        val dsPhone = try {
            UserPreferencesManager(this).userData.first().phoneNumber
        } catch (e: Exception) { "" }
        
        val result = sqlitePhone.ifEmpty { spPhone.ifEmpty { dsPhone } }
        Log.d("RecipeDetails", "Phone Lookup Results: Room=$sqlitePhone, SP=$spPhone, DataStore=$dsPhone -> Final=$result")
        return result
    }

    private suspend fun sendRecipeNotifications(recipe: SavedRecipe) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@RecipeDetailsActivity, "Preparing notifications...", Toast.LENGTH_SHORT).show()
        }
        
        val phoneNumber = getTargetPhoneNumber()

        if (phoneNumber.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@RecipeDetailsActivity, "No phone number found in profile.", Toast.LENGTH_LONG).show()
            }
            return
        }

        val message = "Check out this recipe I found on CookGPT: ${recipe.title}\n\nIngredients:\n${recipe.ingredients.take(120)}...\n\nFull Recipe in CookGPT App!"

        // 1. Send SMS
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                this.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            if (smsManager != null) {
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecipeDetailsActivity, "SMS sent to $phoneNumber", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("RecipeDetails", "SMS failed", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@RecipeDetailsActivity, "SMS failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Open WhatsApp
        withContext(Dispatchers.Main) {
            openWhatsApp(recipe, phoneNumber)
        }
    }

    private fun openWhatsApp(recipe: SavedRecipe, existingPhone: String? = null) {
        lifecycleScope.launch {
            val phoneNumber = existingPhone ?: getTargetPhoneNumber()
            
            if (phoneNumber.isNotEmpty()) {
                val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
                val message = "Check out this recipe: ${recipe.title}\n\nIngredients:\n${recipe.ingredients.take(120)}...\n\nImage: ${recipe.image}"
                try {
                    val intent = Intent(Intent.ACTION_VIEW)
                    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}")
                    intent.data = uri
                    intent.setPackage("com.whatsapp")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("WhatsApp", "Package check failed, trying browser-based wa.me", e)
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanPhone?text=${Uri.encode(message)}"))
                        startActivity(browserIntent)
                    } catch (e2: Exception) {
                        Toast.makeText(this@RecipeDetailsActivity, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this@RecipeDetailsActivity, "No phone number found for WhatsApp", Toast.LENGTH_SHORT).show()
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
            
            Glide.with(this).load(it.image).into(ivDetailImage)
        }
    }
}
