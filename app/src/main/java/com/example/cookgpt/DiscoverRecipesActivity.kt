package com.example.cookgpt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.os.Handler
import android.os.Looper
import com.example.cookgpt.data.ApiService
import com.example.cookgpt.data.Constants
import com.example.cookgpt.data.Recipe
import com.example.cookgpt.data.RecipeResponse
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DiscoverRecipesActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnSearch: ImageButton
    private lateinit var rvRecipes: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var cgDiet: ChipGroup
    private lateinit var cgHistory: ChipGroup
    private lateinit var tvHistoryLabel: TextView

    private val apiService = ApiService.create()
    private val gson = Gson()
    private lateinit var prefs: android.content.SharedPreferences

    // Currently selected diet chip value (empty = no filter)
    private var selectedDiet = ""

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discover_recipes)

        prefs     = getSharedPreferences("discover_prefs", Context.MODE_PRIVATE)

        etSearch      = findViewById(R.id.etSearch)
        btnSearch     = findViewById(R.id.btnSearch)
        rvRecipes     = findViewById(R.id.rvRecipes)
        progressBar   = findViewById(R.id.progressBar)
        tvEmptyState  = findViewById(R.id.tvEmptyState)
        cgDiet        = findViewById(R.id.cgDiet)
        cgHistory     = findViewById(R.id.cgHistory)
        tvHistoryLabel = findViewById(R.id.tvHistoryLabel)

        rvRecipes.layoutManager = GridLayoutManager(this, 2)

        // TASK 4: Diet chips
        cgDiet.setOnCheckedStateChangeListener { group, checkedIds ->
            selectedDiet = if (checkedIds.isNotEmpty()) {
                (group.findViewById<Chip>(checkedIds[0])).text.toString().lowercase()
            } else ""
        }

        // TASK 6: Show/hide history chipgroup based on focus and text
        etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && etSearch.text.isEmpty()) refreshHistoryChips(show = true)
        }
        
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshHistoryChips(show = s.isNullOrEmpty())
                
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                
                val query = s?.toString()?.trim() ?: ""
                
                if (query.isEmpty()) {
                    searchRunnable = Runnable { searchRecipes("popular recipes") }
                    searchHandler.postDelayed(searchRunnable!!, 500)
                } else if (query.length >= 2) {
                    searchRunnable = Runnable { searchRecipes(query) }
                    searchHandler.postDelayed(searchRunnable!!, 500)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = etSearch.text.toString().trim()
                if (q.isNotEmpty()) {
                    triggerSearch()
                }
                true
            } else false
        }

        btnSearch.setOnClickListener { triggerSearch() }
    }

    override fun onResume() {
        super.onResume()
        refreshHistoryChips(show = etSearch.text.isEmpty())
        
        if (etSearch.text.isEmpty()) {
            searchRecipes("popular recipes")
        }
    }

    private fun triggerSearch() {
        val query = etSearch.text.toString().trim()
        if (query.isEmpty()) {
            etSearch.error = "Please enter a recipe name"
            return
        }
        hideKeyboard()
        saveToHistory(query)
        refreshHistoryChips(show = false)
        searchRecipes(query)
    }

    private fun searchRecipes(query: String) {
        progressBar.visibility  = View.VISIBLE
        tvEmptyState.visibility = View.GONE
        rvRecipes.adapter       = null

        // TASK 4: Read allergies from user_data and always inject as intolerances
        val allergies = getSharedPreferences("user_data", Context.MODE_PRIVATE)
            .getString("allergies", "") ?: ""

        Log.d("Discover", "Searching: $query | diet=$selectedDiet | intolerances=$allergies")

        apiService.searchDiscoverRecipes(
            query        = query,
            apiKey       = Constants.API_KEY,
            intolerances = allergies,
            diet         = selectedDiet,
            addRecipeNutrition = true
        ).enqueue(object : Callback<RecipeResponse> {
            override fun onResponse(call: Call<RecipeResponse>, response: Response<RecipeResponse>) {
                progressBar.visibility = View.GONE
                if (response.isSuccessful && response.body() != null) {
                    val recipes = response.body()?.results ?: emptyList()
                    if (recipes.isEmpty()) {
                        tvEmptyState.text = "No recipes found for \"$query\""
                        tvEmptyState.visibility = View.VISIBLE
                    } else {
                        rvRecipes.adapter = DiscoverRecipeAdapter(recipes)
                    }
                } else {
                    tvEmptyState.text = "API Error: ${response.code()}"
                    tvEmptyState.visibility = View.VISIBLE
                    Log.e("Discover", response.errorBody()?.string() ?: "Unknown")
                }
            }
            override fun onFailure(call: Call<RecipeResponse>, t: Throwable) {
                progressBar.visibility  = View.GONE
                tvEmptyState.text       = "Network Error — check connection"
                tvEmptyState.visibility = View.VISIBLE
                Log.e("Discover", t.message ?: "Unknown error")
            }
        })
    }

    // ── TASK 6: Search history ──────────────────────────────────────────────

    private fun saveToHistory(query: String) {
        val existing = loadHistory().toMutableList()
        existing.remove(query)           // remove duplicate
        existing.add(0, query)           // prepend
        if (existing.size > 5) existing.removeLast()
        prefs.edit().putString("recent_searches", gson.toJson(existing)).apply()
    }

    private fun loadHistory(): List<String> {
        val json = prefs.getString("recent_searches", "[]") ?: "[]"
        return try {
            gson.fromJson(json, Array<String>::class.java).toList()
        } catch (_: Exception) { emptyList() }
    }

    private fun refreshHistoryChips(show: Boolean) {
        cgHistory.removeAllViews()
        val history = loadHistory()

        if (!show || history.isEmpty()) {
            cgHistory.visibility    = View.GONE
            tvHistoryLabel.visibility = View.GONE
            return
        }

        cgHistory.visibility    = View.VISIBLE
        tvHistoryLabel.visibility = View.VISIBLE

        history.forEach { query ->
            val chip = Chip(this)
            chip.text = query
            chip.isClickable = true
            chip.setOnClickListener {
                etSearch.setText(query)
                triggerSearch()
            }
            cgHistory.addView(chip)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }
}
