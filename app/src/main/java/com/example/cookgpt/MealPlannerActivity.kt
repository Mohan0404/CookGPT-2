package com.example.cookgpt

import android.os.Bundle
import android.widget.CalendarView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MealPlannerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meal_planner)

        findViewById<android.view.View>(R.id.btn_back).setOnClickListener { finish() }

        val calendarView = findViewById<CalendarView>(R.id.calendar_view)
        val tvSelectedDay = findViewById<TextView>(R.id.tv_selected_day)
        val tvShoppingList = findViewById<TextView>(R.id.tv_shopping_list)
        val btnAddMeal = findViewById<android.widget.Button>(R.id.btn_add_meal)

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = "$dayOfMonth/${month + 1}/$year"
            tvSelectedDay.text = "Plan for: $selectedDate"
            
            // Mock logic for shopping list generation
            if (dayOfMonth % 2 == 0) {
                tvShoppingList.text = "• Chicken Breast\n• Broccoli\n• Olive Oil\n• Garlic"
            } else {
                tvShoppingList.text = "• Salmon\n• Asparagus\n• Lemon\n• Quinoa"
            }
        }

        btnAddMeal.setOnClickListener {
            Toast.makeText(this, "Recipe added to your weekly plan!", Toast.LENGTH_SHORT).show()
        }
    }
}
