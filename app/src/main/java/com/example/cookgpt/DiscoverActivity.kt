package com.example.cookgpt

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cookgpt.data.YouTubeSearchResponse
import com.example.cookgpt.news.NewsAdapter
import com.example.cookgpt.news.NewsRetrofitClient
import com.example.cookgpt.news.NewsResponse
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DiscoverActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var tvEmptyState: TextView
    private lateinit var pbLoading: ProgressBar
    private lateinit var rvVideos: RecyclerView

    // ADDED — news section state
    private lateinit var newsAdapter: NewsAdapter
    private var currentNewsQuery = "food OR cooking OR nutrition OR healthy eating"

    private var videoQueue: List<com.example.cookgpt.data.YouTubeItem> = emptyList()

    // 600ms debounce
    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    // ──────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discover)

        etSearch          = findViewById(R.id.etSearch)
        tvEmptyState      = findViewById(R.id.tvEmptyState)
        pbLoading         = findViewById(R.id.pbLoading)
        rvVideos          = findViewById(R.id.rvVideos)

        rvVideos.layoutManager = LinearLayoutManager(this)
        rvVideos.setHasFixedSize(false)

        // 600ms debounce — minimum 3 characters before firing
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Cancel any pending search on EVERY keystroke
                searchRunnable?.let { handler.removeCallbacks(it) }
            }

            override fun afterTextChanged(s: Editable?) {
                // TASK 3 — log every keystroke; API only fires after 600ms pause
                Log.d("VIDEO_DEBUG", "TEXT CHANGED: ${s.toString()}")
                val query = s.toString().trim()
                if (query.length < 3) return
                searchRunnable = Runnable { searchYouTube(query) }
                handler.postDelayed(searchRunnable!!, 600)
            }
        })

        // ADDED — initialise news feed
        setupNewsSection()
    }

    // ── Search: fetch 10 embeddable videos ────────────────────────────────
    private fun searchYouTube(query: String) {
        // TASK 1 — log search trigger
        Log.d("VIDEO_DEBUG", "SEARCH TRIGGERED: $query")

        val apiKey = BuildConfig.YOUTUBE_API_KEY
        if (apiKey.isEmpty() || apiKey == "dummy_key") {
            Log.e("VIDEO_DEBUG", "YOUTUBE API KEY MISSING OR PLACEHOLDER — aborting search")
            Toast.makeText(
                this,
                "Add your YouTube Data API v3 key to local.properties\n(YOUTUBE_API_KEY=...)",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Show progress, hide list and empty state.
        pbLoading.visibility    = View.VISIBLE
        rvVideos.visibility     = View.GONE
        tvEmptyState.visibility = View.GONE

        // TASK 3 — richer query targets full recipe tutorials, not clips or Shorts
        val searchQuery = "$query full recipe cooking tutorial"
        Log.d("VIDEO_DEBUG", "FULL SEARCH QUERY SENT TO API: $searchQuery")

        YouTubeRetrofitClient.instance
            .searchVideos(query = searchQuery, apiKey = apiKey)
            .enqueue(object : Callback<YouTubeSearchResponse> {

                override fun onResponse(
                    call: Call<YouTubeSearchResponse>,
                    response: Response<YouTubeSearchResponse>
                ) {
                    pbLoading.visibility = View.GONE

                    // TASK 2 — log raw API result
                    Log.d("VIDEO_DEBUG", "API CALLED SUCCESSFULLY — HTTP ${response.code()}")

                    if (!response.isSuccessful) {
                        Log.e("VIDEO_DEBUG", "API RESPONSE NOT SUCCESSFUL: ${response.code()} ${response.message()}")
                        showEmpty("API error (${response.code()}). Try again.")
                        Toast.makeText(
                            this@DiscoverActivity,
                            "YouTube search failed: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    val body = response.body()
                    if (body == null) {
                        Log.e("VIDEO_DEBUG", "RESPONSE BODY IS NULL")
                        showEmpty("Empty response. Try again.")
                        return
                    }

                    // TASK 2 — log every video returned by the API
                    val rawItems = body.items
                    if (rawItems.isNullOrEmpty()) {
                        Log.d("VIDEO_DEBUG", "NO VIDEOS RETURNED by API")
                    } else {
                        Log.d("VIDEO_DEBUG", "TOTAL ITEMS IN RESPONSE: ${rawItems.size}")
                        rawItems.forEachIndexed { index, item ->
                            Log.d("VIDEO_DEBUG",
                                "$index -> videoId=${item.id?.videoId} | title=${item.snippet?.title}")
                        }
                    }

                    // ── TASK 1 & 7 — STRICT SHORTS FILTER + SMART RANKING ────────
                    val filteredItems = rawItems?.filter { item ->
                        val title   = item.snippet?.title ?: return@filter false
                        val videoId = item.id?.videoId    ?: return@filter false

                        val isShort = title.contains("#short",  ignoreCase = true) ||
                                      title.contains("shorts",   ignoreCase = true) ||
                                      title.contains("#shorts",  ignoreCase = true) ||
                                      title.length < 20

                        if (isShort) {
                            Log.d("VIDEO_DEBUG", "EXCLUDED: videoId=$videoId | title=\"$title\"")
                        }
                        !isShort
                    } ?: emptyList()

                    // TASK 7: Smart Ranking — prefer "recipe", "cooking", "full" in title
                    val rankedItems = filteredItems.sortedByDescending { item ->
                        val title = item.snippet?.title?.lowercase() ?: ""
                        var score = 0
                        if (title.contains("recipe"))  score += 10
                        if (title.contains("cooking")) score += 5
                        if (title.contains("full"))    score += 5
                        if (title.contains("tutorial")) score += 3
                        score
                    }

                    Log.d("VIDEO_DEBUG", "TOTAL: ${rawItems?.size} → VALID/RANKED: ${rankedItems.size}")

                    if (rankedItems.isEmpty()) {
                        Log.d("VIDEO_DEBUG", "NO VALID VIDEOS after filter — showing empty state")
                        showEmpty("No full recipe videos found for \"$query\". Try a more specific name.")
                        return
                    }

                    // TASK 2: Store for fallback
                    videoQueue = rankedItems

                    // Populate RecyclerView — user must tap a card to play
                    tvEmptyState.visibility = View.GONE
                    rvVideos.visibility     = View.VISIBLE
                    rvVideos.adapter        = VideoAdapter(rankedItems)
                }

                override fun onFailure(call: Call<YouTubeSearchResponse>, t: Throwable) {
                    // TASK 1 — log API failure
                    Log.e("VIDEO_DEBUG", "API FAILED: ${t.message}", t)
                    pbLoading.visibility = View.GONE
                    showEmpty("No connection. Check internet and try again.")
                    Toast.makeText(
                        this@DiscoverActivity,
                        "Network error: ${t.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    // ─────────────────────────────────────────────────────
    // ADDED — News Section (moved from HomeActivity)
    // ─────────────────────────────────────────────────────

    private fun setupNewsSection() {
        newsAdapter = NewsAdapter(mutableListOf()) { article -> openArticleInBrowser(article.url) }
        val rvNews = findViewById<RecyclerView>(R.id.rvNews)
        rvNews.layoutManager = LinearLayoutManager(this)
        rvNews.adapter = newsAdapter
        rvNews.isNestedScrollingEnabled = false
        setupFilterChips()

        // ADDED — load personalised news from user profile
        val uid = SessionManager.getUserId(this)
        if (uid.isNotEmpty()) {
            FirebaseProfileLoader.loadUserProfile(uid, { profile ->
                val baseQuery = "food OR cooking OR nutrition"
                val userQuery = buildString {
                    append(baseQuery)
                    if (profile.dietType.isNotEmpty())         append(" OR ${profile.dietType} food")
                    if (profile.fitnessGoal.isNotEmpty())      append(" OR ${profile.fitnessGoal} diet")
                    if (profile.preferredCuisine.isNotEmpty()) append(" OR ${profile.preferredCuisine} recipes")
                }
                currentNewsQuery = userQuery
                loadNews(currentNewsQuery)
            }, {
                loadNews(currentNewsQuery)
            })
        } else {
            loadNews(currentNewsQuery)
        }
    }

    private fun setupFilterChips() {
        val chipGroupNewsFilter = findViewById<ChipGroup>(R.id.chipGroupNewsFilter)
        chipGroupNewsFilter.setOnCheckedStateChangeListener { group, checkedIds ->
            val selectedChip = group.findViewById<Chip>(checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener)
            val query = getQueryForChip(selectedChip.text.toString())
            currentNewsQuery = query
            newsAdapter.currentCategory = selectedChip.text.toString()
            loadNews(query)
        }
    }

    private fun loadNews(query: String) {
        val progressBarNews = findViewById<ProgressBar>(R.id.progressBarNews)
        val tvNoNews = findViewById<TextView>(R.id.tvNoNews)
        progressBarNews.visibility = View.VISIBLE
        NewsRetrofitClient.instance.getNews(query = query, apiKey = BuildConfig.NEWS_API_KEY).enqueue(object : Callback<NewsResponse> {
            override fun onResponse(call: Call<NewsResponse>, response: Response<NewsResponse>) {
                progressBarNews.visibility = View.GONE
                if (response.isSuccessful) {
                    val articles = response.body()?.articles ?: emptyList()
                    newsAdapter.updateArticles(articles)
                    tvNoNews.visibility = if (articles.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    showNewsError("Could not load news. Try again.")
                }
            }
            override fun onFailure(call: Call<NewsResponse>, t: Throwable) {
                progressBarNews.visibility = View.GONE
                showNewsError("Network error: ${t.localizedMessage}")
            }
        })
    }

    // ADDED — chip label → NewsAPI query mapping
    private fun getQueryForChip(label: String): String = when (label) {
        "Healthy"      -> "healthy food OR clean eating OR wholesome meals"
        "Fitness"      -> "fitness meals OR workout nutrition OR sports diet"
        "Diet"         -> "diet food OR weight loss diet OR calorie deficit"
        "Recipes"      -> "easy recipes OR quick cooking OR meal prep"
        "Nutrition"    -> "nutrition tips OR vitamins OR macros OR superfoods"
        "Weight Loss"  -> "weight loss food OR fat burning meals OR low calorie"
        "Vegan"        -> "vegan recipes OR plant based food OR vegan nutrition"
        "High Protein" -> "high protein meals OR protein rich food OR muscle diet"
        else           -> "food OR cooking OR nutrition OR healthy eating" // All
    }

    private fun openArticleInBrowser(url: String) {
        val builder = CustomTabsIntent.Builder().setShowTitle(true).setToolbarColor(ContextCompat.getColor(this, R.color.colorPrimary)).build()
        builder.launchUrl(this, Uri.parse(url))
    }

    private fun showNewsError(message: String) {
        val tvNoNews = findViewById<TextView>(R.id.tvNoNews)
        tvNoNews.text = message
        tvNoNews.visibility = View.VISIBLE
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private fun showEmpty(message: String) {
        rvVideos.visibility     = View.GONE
        tvEmptyState.text       = message
        tvEmptyState.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        searchRunnable?.let { handler.removeCallbacks(it) }
    }
}
