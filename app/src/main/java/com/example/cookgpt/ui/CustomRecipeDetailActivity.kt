package com.example.cookgpt.ui

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.cookgpt.R

class CustomRecipeDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_recipe_detail)

        val title       = intent.getStringExtra("RECIPE_TITLE") ?: ""
        val imageUrl    = intent.getStringExtra("RECIPE_IMAGE") ?: ""
        val ingredients = intent.getStringExtra("RECIPE_INGREDIENTS") ?: ""
        val steps       = intent.getStringExtra("RECIPE_STEPS") ?: ""
        val cuisine     = intent.getStringExtra("RECIPE_CUISINE") ?: ""
        val prepTime    = intent.getIntExtra("RECIPE_PREP", 0)
        val cookTime    = intent.getIntExtra("RECIPE_COOK", 0)

        supportActionBar?.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<TextView>(R.id.tvDetailTitle).text = title
        findViewById<TextView>(R.id.tvDetailCuisine).text = cuisine
        findViewById<TextView>(R.id.tvDetailTime).text = "Prep ${prepTime}min  ·  Cook ${cookTime}min"
        findViewById<TextView>(R.id.tvDetailIngredients).text = ingredients
        findViewById<TextView>(R.id.tvDetailSteps).text = steps

        val iv = findViewById<ImageView>(R.id.ivDetailImage)
        if (imageUrl.isNotBlank()) {
            Glide.with(this).load(imageUrl).centerCrop().into(iv)
        } else {
            iv.setImageResource(R.drawable.ic_recipe_placeholder)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
