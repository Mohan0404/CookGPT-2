package com.example.cookgpt

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.*
import kotlin.math.*

data class SampleStore(val name: String, val latitude: Double, val longitude: Double, val address: String)

class IngredientFinderActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var etStoreName: EditText
    private lateinit var tvStoreResult: TextView
    private lateinit var tvDistanceResult: TextView
    private lateinit var tvAddressResult: TextView
    private lateinit var pbLoading: ProgressBar
    private lateinit var cardResult: View

    private var userLocation: Location? = null

    // Sample stores at different global locations
    private val sampleStores = listOf(
        SampleStore("Whole Foods Market", 40.7812, -73.9665, "10 Columbus Cir, New York, NY 10019"),
        SampleStore("Trader Joe's", 34.0195, -118.4912, "500 Broadway, Santa Monica, CA 90401"),
        SampleStore("Tesco Express", 51.5072, -0.1276, "Charing Cross, London WC2N 5HS, UK"),
        SampleStore("Local Veggie Stand", 35.6895, 139.6917, "2-8-1 Nishi-Shinjuku, Shinjuku, Tokyo"),
        SampleStore("Healthy Greens", 1.2902, 103.8519, "City Hall, Singapore"),
        SampleStore("Organic Haven", -33.8688, 151.2093, "Sydney CBD, NSW, Australia"),
        // Additional "Nearby" mock stores (assumes user might be testing in common dev locations)
        SampleStore("Quick Mart", 12.9716, 77.5946, "MG Road, Bangalore, India"),
        SampleStore("Convenience Plus", 37.7749, -122.4194, "Market St, San Francisco, CA"),
        SampleStore("Green Grocers", 48.8566, 2.3522, "Rue de Rivoli, Paris, France")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ingredient_finder)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        etStoreName = findViewById(R.id.et_store_name)
        tvStoreResult = findViewById(R.id.tv_store_result)
        tvDistanceResult = findViewById(R.id.tv_distance_result)
        tvAddressResult = findViewById(R.id.tv_address_result)
        pbLoading = findViewById(R.id.pb_loading)
        cardResult = findViewById(R.id.card_result)

        val btnFind = findViewById<Button>(R.id.btn_find_store)
        btnFind.setOnClickListener {
            val store = etStoreName.text.toString()
            if (store.isNotEmpty()) {
                findStore(store)
            } else {
                // If input is empty, find the ABSOLUTE closest store from our sample list
                findClosestSampleStore()
            }
        }

        requestLocation()
    }

    private fun requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            userLocation = location
        }
    }

    private fun findStore(storeName: String) {
        if (userLocation == null) {
            Toast.makeText(this, "Location not available. Please ensure GPS is on.", Toast.LENGTH_SHORT).show()
            return
        }

        pbLoading.visibility = View.VISIBLE
        cardResult.visibility = View.GONE

        // 1. Check in Sample Stores first for an exact or partial match
        val matchedStore = sampleStores.find { it.name.contains(storeName, ignoreCase = true) }
        
        if (matchedStore != null) {
            displayStore(matchedStore.name, matchedStore.latitude, matchedStore.longitude, matchedStore.address)
        } else {
            // 2. Fallback to Geocoder for real stores
            performGeocodeSearch(storeName)
        }
    }

    private fun findClosestSampleStore() {
        if (userLocation == null) {
            Toast.makeText(this, "Detecting location... please try again in a moment.", Toast.LENGTH_SHORT).show()
            return
        }

        pbLoading.visibility = View.VISIBLE
        
        var closestStore: SampleStore? = null
        var minDistance = Double.MAX_VALUE

        for (store in sampleStores) {
            val dist = calculateDistance(userLocation!!.latitude, userLocation!!.longitude, store.latitude, store.longitude)
            if (dist < minDistance) {
                minDistance = dist
                closestStore = store
            }
        }

        if (closestStore != null) {
            displayStore(closestStore.name, closestStore.latitude, closestStore.longitude, closestStore.address)
            Toast.makeText(this, "Found the closest sample store!", Toast.LENGTH_SHORT).show()
        } else {
            pbLoading.visibility = View.GONE
        }
    }

    private fun performGeocodeSearch(storeName: String) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocationName(storeName, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        runOnUiThread {
                            if (!addresses.isNullOrEmpty()) {
                                val addr = addresses[0]
                                displayStore(storeName, addr.latitude, addr.longitude, addr.getAddressLine(0) ?: "Address not found")
                            } else {
                                Toast.makeText(this@IngredientFinderActivity, "Store not found locally or globally", Toast.LENGTH_SHORT).show()
                                pbLoading.visibility = View.GONE
                            }
                        }
                    }
                    override fun onError(errorMessage: String?) {
                        runOnUiThread {
                            Toast.makeText(this@IngredientFinderActivity, "Search error: $errorMessage", Toast.LENGTH_SHORT).show()
                            pbLoading.visibility = View.GONE
                        }
                    }
                })
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(storeName, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    displayStore(storeName, addr.latitude, addr.longitude, addr.getAddressLine(0) ?: "Address not found")
                } else {
                    Toast.makeText(this, "Store not found", Toast.LENGTH_SHORT).show()
                    pbLoading.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Search error", Toast.LENGTH_SHORT).show()
            pbLoading.visibility = View.GONE
        }
    }

    private fun displayStore(name: String, lat: Double, lng: Double, address: String) {
        val distance = calculateDistance(
            userLocation!!.latitude, userLocation!!.longitude,
            lat, lng
        )

        tvStoreResult.text = name
        tvDistanceResult.text = "${String.format(Locale.getDefault(), "%.1f", distance)} miles away"
        tvAddressResult.text = address
        cardResult.visibility = View.VISIBLE
        pbLoading.visibility = View.GONE
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 3958.8 // Radius of the earth in miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestLocation()
        }
    }
}
