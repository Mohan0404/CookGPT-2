package com.example.cookgpt

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AllergiesRestrictionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_allergies_restrictions)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.allergies_restrictions_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupTagGroup(findViewById(R.id.allergies_grid))
        setupTagGroup(findViewById(R.id.restrictions_grid))

        findViewById<android.widget.ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_complete).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finishAffinity() // Clear the setup stack as we are now in the main app
        }
    }

    private fun setupTagGroup(grid: GridLayout) {
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            if (child is TextView) {
                child.setOnClickListener {
                    child.isSelected = !child.isSelected
                }
            }
        }
    }
}