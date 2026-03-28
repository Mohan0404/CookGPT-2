package com.example.cookgpt

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.cookgpt.ui.MyRecipesActivity
import com.google.android.material.snackbar.Snackbar
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.cookgpt.data.RecipeDatabaseHelper
import com.example.cookgpt.data.SavedRecipe
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class HomeActivity : AppCompatActivity() {

    private lateinit var prefs: UserPreferencesManager
    private lateinit var tvGreeting: TextView
    private lateinit var dbHelper: RecipeDatabaseHelper

    // Real-time Firebase listener (attached in onStart, removed in onStop)
    private var recipesListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── IMPROVEMENT 3: Auth guard ────────────────────────────────────────
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null || !SessionManager.isLoggedIn(this)) {
            Log.w("HomeActivity", "Invalid session detected — redirecting to LoginActivity")
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }
        // ────────────────────────────────────────────────────────────────────

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

        // ── IMPROVEMENT 5: One-time migration of old /saved_recipes/{uid}/ data ──
        migrateOldRecipesIfNeeded(firebaseUser.uid)

        // ── Three-dot overflow menu (logout removed — only Settings has logout) ──
        val btnMore = findViewById<ImageView>(R.id.btn_more)
        btnMore.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.home_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_about_us -> {
                        startActivity(Intent(this, AboutUsActivity::class.java)); true
                    }
                    R.id.menu_team_details -> {
                        startActivity(Intent(this, TeamDetailsActivity::class.java)); true
                    }
                    R.id.menu_project_description -> {
                        startActivity(Intent(this, ProjectDescriptionActivity::class.java)); true
                    }
                    R.id.menu_test_notification -> {
                        sendTestNotification(); true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        findViewById<ImageView>(R.id.btn_profile).setOnClickListener {
            startActivity(Intent(this, RegistrationActivity::class.java).apply {
                putExtra("is_edit_mode", true)
            })
        }

        // Discover tab — opens DiscoverActivity (YouTube embedded search)
        findViewById<LinearLayout>(R.id.btn_discover_tab).setOnClickListener {
            startActivity(Intent(this, DiscoverActivity::class.java))
        }

        // Settings tab — opens SettingsActivity (logout is in there)
        findViewById<LinearLayout>(R.id.btn_settings_tab).setOnClickListener {
            showProgressAndNavigate()
        }

        // Feature card buttons
        findViewById<CardView>(R.id.btn_prep_reminder).setOnClickListener {
            startActivity(Intent(this, PrepReminderActivity::class.java))
        }
        findViewById<CardView>(R.id.btn_meal_calendar).setOnClickListener {
            startActivity(Intent(this, MealPlannerActivity::class.java))
        }
        findViewById<CardView>(R.id.btn_local_flavor).setOnClickListener {
            if (FirebaseAuth.getInstance().currentUser == null) {
                Snackbar.make(findViewById(android.R.id.content), "Please log in to create your own recipes.", Snackbar.LENGTH_LONG).show()
            } else {
                startActivity(Intent(this, MyRecipesActivity::class.java))
            }
        }
        findViewById<CardView>(R.id.btn_ingredient_finder).setOnClickListener {
            startActivity(Intent(this, IngredientFinderActivity::class.java))
        }
        findViewById<CardView>(R.id.btn_discover).setOnClickListener {
            startActivity(Intent(this, DiscoverRecipesActivity::class.java))
        }
        findViewById<CardView>(R.id.btn_saved).setOnClickListener {
            startActivity(Intent(this, SavedRecipesActivity::class.java))
        }
    }

    // ── IMPROVEMENT 1: Real-time Firebase listener ───────────────────────────
    override fun onStart() {
        super.onStart()
        val uid = SessionManager.getUserId(this)
        if (uid.isEmpty()) return

        val ref = FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("saved_recipes")

        recipesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("HomeActivity", "Recipe listener fired — ${snapshot.childrenCount} recipes")
                dbHelper.clearAllRecipes()
                for (child in snapshot.children) {
                    child.getValue(SavedRecipe::class.java)?.let { recipe ->
                        dbHelper.insertRecipe(recipe, uid)
                    }
                }
                // SavedRecipesActivity will auto-refresh when it resumes
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeActivity", "Recipe listener cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(recipesListener!!)
    }

    override fun onStop() {
        super.onStop()
        val uid = SessionManager.getUserId(this)
        if (uid.isNotEmpty()) {
            recipesListener?.let { listener ->
                FirebaseDatabase.getInstance().reference
                    .child("users").child(uid).child("saved_recipes")
                    .removeEventListener(listener)
            }
        }
        recipesListener = null
    }
    // ────────────────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        loadUserData()
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            val name = prefs.userName.first()
            tvGreeting.text = if (!name.isNullOrEmpty()) "Hello, $name! 👋" else "Hello! 👋"
        }
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

    // ── IMPROVEMENT 5: Migrate data from old /saved_recipes/{uid}/ Firebase path ──
    private fun migrateOldRecipesIfNeeded(uid: String) {
        val db = FirebaseDatabase.getInstance().reference
        db.child("saved_recipes").child(uid).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists() || !snapshot.hasChildren()) return@addOnSuccessListener

                Log.d("Migration", "Found ${snapshot.childrenCount} recipes at old path — migrating…")
                for (child in snapshot.children) {
                    val recipeId = child.key ?: continue
                    db.child("users").child(uid)
                        .child("saved_recipes").child(recipeId)
                        .setValue(child.value)
                        .addOnSuccessListener {
                            Log.d("Migration", "Migrated recipe $recipeId to new path")
                        }
                }
                // Delete old top-level /saved_recipes/{uid}/ node after migration
                db.child("saved_recipes").child(uid).removeValue()
                    .addOnSuccessListener {
                        Log.d("Migration", "Old /saved_recipes/$uid deleted successfully")
                    }
            }
            .addOnFailureListener { e ->
                Log.e("Migration", "Migration check failed: ${e.localizedMessage}")
            }
    }

    // ── Logout (called from SettingsActivity — kept here for HomeActivity popup consistency) ──
    private fun doLogout() {
        AlertDialog.Builder(this)
            .setTitle("Log out")
            .setMessage("Your data is saved to the cloud and will be restored when you log back in.")
            .setPositiveButton("Log out") { _, _ ->
                FirebaseAuth.getInstance().signOut()
                SessionManager.logout(this)
                dbHelper.clearAllRecipes()
                getSharedPreferences("user_data", Context.MODE_PRIVATE).edit().clear().apply()
                lifecycleScope.launch {
                    prefs.clearAllData()
                    val intent = Intent(this@HomeActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    Toast.makeText(this@HomeActivity, "Logged out", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun scheduleHourlyNotification() {
        val request = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "HourlyStatusNotification",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "COOKGPT_CHANNEL",
                "CookGPT Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Channel for status updates" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun sendTestNotification() {
        val builder = NotificationCompat.Builder(this, "COOKGPT_CHANNEL")
            .setSmallIcon(R.drawable.ic_chef_hat)
            .setContentTitle("CookGPT Update")
            .setContentText("This is a test notification from CookGPT!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
