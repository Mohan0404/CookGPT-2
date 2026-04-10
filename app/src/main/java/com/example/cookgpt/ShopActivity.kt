package com.example.cookgpt

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

/**
 * ShopActivity — Smart AI Grocery + Quick Buy Engine
 *
 * NOVELTY FEATURES:
 *   1. AI chip grocery generation (6 diet categories)
 *   2. Generate from today's meals (profile-aware)
 *   3. Smart category grouping (Produce/Dairy/Protein/Grains/Snacks)
 *   4. Pantry mode (hide bought items)
 *   5. Price estimation (40+ items)
 *   6. Voice input (SpeechRecognizer)
 *   7. Smart checkout filter
 *   8. SharedPreferences persistence
 *   9. Swipe left=delete, right=toggle bought
 *  10. Progress tracking (N/M + animated bar)
 *  11. Deep links to vendor apps (Swiggy/Zepto/Blinkit)
 *  12. Floating overlay shopping list on vendor apps
 */
class ShopActivity : AppCompatActivity() {

    private lateinit var adapter: ShoppingAdapter
    private lateinit var rvShoppingList: RecyclerView
    private lateinit var tvEstimatedCost: TextView
    private lateinit var tvProgressFraction: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var emptyState: LinearLayout
    private lateinit var prefs: SharedPreferences

    private val shoppingItems = mutableListOf<ShoppingItem>()

    // ── Average price database (₹) ──
    private val priceDb = mapOf(
        "Eggs" to 60, "Chicken" to 180, "Chicken breast" to 200,
        "Paneer" to 90, "Greek Yogurt" to 80, "Oats" to 50,
        "Broccoli" to 40, "Spinach" to 30, "Almonds" to 120,
        "Tofu" to 70, "Quinoa" to 150, "Lentils" to 60,
        "Brown Rice" to 80, "Sweet Potato" to 40, "Avocado" to 100,
        "Salmon" to 350, "Tuna" to 200, "Whey Protein" to 500,
        "Peanut Butter" to 180, "Banana" to 30, "Milk" to 60,
        "Bread" to 40, "Butter" to 55, "Cheese" to 120,
        "Yogurt" to 50, "Tomato" to 25, "Onion" to 30,
        "Garlic" to 20, "Ginger" to 15, "Rice" to 70,
        "Pasta" to 90, "Olive Oil" to 250, "Coconut Oil" to 180,
        "Mushrooms" to 60, "Bell Pepper" to 50, "Cucumber" to 20,
        "Carrot" to 25, "Apple" to 150, "Blueberries" to 200,
        "Cottage Cheese" to 90, "Chickpeas" to 55, "Black Beans" to 65
    )

    // ── Category mapping ──
    private val categoryDb = mapOf(
        "Eggs" to "Protein", "Chicken" to "Protein", "Chicken breast" to "Protein",
        "Paneer" to "Dairy", "Greek Yogurt" to "Dairy", "Yogurt" to "Dairy",
        "Cottage Cheese" to "Dairy", "Milk" to "Dairy", "Butter" to "Dairy",
        "Cheese" to "Dairy",
        "Oats" to "Grains", "Quinoa" to "Grains", "Lentils" to "Grains",
        "Brown Rice" to "Grains", "Rice" to "Grains", "Pasta" to "Grains",
        "Bread" to "Grains", "Chickpeas" to "Grains", "Black Beans" to "Grains",
        "Broccoli" to "Produce", "Spinach" to "Produce", "Tomato" to "Produce",
        "Onion" to "Produce", "Garlic" to "Produce", "Ginger" to "Produce",
        "Sweet Potato" to "Produce", "Avocado" to "Produce",
        "Mushrooms" to "Produce", "Bell Pepper" to "Produce",
        "Cucumber" to "Produce", "Carrot" to "Produce", "Banana" to "Produce",
        "Apple" to "Produce", "Blueberries" to "Produce",
        "Almonds" to "Snacks", "Peanut Butter" to "Snacks",
        "Tofu" to "Protein", "Salmon" to "Protein", "Tuna" to "Protein",
        "Whey Protein" to "Protein",
        "Olive Oil" to "Snacks", "Coconut Oil" to "Snacks"
    )

