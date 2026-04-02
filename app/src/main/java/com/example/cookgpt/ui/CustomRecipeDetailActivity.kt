package com.example.cookgpt.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.cookgpt.R
import com.google.android.material.button.MaterialButton

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

        findViewById<MaterialButton>(R.id.btnShareRecipe).setOnClickListener {
            shareRecipe(title, ingredients, steps)
        }
    }

    private fun shareRecipe(title: String, ingredients: String, steps: String) {
        val shareText = """
            Recipe: $title
            
            Ingredients:
            $ingredients
            
            Steps:
            $steps
            
            Shared via CookGPT
        """.trimIndent()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Check out this recipe: $title")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }

        // Standard share sheet which includes Bluetooth, WhatsApp, etc.
        startActivity(Intent.createChooser(shareIntent, "Share Recipe via"))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
