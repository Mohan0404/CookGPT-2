package com.example.cookgpt

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.*

class LocalFlavorActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tvCurrentCity: TextView
    private lateinit var tvRecipeTitle: TextView
    private lateinit var tvRecipeDescription: TextView
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvSuggestionHeader: TextView
    private lateinit var cardSuggestion: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_flavor)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        tvCurrentCity = findViewById(R.id.tv_current_city)
        tvRecipeTitle = findViewById(R.id.tv_recipe_title)
        tvRecipeDescription = findViewById(R.id.tv_recipe_description)
        pbLoading = findViewById(R.id.pb_loading)
        tvSuggestionHeader = findViewById(R.id.tv_suggestion_header)
        cardSuggestion = findViewById(R.id.card_suggestion)
        
        val btnDetect = findViewById<Button>(R.id.btn_detect_location)
        btnDetect.setOnClickListener { detectLocation() }

        detectLocation()
    }

    private fun detectLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }

        pbLoading.visibility = View.VISIBLE
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                reverseGeocode(location.latitude, location.longitude)
            } else {
                pbLoading.visibility = View.GONE
                Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun reverseGeocode(lat: Double, lng: Double) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            // Using the modern listener-based API for Geocoder if available, otherwise falling back
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        runOnUiThread {
                            processAddress(addresses)
                        }
                    }
                    override fun onError(errorMessage: String?) {
                        runOnUiThread {
                            tvCurrentCity.text = "Error detecting city"
                            pbLoading.visibility = View.GONE
                        }
                    }
                })
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                processAddress(addresses)
                pbLoading.visibility = View.GONE
            }
        } catch (e: Exception) {
            tvCurrentCity.text = "Error detecting city"
            pbLoading.visibility = View.GONE
        }
    }

    private fun processAddress(addresses: List<Address>?) {
        if (!addresses.isNullOrEmpty()) {
            val city = addresses[0].locality ?: addresses[0].subAdminArea ?: "Unknown City"
            val country = addresses[0].countryName ?: ""
            tvCurrentCity.text = "$city, $country"
            suggestLocalRecipe(city)
        } else {
            tvCurrentCity.text = "City not found"
        }
    }

    private fun suggestLocalRecipe(city: String) {
        tvSuggestionHeader.visibility = View.VISIBLE
        cardSuggestion.visibility = View.VISIBLE
        
        when {
            city.contains("New Orleans", true) -> {
                tvRecipeTitle.text = "Shrimp Gumbo"
                tvRecipeDescription.text = "A classic Creole specialty from New Orleans. Rich roux, holy trinity of veggies, and fresh local shrimp."
            }
            city.contains("Tokyo", true) -> {
                tvRecipeTitle.text = "Miso Glazed Salmon"
                tvRecipeDescription.text = "Fresh seasonal salmon with a traditional Japanese miso glaze, popular in the Tokyo region."
            }
            city.contains("London", true) -> {
                tvRecipeTitle.text = "Fish and Chips"
                tvRecipeDescription.text = "The ultimate British comfort food. Crispy battered fish served with chunky chips."
            }
            else -> {
                tvRecipeTitle.text = "Seasonal Garden Salad"
                tvRecipeDescription.text = "Since you are in $city, we recommend a fresh salad using local seasonal greens and herbs from your region."
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            detectLocation()
        }
    }
}
