package com.example.cookgpt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class HealthProfileActivity : AppCompatActivity() {

    private var selectedGender: String = ""
    private lateinit var etName: EditText
    private lateinit var etAge: EditText
    private lateinit var btnMale: TextView
    private lateinit var btnFemale: TextView
    private lateinit var btnOther: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_profile)

        etName = findViewById(R.id.et_name)
        etAge = findViewById(R.id.et_age)
        btnMale = findViewById(R.id.btn_male)
        btnFemale = findViewById(R.id.btn_female)
        btnOther = findViewById(R.id.btn_other)

        val genderButtons = listOf(btnMale, btnFemale, btnOther)

        genderButtons.forEach { button ->
            button.setOnClickListener {
                genderButtons.forEach { it.isSelected = false }
                button.isSelected = true
                selectedGender = button.text.toString()
            }
        }

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Load existing data
        val prefs = getSharedPreferences("user_data", Context.MODE_PRIVATE)
        etName.setText(prefs.getString("name", ""))
        etAge.setText(prefs.getString("age", ""))
        selectedGender = prefs.getString("gender", "") ?: ""
        if (selectedGender.isNotEmpty()) {
            genderButtons.forEach { it.isSelected = it.text.toString() == selectedGender }
        }

        val isEditMode = intent.getStringExtra("mode") == "edit"

        // If in edit mode, change the heading
        if (isEditMode) {
            try { (findViewById<android.widget.TextView>(R.id.tv_title))?.text = "Update Profile" } catch (_: Exception) {}
        }

        findViewById<Button>(R.id.btn_continue).setOnClickListener {
            saveAndProceed(isEditMode)
        }
    }

    private fun saveAndProceed(editMode: Boolean = false) {
        val userId = SessionManager.getUserId(this)
        if (userId.isEmpty()) return

        val name = etName.text.toString().trim()
        val age = etAge.text.toString().trim()
        val gender = selectedGender

        if (name.isEmpty() || age.isEmpty() || gender.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = mapOf(
            "name" to name,
            "age" to age,
            "gender" to gender
        )

        // Save locally
        getSharedPreferences("user_data", android.content.Context.MODE_PRIVATE).edit()
            .putString("name", name)
            .putString("age", age)
            .putString("gender", gender)
            .apply()

        // Firebase write
        com.google.firebase.database.FirebaseDatabase.getInstance().reference
            .child("users").child(userId)
            .updateChildren(updates)
            .addOnSuccessListener {
                if (editMode) {
                    // Edit mode: go back to SettingsActivity
                    finish()
                } else {
                    startActivity(android.content.Intent(this, BodyMetricsActivity::class.java))
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Save failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
}
