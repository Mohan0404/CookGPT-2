package com.example.cookgpt

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * Data model for a single shopping item.
 */
data class ShoppingItem(
    val name: String,
    var quantity: String = "1",
    var category: String = "General",
    var price: Int = 0,
    var isChecked: Boolean = false,
    var isFirstInCategory: Boolean = false
)

/**
 * RecyclerView Adapter for the Smart Grocery shopping list.
 * Supports:
 *  - Checkbox toggle with strike-through animation
 *  - Section headers for smart category grouping (OUTSIDE CardView)
 *  - Edit / Delete callbacks
 *  - Pantry mode filtering
 */
class ShoppingAdapter(
    private var items: MutableList<ShoppingItem>,
    private val onItemChecked: (position: Int, isChecked: Boolean) -> Unit,
    private val onItemDeleted: (position: Int) -> Unit,
    private val onItemEdited: (position: Int) -> Unit
) : RecyclerView.Adapter<ShoppingAdapter.ShoppingViewHolder>() {

    private var pantryMode = false

    inner class ShoppingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cbItem: CheckBox = itemView.findViewById(R.id.cbItem)
        val tvItemName: TextView = itemView.findViewById(R.id.tvItemName)
        val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
        val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        val chipCategory: TextView = itemView.findViewById(R.id.chipCategory)
        val btnEdit: ImageView = itemView.findViewById(R.id.btnEditItem)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDeleteItem)
        val sectionHeader: LinearLayout = itemView.findViewById(R.id.sectionHeader)
        val tvSectionTitle: TextView = itemView.findViewById(R.id.tvSectionTitle)
        val ivCategoryIcon: ImageView = itemView.findViewById(R.id.ivCategoryIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShoppingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shopping, parent, false)
        return ShoppingViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShoppingViewHolder, position: Int) {
        val item = getFilteredItems()[position]
        val ctx = holder.itemView.context

        // ── Item data ──
        holder.tvItemName.text = item.name
        holder.tvQuantity.text = "Qty: ${item.quantity}"
        holder.tvPrice.text = "₹${item.price}"
        holder.chipCategory.text = item.category

        // ── Section header — show only for first item in its category ──
        if (item.isFirstInCategory) {
            holder.sectionHeader.visibility = View.VISIBLE
            holder.tvSectionTitle.text = item.category
            val iconRes = getCategoryIcon(item.category)
            val colorRes = getCategoryColor(item.category)
            holder.ivCategoryIcon.setImageResource(iconRes)
            holder.ivCategoryIcon.setColorFilter(ContextCompat.getColor(ctx, colorRes))
            holder.tvSectionTitle.setTextColor(ContextCompat.getColor(ctx, colorRes))
        } else {
            holder.sectionHeader.visibility = View.GONE
        }

        // ── Checkbox state ──
        holder.cbItem.setOnCheckedChangeListener(null)
        holder.cbItem.isChecked = item.isChecked

        // Strike-through effect for checked items
        applyStrikeThrough(holder.tvItemName, item.isChecked)
        holder.itemView.alpha = if (item.isChecked) 0.55f else 1.0f

        holder.cbItem.setOnCheckedChangeListener { _, isChecked ->
            val realPos = items.indexOf(item)
            if (realPos != -1) {
                onItemChecked(realPos, isChecked)
            }
        }

        // ── Delete ──
        holder.btnDelete.setOnClickListener {
            val realPos = items.indexOf(item)
            if (realPos != -1) {
                onItemDeleted(realPos)
            }
        }

        // ── Edit ──
        holder.btnEdit.setOnClickListener {
            val realPos = items.indexOf(item)
            if (realPos != -1) {
                onItemEdited(realPos)
            }
        }
    }

    override fun getItemCount(): Int = getFilteredItems().size

    private fun getFilteredItems(): List<ShoppingItem> {
        return if (pantryMode) items.filter { !it.isChecked } else items
    }

    fun setPantryMode(enabled: Boolean) {
        pantryMode = enabled
        notifyDataSetChanged()
    }

    fun updateItems(newItems: MutableList<ShoppingItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getItems(): MutableList<ShoppingItem> = items

    fun getUncheckedCount(): Int = items.count { !it.isChecked }

    fun getUncheckedItems(): List<ShoppingItem> = items.filter { !it.isChecked }

    // ── Helpers ──

    private fun applyStrikeThrough(tv: TextView, strike: Boolean) {
        tv.paintFlags = if (strike) {
            tv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            tv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    private fun getCategoryIcon(category: String): Int = when (category) {
        "Produce" -> R.drawable.ic_leaf
        "Dairy" -> R.drawable.ic_milk
        "Protein" -> R.drawable.ic_fitness
        "Grains" -> R.drawable.ic_grain
        "Snacks" -> R.drawable.ic_local_fire
        else -> R.drawable.ic_shop
    }

    private fun getCategoryColor(category: String): Int = when (category) {
        "Produce" -> R.color.bg_green
        "Dairy" -> R.color.bg_blue
        "Protein" -> R.color.bg_orange
        "Grains" -> R.color.bg_purple
        "Snacks" -> R.color.text_red
        else -> R.color.text_grey
    }
}
