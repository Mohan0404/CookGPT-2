package com.example.cookgpt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cookgpt.data.ApiService
import com.example.cookgpt.data.Constants
import com.example.cookgpt.data.RecipeResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LocalFlavorActivity : AppCompatActivity() {

    private lateinit var tvCurrentCity: TextView
    private lateinit var etManualCity: EditText
    private lateinit var btnSearch: Button
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var rvRecipes: RecyclerView

    private val apiService = ApiService.create()
    private var detectedCity = ""
    private lateinit var llCityFallback: android.widget.LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_flavor)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        tvCurrentCity  = findViewById(R.id.tv_current_city)
        llCityFallback  = findViewById(R.id.ll_city_fallback)
        etManualCity   = findViewById(R.id.et_manual_city)
        btnSearch      = findViewById(R.id.btn_search_local)
        pbLoading      = findViewById(R.id.pb_loading)
        tvEmptyState   = findViewById(R.id.tv_empty_state)
        rvRecipes      = findViewById(R.id.rv_local_recipes)

        rvRecipes.layoutManager = LinearLayoutManager(this)

        btnSearch.setOnClickListener {
            val city = etManualCity.text.toString().trim().ifEmpty { detectedCity }
            if (city.isEmpty()) {
                Toast.makeText(this, "Enter a city name", Toast.LENGTH_SHORT).show()
            } else {
                searchLocalRecipes(city)
            }
        }

        // Try location-based city detection first
        detectCityFromLocation()
    }

    private fun detectCityFromLocation() {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            tvCurrentCity.text = "Location permission not granted"
            etManualCity.visibility = View.VISIBLE
            btnSearch.visibility = View.VISIBLE
            return
        }

        com.google.android.gms.location.LocationServices
            .getFusedLocationProviderClient(this)
            .lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    reverseGeocodeCity(location.latitude, location.longitude)
                } else {
                    tvCurrentCity.text = "Could not detect location"
                    etManualCity.visibility = View.VISIBLE
                    btnSearch.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener {
                tvCurrentCity.text = "Location unavailable"
                etManualCity.visibility = View.VISIBLE
                btnSearch.visibility = View.VISIBLE
            }
    }

    private fun reverseGeocodeCity(lat: Double, lng: Double) {
        try {
            val geocoder = android.location.Geocoder(this, java.util.Locale.getDefault())
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocation(lat, lng, 1, object : android.location.Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) {
                        runOnUiThread {
                            val city = addresses.firstOrNull()?.locality
                                ?: addresses.firstOrNull()?.subAdminArea
                                ?: ""
                            if (city.isNotEmpty()) {
                                showCityAndSearch(city)
                            } else {
                                showManualEntry()
                            }
                        }
                    }
                    override fun onError(errorMessage: String?) {
                        runOnUiThread { showManualEntry() }
                    }
                })
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                val city = addresses?.firstOrNull()?.locality
                    ?: addresses?.firstOrNull()?.subAdminArea
                    ?: ""
                if (city.isNotEmpty()) showCityAndSearch(city) else showManualEntry()
            }
        } catch (e: Exception) {
            Log.e("LocalFlavor", "Geocoder error: ${e.localizedMessage}")
            showManualEntry()
        }
    }

    private fun showCityAndSearch(city: String) {
        detectedCity = city
        tvCurrentCity.text = "📍 $city"
        Log.d("LocalFlavor", "Detected city: $city — searching recipes")
        searchLocalRecipes(city)
    }

    private fun showManualEntry() {
        tvCurrentCity.text = "Could not detect city"
        llCityFallback.visibility = View.VISIBLE
    }

    /**
     * TASK 3: Use Spoonacular live API with city as query keyword.
     * Replaces all hardcoded when/if city matching.
     */
    private fun searchLocalRecipes(city: String) {
        tvEmptyState.visibility = View.GONE
        pbLoading.visibility    = View.VISIBLE
        rvRecipes.adapter       = null

        val query = "$city traditional food"
        Log.d("LocalFlavor", "Searching Spoonacular for: $query")

        apiService.searchRecipes(query, Constants.API_KEY).enqueue(object : Callback<RecipeResponse> {
            override fun onResponse(call: Call<RecipeResponse>, response: Response<RecipeResponse>) {
                pbLoading.visibility = View.GONE
                if (response.isSuccessful) {
                    val recipes = response.body()?.results ?: emptyList()
                    Log.d("LocalFlavor", "Got ${recipes.size} recipes for $city")
                    if (recipes.isEmpty()) {
                        tvEmptyState.text = "No results found for \"$city\""
                        tvEmptyState.visibility = View.VISIBLE
                    } else {
                        rvRecipes.adapter = RecipeAdapter(recipes) { recipe ->
                            Log.d("Navigation", "Clicked local recipe: ${recipe.title}")
                            val intent = Intent(this@LocalFlavorActivity, RecipeDetailsActivity::class.java)
                            intent.putExtra("RECIPE", recipe)
                            Log.d("Navigation", "Intent created for RecipeDetailsActivity with local recipe: ${recipe.id}")
                            startActivity(intent)
                        }
                    }
                } else {
                    Log.e("LocalFlavor", "API error ${response.code()}")
                    tvEmptyState.text = "API error: ${response.code()}"
                    tvEmptyState.visibility = View.VISIBLE
                }
            }
            override fun onFailure(call: Call<RecipeResponse>, t: Throwable) {
                pbLoading.visibility = View.GONE
                Log.e("LocalFlavor", "Network error: ${t.localizedMessage}")
                tvEmptyState.text = "No connection — check your internet"
                tvEmptyState.visibility = View.VISIBLE
            }
        })
    }
}
