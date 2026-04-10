package com.example.cookgpt

import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt

class WellnessActivity : AppCompatActivity() {

    private lateinit var tvCaloriesValue: TextView
    private lateinit var tvProteinValue: TextView
    private lateinit var tvCarbsValue: TextView
    private lateinit var tvWaterCount: TextView
    private lateinit var tvBmiValue: TextView
    private lateinit var tvBmiStatus: TextView
    private lateinit var tvWeight: TextView
    private lateinit var tvHeight: TextView

    private lateinit var pbCalories: ProgressBar
    private lateinit var pbProtein: ProgressBar
    private lateinit var pbCarbs: ProgressBar

    private var currentWater = 0
    private val waterGoal = 8
    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wellness)

        // Bind Views
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        tvCaloriesValue = findViewById(R.id.tvCaloriesValue)
        tvProteinValue = findViewById(R.id.tvProteinValue)
        tvCarbsValue = findViewById(R.id.tvCarbsValue)
        tvWaterCount = findViewById(R.id.tvWaterCount)
        tvBmiValue = findViewById(R.id.tvBmiValue)
        tvBmiStatus = findViewById(R.id.tvBmiStatus)
        tvWeight = findViewById(R.id.tvWeight)
        tvHeight = findViewById(R.id.tvHeight)

        pbCalories = findViewById(R.id.pbCalories)
        pbProtein = findViewById(R.id.pbProtein)
        pbCarbs = findViewById(R.id.pbCarbs)

        val btnWaterPlus = findViewById<ImageButton>(R.id.btnWaterPlus)

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            database = FirebaseDatabase.getInstance().reference.child("users").child(userId)
            loadMacrosAndWater()
        }

        loadBodyMetrics()

        btnWaterPlus.setOnClickListener {
            if (currentWater < waterGoal) {
                currentWater++
                updateWaterUI()
                userId?.let { database.child("water").child(today).setValue(currentWater) }
                if (currentWater == waterGoal) {
                    Toast.makeText(this, "Daily Water Goal Reached! \uD83E\uDD73", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadMacrosAndWater() {
        // Hydration listener
        database.child("water").child(today).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentWater = snapshot.getValue(Int::class.java) ?: 0
                updateWaterUI()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Macros listener
        database.child("macros").child(today).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cals = snapshot.child("calories").getValue(Int::class.java) ?: 0
                val pro = snapshot.child("protein").getValue(Int::class.java) ?: 0
                val carb = snapshot.child("carbs").getValue(Int::class.java) ?: 0

                tvCaloriesValue.text = "$cals kcal"
                tvProteinValue.text = "${pro}g Pro"
                tvCarbsValue.text = "${carb}g Carb"

                pbCalories.progress = cals
                pbProtein.progress = pro
                pbCarbs.progress = carb
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadBodyMetrics() {
        val prefs = getSharedPreferences("user_data", Context.MODE_PRIVATE)
        val weightStr = prefs.getString("weight", "0") ?: "0"
        val heightStr = prefs.getString("height", "0") ?: "0"

        val weight = weightStr.toFloatOrNull() ?: 0f
        val height = heightStr.toFloatOrNull() ?: 0f

        tvWeight.text = "$weight kg"
        tvHeight.text = "$height cm"

        if (weight > 0 && height > 0) {
            val bmi = weight / (height / 100).pow(2)
            val roundedBmi = (bmi * 10).roundToInt() / 10.0
            tvBmiValue.text = roundedBmi.toString()

            val (status, color) = when {
                bmi < 18.5 -> "Underweight" to 0xFF3B82F6.toInt() // Blue
                bmi < 25.0 -> "Normal Weight" to 0xFF10B981.toInt() // Green
                bmi < 30.0 -> "Overweight" to 0xFFF59E0B.toInt() // Orange
                else -> "Obese" to 0xFFEF4444.toInt() // Red
            }
            tvBmiStatus.text = status
            tvBmiStatus.setBackgroundColor(color and 0x33FFFFFF)
            tvBmiStatus.setTextColor(color)
        } else {
            tvBmiValue.text = "--"
            tvBmiStatus.text = "Metrics not set"
        }
    }

    private fun updateWaterUI() {
        tvWaterCount.text = "$currentWater of $waterGoal glasses"
    }
}