    // ── Deep link URIs for vendor apps ──
    private val vendorDeepLinks = mapOf(
        "swiggy" to DeepLinkConfig(
            appUri = "swiggy://instamart",
            webFallback = "https://www.swiggy.com/instamart",
            packageName = "in.swiggy.android"
        ),
        "zepto" to DeepLinkConfig(
            appUri = "zepto://",
            webFallback = "https://www.zeptonow.com",
            packageName = "com.zeptoconsumerapp"
        ),
        "blinkit" to DeepLinkConfig(
            appUri = "blinkit://",
            webFallback = "https://blinkit.com",
            packageName = "com.grofers.customerapp"
        )
    )

    data class DeepLinkConfig(
        val appUri: String,
        val webFallback: String,
        val packageName: String
    )

    // ── Voice input launcher ──
    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: return@registerForActivityResult
            parseVoiceInput(spoken)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)

        prefs = getSharedPreferences("cookgpt_shop", Context.MODE_PRIVATE)

        // ── Bind views ──
        rvShoppingList = findViewById(R.id.rvShoppingList)
        tvEstimatedCost = findViewById(R.id.tvEstimatedCost)
        tvProgressFraction = findViewById(R.id.tvProgressFraction)
        progressBar = findViewById(R.id.progressBar)
        emptyState = findViewById(R.id.emptyState)

        // ── Back button ──
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // ── Restore saved list ──
        restoreShoppingList()

        // ── Setup RecyclerView ──
        adapter = ShoppingAdapter(
            items = shoppingItems,
            onItemChecked = { pos, checked ->
                shoppingItems[pos].isChecked = checked
                adapter.notifyDataSetChanged()
                updateProgress()
                saveShoppingList()
            },
            onItemDeleted = { pos ->
                shoppingItems.removeAt(pos)
                refreshListWithGrouping()
                updateProgress()
                saveShoppingList()
            },
            onItemEdited = { pos ->
                showEditDialog(pos)
            }
        )
        rvShoppingList.layoutManager = LinearLayoutManager(this)
        rvShoppingList.adapter = adapter

        // ── Swipe actions ──
        setupSwipeActions()

        // ── FAB — Add Item ──
        findViewById<FloatingActionButton>(R.id.fabAddItem).setOnClickListener {
            showAddItemDialog()
        }

        // ── Buy button ──
        findViewById<MaterialButton>(R.id.btnBuy).setOnClickListener {
            if (shoppingItems.isEmpty()) {
                Toast.makeText(this, "Your list is empty!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showBuyBottomSheet()
        }

        // ── Voice input ──
        findViewById<View>(R.id.btnVoiceInput).setOnClickListener {
            launchVoiceInput()
        }

        // ── Pantry mode toggle ──
        findViewById<SwitchMaterial>(R.id.switchPantry).setOnCheckedChangeListener { _, isOn ->
            adapter.setPantryMode(isOn)
            updateEmptyState()
        }

        // ── AI Suggestion Chips ──
        setupChips()

        // ── Generate from today's meals ──
        findViewById<MaterialButton>(R.id.btnGenerateFromMeals).setOnClickListener {
            generateFromTodaysMeals()
        }

        updateProgress()
        updateEmptyState()
    }

    override fun onResume() {
        super.onResume()
        // Ensure floating overlay is closed whenever the user returns to CookGPT
        stopService(Intent(this, FloatingShopService::class.java))
    }

    // ══════════════════════════════════════════════
    //   AI CHIP LOGIC
    // ══════════════════════════════════════════════
    private fun setupChips() {
        findViewById<Chip>(R.id.chipRecipeOfDay).setOnClickListener {
            loadAiList("Recipe of Day", listOf(
                "Chicken", "Tomato", "Onion", "Garlic", "Ginger",
                "Rice", "Yogurt", "Bell Pepper", "Olive Oil"
            ))
        }
        findViewById<Chip>(R.id.chipHighProtein).setOnClickListener {
            loadAiList("High Protein", listOf(
                "Eggs", "Chicken", "Paneer", "Greek Yogurt", "Oats",
                "Tofu", "Lentils", "Cottage Cheese", "Almonds"
            ))
        }
        findViewById<Chip>(R.id.chipWeightLoss).setOnClickListener {
            loadAiList("Weight Loss", listOf(
                "Broccoli", "Spinach", "Chicken breast", "Oats",
                "Almonds", "Sweet Potato", "Cucumber", "Carrot",
                "Greek Yogurt", "Quinoa"
            ))
        }
        findViewById<Chip>(R.id.chipVegan).setOnClickListener {
            loadAiList("Vegan", listOf(
                "Tofu", "Quinoa", "Lentils", "Spinach", "Avocado",
                "Chickpeas", "Black Beans", "Brown Rice",
                "Mushrooms", "Bell Pepper", "Coconut Oil"
            ))
        }
        findViewById<Chip>(R.id.chipMuscleGain).setOnClickListener {
            loadAiList("Muscle Gain", listOf(
                "Eggs", "Chicken breast", "Salmon", "Oats",
                "Peanut Butter", "Banana", "Whey Protein",
                "Brown Rice", "Cottage Cheese", "Almonds"
            ))
        }
        findViewById<Chip>(R.id.chipLowCarb).setOnClickListener {
            loadAiList("Low Carb", listOf(
                "Eggs", "Chicken", "Paneer", "Avocado",
                "Spinach", "Mushrooms", "Cheese", "Butter",
                "Almonds", "Olive Oil"
            ))
        }
    }

    private fun loadAiList(label: String, ingredients: List<String>) {
        val existingNames = shoppingItems.map { it.name }.toSet()
        var addedCount = 0
        for (name in ingredients) {
            if (name !in existingNames) {
                shoppingItems.add(ShoppingItem(
                    name = name,
                    quantity = "1",
                    category = categoryDb[name] ?: "General",
                    price = priceDb[name] ?: 50
                ))
                addedCount++
            }
        }
        refreshListWithGrouping()
        updateProgress()
        updateEmptyState()
        saveShoppingList()
        Toast.makeText(this, "✨ $label: $addedCount items added", Toast.LENGTH_SHORT).show()
    }

    private fun generateFromTodaysMeals() {
        val healthPrefs = getSharedPreferences("health_prefs", Context.MODE_PRIVATE)
        val goal = healthPrefs.getString("fitness_goal", "")

        val baseItems = listOf("Chicken", "Rice", "Tomato", "Onion", "Yogurt", "Garlic")
        val goalItems = when {
            goal?.contains("muscle", true) == true -> listOf("Eggs", "Whey Protein", "Oats", "Banana")
            goal?.contains("weight loss", true) == true -> listOf("Spinach", "Broccoli", "Quinoa", "Almonds")
            else -> listOf("Milk", "Bread", "Butter", "Apple")
        }
        loadAiList("Today's Meals", baseItems + goalItems)
    }

    // ══════════════════════════════════════════════
    //   PROGRESS + COST
    // ══════════════════════════════════════════════
    private fun updateProgress() {
        val total = shoppingItems.size
        val checked = shoppingItems.count { it.isChecked }
        tvProgressFraction.text = "$checked / $total ready"
        progressBar.max = if (total > 0) total else 1
        progressBar.setProgressCompat(checked, true)

        val cost = shoppingItems.filter { !it.isChecked }.sumOf { it.price }
        tvEstimatedCost.text = "₹$cost"
    }

    private fun updateEmptyState() {
        emptyState.visibility = if (shoppingItems.isEmpty()) View.VISIBLE else View.GONE
        rvShoppingList.visibility = if (shoppingItems.isEmpty()) View.GONE else View.VISIBLE
    }

    // ══════════════════════════════════════════════
    //   SMART GROUPING
    // ══════════════════════════════════════════════
    private fun refreshListWithGrouping() {
        val order = listOf("Produce", "Dairy", "Protein", "Grains", "Snacks", "General")
        shoppingItems.sortWith(compareBy {
            val idx = order.indexOf(it.category)
            if (idx == -1) 99 else idx
        })

        var lastCategory = ""
        for (item in shoppingItems) {
            item.isFirstInCategory = item.category != lastCategory
            lastCategory = item.category
        }
        adapter.updateItems(shoppingItems)
    }

    // ══════════════════════════════════════════════
    //   ADD / EDIT DIALOGS
    // ══════════════════════════════════════════════
    private fun showAddItemDialog() {
        val input = EditText(this).apply {
            hint = "e.g. Milk, Eggs, Bread"
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Add Items")
            .setMessage("Separate multiple items with commas")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val raw = input.text.toString().trim()
                if (raw.isNotEmpty()) {
                    val names = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    for (name in names) {
                        val capitalized = name.replaceFirstChar { c -> c.uppercase() }
                        if (shoppingItems.none { it.name.equals(capitalized, true) }) {
                            shoppingItems.add(ShoppingItem(
                                name = capitalized,
                                quantity = "1",
                                category = categoryDb[capitalized] ?: "General",
                                price = priceDb[capitalized] ?: 50
                            ))
                        }
                    }
                    refreshListWithGrouping()
                    updateProgress()
                    updateEmptyState()
                    saveShoppingList()
                    Toast.makeText(this, "✅ ${names.size} item(s) added", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(position: Int) {
        if (position < 0 || position >= shoppingItems.size) return
        val item = shoppingItems[position]
        val input = EditText(this).apply {
            setText(item.quantity)
            hint = "Quantity"
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Edit ${item.name}")
            .setMessage("Change quantity:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newQty = input.text.toString().trim()
                if (newQty.isNotEmpty()) {
                    shoppingItems[position].quantity = newQty
                    adapter.notifyDataSetChanged()
                    saveShoppingList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ══════════════════════════════════════════════
    //   VOICE INPUT — improved parsing
    // ══════════════════════════════════════════════
    private fun launchVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say items: e.g. 'milk and bread and eggs'")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseVoiceInput(spoken: String) {
        // Clean and split on common separators
        val cleaned = spoken
            .replace(Regex("(?i)^(add|buy|get|i need|please add)\\s*"), "")
            .trim()

        val parts = cleaned.split(Regex("\\s+and\\s+|,\\s*|\\s*&\\s*"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var added = 0
        for (part in parts) {
            val capitalized = part.replaceFirstChar { c -> c.uppercase() }
            if (shoppingItems.none { it.name.equals(capitalized, true) }) {
                shoppingItems.add(ShoppingItem(
                    name = capitalized,
                    quantity = "1",
                    category = categoryDb[capitalized] ?: "General",
                    price = priceDb[capitalized] ?: 50
                ))
                added++
            }
        }

        if (added > 0) {
            refreshListWithGrouping()
            updateProgress()
            updateEmptyState()
            saveShoppingList()
            Toast.makeText(this, "🎤 $added item(s) added via voice", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Items already in list or couldn't parse input", Toast.LENGTH_SHORT).show()
        }
    }

    // ══════════════════════════════════════════════
    //   SWIPE ACTIONS
    // ══════════════════════════════════════════════
    private fun setupSwipeActions() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return

                val filteredItems = adapter.getItems()
                if (pos >= filteredItems.size) return
                val item = filteredItems[pos]
                val realPos = shoppingItems.indexOf(item)
                if (realPos == -1) return

                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        shoppingItems.removeAt(realPos)
                        refreshListWithGrouping()
                        updateProgress()
                        updateEmptyState()
                        saveShoppingList()
                        Toast.makeText(this@ShopActivity, "🗑️ ${item.name} removed", Toast.LENGTH_SHORT).show()
                    }
                    ItemTouchHelper.RIGHT -> {
                        shoppingItems[realPos].isChecked = !shoppingItems[realPos].isChecked
                        adapter.notifyDataSetChanged()
                        updateProgress()
                        saveShoppingList()
                        val status = if (shoppingItems[realPos].isChecked) "✅ marked bought" else "↩️ unmarked"
                        Toast.makeText(this@ShopActivity, "${item.name} $status", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                     dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val bg = if (dX < 0) {
                    ColorDrawable(Color.parseColor("#FEE2E2"))
                } else {
                    ColorDrawable(Color.parseColor("#ECFDF5"))
                }
                bg.setBounds(
                    if (dX < 0) vh.itemView.right + dX.toInt() else vh.itemView.left,
                    vh.itemView.top,
                    if (dX < 0) vh.itemView.right else vh.itemView.left + dX.toInt(),
                    vh.itemView.bottom
                )
                bg.draw(c)
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(rvShoppingList)
    }

    // ══════════════════════════════════════════════
    //   BUY BOTTOMSHEET — DEEP LINKS + FLOATING
    // ══════════════════════════════════════════════
    private fun showBuyBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_quick_buy, null)
        sheet.setContentView(view)

        val uncheckedCount = adapter.getUncheckedCount()
        view.findViewById<TextView>(R.id.tvBuyItemCount).text = "$uncheckedCount items to buy"

        view.findViewById<View>(R.id.btnSwiggy).setOnClickListener {
            sheet.dismiss()
            launchVendorApp("swiggy")
        }
        view.findViewById<View>(R.id.btnZepto).setOnClickListener {
            sheet.dismiss()
            launchVendorApp("zepto")
        }
        view.findViewById<View>(R.id.btnBlinkit).setOnClickListener {
            sheet.dismiss()
            launchVendorApp("blinkit")
        }

        sheet.show()
    }

    /**
     * Launches vendor app via deep link with a floating shopping list overlay.
     *
     * Flow:
     * 1. Start floating overlay service (foreground) with unchecked items
     * 2. After 500ms delay (so overlay attaches), open vendor app
     * 3. Priority: package launch → deep link → Play Store → web
     */
    private fun launchVendorApp(vendor: String) {
        val config = vendorDeepLinks[vendor] ?: return

        // ── Step 1: Check overlay permission first ──
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please enable 'Display over other apps' for floating list", Toast.LENGTH_LONG).show()
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
            return
        }

        // ── Step 2: Launch the floating overlay FIRST ──
        launchFloatingOverlay()

        // ── Step 3: Delay 500ms then open vendor app ──
        // This ensures the overlay is fully attached before switching apps
        android.os.Handler(mainLooper).postDelayed({
            openVendorApp(config, vendor)
        }, 500)
    }

    /**
     * Opens the vendor app using the most reliable method available.
     * Priority: getLaunchIntentForPackage → deep link URI → Play Store → web
     */
    private fun openVendorApp(config: DeepLinkConfig, vendor: String) {
        try {
            // ── PRIORITY 1: Open app directly by package name ──
            // This is the MOST RELIABLE method on all Android versions
            val launchIntent = packageManager.getLaunchIntentForPackage(config.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Toast.makeText(this, "Opening $vendor... 🛒 List is floating!", Toast.LENGTH_SHORT).show()
                return
            }

            // ── PRIORITY 2: Try deep link URI scheme ──
            val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(config.appUri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(deepLinkIntent)
                Toast.makeText(this, "Opening $vendor... 🛒 List is floating!", Toast.LENGTH_SHORT).show()
                return
            } catch (_: Exception) {
                // Deep link not available
            }

            // ── PRIORITY 3: App not installed → Play Store ──
            Toast.makeText(this, "$vendor not installed. Opening Play Store...", Toast.LENGTH_SHORT).show()
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=${config.packageName}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {
                // ── PRIORITY 4: No Play Store → web browser ──
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${config.packageName}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open $vendor: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Starts FloatingShopService as a FOREGROUND SERVICE so it stays alive
     * when the user switches to the vendor app. The overlay floats on top.
     */
    private fun launchFloatingOverlay() {
        // Build the items list for the overlay
        val uncheckedItems = adapter.getUncheckedItems()
        val itemNames = ArrayList(uncheckedItems.map { "${it.name} (${it.quantity})" })
        val totalCost = uncheckedItems.sumOf { it.price }

        val serviceIntent = Intent(this, FloatingShopService::class.java).apply {
            putStringArrayListExtra("SHOP_ITEMS", itemNames)
            putExtra("SHOP_TOTAL", totalCost)
        }

        // Stop any existing overlay first
        stopService(Intent(this, FloatingShopService::class.java))

        // Start as FOREGROUND SERVICE so Android doesn't kill it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    // ══════════════════════════════════════════════
    //   PERSISTENCE
    // ══════════════════════════════════════════════
    private fun saveShoppingList() {
        val json = Gson().toJson(shoppingItems)
        prefs.edit().putString("shop_list_json", json).apply()
    }

    private fun restoreShoppingList() {
        val json = prefs.getString("shop_list_json", null) ?: return
        try {
            val type = object : TypeToken<MutableList<ShoppingItem>>() {}.type
            val restored: MutableList<ShoppingItem> = Gson().fromJson(json, type)
            shoppingItems.clear()
            shoppingItems.addAll(restored)

            // Re-apply grouping
            val order = listOf("Produce", "Dairy", "Protein", "Grains", "Snacks", "General")
            shoppingItems.sortWith(compareBy {
                val idx = order.indexOf(it.category)
                if (idx == -1) 99 else idx
            })
            var lastCat = ""
            for (item in shoppingItems) {
                item.isFirstInCategory = item.category != lastCat
                lastCat = item.category
            }
        } catch (e: Exception) {
            shoppingItems.clear()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveShoppingList()
    }
}
