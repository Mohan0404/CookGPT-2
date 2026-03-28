package com.example.cookgpt

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RegistrationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        val tvTitle = findViewById<TextView>(R.id.tv_reg_title)
        val etName = findViewById<EditText>(R.id.et_name)
        val etEmail = findViewById<EditText>(R.id.et_email)
        val etAge = findViewById<EditText>(R.id.et_age)
        val etWeight = findViewById<EditText>(R.id.et_weight)
        val etHeight = findViewById<EditText>(R.id.et_height)
        val spinnerGender = findViewById<Spinner>(R.id.spinner_gender)
        val etPreferences = findViewById<EditText>(R.id.et_preferences)
        val btnSave = findViewById<Button>(R.id.btn_save_profile)

        val db = AppDatabase.getDatabase(this)
        val prefs = UserPreferencesManager(this)
        val isEditMode = intent.getBooleanExtra("is_edit_mode", false)

        if (isEditMode) {
            tvTitle.text = "Edit Your Profile"
            btnSave.text = "Update Profile"

            lifecycleScope.launch {
                val user = db.userDao().getUser()
                if (user != null) {
                    etName.setText(user.name)
                    etAge.setText(user.age.toString())
                    etWeight.setText(user.weight.toString())
                    etHeight.setText(user.height.toString())

                    val adapter = spinnerGender.adapter as? ArrayAdapter<String>
                    val position = adapter?.getPosition(user.gender) ?: 0
                    spinnerGender.setSelection(position)

                    etPreferences.setText(user.preferences)
                }
            }
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val ageStr = etAge.text.toString().trim()
            val weightStr = etWeight.text.toString().trim()
            val heightStr = etHeight.text.toString().trim()
            val gender = spinnerGender.selectedItem.toString()
            val preferences = etPreferences.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || ageStr.isEmpty() || weightStr.isEmpty() || heightStr.isEmpty()) {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val message = if (isEditMode) "Profile Updated!" else "Profile Created!"
                Toast.makeText(this@RegistrationActivity, message, Toast.LENGTH_SHORT).show()

                if (isEditMode) {
                    finish()
                } else {
                    startActivity(Intent(this@RegistrationActivity, HomeActivity::class.java))
                    finish()
                }
            }
        }
    }

    private fun saveToFirebase(userId: String, name: String, email: String) {
        try {
            Log.d("FIREBASE_DEBUG", "Attempting to save user: $name ($userId)")
            val database = FirebaseDatabase.getInstance().getReference("users")
            val userMap = mapOf("name" to name, "email" to email)

            database.child(userId).setValue(userMap)
                .addOnSuccessListener {
                    Log.d("FIREBASE_DEBUG", "User data successfully written to Firebase")
                }
                .addOnFailureListener { e ->
                    Log.e("FIREBASE_DEBUG", "Error writing user data", e)
                }
        } catch (e: Exception) {
            Log.e("FIREBASE_DEBUG", "Firebase Exception", e)
        }
    }
}
