package com.example.cookgpt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class HealthProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_health_profile)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.health_profile_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnMale = findViewById<TextView>(R.id.btn_male)
        val btnFemale = findViewById<TextView>(R.id.btn_female)
        val btnOther = findViewById<TextView>(R.id.btn_other)

        val genderButtons = listOf(btnMale, btnFemale, btnOther)

        genderButtons.forEach { button ->
            button.setOnClickListener {
                genderButtons.forEach { it.isSelected = false }
                button.isSelected = true
            }
        }

        findViewById<android.widget.ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_continue).setOnClickListener {
            // Navigating to Body Metrics (Step 2)
            val intent = Intent(this, BodyMetricsActivity::class.java)
            startActivity(intent)
        }
    }
}