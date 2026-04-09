package com.example.cookgpt

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cookgpt.data.RssApiService
import com.example.cookgpt.data.RssParser
import com.example.cookgpt.databinding.ActivityRssFeedBinding
import com.example.cookgpt.ui.RssFeedAdapter
import com.google.android.gms.ads.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
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

        setupAdMob()
        setupRecyclerView()
        binding.btnBack.setOnClickListener { finish() }

        fetchFeeds()
    }

    private fun setupAdMob() {
        try {
            // Check if we should use test ad ID for development
            val adUnitId = if (BuildConfig.DEBUG) {
                "ca-app-pub-3940256099942544/6300978111" // Official Google Test ID
            } else {
                "ca-app-pub-6826466751729494/5210012579" // Your Production ID
            }
            
            binding.adView.adUnitId = adUnitId
            
            val adRequest = AdRequest.Builder().build()
            binding.adView.adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AdMob", "Ad failed: ${error.message}. Using Test ID fallback.")
                }
                override fun onAdLoaded() {
                    Log.d("AdMob", "Ad loaded successfully")
                }
            }
            binding.adView.loadAd(adRequest)
        } catch (e: Exception) {
            Log.e("AdMob", "Error setup: ${e.message}")
        }
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

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://google.com/")
            .client(okHttpClient)
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
                                } else emptyList()
                            } catch (e: Exception) { emptyList() }
                        }
                    }.awaitAll().flatten().sortedByDescending { it.pubDate }
                }

                binding.progressBar.visibility = View.GONE
                if (results.isEmpty()) {
                    Toast.makeText(this@RssFeedActivity, "No feed updates found. Please check internet.", Toast.LENGTH_SHORT).show()
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
        try {
            val colorSchemeParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(ContextCompat.getColor(this, R.color.bg_green))
                .build()
            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorSchemeParams)
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(this, url.toUri())
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }
    }

    override fun onPause() {
        binding.adView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.adView.resume()
    }

    override fun onDestroy() {
        binding.adView.destroy()
        super.onDestroy()
    }
}
