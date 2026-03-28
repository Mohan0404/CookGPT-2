package com.example.cookgpt

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cookgpt.data.ApiService
import com.example.cookgpt.data.Constants
import com.example.cookgpt.data.Recipe
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class MealPlannerActivity : AppCompatActivity() {

    private lateinit var calendarView: CalendarView
    private lateinit var tvSelectedDay: TextView
    private lateinit var tvBreakfast: TextView
    private lateinit var tvLunch: TextView
    private lateinit var tvDinner: TextView
    private lateinit var tvShoppingList: TextView
    private lateinit var btnAddMeal: Button
    private lateinit var progressBar: ProgressBar

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var selectedDateKey = dateFormat.format(Date())
    private val apiService = ApiService.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meal_planner)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        calendarView    = findViewById(R.id.calendar_view)
        tvSelectedDay   = findViewById(R.id.tv_selected_day)
        tvBreakfast     = findViewById(R.id.tv_breakfast_meal)
        tvLunch         = findViewById(R.id.tv_lunch_meal)
        tvDinner        = findViewById(R.id.tv_dinner_meal)
        tvShoppingList  = findViewById(R.id.tv_shopping_list)
        btnAddMeal      = findViewById(R.id.btn_add_meal)
        progressBar     = findViewById(R.id.progress_bar)

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth)
            selectedDateKey = dateFormat.format(cal.time)
            tvSelectedDay.text = "Plan for: $dayOfMonth/${month + 1}/$year"
            loadMealPlan(selectedDateKey)
        }

        btnAddMeal.setOnClickListener { showAddMealDialog() }

        loadMealPlan(selectedDateKey)
    }

    private fun loadMealPlan(dateKey: String) {
        val uid = SessionManager.getUserId(this)
        if (uid.isEmpty()) return

        progressBar.visibility = View.VISIBLE

        // Try Firebase first
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid)
            .child("meal_plans").child(dateKey)
            .get()
            .addOnSuccessListener { snapshot ->
                progressBar.visibility = View.GONE
                if (snapshot.exists()) {
                    val breakfast = snapshot.child("breakfast").getValue(String::class.java) ?: "—"
                    val lunch     = snapshot.child("lunch").getValue(String::class.java) ?: "—"
                    val dinner    = snapshot.child("dinner").getValue(String::class.java) ?: "—"
                    tvBreakfast.text = breakfast
                    tvLunch.text     = lunch
                    tvDinner.text    = dinner
                    Log.d("MealPlanner", "Loaded plan for $dateKey from Firebase")

                    // Generate shopping list from assigned meals
                    val meals = listOf(breakfast, lunch, dinner).filter { it != "—" && it.isNotEmpty() }
                    if (meals.isNotEmpty()) {
                        tvShoppingList.text = meals.joinToString("\n") { "• $it" }
                    } else {
                        tvShoppingList.text = "No meals planned — shopping list empty"
                    }
                } else {
                    clearMealDisplay()
                    Log.d("MealPlanner", "No plan found for $dateKey")
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e("MealPlanner", "Failed to load plan: ${e.localizedMessage}")
                // Fall back to local SharedPreferences cache
                loadFromLocalCache(dateKey)
            }
    }

    private fun loadFromLocalCache(dateKey: String) {
        val cached = getSharedPreferences("meal_plans", MODE_PRIVATE)
            .getString("meal_plan_$dateKey", null)
        if (cached != null) {
            val parts = cached.split("|")
            tvBreakfast.text = parts.getOrNull(0) ?: "—"
            tvLunch.text     = parts.getOrNull(1) ?: "—"
            tvDinner.text    = parts.getOrNull(2) ?: "—"
        } else {
            clearMealDisplay()
        }
    }

    private fun clearMealDisplay() {
        tvBreakfast.text    = "—"
        tvLunch.text        = "—"
        tvDinner.text       = "—"
        tvShoppingList.text = "No meals planned for this day"
    }

    private fun showAddMealDialog() {
        val mealTypes = arrayOf("Breakfast", "Lunch", "Dinner")
        var selectedMealType = mealTypes[0]

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_meal, null)
        val etRecipeName  = dialogView.findViewById<EditText>(R.id.et_meal_name)
        val chipGroup     = dialogView.findViewById<ChipGroup>(R.id.chip_group_meal_type)

        // Pre-select Breakfast chip
        (chipGroup.getChildAt(0) as? Chip)?.isChecked = true

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                selectedMealType = (group.findViewById<Chip>(checkedIds[0])).text.toString()
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Add Meal")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val recipeName = etRecipeName.text.toString().trim()
                if (recipeName.isNotEmpty()) {
                    saveMeal(selectedMealType.lowercase(), recipeName)
                } else {
                    Toast.makeText(this, "Please enter a meal name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveMeal(mealType: String, recipeName: String) {
        val uid = SessionManager.getUserId(this)
        if (uid.isEmpty()) return

        progressBar.visibility = View.VISIBLE

        // Write to Firebase immediately using updateChildren so existing meals aren't overwritten
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid)
            .child("meal_plans").child(selectedDateKey)
            .updateChildren(mapOf(mealType to recipeName))
            .addOnSuccessListener {
                Log.d("MealPlanner", "Saved $mealType=$recipeName for $selectedDateKey")
                progressBar.visibility = View.GONE
                // Update display directly — no need for another round-trip
                when (mealType) {
                    "breakfast" -> tvBreakfast.text = recipeName
                    "lunch"     -> tvLunch.text     = recipeName
                    "dinner"    -> tvDinner.text    = recipeName
                }
                // Refresh shopping list
                val meals = listOf(tvBreakfast.text.toString(), tvLunch.text.toString(), tvDinner.text.toString())
                    .filter { it != "—" && it.isNotEmpty() }
                tvShoppingList.text = if (meals.isNotEmpty()) meals.joinToString("\n") { "• $it" }
                                      else "No meals planned — shopping list empty"

                // Mirror to local SharedPreferences for offline display
                val cacheValue = "${tvBreakfast.text}|${tvLunch.text}|${tvDinner.text}"
                getSharedPreferences("meal_plans", MODE_PRIVATE).edit()
                    .putString("meal_plan_$selectedDateKey", cacheValue)
                    .apply()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e("MealPlanner", "Failed to save meal: ${e.localizedMessage}")
                Toast.makeText(this, "Save failed — check connection", Toast.LENGTH_SHORT).show()
            }
    }
}
