package com.example.cookgpt

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private var authListenerFired = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── IMPROVEMENT 4: AuthStateListener handles token expiry gracefully ──
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            // Guard: only run once — the listener can fire multiple times
            if (authListenerFired) return@addAuthStateListener
            authListenerFired = true

            val destination = when {
                auth.currentUser != null && SessionManager.isLoggedIn(this) -> {
                    Log.d("SplashActivity", "Valid session → HomeActivity")
                    HomeActivity::class.java
                }
                else -> {
                    // If Firebase session expired but local prefs still say logged in — clear it
                    if (SessionManager.isLoggedIn(this)) {
                        Log.w("SplashActivity", "Firebase session expired — clearing local session")
                        SessionManager.logout(this)
                    } else {
                        Log.d("SplashActivity", "No session → LoginActivity")
                    }
                    LoginActivity::class.java
                }
            }

            val intent = Intent(this, destination)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
