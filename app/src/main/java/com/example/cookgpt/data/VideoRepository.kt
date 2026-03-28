package com.example.cookgpt.data

import android.util.Log
import java.util.regex.Pattern

/**
 * Fetches a YouTube search result, then verifies every video via the Videos API.
 * Only returns videos that are confirmed embeddable with a valid duration.
 */
class VideoRepository(
    private val apiKey: String,
    private val apiService: YouTubeApiService = YouTubeApiService.create()
) {
    companion object {
        private const val TAG = "VideoRepository"
        private const val MIN_DURATION_SECONDS = 60
        private const val MAX_DURATION_SECONDS = 7200
        private val BLOCKED_TITLE_TOKENS = listOf(
            "#shorts", "#short", "shorts",
            "🔴 live", "| live", "live stream", "livestream",
            "full movie", "full episode"
        )
        private val ISO_DURATION: Pattern =
            Pattern.compile("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""")
    }

    /**
     * Returns a list of verified, embeddable video candidates for [query].
     * Callers pass this list to VideoPlayerManager as the play queue.
     */
    suspend fun fetchVerifiedVideos(query: String, maxResults: Int = 15): List<VideoCandidate> {
        // ── Step 1: Search API ──────────────────────────────────────────
        val searchItems: List<YouTubeItem> = try {
            val response = apiService.searchVideos(
                query      = "$query recipe",
                apiKey     = apiKey,
                maxResults = maxResults
            ).awaitResponse()
            if (!response.isSuccessful) {
                Log.e(TAG, "Search API ${response.code()} for: $query")
                return emptyList()
            }
            response.body()?.items.orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Search exception: ${e.message}", e)
            return emptyList()
        }

        if (searchItems.isEmpty()) return emptyList()

        // ── Step 2: Title filter ────────────────────────────────────────
        val filtered = searchItems.filter { item ->
            val title = item.snippet?.title?.lowercase().orEmpty()
            val id    = item.id?.videoId
            id != null && title.isNotBlank() &&
                BLOCKED_TITLE_TOKENS.none { title.contains(it, ignoreCase = true) }
        }
        if (filtered.isEmpty()) return emptyList()

        // ── Step 3: Videos API — authoritative embeddability check ──────
        val ids = filtered.mapNotNull { it.id?.videoId }.joinToString(",")
        val detailMap: Map<String, YouTubeVideoDetail> = try {
            val response = apiService.getVideoDetails(ids = ids, apiKey = apiKey).awaitResponse()
            if (!response.isSuccessful) {
                // Degrade: return title-filtered list without duration check
                return filtered.mapNotNull { it.toCandidate() }
            }
            response.body()?.items
                ?.filter { it.id != null }
                ?.associateBy { it.id!! }
                .orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Videos API exception: ${e.message}", e)
            return filtered.mapNotNull { it.toCandidate() }
        }

        // ── Step 4: Build verified queue ────────────────────────────────
        return filtered.mapNotNull { item ->
            val videoId = item.id?.videoId ?: return@mapNotNull null
            val detail  = detailMap[videoId] ?: return@mapNotNull null  // deleted/private
            // status is nullable in the model — treat missing status as non-embeddable
            if (detail.status?.embeddable != true) return@mapNotNull null
            val secs = detail.contentDetails?.duration?.let { parseDuration(it) } ?: 0
            if (secs < MIN_DURATION_SECONDS || secs > MAX_DURATION_SECONDS) return@mapNotNull null
            VideoCandidate(
                videoId      = videoId,
                title        = item.snippet?.title.orEmpty(),
                channelTitle = item.snippet?.channelTitle.orEmpty(),
                thumbnailUrl = item.snippet?.thumbnails?.high?.url
                    ?: item.snippet?.thumbnails?.medium?.url,
                durationSecs = secs
            )
        }
    }

    private fun parseDuration(iso: String): Int {
        val m = ISO_DURATION.matcher(iso)
        if (!m.matches()) return 0
        return (m.group(1)?.toIntOrNull() ?: 0) * 3600 +
               (m.group(2)?.toIntOrNull() ?: 0) * 60 +
               (m.group(3)?.toIntOrNull() ?: 0)
    }

    private fun YouTubeItem.toCandidate(): VideoCandidate? {
        val videoId = id?.videoId ?: return null
        return VideoCandidate(
            videoId      = videoId,
            title        = snippet?.title.orEmpty(),
            channelTitle = snippet?.channelTitle.orEmpty(),
            thumbnailUrl = snippet?.thumbnails?.high?.url ?: snippet?.thumbnails?.medium?.url,
            durationSecs = 0
        )
    }
}

/** A single verified video candidate ready for playback. */
data class VideoCandidate(
    val videoId:      String,
    val title:        String,
    val channelTitle: String,
    val thumbnailUrl: String?,
    val durationSecs: Int
)
