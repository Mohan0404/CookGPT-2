package com.example.cookgpt

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null && SessionManager.isLoggedIn(this)) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_register)

        findViewById<TextView>(R.id.tvLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            handleRegister()
        }
    }

    private fun handleRegister() {
        val email    = findViewById<EditText>(R.id.etEmail).text.toString().trim()
        val phone    = findViewById<EditText>(R.id.etPhone).text.toString().trim()
        val password = findViewById<EditText>(R.id.etPassword).text.toString().trim()
        val confirm  = findViewById<EditText>(R.id.etConfirmPassword).text.toString().trim()

        when {
            email.isEmpty() -> { showError("Email is required"); return }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> { showError("Enter a valid email address"); return }
            phone.isEmpty() -> { showError("Phone number is required"); return }
            password.isEmpty() -> { showError("Password is required"); return }
            password.length < 6 -> { showError("Password must be at least 6 characters"); return }
            confirm.isEmpty() -> { showError("Please confirm your password"); return }
            password != confirm -> { showError("Passwords do not match"); return }
        }

        setLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                Log.d("RegisterActivity", "Auth account created. UID: $uid")

                val userNode = mapOf(
                    "email"        to email,
                    "phone_number" to phone,
                    "created_at"   to ServerValue.TIMESTAMP
                )

                FirebaseDatabase.getInstance().reference
                    .child("users").child(uid)
                    .setValue(userNode)
                    .addOnSuccessListener {
                        Log.d("RegisterActivity", "User node written to DB.")
                        
                        lifecycleScope.launch {
                            val prefs = UserPreferencesManager(this@RegisterActivity)
                            prefs.savePhoneNumber(phone)
                            prefs.saveUserEmail(email)
                            
                            SessionManager.setNewUser(this@RegisterActivity, uid)
                            setLoading(false)
                            startActivity(Intent(this@RegisterActivity, RecipeDiscoveryActivity::class.java))
                            finish()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("RegisterActivity", "DB write failed", e)
                        setLoading(false)
                        SessionManager.setNewUser(this, uid)
                        showError("Profile save failed, but account created.")
                        startActivity(Intent(this, RecipeDiscoveryActivity::class.java))
                        finish()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("RegisterActivity", "Auth failed", e)
                setLoading(false)
                showError(e.localizedMessage ?: "Registration failed.")
            }
    }

    private fun showError(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun setLoading(on: Boolean) {
        findViewById<Button>(R.id.btnRegister).isEnabled = !on
        findViewById<ProgressBar>(R.id.progressBar).visibility = if (on) View.VISIBLE else View.GONE
    }
}
