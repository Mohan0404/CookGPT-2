package com.example.cookgpt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.cookgpt.util.WaveView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : AppCompatActivity() {

    private lateinit var tvCaloriesValue: TextView
    private lateinit var tvProteinValue: TextView
    private lateinit var tvCarbsValue: TextView
    private lateinit var tvWaterCount: TextView
    private lateinit var waveView: WaveView
    
    private lateinit var pbCalories: ProgressBar
    private lateinit var pbProtein: ProgressBar
    private lateinit var pbCarbs: ProgressBar

    private var currentWater = 0
    private val waterGoal = 8
    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Bind Views
        tvCaloriesValue = findViewById(R.id.tvCaloriesValue)
        tvProteinValue = findViewById(R.id.tvProteinValue)
        tvCarbsValue = findViewById(R.id.tvCarbsValue)
        tvWaterCount = findViewById(R.id.tvWaterCount)
        waveView = findViewById(R.id.waveView)

        pbCalories = findViewById(R.id.pbCalories)
        pbProtein = findViewById(R.id.pbProtein)
        pbCarbs = findViewById(R.id.pbCarbs)

        setupNavigation()

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            database = FirebaseDatabase.getInstance().reference.child("users").child(userId)
            loadMacrosAndWater()
        }

        findViewById<ImageButton>(R.id.btnWaterPlus).setOnClickListener {
            if (currentWater < waterGoal) {
                currentWater++
                updateWaterUI()
                userId?.let { database.child("water").child(today).setValue(currentWater) }
            }
        }

        findViewById<ImageButton>(R.id.btnWaterMinus).setOnClickListener {
            if (currentWater > 0) {
                currentWater--
                updateWaterUI()
                userId?.let { database.child("water").child(today).setValue(currentWater) }
            }
        }

        findViewById<MaterialCardView>(R.id.btn_ai_chef).setOnClickListener {
            startActivity(Intent(this, AiChefActivity::class.java))
        }
    }

    private fun loadMacrosAndWater() {
        database.child("water").child(today).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentWater = snapshot.getValue(Int::class.java) ?: 0
                updateWaterUI()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

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

    private fun updateWaterUI() {
        tvWaterCount.text = "$currentWater / $waterGoal glasses"
        waveView.setProgress(currentWater.toFloat() / waterGoal.toFloat())
    }

    private fun setupNavigation() {
        findViewById<LinearLayout>(R.id.btn_wellness_tab).setOnClickListener {
            startActivity(Intent(this, WellnessActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btn_settings_tab).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }
}
