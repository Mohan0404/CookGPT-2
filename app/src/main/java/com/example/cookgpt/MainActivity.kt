package com.example.cookgpt

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val prefs = UserPreferencesManager(this)

        lifecycleScope.launch {
            val isCompleted = prefs.isOnboardingCompleted.first()
            if (isCompleted) {
                startActivity(Intent(this@MainActivity, HomeActivity::class.java))
            } else {
                val currentStep = prefs.currentStep.first()
                val targetActivity = when (currentStep) {
                    2 -> RecipeDiscoveryActivity::class.java
                    3 -> NutritionAnalysisActivity::class.java
                    4 -> GroceryShoppingActivity::class.java
                    5 -> HealthProfileActivity::class.java
                    6 -> BodyMetricsActivity::class.java
                    7 -> FitnessGoalActivity::class.java
                    8 -> AllergiesRestrictionsActivity::class.java
                    else -> OnboardingActivity::class.java
                }
                startActivity(Intent(this@MainActivity, targetActivity))
            }
            finish()
        }
    }
}
