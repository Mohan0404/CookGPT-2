package com.example.cookgpt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cookgpt.data.YouTubeItem
import com.example.cookgpt.util.openYouTubeInAppBrowser

class VideoAdapter(
    private val items: List<YouTubeItem>
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumbnail: ImageView  = view.findViewById(R.id.ivThumbnail)
        val tvVideoTitle: TextView  = view.findViewById(R.id.tvVideoTitle)
        val tvChannelName: TextView = view.findViewById(R.id.tvChannelName)
        val cardVideo: View         = view.findViewById(R.id.cardVideo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val item = items[position]

        // Title
        holder.tvVideoTitle.text  = item.snippet?.title ?: "Untitled"
        holder.tvChannelName.text = item.snippet?.channelTitle ?: ""

        // Thumbnail — prefer medium quality; fall back to stable YouTube CDN url
        val videoId = item.id?.videoId
        val thumbUrl = item.snippet?.thumbnails?.medium?.url
            ?: if (videoId != null) "https://img.youtube.com/vi/$videoId/mqdefault.jpg"
               else null

        Glide.with(holder.itemView.context)
            .load(thumbUrl)
            .placeholder(R.drawable.logo_bg)
            .error(R.drawable.logo_bg)
            .centerCrop()
            .into(holder.ivThumbnail)

        // Click — only fire if we have a valid videoId
        if (videoId != null) {
            holder.cardVideo.setOnClickListener {
                openYouTubeInAppBrowser(holder.itemView.context, videoId)
            }
        } else {
            holder.cardVideo.setOnClickListener(null)
        }
    }

    override fun getItemCount(): Int = items.size
}
