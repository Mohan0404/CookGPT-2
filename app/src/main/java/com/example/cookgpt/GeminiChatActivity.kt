package com.example.cookgpt

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GeminiChatActivity : AppCompatActivity() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var chatAdapter: ChatMessageAdapter

    private val messages = mutableListOf<ChatMessage>()
    private var userProfile: UserHealthProfile? = null
    private var generativeModel: GenerativeModel? = null
    private var chat: Chat? = null

    // ADDED — prevent welcome message repeating on every visit (BUG 3)
    private var welcomeShown = false

    // ADDED — track current model for fallback chain (BUG 1)
    private var currentModelIndex = 0
    private val modelFallbackChain = listOf(
        "gemini-2.0-flash",
        "gemini-1.5-flash-latest",
        "gemini-1.5-flash",
        "gemini-1.0-pro"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gemini_chat)
        initViews()
        // BUG 4 FIX — restore chat history first, then init Gemini
        restoreChatHistory()
        loadProfileThenInitGemini()
    }

    private fun initViews() {
        rvMessages  = findViewById(R.id.rvMessages)
        etMessage   = findViewById(R.id.etMessage)
        btnSend     = findViewById(R.id.btnSend)
        progressBar = findViewById(R.id.progressBar)

        chatAdapter = ChatMessageAdapter(messages)
        // MODIFIED — stackFromEnd so new messages appear at bottom (BUG 6)
        rvMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvMessages.adapter = chatAdapter

        btnSend.setOnClickListener { sendMessage() }
        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }

        setupQuickChips()
    }

    private fun setupQuickChips() {
        val layoutQuickChips = findViewById<LinearLayout>(R.id.layoutQuickChips)
        val chipSuggestMeals = findViewById<TextView>(R.id.chipSuggestMeals)
        val chipLunch = findViewById<TextView>(R.id.chipLunch)
        val chipProtein = findViewById<TextView>(R.id.chipProtein)

        val listener = View.OnClickListener { v ->
            val textView = v as TextView
            etMessage.setText(textView.text)
            sendMessage()
            layoutQuickChips.visibility = View.GONE
        }

        chipSuggestMeals.setOnClickListener(listener)
        chipLunch.setOnClickListener(listener)
        chipProtein.setOnClickListener(listener)
    }

    // MODIFIED — save chat on pause for persistence (BUG 4)
    override fun onPause() {
        super.onPause()
        val gson = Gson()
        val toSave = messages.takeLast(20)
        getSharedPreferences("chat_prefs", MODE_PRIVATE)
            .edit()
            .putString("last_chat", gson.toJson(toSave))
            .apply()
    }

    // MODIFIED — restore chat history, skip welcome if history exists (BUG 3/4)
    private fun restoreChatHistory() {
        val prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE)
        val json = prefs.getString("last_chat", null) ?: return
        try {
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            val saved = Gson().fromJson<List<ChatMessage>>(json, type) ?: return
            if (saved.isNotEmpty()) {
                messages.clear()
                messages.addAll(saved)
                chatAdapter.notifyDataSetChanged()

                // ADDED — mark welcome as shown since we restored history
                welcomeShown = true

                // Hide chips if restoring history
                findViewById<LinearLayout>(R.id.layoutQuickChips).visibility = View.GONE

                // MODIFIED — scroll to bottom after restore (BUG 5)
                rvMessages.post {
                    rvMessages.scrollToPosition(messages.size - 1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadProfileThenInitGemini() {
        val uid = SessionManager.getUserId(this)
        if (uid.isEmpty()) {
            initGemini(UserHealthProfile())
            // MODIFIED — only show welcome if not already shown (BUG 3)
            if (!welcomeShown) {
                addWelcomeMessage(UserHealthProfile())
                welcomeShown = true
            }
            return
        }

        FirebaseProfileLoader.loadUserProfile(
            uid = uid,
            onSuccess = { profile ->
                userProfile = profile
                initGemini(profile)
                // MODIFIED — only show welcome if not already shown (BUG 3)
                if (!welcomeShown) {
                    addWelcomeMessage(profile)
                    welcomeShown = true
                }
            },
            onFailure = {
                initGemini(UserHealthProfile())
                // MODIFIED — only show welcome if not already shown (BUG 3)
                if (!welcomeShown) {
                    addWelcomeMessage(UserHealthProfile())
                    welcomeShown = true
                }
            }
        )
    }

    private fun buildSystemPrompt(profile: UserHealthProfile): String = """
        You are CookGPT, a personal health food assistant integrated into a recipe app.

        You MUST always:
        - Recommend recipes that match the user's dietary preferences
        - NEVER suggest foods containing any of the user's allergens
        - Always optimize suggestions for the user's fitness goal
        - Keep responses concise, friendly, and actionable
        - Suggest practical meal ideas with rough calorie counts
        - Offer healthy alternatives when user asks for unhealthy foods
        - Remember context from earlier in this conversation

        User Profile:
        - Name: ${profile.name.ifEmpty { "User" }}
        - Allergies: ${profile.allergies.ifEmpty { "None specified" }}
        - Diet Type: ${profile.dietType.ifEmpty { "No restriction" }}
        - Fitness Goal: ${profile.fitnessGoal.ifEmpty { "General health" }}
        - Daily Calorie Target: ${profile.calorieGoal.ifEmpty { "Not specified" }} kcal
        - Weight Goal: ${profile.weightGoal.ifEmpty { "Not specified" }}
        - Disliked Foods: ${profile.dislikedFoods.ifEmpty { "None" }}
        - Preferred Cuisine: ${profile.preferredCuisine.ifEmpty { "Any" }}
        - Age: ${profile.age}, Weight: ${profile.weight}, Height: ${profile.height}, Gender: ${profile.gender}

        Start by briefly greeting the user by name and asking what they need help with today.
    """.trimIndent()

    // MODIFIED — Updated to supported Gemini model
    private fun initGemini(profile: UserHealthProfile) {
        generativeModel = GenerativeModel(
            modelName = "gemini-flash-latest",
            apiKey = BuildConfig.GEMINI_API_KEY,
            systemInstruction = content { text(buildSystemPrompt(profile)) }
        )
        chat = generativeModel!!.startChat()
    }

    private fun addWelcomeMessage(profile: UserHealthProfile) {
        if (messages.isNotEmpty()) return
        val name = profile.name.ifEmpty { "there" }
        val welcome = "Hi $name! I'm CookGPT, your personal food and health assistant. " +
            "Ask me anything — recipe ideas, meal plans, nutrition advice, or " +
            "healthy alternatives tailored just for you!"
        messages.add(ChatMessage(text = welcome, isFromUser = false))
        chatAdapter.notifyItemInserted(messages.size - 1)
        rvMessages.post {
            rvMessages.scrollToPosition(messages.size - 1)
        }
    }

    private fun sendMessage() {
        val userText = etMessage.text.toString().trim()
        if (userText.isEmpty()) return
        etMessage.text.clear()
        hideKeyboard()

        messages.add(ChatMessage(text = userText, isFromUser = true))
        chatAdapter.notifyItemInserted(messages.size - 1)
        rvMessages.post {
            rvMessages.scrollToPosition(messages.size - 1)
        }

        progressBar.visibility = View.VISIBLE
        btnSend.isEnabled = false

        lifecycleScope.launch {
            try {
                val activeChat = chat ?: run {
                    withContext(Dispatchers.Main) { retryInitialize() }
                    withContext(Dispatchers.Main) {
                        messages.add(ChatMessage(
                            text = "AI not ready yet. Reconnecting… please try again in a moment.",
                            isFromUser = false,
                            isError = true
                        ))
                        chatAdapter.notifyItemInserted(messages.size - 1)
                        rvMessages.post { rvMessages.scrollToPosition(messages.size - 1) }
                        progressBar.visibility = View.GONE
                        btnSend.isEnabled = true
                    }
                    return@launch
                }

                // Call without inner try-catch to properly parse
                val response = activeChat.sendMessage(userText)
                val aiText = response.text ?: "I couldn't generate a response."

                withContext(Dispatchers.Main) {
                    messages.add(ChatMessage(text = aiText, isFromUser = false))
                    chatAdapter.notifyItemInserted(messages.size - 1)
                    rvMessages.post {
                        rvMessages.scrollToPosition(messages.size - 1)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errMsg = e.message ?: e.toString()
                    messages.add(ChatMessage(
                        text = "Something went wrong: ${e.localizedMessage}",
                        isFromUser = false,
                        isError = true
                    ))
                    chatAdapter.notifyItemInserted(messages.size - 1)
                    rvMessages.post { rvMessages.scrollToPosition(messages.size - 1) }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnSend.isEnabled = true
                    rvMessages.post {
                        rvMessages.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }
    }

    private fun retryInitialize() {
        progressBar.visibility = View.VISIBLE
        loadProfileThenInitGemini()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etMessage.windowToken, 0)
    }
}
