package com.example.cookgpt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PantryAdapter(
    private val ingredients: List<String>,
    private val onCheckedChange: (String, Boolean) -> Unit
) : RecyclerView.Adapter<PantryAdapter.ViewHolder>() {

    private val checkedItems = mutableSetOf<String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkBox: CheckBox = view.findViewById(R.id.cbIngredient)
        val name: TextView = view.findViewById(R.id.tvIngredientName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ingredient_pantry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ingredient = ingredients[position]
        holder.name.text = ingredient
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = checkedItems.contains(ingredient)
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checkedItems.add(ingredient) else checkedItems.remove(ingredient)
            onCheckedChange(ingredient, isChecked)
        }
    }

    override fun getItemCount() = ingredients.size

    fun getUncheckedItems(): List<String> {
        return ingredients.filter { !checkedItems.contains(it) }
    }
}
