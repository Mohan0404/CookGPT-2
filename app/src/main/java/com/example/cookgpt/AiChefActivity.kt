package com.example.cookgpt

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cookgpt.data.GeminiRecipe
import com.example.cookgpt.util.GeminiChef
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class AiChefActivity : AppCompatActivity() {

    private lateinit var prefs: UserPreferencesManager
    private val chef = GeminiChef()
    private lateinit var adapter: PantryAdapter
    private var currentRecipe: GeminiRecipe? = null

    private lateinit var tvRecipeName: TextView
    private lateinit var tvSteps: TextView
    private lateinit var rvPantry: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var cardRecipe: MaterialCardView
    private lateinit var btnOrderZepto: Button
    private lateinit var btnSendSms: Button
    private lateinit var btnGenerateAgain: Button

    private val airplaneModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isAirplaneModeOn = intent?.getBooleanExtra("state", false) ?: false
            updateUiForNetwork(isAirplaneModeOn)
            sendAirplaneModeNotification(context, isAirplaneModeOn)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chef)

        prefs = UserPreferencesManager(this)
        initViews()
        
        // Initial check and trigger
        if (isWifiConnected(this)) {
            generateNewRecipe()
        } else {
            showWifiRequirement()
        }

        registerReceiver(airplaneModeReceiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED))
    }

    private fun initViews() {
        tvRecipeName = findViewById(R.id.tvRecipeName)
        tvSteps = findViewById(R.id.tvSteps)
        rvPantry = findViewById(R.id.rvPantry)
        pbLoading = findViewById(R.id.pbLoading)
        cardRecipe = findViewById(R.id.cardRecipe)
        btnOrderZepto = findViewById(R.id.btnOrderZepto)
        btnSendSms = findViewById(R.id.btnSendSms)
        btnGenerateAgain = findViewById(R.id.btnGenerateAgain)

        rvPantry.layoutManager = LinearLayoutManager(this)

        btnOrderZepto.setOnClickListener { 
            playClickSound()
            handleOrderZepto() 
        }
        btnSendSms.setOnClickListener { 
            playClickSound()
            handleSendSms() 
        }
        btnGenerateAgain.setOnClickListener { 
            playClickSound()
            if (isWifiConnected(this)) {
                generateNewRecipe()
            } else {
                openWifiSettings()
            }
        }
    }

    private fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun openWifiSettings() {
        Toast.makeText(this, "Opening WiFi settings...", Toast.LENGTH_SHORT).show()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
            startActivityForResult(panelIntent, 101)
        } else {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    private fun showWifiRequirement() {
        Toast.makeText(this, "Connect to WiFi to save data while the AI Chef cooks!", Toast.LENGTH_LONG).show()
        pbLoading.visibility = View.GONE
        cardRecipe.visibility = View.GONE
        btnGenerateAgain.visibility = View.VISIBLE
        btnGenerateAgain.text = "Turn ON WiFi"
    }

    private fun updateUiForNetwork(isAirplaneModeOn: Boolean) {
        btnOrderZepto.isEnabled = !isAirplaneModeOn
        if (isAirplaneModeOn) {
            Toast.makeText(this, "Airplane Mode is ON. Network features disabled.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendAirplaneModeNotification(context: Context?, isOn: Boolean) {
        val channelId = "AIRPLANE_MODE_CHANNEL"
        val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "System Updates", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val contentText = if (isOn) {
            "Airplane mode is ON, turn off to generate recipe."
        } else {
            "Airplane Mode has been turned OFF."
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_chef_hat)
            .setContentTitle("CookGPT System Update")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(202, builder.build())
    }

    private fun playClickSound() {
        try {
            val mp = MediaPlayer.create(this, R.raw.btn_click)
            mp.setOnCompletionListener { it.release() }
            mp.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateNewRecipe() {
        pbLoading.visibility = View.VISIBLE
        cardRecipe.visibility = View.GONE
        btnGenerateAgain.visibility = View.GONE

        lifecycleScope.launch {
            val userData = prefs.getCurrentUserData()
            val recipe = chef.generateRecipe(userData, "Lunch")
            if (recipe != null) {
                displayRecipe(recipe)
            } else {
                Toast.makeText(this@AiChefActivity, "Failed to generate recipe. Check connection.", Toast.LENGTH_LONG).show()
                pbLoading.visibility = View.GONE
                btnGenerateAgain.visibility = View.VISIBLE
                btnGenerateAgain.text = "Try Again"
            }
        }
    }

    private fun displayRecipe(recipe: GeminiRecipe) {
        currentRecipe = recipe
        tvRecipeName.text = recipe.title
        tvSteps.text = recipe.steps.joinToString("\n\n") { "• $it" }

        adapter = PantryAdapter(recipe.ingredients) { _, _ -> }
        rvPantry.adapter = adapter

        pbLoading.visibility = View.GONE
        cardRecipe.visibility = View.VISIBLE
        btnGenerateAgain.visibility = View.VISIBLE
        btnGenerateAgain.text = "Generate New Recipe"
    }

    private fun handleOrderZepto() {
        if (!checkOverlayPermission()) return
        val missingItems = adapter.getUncheckedItems()
        if (missingItems.isEmpty()) {
            Toast.makeText(this, "You have all ingredients!", Toast.LENGTH_SHORT).show()
            return
        }
        val serviceIntent = Intent(this, FloatingPantryService::class.java)
        serviceIntent.putStringArrayListExtra("MISSING_INGREDIENTS", ArrayList(missingItems))
        startService(serviceIntent)
        val query = missingItems.first()
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("zepto://search?query=$query"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.zeptonow.com/search?q=$query"))
            startActivity(intent)
        }
    }

    private fun handleSendSms() {
        val recipe = currentRecipe ?: return
        val missingItems = adapter.getUncheckedItems()
        val message = "CookGPT Shopping List for ${recipe.title}:\n" + missingItems.joinToString("\n") { "- $it" }
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:")
                putExtra("sms_body", message)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open SMS app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return false
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101) {
            if (isWifiConnected(this)) {
                generateNewRecipe()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(airplaneModeReceiver)
        } catch (e: Exception) {}
    }
}
