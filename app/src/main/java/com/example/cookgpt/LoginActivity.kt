package com.example.cookgpt

import android.content.Context
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // If already fully logged in skip straight to HomeActivity
        if (auth.currentUser != null && SessionManager.isLoggedIn(this)) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        progressBar = findViewById(R.id.progressBar)
        tvStatus    = findViewById(R.id.tvStatus)
        btnLogin    = findViewById(R.id.btnLogin)

        findViewById<TextView>(R.id.tvRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        btnLogin.setOnClickListener { handleLogin() }
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

                // Show restore status while fetching data from Firebase
                setLoading(true, "Restoring your data…")

                fetchAndRestoreUserData(uid) {
                    // onComplete is called on a background thread — must post to UI
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

    /**
     * Dual-flag pattern: both profile AND recipes must complete before onComplete() fires.
     * Restores from CORRECT paths:
     *   profile → /users/{uid}/
     *   recipes → /users/{uid}/saved_recipes/
     */
    private fun fetchAndRestoreUserData(uid: String, onComplete: () -> Unit) {
        val db       = FirebaseDatabase.getInstance().reference
        val prefs    = getSharedPreferences("user_data", Context.MODE_PRIVATE)
        val dbHelper = RecipeDatabaseHelper(this)

        var profileDone = false
        var recipesDone = false

        fun checkCompletion() {
            if (profileDone && recipesDone) onComplete()
        }

        // ── Restore profile from /users/{uid}/ ─────────────────────────────
        db.child("users").child(uid).get()
            .addOnSuccessListener { snapshot ->
                Log.d("Restore", "Profile snapshot received: ${snapshot.value}")
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
                // TASK 9: Populate DataStore so HomeActivity greeting shows real name
                if (restoredName.isNotEmpty()) {
                    lifecycleScope.launch {
                        UserPreferencesManager(this@LoginActivity).saveUserName(restoredName)
                    }
                }
                profileDone = true
                checkCompletion()
            }
            .addOnFailureListener { e ->
                Log.e("Restore", "Profile restore failed: ${e.localizedMessage}")
                profileDone = true
                checkCompletion()
            }

        // ── Restore recipes from /users/{uid}/saved_recipes/ ───────────────
        db.child("users").child(uid).child("saved_recipes").get()
            .addOnSuccessListener { snapshot ->
                Log.d("Restore", "Recipe snapshot exists=${snapshot.exists()}, count=${snapshot.childrenCount}")

                dbHelper.clearAllRecipes()

                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    Log.d("Restore", "No saved recipes in Firebase for uid=$uid")
                    recipesDone = true
                    checkCompletion()
                    return@addOnSuccessListener
                }

                for (child in snapshot.children) {
                    try {
                        val recipe = child.getValue(SavedRecipe::class.java)
                        if (recipe != null) {
                            dbHelper.insertRecipe(recipe, uid)
                            Log.d("Restore", "Restored recipe: id=${recipe.id} title=${recipe.title}")
                        } else {
                            Log.w("Restore", "Firebase returned null for recipe key=${child.key}")
                        }
                    } catch (e: Exception) {
                        Log.e("Restore", "Failed to parse recipe key=${child.key}: ${e.localizedMessage}")
                    }
                }

                recipesDone = true
                checkCompletion()
            }
            .addOnFailureListener { e ->
                Log.e("Restore", "Recipe restore failed: ${e.localizedMessage}")
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
