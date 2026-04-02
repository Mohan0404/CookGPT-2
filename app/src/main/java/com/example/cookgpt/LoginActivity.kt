package com.example.cookgpt

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.cookgpt.data.RecipeDatabaseHelper
import com.example.cookgpt.data.SavedRecipe
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var btnLogin: Button
    private lateinit var videoViewBg: VideoView
    private var mediaPlayerClick: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make it full screen by hiding the status bar
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null && SessionManager.isLoggedIn(this)) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        progressBar = findViewById(R.id.progressBar)
        tvStatus    = findViewById(R.id.tvStatus)
        btnLogin    = findViewById(R.id.btnLogin)
        videoViewBg  = findViewById(R.id.videoViewBg)

        setupBackgroundVideo()
        
        mediaPlayerClick = MediaPlayer.create(this, R.raw.btn_click)

        findViewById<TextView>(R.id.tvRegister).setOnClickListener {
            playClickSound()
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        btnLogin.setOnClickListener { 
            playClickSound()
            handleLogin() 
        }
    }

    private fun setupBackgroundVideo() {
        val videoPath = "android.resource://" + packageName + "/" + R.raw.bg_video
        val uri = Uri.parse(videoPath)
        videoViewBg.setVideoURI(uri)
        videoViewBg.setOnPreparedListener { mp ->
            mp.isLooping = true
            
            // Calculate video and screen aspect ratios
            val videoWidth = mp.videoWidth.toFloat()
            val videoHeight = mp.videoHeight.toFloat()
            val videoRatio = videoWidth / videoHeight
            
            val screenWidth = videoViewBg.width.toFloat()
            val screenHeight = videoViewBg.height.toFloat()
            val screenRatio = screenWidth / screenHeight

            // Scale the video to cover the entire screen (Center Crop effect)
            if (videoRatio > screenRatio) {
                // Video is wider than screen
                videoViewBg.scaleX = videoRatio / screenRatio
            } else {
                // Video is taller than screen
                videoViewBg.scaleY = screenRatio / videoRatio
            }

            videoViewBg.start()
        }
    }

    private fun playClickSound() {
        mediaPlayerClick?.start()
    }

    override fun onResume() {
        super.onResume()
        videoViewBg.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayerClick?.release()
        mediaPlayerClick = null
    }

    private fun handleLogin() {
        val email    = findViewById<EditText>(R.id.etEmail).text.toString().trim()
        val password = findViewById<EditText>(R.id.etPassword).text.toString().trim()

        when {
            email.isEmpty()    -> { showError("Enter your email address"); return }
            password.isEmpty() -> { showError("Enter your password"); return }
        }

        setLoading(true, "Signing in…")

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                Log.d("LoginActivity", "Sign-in success. UID=$uid")
                SessionManager.setLogin(this, uid)

                setLoading(true, "Restoring your data…")

                fetchAndRestoreUserData(uid) {
                    runOnUiThread {
                        setLoading(false)
                        val intent = Intent(this, HomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("LoginActivity", "Sign-in failed: ${e.localizedMessage}")
                setLoading(false)
                showError(e.localizedMessage ?: "Login failed. Check your email and password.")
            }
    }

    private fun fetchAndRestoreUserData(uid: String, onComplete: () -> Unit) {
        val db       = FirebaseDatabase.getInstance().reference
        val prefs    = getSharedPreferences("user_data", Context.MODE_PRIVATE)
        val dbHelper = RecipeDatabaseHelper(this)

        var profileDone = false
        var recipesDone = false

        fun checkCompletion() {
            if (profileDone && recipesDone) onComplete()
        }

        db.child("users").child(uid).get()
            .addOnSuccessListener { snapshot ->
                val restoredName = snapshot.child("name").getValue(String::class.java) ?: ""
                prefs.edit()
                    .putString("name",      restoredName)
                    .putString("age",       snapshot.child("age").getValue(String::class.java) ?: "")
                    .putString("height",    snapshot.child("height").getValue(String::class.java) ?: "")
                    .putString("weight",    snapshot.child("weight").getValue(String::class.java) ?: "")
                    .putString("gender",    snapshot.child("gender").getValue(String::class.java) ?: "")
                    .putString("goal",      snapshot.child("goal").getValue(String::class.java) ?: "")
                    .putString("allergies", snapshot.child("allergies").getValue(String::class.java) ?: "")
                    .putString("email",     snapshot.child("email").getValue(String::class.java)
                        ?: auth.currentUser?.email ?: "")
                    .apply()
                if (restoredName.isNotEmpty()) {
                    lifecycleScope.launch {
                        UserPreferencesManager(this@LoginActivity).saveUserName(restoredName)
                    }
                }
                profileDone = true
                checkCompletion()
            }
            .addOnFailureListener { e ->
                profileDone = true
                checkCompletion()
            }

        db.child("users").child(uid).child("saved_recipes").get()
            .addOnSuccessListener { snapshot ->
                dbHelper.clearAllRecipes()
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    recipesDone = true
                    checkCompletion()
                    return@addOnSuccessListener
                }
                for (child in snapshot.children) {
                    try {
                        val recipe = child.getValue(SavedRecipe::class.java)
                        if (recipe != null) {
                            dbHelper.insertRecipe(recipe, uid)
                        }
                    } catch (e: Exception) {
                        Log.e("Restore", "Failed to parse recipe: ${e.localizedMessage}")
                    }
                }
                recipesDone = true
                checkCompletion()
            }
            .addOnFailureListener { e ->
                recipesDone = true
                checkCompletion()
            }
    }

    private fun showError(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun setLoading(on: Boolean, statusText: String = "") {
        btnLogin.isEnabled      = !on
        progressBar.visibility  = if (on) View.VISIBLE else View.GONE
        tvStatus.visibility     = if (on && statusText.isNotEmpty()) View.VISIBLE else View.GONE
        if (statusText.isNotEmpty()) tvStatus.text = statusText
    }
}
