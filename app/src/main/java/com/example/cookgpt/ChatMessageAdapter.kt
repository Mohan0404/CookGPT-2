package com.example.cookgpt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatMessageAdapter(
    private val messages: List<ChatMessage>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_BOT  = 2
    }

    override fun getItemViewType(position: Int) =
        if (messages[position].isFromUser) VIEW_TYPE_USER else VIEW_TYPE_BOT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> UserMessageViewHolder(
                inflater.inflate(R.layout.item_message_user, parent, false))
            else           -> BotMessageViewHolder(
                inflater.inflate(R.layout.item_message_bot, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(message.timestamp))

        when (holder) {
            is UserMessageViewHolder -> {
                holder.tvMessage.text = message.text
                holder.tvTime.text    = timeStr
            }
            is BotMessageViewHolder -> {
                holder.tvMessage.text = message.text
                holder.tvTime.text    = timeStr
                holder.tvMessage.setTextColor(
                    ContextCompat.getColor(holder.itemView.context,
                        if (message.isError) R.color.colorError else R.color.colorOnSurface)
                )
            }
        }
    }

    override fun getItemCount() = messages.size

    inner class UserMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val tvTime: TextView    = view.findViewById(R.id.tvTime)
    }

    inner class BotMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val tvTime: TextView    = view.findViewById(R.id.tvTime)
    }
}
