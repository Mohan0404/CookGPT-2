package com.example.cookgpt

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.cookgpt.AppDatabase
import com.example.cookgpt.SessionManager
import com.example.cookgpt.data.RecipeDatabaseHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null || !SessionManager.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, 0, sys.right, sys.bottom)
            insets
        }

        findViewById<android.widget.ImageView>(R.id.btn_back).setOnClickListener { finish() }

        val switchDarkMode = findViewById<SwitchCompat>(R.id.switch_dark_mode)
        switchDarkMode.isChecked = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        switchDarkMode.setOnCheckedChangeListener { _, checked ->
            AppCompatDelegate.setDefaultNightMode(if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }

        findViewById<Button>(R.id.btn_edit_profile).setOnClickListener {
            startActivity(Intent(this, RegistrationActivity::class.java).apply {
                putExtra("is_edit_mode", true)
            })
        }

        findViewById<Button>(R.id.btn_edit_metrics).setOnClickListener { startActivity(Intent(this, BodyMetricsActivity::class.java).putExtra("mode", "edit")) }
        findViewById<Button>(R.id.btn_edit_goal).setOnClickListener { startActivity(Intent(this, FitnessGoalActivity::class.java).putExtra("mode", "edit")) }
        findViewById<Button>(R.id.btn_edit_allergies).setOnClickListener { startActivity(Intent(this, AllergiesRestrictionsActivity::class.java).putExtra("mode", "edit")) }


        // Instagram Click Logic
        findViewById<LinearLayout>(R.id.layout_instagram).setOnClickListener {
            val instaUri = Uri.parse("https://www.instagram.com/_.mohan0404._/")
            val intent = Intent(Intent.ACTION_VIEW, instaUri)
            intent.setPackage("com.instagram.android")
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, instaUri))
            }
        }

        bindUserData()

        findViewById<Button>(R.id.btn_logout).setOnClickListener { performLogout() }
    }

    override fun onResume() {
        super.onResume()
        bindUserData()
    }

    private fun bindUserData() {
        val prefs = getSharedPreferences("user_data", Context.MODE_PRIVATE)
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val db = AppDatabase.getDatabase(this)

        val tvUsername     = findViewById<TextView>(R.id.tv_user_name_display)
        val tvName         = findViewById<TextView>(R.id.tv_user_metrics)
        val tvValWeight    = findViewById<TextView>(R.id.tv_val_weight)
        val tvValHeight    = findViewById<TextView>(R.id.tv_val_height)
        val tvValBmi       = findViewById<TextView>(R.id.tv_val_bmi)
        val tvValBmiStatus = findViewById<TextView>(R.id.tv_val_bmi_status)
        val llBmiContainer = findViewById<LinearLayout>(R.id.ll_bmi_container)
        val tvGoal         = findViewById<TextView>(R.id.tv_val_goal)
        val tvAllergies    = findViewById<TextView>(R.id.tv_val_allergies)
        val tvRestrictions = findViewById<TextView>(R.id.tv_val_restrictions)

        tvUsername.text = firebaseUser?.email ?: prefs.getString("email", "") ?: "User"

        lifecycleScope.launch {
            val user = db.userDao().getUser()
            if (user != null) {
                tvName.text = "${user.name} | ${user.age} yrs | ${user.gender}"
            } else {
                val name   = prefs.getString("name", "—")
                val age    = prefs.getString("age",  "—")
                val gender = prefs.getString("gender","—")
                tvName.text = "$name | $age yrs | $gender"
            }
        }

        val weightStr = prefs.getString("weight", "0") ?: "0"
        val heightStr = prefs.getString("height", "0") ?: "0"
        val weight = weightStr.toFloatOrNull() ?: 0f
        val height = heightStr.toFloatOrNull() ?: 0f

        tvValWeight.text = "$weightStr kg"
        tvValHeight.text = "$heightStr cm"

        if (weight > 0 && height > 0) {
            val bmi = weight / (height / 100).pow(2)
            val roundedBmi = (bmi * 10).roundToInt() / 10.0
            tvValBmi.text = roundedBmi.toString()
            
            val status = when {
                bmi < 18.5f -> "(Underweight)"
                bmi < 25.0f -> "(Normal Weight)"
                bmi < 30.0f -> "(Overweight)"
                else -> "(Obese)"
            }
            tvValBmiStatus.text = status
            llBmiContainer.visibility = View.VISIBLE
        } else {
            llBmiContainer.visibility = View.GONE
        }

        tvGoal.text         = prefs.getString("goal", "—") ?: "—"
        tvAllergies.text    = prefs.getString("allergies","None") ?: "None"
        tvRestrictions.text = prefs.getString("restrictions","None") ?: "None"
    }

    private fun performLogout() {
        AlertDialog.Builder(this).setTitle("Log out").setMessage("Logout?").setPositiveButton("Log out") { _, _ -> doLogout() }.setNegativeButton("Cancel", null).show()
    }

    private fun doLogout() {
        FirebaseAuth.getInstance().signOut()
        SessionManager.logout(this)
        RecipeDatabaseHelper(this).clearAllRecipes()
        getSharedPreferences("user_data", Context.MODE_PRIVATE).edit().clear().apply()
        startActivity(Intent(this, LoginActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finish()
    }
}
