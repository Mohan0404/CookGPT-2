package com.example.cookgpt

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        createNotificationChannel()
        scheduleHourlyNotification()

        val btnMore = findViewById<ImageView>(R.id.btn_more)
        btnMore.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.home_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_about_us -> {
                        startActivity(Intent(this, AboutUsActivity::class.java))
                        true
                    }
                    R.id.menu_team_details -> {
                        startActivity(Intent(this, TeamDetailsActivity::class.java))
                        true
                    }
                    R.id.menu_project_description -> {
                        startActivity(Intent(this, ProjectDescriptionActivity::class.java))
                        true
                    }
                    R.id.menu_test_notification -> {
                        sendTestNotification()
                        true
                    }
                    R.id.menu_logout -> {
                        showLogoutDialog()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        findViewById<LinearLayout>(R.id.btn_settings_tab).setOnClickListener {
            showProgressAndNavigate()
        }

        // Feature Buttons
        findViewById<CardView>(R.id.btn_prep_reminder).setOnClickListener {
            startActivity(Intent(this, PrepReminderActivity::class.java))
        }

        findViewById<CardView>(R.id.btn_meal_calendar).setOnClickListener {
            startActivity(Intent(this, MealPlannerActivity::class.java))
        }

        findViewById<CardView>(R.id.btn_local_flavor).setOnClickListener {
            startActivity(Intent(this, LocalFlavorActivity::class.java))
        }

        findViewById<CardView>(R.id.btn_ingredient_finder).setOnClickListener {
            startActivity(Intent(this, IngredientFinderActivity::class.java))
        }
        
        findViewById<CardView>(R.id.btn_discover).setOnClickListener {
            startActivity(Intent(this, RecipeDiscoveryActivity::class.java))
        }
        
        findViewById<CardView>(R.id.btn_saved).setOnClickListener {
            // startActivity(Intent(this, SavedRecipesActivity::class.java))
        }
    }

    private fun showProgressAndNavigate() {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Loading Settings...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        Handler(Looper.getMainLooper()).postDelayed({
            progressDialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }, 1500)
    }

    private fun scheduleHourlyNotification() {
        val notificationWorkRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
            1, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "HourlyStatusNotification",
            ExistingPeriodicWorkPolicy.KEEP,
            notificationWorkRequest
        )
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to exit the application?")
            .setPositiveButton("Yes") { _, _ ->
                finishAffinity()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "CookGPT Notifications"
            val descriptionText = "Channel for status updates"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("COOKGPT_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendTestNotification() {
        val builder = NotificationCompat.Builder(this, "COOKGPT_CHANNEL")
            .setSmallIcon(R.drawable.ic_chef_hat)
            .setContentTitle("CookGPT Update")
            .setContentText("This is a test notification from HealthSync Kitchen AI!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
