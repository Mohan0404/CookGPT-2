package com.example.cookgpt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.onboarding_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val prefs = UserPreferencesManager(this)

        findViewById<Button>(R.id.btn_next).setOnClickListener {
            lifecycleScope.launch {
                prefs.saveCurrentStep(2)
                val intent = Intent(this@OnboardingActivity, RecipeDiscoveryActivity::class.java)
                startActivity(intent)
            }
        }

        findViewById<TextView>(R.id.btn_skip).setOnClickListener {
            lifecycleScope.launch {
                prefs.saveCurrentStep(5)
                val intent = Intent(this@OnboardingActivity, HealthProfileActivity::class.java)
                startActivity(intent)
            }
        }
    }
}
