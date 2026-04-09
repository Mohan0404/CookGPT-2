package com.example.cookgpt.news

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cookgpt.R
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class NewsAdapter( private var articles: MutableList<NewsArticle>, private val onArticleClick: (NewsArticle) -> Unit ) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() { 
    inner class NewsViewHolder(view: View) : RecyclerView.ViewHolder(view) { 
        val ivImage: ImageView = view.findViewById(R.id.ivNewsImage) 
        val tvTitle: TextView = view.findViewById(R.id.tvNewsTitle) 
        val tvSource: TextView = view.findViewById(R.id.tvNewsSource) 
        val tvTime: TextView = view.findViewById(R.id.tvNewsTime) 
        val chipCategory: Chip = view.findViewById(R.id.chipCategory) 
    } 
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = NewsViewHolder(LayoutInflater.from(parent.context) .inflate(R.layout.item_news_card, parent, false)) 
    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) { 
        val article = articles[position] 
        holder.tvTitle.text = article.title 
        holder.tvSource.text = article.source.name 
        holder.tvTime.text = formatPublishedTime(article.publishedAt) 
        holder.chipCategory.text = currentCategory 
        Glide.with(holder.itemView.context) .load(article.urlToImage) .placeholder(R.drawable.ic_recipe_placeholder) .error(R.drawable.ic_recipe_placeholder) .centerCrop() .into(holder.ivImage) 
        holder.itemView.setOnClickListener { onArticleClick(article) } 
    } 
    override fun getItemCount() = articles.size 
    fun updateArticles(newList: List<NewsArticle>) { 
        articles.clear() 
        articles.addAll(newList) 
        notifyDataSetChanged() 
    } 
    private fun formatPublishedTime(publishedAt: String): String { 
        return try { 
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()) 
            sdf.timeZone = TimeZone.getTimeZone("UTC") 
            val date = sdf.parse(publishedAt) ?: return "" 
            val diff = System.currentTimeMillis() - date.time 
            when { 
                diff < 3_600_000 -> "${diff / 60_000}m ago" 
                diff < 86_400_000 -> "${diff / 3_600_000}h ago" 
                else -> "${diff / 86_400_000}d ago" 
            } 
        } catch (e: Exception) { "" } 
    } 
    var currentCategory: String = "All" 
}
