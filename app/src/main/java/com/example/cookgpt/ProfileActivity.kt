package com.example.cookgpt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        findViewById<android.view.View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        val tvName = findViewById<TextView>(R.id.tv_profile_name)
        val tvStats = findViewById<TextView>(R.id.tv_profile_stats)
        val tvGender = findViewById<TextView>(R.id.tv_profile_gender)
        val tvPrefs = findViewById<TextView>(R.id.tv_profile_preferences)
        val btnEdit = findViewById<Button>(R.id.btn_edit_profile)

        val db = AppDatabase.getDatabase(this)

        lifecycleScope.launch {
            val user = db.userDao().getUser()
            if (user != null) {
                tvName.text = user.name
                tvStats.text = "Age: ${user.age} | ${user.weight}kg | ${user.height}cm"
                tvGender.text = "Gender: ${user.gender}"
                tvPrefs.text = if (user.preferences.isNotEmpty()) user.preferences else "No preferences set."
            }
        }

        btnEdit.setOnClickListener {
            val intent = Intent(this, RegistrationActivity::class.java)
            intent.putExtra("is_edit_mode", true)
            startActivity(intent)
        }
    }
}
