package com.example.cookgpt

import android.content.Context
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
        val etPhone = findViewById<EditText>(R.id.et_phone)
        val etEmail = findViewById<EditText>(R.id.et_email)
        val etAge = findViewById<EditText>(R.id.et_age)
        val etWeight = findViewById<EditText>(R.id.et_weight)
        val etHeight = findViewById<EditText>(R.id.et_height)
        val spinnerGender = findViewById<Spinner>(R.id.spinner_gender)
        val etPreferences = findViewById<EditText>(R.id.et_preferences)
        val btnSave = findViewById<Button>(R.id.btn_save_profile)

        val db = AppDatabase.getDatabase(this)
        val prefs = UserPreferencesManager(this)
        val sharedPrefs = getSharedPreferences("user_data", Context.MODE_PRIVATE)
        val isEditMode = intent.getBooleanExtra("is_edit_mode", false)

        if (isEditMode) {
            tvTitle.text = "Edit Your Profile"
            btnSave.text = "Update Profile"
        }

        // Pre-fill data logic
        lifecycleScope.launch {
            val userData = prefs.getCurrentUserData()
            // 1. Try loading from SQLite first
            val user = db.userDao().getUser()
            
            if (user != null) {
                etName.setText(user.name)
                etPhone.setText(user.phoneNumber)
                etEmail.setText(userData.email.ifEmpty { user.preferences.split("|").lastOrNull() ?: "" })
                if (user.age > 0) etAge.setText(user.age.toString())
                if (user.weight > 0) etWeight.setText(user.weight.toString())
                if (user.height > 0) etHeight.setText(user.height.toString())

                val adapter = spinnerGender.adapter as? ArrayAdapter<String>
                val position = adapter?.getPosition(user.gender) ?: 0
                spinnerGender.setSelection(position)

                etPreferences.setText(user.preferences)
            } else {
                // 2. If SQLite is empty, check SharedPrefs "user_data" (used by HealthProfile, BodyMetrics, etc.)
                val spName = sharedPrefs.getString("name", "") ?: ""
                val spAge = sharedPrefs.getString("age", "") ?: ""
                val spHeight = sharedPrefs.getString("height", "") ?: ""
                val spWeight = sharedPrefs.getString("weight", "") ?: ""
                val spGender = sharedPrefs.getString("gender", "") ?: ""
                val spEmail = sharedPrefs.getString("email", "") ?: ""
                val spPhone = sharedPrefs.getString("phone", "") ?: ""
                val spAllergies = sharedPrefs.getString("allergies", "") ?: ""
                val spRestrictions = sharedPrefs.getString("restrictions", "") ?: ""

                if (spName.isNotEmpty()) etName.setText(spName)
                if (spAge.isNotEmpty()) etAge.setText(spAge)
                if (spHeight.isNotEmpty()) etHeight.setText(spHeight)
                if (spWeight.isNotEmpty()) etWeight.setText(spWeight)
                if (spEmail.isNotEmpty()) etEmail.setText(spEmail)
                else etEmail.setText(userData.email)
                
                if (spPhone.isNotEmpty()) etPhone.setText(spPhone)

                val adapter = spinnerGender.adapter as? ArrayAdapter<String>
                val position = adapter?.getPosition(spGender) ?: 0
                spinnerGender.setSelection(position)

                val combined = listOf(spAllergies, spRestrictions).filter { it.isNotEmpty() }.joinToString(", ")
                if (combined.isNotEmpty()) {
                    etPreferences.setText(combined)
                } else {
                    val dsCombined = (userData.allergies + userData.restrictions).joinToString(", ")
                    etPreferences.setText(dsCombined)
                }
                
                if (etPhone.text.isEmpty()) etPhone.setText(userData.phoneNumber)
            }
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val ageStr = etAge.text.toString().trim()
            val weightStr = etWeight.text.toString().trim()
            val heightStr = etHeight.text.toString().trim()
            val gender = spinnerGender.selectedItem.toString()
            val preferences = etPreferences.text.toString().trim()

            if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || ageStr.isEmpty() || weightStr.isEmpty() || heightStr.isEmpty()) {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val age = ageStr.toIntOrNull() ?: 0
                val weight = weightStr.toFloatOrNull() ?: 0f
                val height = heightStr.toFloatOrNull() ?: 0f

                val user = User(
                    id = 1,
                    name = name,
                    age = age,
                    weight = weight,
                    height = height,
                    gender = gender,
                    preferences = preferences,
                    phoneNumber = phone
                )
                
                // Save to SQLite
                db.userDao().saveUser(user)
                
                // Sync to SharedPreferences (DataStore)
                prefs.saveUserName(name)
                prefs.savePhoneNumber(phone)
                prefs.saveUserEmail(email)
                prefs.saveBodyMetrics(age, weight, height)
                prefs.saveHealthProfile(gender)
                
                val dietPrefs = preferences.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                prefs.saveDietaryInfo(dietPrefs, emptySet())
                
                // Also sync to legacy SharedPrefs "user_data" to keep other activities working
                sharedPrefs.edit()
                    .putString("name", name)
                    .putString("phone", phone)
                    .putString("age", ageStr)
                    .putString("height", heightStr)
                    .putString("weight", weightStr)
                    .putString("gender", gender)
                    .putString("email", email)
                    .apply()

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
}
