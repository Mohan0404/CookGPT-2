package com.example.cookgpt.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cookgpt.R
import com.example.cookgpt.data.CustomRecipe
import com.example.cookgpt.data.CustomRecipeRepository
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MyRecipesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var adapter: MyRecipesAdapter
    private val repository = CustomRecipeRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_recipes)

        // Auth gate
        if (FirebaseAuth.getInstance().currentUser == null) {
            Snackbar.make(
                findViewById(android.R.id.content),
                "Please log in to create your own recipes.",
                Snackbar.LENGTH_LONG
            ).show()
            finish()
            return
        }

        recyclerView = findViewById(R.id.rvMyRecipes)
        emptyView    = findViewById(R.id.tvEmptyMyRecipes)
        fab          = findViewById(R.id.fabAddRecipe)

        supportActionBar?.title = "My Recipes"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = MyRecipesAdapter(
            onItemClick = { recipe ->
                val intent = Intent(this, CustomRecipeDetailActivity::class.java)
                intent.putExtra("RECIPE_ID", recipe.id)
                intent.putExtra("RECIPE_TITLE", recipe.title)
                intent.putExtra("RECIPE_IMAGE", recipe.imageUrl)
                intent.putExtra("RECIPE_INGREDIENTS", recipe.ingredients)
                intent.putExtra("RECIPE_STEPS", recipe.steps)
                intent.putExtra("RECIPE_CUISINE", recipe.cuisine)
                intent.putExtra("RECIPE_PREP", recipe.prepTimeMinutes)
                intent.putExtra("RECIPE_COOK", recipe.cookTimeMinutes)
                startActivity(intent)
            },
            onItemLongClick = { recipe ->
                AlertDialog.Builder(this)
                    .setTitle("Delete recipe")
                    .setMessage("Delete \"${recipe.title}\"? This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ -> deleteRecipe(recipe) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fab.setOnClickListener {
            startActivity(Intent(this, CreateRecipeActivity::class.java))
        }

        observeRecipes()
    }

    private fun observeRecipes() {
        lifecycleScope.launch {
            repository.observeMyRecipes().collect { recipes ->
                adapter.submitList(recipes)
                emptyView.visibility = if (recipes.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (recipes.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun deleteRecipe(recipe: CustomRecipe) {
        lifecycleScope.launch {
            val result = repository.deleteRecipe(recipe.id, recipe.imageUrl)
            if (result.isFailure) {
                Toast.makeText(this@MyRecipesActivity, "Delete failed. Try again.", Toast.LENGTH_SHORT).show()
            }
            // On success, the Flow above auto-refreshes the list
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

// ── Adapter ──────────────────────────────────────────────────────────────────

class MyRecipesAdapter(
    private val onItemClick: (CustomRecipe) -> Unit,
    private val onItemLongClick: (CustomRecipe) -> Unit
) : RecyclerView.Adapter<MyRecipesAdapter.ViewHolder>() {

    private val items = mutableListOf<CustomRecipe>()

    fun submitList(newList: List<CustomRecipe>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_custom_recipe, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView  = itemView.findViewById(R.id.ivRecipeImage)
        private val title: TextView   = itemView.findViewById(R.id.tvRecipeTitle)
        private val cuisine: TextView = itemView.findViewById(R.id.tvCuisineTag)
        private val time: TextView    = itemView.findViewById(R.id.tvCookTime)

        fun bind(recipe: CustomRecipe) {
            title.text   = recipe.title
            cuisine.text = recipe.cuisine
            time.text    = "${recipe.cookTimeMinutes} min"

            if (recipe.imageUrl.isNotBlank()) {
                Glide.with(itemView.context)
                    .load(recipe.imageUrl)
                    .placeholder(R.drawable.ic_recipe_placeholder)
                    .centerCrop()
                    .into(image)
            } else {
                image.setImageResource(R.drawable.ic_recipe_placeholder)
            }

            itemView.setOnClickListener { onItemClick(recipe) }
            itemView.setOnLongClickListener { onItemLongClick(recipe); true }
        }
    }
}
