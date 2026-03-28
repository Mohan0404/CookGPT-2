package com.example.cookgpt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase
import java.util.Locale

class BodyMetricsActivity : AppCompatActivity() {

    private lateinit var etHeight: EditText
    private lateinit var etWeight: EditText
    private lateinit var tvBmiValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_body_metrics)

        etHeight = findViewById(R.id.et_height)
        etWeight = findViewById(R.id.et_weight)
        tvBmiValue = findViewById(R.id.tv_bmi_value)

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                calculateBmi()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        etHeight.addTextChangedListener(textWatcher)
        etWeight.addTextChangedListener(textWatcher)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Load existing data
        val prefs = getSharedPreferences("user_data", Context.MODE_PRIVATE)
        etHeight.setText(prefs.getString("height", ""))
        etWeight.setText(prefs.getString("weight", ""))
        calculateBmi()

        val isEditMode = intent.getStringExtra("mode") == "edit"

        findViewById<Button>(R.id.btn_continue).setOnClickListener {
            saveAndProceed(isEditMode)
        }
    }

    private fun saveAndProceed(editMode: Boolean = false) {
        val userId = SessionManager.getUserId(this)
        if (userId.isEmpty()) return

        val height = etHeight.text.toString().trim()
        val weight = etWeight.text.toString().trim()

        if (height.isEmpty() || weight.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = mapOf(
            "height" to height,
            "weight" to weight
        )

        getSharedPreferences("user_data", Context.MODE_PRIVATE).edit()
            .putString("height", height)
            .putString("weight", weight)
            .apply()

        FirebaseDatabase.getInstance().reference
            .child("users").child(userId)
            .updateChildren(updates)
            .addOnSuccessListener {
                if (editMode) finish()
                else { startActivity(Intent(this, FitnessGoalActivity::class.java)); finish() }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Save failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun calculateBmi() {
        val heightStr = etHeight.text.toString()
        val weightStr = etWeight.text.toString()

        if (heightStr.isNotEmpty() && weightStr.isNotEmpty()) {
            val height = heightStr.toFloatOrNull() ?: 0f
            val weight = weightStr.toFloatOrNull() ?: 0f

            if (height >= 50) {
                val meters = height / 100
                val bmi = weight / (meters * meters)
                tvBmiValue.text = String.format(Locale.getDefault(), "%.1f", bmi)
            } else {
                tvBmiValue.text = "--"
            }
        } else {
            tvBmiValue.text = "--"
        }
    }
}
