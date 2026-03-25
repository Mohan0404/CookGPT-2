package com.example.cookgpt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class RecipeDiscoveryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_recipe_discovery)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.recipe_discovery_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.btn_next).setOnClickListener {
            val intent = Intent(this, NutritionAnalysisActivity::class.java)
            startActivity(intent)
        }

        findViewById<TextView>(R.id.btn_skip).setOnClickListener {
            val intent = Intent(this, HealthProfileActivity::class.java)
            startActivity(intent)
            finishAffinity()
        }
    }
}