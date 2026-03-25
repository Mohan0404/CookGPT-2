package com.example.cookgpt

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class BodyMetricsActivity : AppCompatActivity() {

    private lateinit var etHeight: EditText
    private lateinit var etWeight: EditText
    private lateinit var tvBmiValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_body_metrics)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.body_metrics_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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

        findViewById<Button>(R.id.btn_continue).setOnClickListener {
            val intent = Intent(this, FitnessGoalActivity::class.java)
            startActivity(intent)
        }
    }

    private fun calculateBmi() {
        val heightStr = etHeight.text.toString()
        val weightStr = etWeight.text.toString()

        if (heightStr.isNotEmpty() && weightStr.isNotEmpty()) {
            val height = heightStr.toFloat() / 100 // cm to meters
            val weight = weightStr.toFloat()

            if (height > 0) {
                val bmi = weight / (height * height)
                tvBmiValue.text = String.format(Locale.getDefault(), "%.1f", bmi)
            } else {
                tvBmiValue.text = "--"
            }
        } else {
            tvBmiValue.text = "--"
        }
    }
}