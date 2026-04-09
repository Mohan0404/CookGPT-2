package com.example.cookgpt.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cookgpt.R
import com.example.cookgpt.data.RssItem
import com.example.cookgpt.databinding.ItemRssFeedBinding

class RssFeedAdapter(private val onItemSelected: (RssItem) -> Unit) :
    RecyclerView.Adapter<RssFeedAdapter.RssViewHolder>() {

    private var items: List<RssItem> = emptyList()

    fun submitList(newList: List<RssItem>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RssViewHolder {
        val binding = ItemRssFeedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RssViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RssViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class RssViewHolder(private val binding: ItemRssFeedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RssItem) {
            binding.tvArticleTitle.text = item.title
            binding.tvArticleSource.text = item.source
            binding.tvArticleDate.text = " • ${item.pubDate}"
            binding.tvArticleDesc.text = item.description

            Glide.with(binding.ivArticleImage.context)
                .load(item.imageUrl)
                .placeholder(R.drawable.bg_recipe_image_placeholder)
                .error(R.drawable.bg_recipe_image_placeholder)
                .centerCrop()
                .into(binding.ivArticleImage)

            binding.root.setOnClickListener { onItemSelected(item) }
        }
    }
}
