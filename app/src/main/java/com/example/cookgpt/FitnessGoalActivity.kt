package com.example.cookgpt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class FitnessGoalActivity : AppCompatActivity() {
    
    private var selectedGoal: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fitness_goal)

        val goalCards = mapOf(
            findViewById<View>(R.id.goal_weight_loss) to "Weight Loss",
            findViewById<View>(R.id.goal_muscle_gain) to "Muscle Gain",
            findViewById<View>(R.id.goal_maintain_health) to "Maintain Health",
            findViewById<View>(R.id.goal_more_energy) to "More Energy"
        )

        goalCards.keys.forEach { card ->
            card.setOnClickListener {
                goalCards.keys.forEach { it.isSelected = false }
                card.isSelected = true
                selectedGoal = goalCards[card] ?: ""
            }
        }

        findViewById<android.widget.ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Load existing data
        val prefs = getSharedPreferences("user_data", Context.MODE_PRIVATE)
        selectedGoal = prefs.getString("goal", "") ?: ""
        if (selectedGoal.isNotEmpty()) {
            goalCards.forEach { (view, goal) ->
                view.isSelected = goal == selectedGoal
            }
        }

        val isEditMode = intent.getStringExtra("mode") == "edit"

        findViewById<Button>(R.id.btn_continue).setOnClickListener {
            if (selectedGoal.isNotEmpty()) saveAndProceed(isEditMode)
            else Toast.makeText(this, "Please select a fitness goal", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAndProceed(editMode: Boolean = false) {
        val userId = SessionManager.getUserId(this)
        if (userId.isEmpty()) return

        getSharedPreferences("user_data", Context.MODE_PRIVATE).edit()
            .putString("goal", selectedGoal).apply()

        FirebaseDatabase.getInstance().reference
            .child("users").child(userId).child("goal")
            .setValue(selectedGoal)
            .addOnSuccessListener {
                if (editMode) finish()
                else { startActivity(Intent(this, AllergiesRestrictionsActivity::class.java)); finish() }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Save failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
}
