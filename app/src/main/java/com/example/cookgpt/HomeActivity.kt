package com.example.cookgpt

import android.app.NotificationChannel
import android.app.NotificationManager
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.gson.Gson
import com.example.cookgpt.data.ApiService
import com.example.cookgpt.data.RandomRecipeResponse
import com.example.cookgpt.data.RecipeDetail
import com.example.cookgpt.data.IngredientSearchResponse
import com.example.cookgpt.data.IngredientInfo
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.cookgpt.data.RecipeDatabaseHelper
import com.example.cookgpt.data.SavedRecipe
import com.example.cookgpt.news.NewsAdapter
import com.example.cookgpt.news.NewsArticle
import com.example.cookgpt.news.NewsResponse
import com.example.cookgpt.news.NewsRetrofitClient
import com.example.cookgpt.ui.MyRecipesActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.TimeUnit

class HomeActivity : AppCompatActivity() {

    private lateinit var prefs: UserPreferencesManager
    private lateinit var tvGreeting: TextView
    private lateinit var dbHelper: RecipeDatabaseHelper

    private var recipesListener: ValueEventListener? = null

    // REMOVED — news section moved to Discover tab

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null || !SessionManager.isLoggedIn(this)) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_home)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        prefs    = UserPreferencesManager(this)
        tvGreeting = findViewById(R.id.tv_greeting)
        dbHelper = RecipeDatabaseHelper(this)

        loadUserData()
        createNotificationChannel()
        scheduleHourlyNotification()
        migrateOldRecipesIfNeeded(firebaseUser.uid)
        
        setupAnimations()

        findViewById<ImageView>(R.id.btn_more).setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.home_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_about_us -> { startActivity(Intent(this, AboutUsActivity::class.java)); true }
                    R.id.menu_team_details -> { startActivity(Intent(this, TeamDetailsActivity::class.java)); true }
                    R.id.menu_project_description -> { startActivity(Intent(this, ProjectDescriptionActivity::class.java)); true }
                    R.id.menu_test_notification -> { sendTestNotification(); true }
                    else -> false
                }
            }
            popup.show()
        }

        // REMOVED: btn_profile click listener (spoon icon deleted from layout)

        // REMOVED: btn_rss_feed click listener (DailyChefFeed removed)

        findViewById<LinearLayout>(R.id.btn_discover_tab).setOnClickListener { startActivity(Intent(this, DiscoverActivity::class.java)) }
        findViewById<LinearLayout>(R.id.btn_shop_tab).setOnClickListener { startActivity(Intent(this, ShopActivity::class.java)) }
        findViewById<LinearLayout>(R.id.btn_wellness_tab).setOnClickListener { startActivity(Intent(this, WellnessActivity::class.java)) }
        findViewById<LinearLayout>(R.id.btn_settings_tab).setOnClickListener { showProgressAndNavigate() }

        // MODIFIED: AI Chef card — now opens GeminiChatActivity directly (no WiFi block)
        findViewById<CardView>(R.id.btn_ai_chef).setOnClickListener { startActivity(Intent(this, GeminiChatActivity::class.java)) }
        findViewById<CardView>(R.id.btn_prep_reminder).setOnClickListener { startActivity(Intent(this, PrepReminderActivity::class.java)) }
        findViewById<CardView>(R.id.btn_meal_calendar).setOnClickListener { startActivity(Intent(this, MealPlannerActivity::class.java)) }
        findViewById<CardView>(R.id.btn_local_flavor).setOnClickListener {
            if (FirebaseAuth.getInstance().currentUser == null) {
                Snackbar.make(findViewById(android.R.id.content), "Please log in.", Snackbar.LENGTH_LONG).show()
            } else {
                startActivity(Intent(this, MyRecipesActivity::class.java))
            }
        }
        findViewById<CardView>(R.id.btn_ingredient_finder).setOnClickListener { startActivity(Intent(this, IngredientFinderActivity::class.java)) }
        findViewById<CardView>(R.id.btn_discover).setOnClickListener { startActivity(Intent(this, DiscoverRecipesActivity::class.java)) }
        findViewById<CardView>(R.id.btn_saved).setOnClickListener { startActivity(Intent(this, SavedRecipesActivity::class.java)) }

        // REMOVED — setupNewsSection moved to Discover tab
        setupRecipeOfDay()
        setupMacroLoggerAndWater()
    }

    private fun setupAnimations() {
        val animation = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in)

        // MODIFIED: removed btn_rss_feed from animation list (card deleted in Phase 2)
        val cards = listOf<View>(
            findViewById(R.id.btn_ai_chef),
            findViewById<View>(R.id.btn_discover),
            findViewById<View>(R.id.btn_saved),
            findViewById<View>(R.id.btn_prep_reminder),
            findViewById<View>(R.id.btn_meal_calendar),
            findViewById<View>(R.id.btn_local_flavor),
            findViewById<View>(R.id.btn_ingredient_finder)
        )

        cards.forEachIndexed { index, view ->
            view.visibility = View.INVISIBLE
            Handler(Looper.getMainLooper()).postDelayed({
                view.visibility = View.VISIBLE
                view.startAnimation(animation)
            }, index * 100L)
        }
    }

    // REMOVED — News Section moved to Discover tab


    override fun onStart() {
        super.onStart()
        val uid = SessionManager.getUserId(this)
        if (uid.isEmpty()) return
        val ref = FirebaseDatabase.getInstance().reference.child("users").child(uid).child("saved_recipes")
        recipesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                dbHelper.clearAllRecipes()
                for (child in snapshot.children) {
                    child.getValue(SavedRecipe::class.java)?.let { dbHelper.insertRecipe(it, uid) }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(recipesListener!!)
    }

    override fun onStop() {
        super.onStop()
        val uid = SessionManager.getUserId(this)
        if (uid.isNotEmpty()) {
            recipesListener?.let { FirebaseDatabase.getInstance().reference.child("users").child(uid).child("saved_recipes").removeEventListener(it) }
        }
        recipesListener = null
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
        // Stop floating shop service if the user returns to Home Activity
        stopService(Intent(this, FloatingShopService::class.java))
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            val name = prefs.userName.first()
            tvGreeting.text = if (!name.isNullOrEmpty()) "Hello, $name! \uD83D\uDC4B" else "Hello! \uD83D\uDC4B"
        }
        // REMOVED — news loading moved to Discover tab
    }

    private fun showProgressAndNavigate() {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Loading Settings…")
        progressDialog.setCancelable(false)
        progressDialog.show()
        Handler(Looper.getMainLooper()).postDelayed({
            progressDialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }, 800)
    }

    private fun migrateOldRecipesIfNeeded(uid: String) {
        val db = FirebaseDatabase.getInstance().reference
        db.child("saved_recipes").child(uid).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists() || !snapshot.hasChildren()) return@addOnSuccessListener
            for (child in snapshot.children) {
                val recipeId = child.key ?: continue
                db.child("users").child(uid).child("saved_recipes").child(recipeId).setValue(child.value)
            }
            db.child("saved_recipes").child(uid).removeValue()
        }
    }

    private fun scheduleHourlyNotification() {
        val request = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("HourlyStatusNotification", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("COOKGPT_CHANNEL", "CookGPT Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun sendTestNotification() {
        val builder = NotificationCompat.Builder(this, "COOKGPT_CHANNEL")
            .setSmallIcon(R.drawable.ic_chef_hat)
            .setContentTitle("CookGPT Update")
            .setContentText("This is a test notification from CookGPT!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(System.currentTimeMillis().toInt(), builder.build())
    }

    // --- FEATURE A: Recipe of the Day ---
    private fun setupRecipeOfDay() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedDate = prefs.getString("recipe_of_day_date", null)
        val savedJson = prefs.getString("recipe_of_day_data", null)

        if (savedDate == today && savedJson != null) {
            val recipe = Gson().fromJson(savedJson, RecipeDetail::class.java)
            bindRecipeOfDay(recipe)
        } else {
            fetchRecipeOfDay(today)
        }
    }

    private fun fetchRecipeOfDay(today: String) {
        ApiService.create().getRandomRecipe(1).enqueue(object : Callback<RandomRecipeResponse> {
            override fun onResponse(call: Call<RandomRecipeResponse>, response: Response<RandomRecipeResponse>) {
                val recipe = response.body()?.recipes?.firstOrNull()
                if (recipe != null) {
                    getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                        .putString("recipe_of_day_date", today)
                        .putString("recipe_of_day_data", Gson().toJson(recipe))
                        .apply()
                    bindRecipeOfDay(recipe)
                }
            }
            override fun onFailure(call: Call<RandomRecipeResponse>, t: Throwable) {}
        })
    }

    private fun bindRecipeOfDay(recipe: RecipeDetail) {
        findViewById<TextView>(R.id.tvRodTitle).text = recipe.title
        findViewById<TextView>(R.id.tvRodCalories).text = "${recipe.nutrition?.nutrients?.find { it.name == "Calories" }?.amount?.toInt() ?: "--"} kcal"
        Glide.with(this).load(recipe.image).into(findViewById<ImageView>(R.id.ivRodImage))
        
        findViewById<CardView>(R.id.cardRecipeOfDay).setOnClickListener {
            val recipeObj = com.example.cookgpt.data.Recipe(id = recipe.id, title = recipe.title, image = recipe.image)
            val detailIntent = Intent(this, com.example.cookgpt.RecipeDetailsActivity::class.java)
            detailIntent.putExtra("RECIPE", recipeObj)
            startActivity(detailIntent)
        }
    }

    // --- FEATURE B & F: Macro Logger and Water Tracker ---
    private val waterGoal = 8
    private var currentWater = 0
    private var currentCals = 0
    private var currentPro = 0
    private var currentCarb = 0

    private fun setupMacroLoggerAndWater() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val uid = SessionManager.getUserId(this)
        if (uid.isEmpty()) return

        val ref = FirebaseDatabase.getInstance().reference.child("users").child(uid)

        // Load Water
        ref.child("water").child(today).get().addOnSuccessListener { snap ->
            currentWater = snap.getValue(Int::class.java) ?: 0
            updateWaterUI()
        }

        findViewById<ImageButton>(R.id.btnWaterPlus).setOnClickListener {
            if (currentWater < waterGoal) {
                currentWater++
                updateWaterUI()
                ref.child("water").child(today).setValue(currentWater)

                if (currentWater == waterGoal) {
                    Toast.makeText(
                        this,
                        "Great job! You've hit your water goal for today!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        findViewById<ImageButton>(R.id.btnWaterMinus).setOnClickListener {
            if (currentWater > 0) {
                currentWater--
                updateWaterUI()
                ref.child("water").child(today).setValue(currentWater)
            }
        }

        // Load Macros
        ref.child("macros").child(today).get().addOnSuccessListener { snap ->
            currentCals = snap.child("calories").getValue(Int::class.java) ?: 0
            currentPro = snap.child("protein").getValue(Int::class.java) ?: 0
            currentCarb = snap.child("carbs").getValue(Int::class.java) ?: 0
            updateMacrosUI()
        }

        findViewById<ImageButton>(R.id.btnAddMacro).setOnClickListener {
            showMacroDialog(today, ref)
        }
    }

    private fun updateWaterUI() {
        findViewById<TextView>(R.id.tvWaterCount).text = "$currentWater / 8 glasses"
    }

    private fun updateMacrosUI() {
        findViewById<ProgressBar>(R.id.pbCalories).progress = currentCals
        findViewById<TextView>(R.id.tvCaloriesValue).text = "$currentCals kcal"

        findViewById<ProgressBar>(R.id.pbProtein).progress = currentPro
        findViewById<TextView>(R.id.tvProteinValue).text = "${currentPro}g Pro"

        findViewById<ProgressBar>(R.id.pbCarbs).progress = currentCarb
        findViewById<TextView>(R.id.tvCarbsValue).text = "${currentCarb}g Carb"
    }

    private fun showMacroDialog(today: String, ref: com.google.firebase.database.DatabaseReference) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_macro_logger, null)
        dialog.setContentView(view)

        val etFood = view.findViewById<EditText>(R.id.etFoodName)
        val etQty = view.findViewById<EditText>(R.id.etQuantity)
        val etCals = view.findViewById<EditText>(R.id.etCalories)
        val etPro = view.findViewById<EditText>(R.id.etProtein)
        val etCarbs = view.findViewById<EditText>(R.id.etCarbs)
        val btnAdd = view.findViewById<Button>(R.id.btnAddLog)

        etFood.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && etFood.text.isNotEmpty()) {
                val query = etFood.text.toString()
                ApiService.create().searchIngredient(query).enqueue(object : Callback<IngredientSearchResponse> {
                    override fun onResponse(call: Call<IngredientSearchResponse>, response: Response<IngredientSearchResponse>) {
                        val id = response.body()?.results?.firstOrNull()?.id ?: return
                        ApiService.create().getIngredientInfo(id).enqueue(object : Callback<IngredientInfo> {
                            override fun onResponse(call: Call<IngredientInfo>, response: Response<IngredientInfo>) {
                                val info = response.body() ?: return
                                val nutrients = info.nutrition?.nutrients ?: return
                                val cals = nutrients.find { it.name.contains("Calories", true) }?.amount ?: 0.0
                                val pro = nutrients.find { it.name.contains("Protein", true) }?.amount ?: 0.0
                                val carb = nutrients.find { it.name.contains("Carbohydrates", true) }?.amount ?: 0.0
                                
                                val modifier = (etQty.text.toString().toDoubleOrNull() ?: 100.0) / 100.0
                                
                                runOnUiThread {
                                    etCals.setText((cals * modifier).toInt().toString())
                                    etPro.setText((pro * modifier).toInt().toString())
                                    etCarbs.setText((carb * modifier).toInt().toString())
                                }
                            }
                            override fun onFailure(call: Call<IngredientInfo>, t: Throwable) {}
                        })
                    }
                    override fun onFailure(call: Call<IngredientSearchResponse>, t: Throwable) {}
                })
            }
        }

        btnAdd.setOnClickListener {
            currentCals += etCals.text.toString().toIntOrNull() ?: 0
            currentPro += etPro.text.toString().toIntOrNull() ?: 0
            currentCarb += etCarbs.text.toString().toIntOrNull() ?: 0
            
            updateMacrosUI()
            
            val macrosMap = mapOf(
                "calories" to currentCals,
                "protein" to currentPro,
                "carbs" to currentCarb
            )
            ref.child("macros").child(today).setValue(macrosMap)
            dialog.dismiss()
        }

        dialog.show()
    }
}
