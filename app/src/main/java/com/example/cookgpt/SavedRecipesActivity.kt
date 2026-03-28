package com.example.cookgpt

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cookgpt.data.Recipe
import com.example.cookgpt.data.RecipeDatabaseHelper
import com.example.cookgpt.data.SavedRecipe
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.FirebaseDatabase

class SavedRecipesActivity : AppCompatActivity() {

    private lateinit var rvSavedRecipes: RecyclerView
    private lateinit var tvEmptySaved: TextView
    private lateinit var dbHelper: RecipeDatabaseHelper

    private val savedList = mutableListOf<SavedRecipe>()
    private lateinit var adapter: SavedRecipeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_recipes)

        rvSavedRecipes = findViewById(R.id.rvSavedRecipes)
        tvEmptySaved   = findViewById(R.id.tvEmptySaved)
        dbHelper       = RecipeDatabaseHelper(this)

        adapter = SavedRecipeAdapter(savedList) { savedRecipe ->
            Log.d("Navigation", "Clicked saved recipe: ${savedRecipe.title}")
            val recipe = Recipe(
                id = savedRecipe.id,
                title = savedRecipe.title,
                image = savedRecipe.image,
                nutrition = null
            )
            val intent = Intent(this, RecipeDetailsActivity::class.java)
            intent.putExtra("RECIPE", recipe)
            Log.d("Navigation", "Intent created for RecipeDetailsActivity with saved recipe: ${savedRecipe.id}")
            startActivity(intent)
        }
        rvSavedRecipes.layoutManager = LinearLayoutManager(this)
        rvSavedRecipes.adapter = adapter

        attachSwipeToDelete()
        loadSavedRecipes()
    }

    override fun onResume() {
        super.onResume()
        loadSavedRecipes()
    }

    private fun loadSavedRecipes() {
        val userId = SessionManager.getUserId(this)
        if (userId.isEmpty()) {
            tvEmptySaved.visibility    = View.VISIBLE
            rvSavedRecipes.visibility  = View.GONE
            return
        }

        val fresh = dbHelper.getAllSavedRecipes(userId)
        savedList.clear()
        savedList.addAll(fresh)

        if (savedList.isEmpty()) {
            tvEmptySaved.visibility   = View.VISIBLE
            rvSavedRecipes.visibility = View.GONE
        } else {
            tvEmptySaved.visibility   = View.GONE
            rvSavedRecipes.visibility = View.VISIBLE
        }
        adapter.notifyDataSetChanged()
    }

    // ── TASK 5: Swipe-to-delete with undo Snackbar ──────────────────────────
    private fun attachSwipeToDelete() {
        val deleteBackground = ColorDrawable(Color.parseColor("#EF4444"))
        val deleteIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_delete)

        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val recipe   = savedList[position]
                val uid      = SessionManager.getUserId(this@SavedRecipesActivity)

                // Remove from SQLite
                dbHelper.deleteRecipe(recipe.id, uid)
                Log.d("SavedRecipes", "Deleted recipe ${recipe.id} from SQLite")

                // Remove from Firebase
                FirebaseDatabase.getInstance().reference
                    .child("users").child(uid)
                    .child("saved_recipes").child(recipe.id.toString())
                    .removeValue()
                    .addOnSuccessListener { Log.d("SavedRecipes", "Deleted ${recipe.id} from Firebase") }
                    .addOnFailureListener { e -> Log.e("SavedRecipes", "Firebase delete failed: ${e.localizedMessage}") }

                // Remove from list
                savedList.removeAt(position)
                adapter.notifyItemRemoved(position)

                if (savedList.isEmpty()) {
                    tvEmptySaved.visibility   = View.VISIBLE
                    rvSavedRecipes.visibility = View.GONE
                }

                // Undo Snackbar
                Snackbar.make(rvSavedRecipes, "${recipe.title} removed", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        dbHelper.insertRecipe(recipe, uid)
                        FirebaseDatabase.getInstance().reference
                            .child("users").child(uid)
                            .child("saved_recipes").child(recipe.id.toString())
                            .setValue(recipe)
                            .addOnSuccessListener { Log.d("SavedRecipes", "Undo: restored ${recipe.id}") }

                        savedList.add(position.coerceAtMost(savedList.size), recipe)
                        adapter.notifyItemInserted(position.coerceAtMost(savedList.size - 1))
                        tvEmptySaved.visibility   = View.GONE
                        rvSavedRecipes.visibility = View.VISIBLE
                    }
                    .show()
            }

            // Draw red delete background + icon during swipe
            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView  = viewHolder.itemView
                val iconMargin = (itemView.height - (deleteIcon?.intrinsicHeight ?: 0)) / 2

                // Red background
                deleteBackground.setBounds(
                    itemView.right + dX.toInt(), itemView.top,
                    itemView.right, itemView.bottom
                )
                deleteBackground.draw(c)

                // Delete icon
                deleteIcon?.let { icon ->
                    val iconLeft   = itemView.right - iconMargin - icon.intrinsicWidth
                    val iconRight  = itemView.right - iconMargin
                    val iconTop    = itemView.top + iconMargin
                    val iconBottom = itemView.bottom - iconMargin
                    icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    icon.setTint(Color.WHITE)
                    icon.draw(c)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(rvSavedRecipes)
    }
}

// ── Dedicated adapter that supports getItem / removeItem / addItem ──────────
class SavedRecipeAdapter(
    private val items: MutableList<SavedRecipe>,
    private val onItemClick: (SavedRecipe) -> Unit
) : RecyclerView.Adapter<SavedRecipeAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivRecipe: android.widget.ImageView = view.findViewById(R.id.ivRecipe)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipe, parent, false)
        return VH(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val recipe = items[position]
        holder.tvTitle.text = recipe.title
        com.bumptech.glide.Glide.with(holder.itemView.context)
            .load(recipe.image)
            .placeholder(R.drawable.ic_chef_hat)
            .error(R.drawable.ic_chef_hat)
            .centerCrop()
            .into(holder.ivRecipe)
        holder.itemView.setOnClickListener { onItemClick(recipe) }
    }

    fun getItem(position: Int): SavedRecipe = items[position]
    fun removeItem(position: Int) { items.removeAt(position) }
    fun addItem(position: Int, item: SavedRecipe) { items.add(position, item) }
}
