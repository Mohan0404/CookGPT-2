package com.example.cookgpt

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.Locale

class IngredientFinderActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var prefs: SharedPreferences
    private lateinit var tvStatus: TextView
    private lateinit var switchUnit: Switch
    private lateinit var etCityFallback: EditText
    private lateinit var btnFind: Button
    private lateinit var btnFindByCity: Button
    private lateinit var pbLoading: ProgressBar

    private var isMetric: Boolean = true
    private var lastKnownLocation: Location? = null

    // Modern permission launcher (replaces deprecated requestPermissions)
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchLocationAndOpenMaps()
        } else {
            showCityFallback()
            Toast.makeText(this, "Location permission denied — enter your city below", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ingredient_finder)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        prefs = getSharedPreferences("ingredient_finder_prefs", MODE_PRIVATE)

        // Detect locale default unit; allow user to override
        isMetric = !Locale.getDefault().country.equals("US", ignoreCase = true)
        val savedUnit = prefs.getString("distance_unit", null)
        if (savedUnit != null) isMetric = savedUnit == "km"

        tvStatus        = findViewById(R.id.tv_status)
        switchUnit      = findViewById(R.id.switch_unit)
        etCityFallback  = findViewById(R.id.et_city_fallback)
        btnFind         = findViewById(R.id.btn_find_store)
        btnFindByCity   = findViewById(R.id.btn_find_by_city)
        pbLoading       = findViewById(R.id.pb_loading)

        switchUnit.isChecked = isMetric
        switchUnit.text = if (isMetric) "Kilometres" else "Miles"

        switchUnit.setOnCheckedChangeListener { _, checked ->
            isMetric = checked
            switchUnit.text = if (isMetric) "Kilometres" else "Miles"
            prefs.edit().putString("distance_unit", if (isMetric) "km" else "mi").apply()
        }

        btnFind.setOnClickListener {
            pbLoading.visibility = View.VISIBLE
            tvStatus.text = "Requesting location…"
            tvStatus.visibility = View.VISIBLE
            // Always request fresh permission via modern launcher
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        btnFindByCity.setOnClickListener {
            val city = etCityFallback.text.toString().trim()
            if (city.isEmpty()) {
                Toast.makeText(this, "Enter your city name first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openMapsForCity(city)
        }

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
    }

    private fun fetchLocationAndOpenMaps() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                pbLoading.visibility = View.GONE
                if (location != null) {
                    lastKnownLocation = location
                    Log.d("IngredientFinder", "Got location: ${location.latitude}, ${location.longitude}")
                    tvStatus.text = "Opening Google Maps for nearby stores…"
                    openMapsWithCoords(location.latitude, location.longitude)
                } else {
                    tvStatus.text = "Could not get GPS fix — enter city below"
                    showCityFallback()
                    Toast.makeText(this, "GPS not available — please enter your city", Toast.LENGTH_LONG).show()
                }
            }.addOnFailureListener { e ->
                pbLoading.visibility = View.GONE
                Log.e("IngredientFinder", "Location fetch failed: ${e.localizedMessage}")
                tvStatus.text = "Location error — enter city below"
                showCityFallback()
            }
        } catch (e: SecurityException) {
            pbLoading.visibility = View.GONE
            Log.e("IngredientFinder", "Permission missing: ${e.localizedMessage}")
            showCityFallback()
        }
    }

    /**
     * TASK 2: Opens Google Maps showing real nearby grocery stores — no API key required.
     * The zoom level 15z shows a ~1 km radius.
     */
    private fun openMapsWithCoords(lat: Double, lng: Double) {
        val mapsUrl = "https://www.google.com/maps/search/grocery+store/@$lat,$lng,15z"
        openMapsUrl(mapsUrl)
    }

    private fun openMapsForCity(city: String) {
        val encoded = Uri.encode("grocery store in $city")
        val mapsUrl = "https://www.google.com/maps/search/$encoded"
        openMapsUrl(mapsUrl)
    }

    private fun openMapsUrl(url: String) {
        Log.d("IngredientFinder", "Opening Maps: $url")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Log.e("IngredientFinder", "Could not open Maps: ${e.localizedMessage}")
            Toast.makeText(this, "Could not open Google Maps", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCityFallback() {
        pbLoading.visibility   = View.GONE
        etCityFallback.visibility = View.VISIBLE
        btnFindByCity.visibility  = View.VISIBLE
    }
}
