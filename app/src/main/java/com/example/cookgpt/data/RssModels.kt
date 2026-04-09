package com.example.cookgpt.data

import androidx.annotation.Keep
import java.io.Serializable

@Keep
data class RssItem(
    val title: String = "",
    val link: String = "",
    val description: String = "",
    val pubDate: String = "",
    val imageUrl: String? = null,
    val source: String = ""
) : Serializable
