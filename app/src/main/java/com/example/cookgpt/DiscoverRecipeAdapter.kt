package com.example.cookgpt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cookgpt.data.Recipe
import android.content.Intent
import android.util.Log
import kotlin.math.roundToInt

class DiscoverRecipeAdapter(
    private val recipes: List<Recipe>
) : RecyclerView.Adapter<DiscoverRecipeAdapter.DiscoverViewHolder>() {

    class DiscoverViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivRecipeThumb: ImageView = view.findViewById(R.id.ivRecipeThumb)
        val tvRecipeTitle: TextView = view.findViewById(R.id.tvRecipeTitle)
        val tvRecipeCalories: TextView = view.findViewById(R.id.tvRecipeCalories)
        val cardDiscoverRecipe: View = view.findViewById(R.id.cardDiscoverRecipe)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiscoverViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recipe_discover, parent, false)
        return DiscoverViewHolder(view)
    }

    override fun onBindViewHolder(holder: DiscoverViewHolder, position: Int) {
        val recipe = recipes[position]
        holder.tvRecipeTitle.text = recipe.title
        
        // Extract calories from nutrition property
        val calNutrient = recipe.nutrition?.nutrients?.find { it.name.equals("Calories", ignoreCase = true) }
        val calAmount = calNutrient?.amount?.roundToInt()
        
        if (calAmount != null && calAmount > 0) {
            holder.tvRecipeCalories.text = "$calAmount kcal"
        } else {
            holder.tvRecipeCalories.text = "— kcal"
        }

        Glide.with(holder.itemView.context)
            .load(recipe.image)
            .placeholder(R.drawable.logo_bg)
            .error(R.drawable.logo_bg)
            .centerCrop()
            .into(holder.ivRecipeThumb)

        holder.cardDiscoverRecipe.setOnClickListener {
            Log.d("Navigation", "Clicked recipe: ${recipe.title}")
            val context = holder.itemView.context
            val intent = Intent(context, RecipeDetailsActivity::class.java)
            intent.putExtra("RECIPE", recipe)
            Log.d("Navigation", "Intent created for RecipeDetailsActivity with recipe: ${recipe.id}")
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = recipes.size
}
