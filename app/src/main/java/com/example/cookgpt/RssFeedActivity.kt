package com.example.cookgpt

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cookgpt.data.RssApiService
import com.example.cookgpt.data.RssParser
import com.example.cookgpt.databinding.ActivityRssFeedBinding
import com.example.cookgpt.ui.RssFeedAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

class RssFeedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRssFeedBinding
    private lateinit var adapter: RssFeedAdapter
    private val rssParser = RssParser()

    private val feeds = mapOf(
        "Serious Eats" to "https://www.seriouseats.com/rss",
        "Food52" to "https://food52.com/rss",
        "BBC Food" to "https://www.bbc.co.uk/food/rss.xml"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRssFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        binding.btnBack.setOnClickListener { finish() }

        fetchFeeds()
    }

    private fun setupRecyclerView() {
        adapter = RssFeedAdapter { item ->
            openArticle(item.link)
        }
        binding.rvRssFeed.layoutManager = LinearLayoutManager(this)
        binding.rvRssFeed.adapter = adapter
    }

    private fun fetchFeeds() {
        binding.progressBar.visibility = View.VISIBLE

        val retrofit = Retrofit.Builder()
            .baseUrl("https://google.com/") // Base URL is required but overridden by @Url
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        val api = retrofit.create(RssApiService::class.java)

        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    feeds.map { (sourceName, url) ->
                        async {
                            try {
                                val response = api.fetchRss(url)
                                if (response.isSuccessful) {
                                    rssParser.parse(response.body() ?: "", sourceName)
                                } else {
                                    emptyList()
                                }
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }
                    }.awaitAll().flatten().sortedByDescending { it.pubDate }
                }

                binding.progressBar.visibility = View.GONE
                if (results.isEmpty()) {
                    Toast.makeText(this@RssFeedActivity, "No feed updates found", Toast.LENGTH_SHORT).show()
                } else {
                    adapter.submitList(results)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@RssFeedActivity, "Error loading feeds", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openArticle(url: String) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setToolbarColor(ContextCompat.getColor(this, R.color.bg_green))
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(this, Uri.parse(url))
    }
}
