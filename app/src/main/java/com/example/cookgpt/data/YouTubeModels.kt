package com.example.cookgpt.data

import androidx.annotation.Keep

@Keep
data class YouTubeSearchResponse(
    val items: List<YouTubeItem>? = null
)

@Keep
data class YouTubeItem(
    val id: YouTubeVideoId? = null,
    val snippet: YouTubeSnippet? = null
)

@Keep
data class YouTubeVideoId(
    val videoId: String? = null
)

@Keep
data class YouTubeSnippet(
    val title: String? = null,
    val channelTitle: String? = null,
    val thumbnails: YouTubeThumbnails? = null
)

@Keep
data class YouTubeThumbnails(
    val medium: YouTubeThumbnailUrl? = null,
    val high: YouTubeThumbnailUrl? = null
)

@Keep
data class YouTubeThumbnailUrl(
    val url: String? = null
)

// ── Videos API models (youtube/v3/videos?part=status,contentDetails) ──────
// Used for the SECOND verification pass to confirm embeddability and duration.

@Keep
data class YouTubeVideoListResponse(
    val items: List<YouTubeVideoDetail>? = null
)

@Keep
data class YouTubeVideoDetail(
    val id: String? = null,
    val status: YouTubeVideoStatus? = null,
    val contentDetails: YouTubeVideoContentDetails? = null
)

@Keep
data class YouTubeVideoStatus(
    val embeddable: Boolean = false
)

@Keep
data class YouTubeVideoContentDetails(
    /** ISO 8601 duration, e.g. "PT10M30S". Null means unknown. */
    val duration: String? = null
)
