package com.example.cookgpt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class AllergiesRestrictionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_allergies_restrictions)

        val allergiesGrid = findViewById<GridLayout>(R.id.allergies_grid)
        val restrictionsGrid = findViewById<GridLayout>(R.id.restrictions_grid)

        setupTagGroup(allergiesGrid)
        setupTagGroup(restrictionsGrid)

        val isEditMode = intent.getStringExtra("mode") == "edit"

        findViewById<android.widget.ImageView>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<Button>(R.id.btn_complete).setOnClickListener {
            saveAndProceed(allergiesGrid, restrictionsGrid, isEditMode)
        }
    }

    private fun saveAndProceed(allergiesGrid: GridLayout, restrictionsGrid: GridLayout, editMode: Boolean = false) {
        val selectedAllergies    = getSelectedTags(allergiesGrid).joinToString(", ")
        val selectedRestrictions = getSelectedTags(restrictionsGrid).joinToString(", ")

        val userId = SessionManager.getUserId(this)
        if (userId.isEmpty()) return

        getSharedPreferences("user_data", Context.MODE_PRIVATE).edit()
            .putString("allergies", selectedAllergies)
            .putString("restrictions", selectedRestrictions)
            .apply()

        val updates = mapOf(
            "allergies" to selectedAllergies,
            "restrictions" to selectedRestrictions
        )

        FirebaseDatabase.getInstance().reference
            .child("users").child(userId)
            .updateChildren(updates)
            .addOnSuccessListener {
                if (editMode) {
                    finish()
                } else {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finishAffinity()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Save failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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

    private fun getSelectedTags(grid: GridLayout): Set<String> {
        val selected = mutableSetOf<String>()
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            if (child is TextView && child.isSelected) {
                selected.add(child.text.toString())
            }
        }
        return selected
    }
}
