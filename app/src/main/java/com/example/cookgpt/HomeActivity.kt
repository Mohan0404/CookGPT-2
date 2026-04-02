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
import android.view.View
import android.view.animation.AnimationUtils
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

    private var recipesListener: ValueEventListener? = null

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

        findViewById<ImageView>(R.id.btn_profile).setOnClickListener {
            startActivity(Intent(this, RegistrationActivity::class.java).apply { putExtra("is_edit_mode", true) })
        }

        findViewById<LinearLayout>(R.id.btn_discover_tab).setOnClickListener { startActivity(Intent(this, DiscoverActivity::class.java)) }
        findViewById<LinearLayout>(R.id.btn_settings_tab).setOnClickListener { showProgressAndNavigate() }

        findViewById<CardView>(R.id.btn_ai_chef).setOnClickListener { startActivity(Intent(this, AiChefActivity::class.java)) }
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
    }

    private fun setupAnimations() {
        val animation = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in)
        
        val cards = listOf<View>(
            findViewById(R.id.btn_ai_chef),
            findViewById<View>(R.id.btn_discover).parent as View, // Handling the NestedScrollView structure
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
            }, index * 100L) // Staggered delay for each card
        }
    }

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
}
