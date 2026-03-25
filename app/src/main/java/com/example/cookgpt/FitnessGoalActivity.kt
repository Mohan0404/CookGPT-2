package com.example.cookgpt

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class FitnessGoalActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_fitness_goal)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fitness_goal_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val goalCards = listOf(
            findViewById<View>(R.id.goal_weight_loss),
            findViewById<View>(R.id.goal_muscle_gain),
            findViewById<View>(R.id.goal_maintain_health),
            findViewById<View>(R.id.goal_more_energy)
        )

        goalCards.forEach { card ->
            card.setOnClickListener {
                goalCards.forEach { it.isSelected = false }
                card.isSelected = true
            }
        }

        findViewById<android.widget.ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_continue).setOnClickListener {
            val intent = Intent(this, AllergiesRestrictionsActivity::class.java)
            startActivity(intent)
        }
    }
}