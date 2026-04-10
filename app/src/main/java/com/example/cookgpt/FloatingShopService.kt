package com.example.cookgpt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.CheckBox
import android.graphics.Color
import android.graphics.Paint
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * FloatingShopService — Foreground service that displays the user's
 * shopping list as a draggable floating overlay on top of vendor apps
 * (Swiggy, Zepto, Blinkit).
 *
 * This runs as a FOREGROUND SERVICE so Android doesn't kill it when
 * the user switches to the vendor app.
 */
class FloatingShopService : Service() {

    companion object {
        const val CHANNEL_ID = "floating_shop_channel"
        const val NOTIFICATION_ID = 2001
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var minimizedView: View? = null
    private var isMinimized = false
    private lateinit var expandedParams: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val itemsList = intent?.getStringArrayListExtra("SHOP_ITEMS") ?: arrayListOf()
        val totalCost = intent?.getIntExtra("SHOP_TOTAL", 0) ?: 0

        // ── Start as foreground service so it survives app switch ──
        startForeground(NOTIFICATION_ID, buildNotification(itemsList.size))

        // ── Show the floating overlay ──
        showFloatingWindow(itemsList, totalCost)

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Shopping List Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows your shopping list while using delivery apps"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(itemCount: Int): Notification {
        val openAppIntent = Intent(this, ShopActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, pendingIntentFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛒 Shopping List Active")
            .setContentText("$itemCount items — Tap to return to CookGPT")
            .setSmallIcon(R.drawable.ic_shop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showFloatingWindow(items: ArrayList<String>, totalCost: Int) {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_shop, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        expandedParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 150
        }

        val llItems = floatingView!!.findViewById<LinearLayout>(R.id.llShoppingItems)
        val tvCount = floatingView!!.findViewById<TextView>(R.id.tvItemCount)
        val tvTotal = floatingView!!.findViewById<TextView>(R.id.tvFloatingTotal)

        renderShoppingItems(llItems, tvCount, tvTotal)

        // ── Close overlay ──
        floatingView!!.findViewById<ImageView>(R.id.btnCloseOverlay).setOnClickListener {
            stopSelf()
        }

        // ── Minimize ──
        floatingView!!.findViewById<ImageView>(R.id.btnMinimize).setOnClickListener {
            toggleMinimize()
        }

        // ── Drag support ──
        setupDrag(floatingView!!.findViewById(R.id.dragHandle))

        windowManager?.addView(floatingView, expandedParams)
    }

    private fun renderShoppingItems(llItems: LinearLayout, tvCount: TextView, tvTotal: TextView) {
        val prefs = getSharedPreferences("cookgpt_shop", Context.MODE_PRIVATE)
        val json = prefs.getString("shop_list_json", null)
        val type = object : TypeToken<MutableList<ShoppingItem>>() {}.type
        val shoppingItems: MutableList<ShoppingItem> = if (json != null) {
            try { Gson().fromJson(json, type) } catch (e: Exception) { mutableListOf() }
        } else {
            mutableListOf()
        }

        llItems.removeAllViews()
        var uncheckedCount = 0
        var uncheckedTotal = 0

        for ((index, item) in shoppingItems.withIndex()) {
            if (!item.isChecked) {
                uncheckedCount++
                uncheckedTotal += item.price
            }

            val cb = CheckBox(this).apply {
                text = "${item.name} (${item.quantity})"
                setTextColor(Color.BLACK)
                textSize = 14f
                isChecked = item.isChecked
                setPadding(0, 8, 0, 8)
                
                // Strike-through effect
                paintFlags = if (isChecked) paintFlags or Paint.STRIKE_THRU_TEXT_FLAG else paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                alpha = if (isChecked) 0.55f else 1.0f

                setOnCheckedChangeListener { _, isChecked ->
                    shoppingItems[index].isChecked = isChecked
                    // Save back to prefs
                    prefs.edit().putString("shop_list_json", Gson().toJson(shoppingItems)).apply()
                    // Re-render
                    renderShoppingItems(llItems, tvCount, tvTotal)
                }
            }
            llItems.addView(cb)
        }

        tvCount.text = "$uncheckedCount items to buy"
        tvTotal.text = "₹$uncheckedTotal"
    }

    private fun toggleMinimize() {
        if (isMinimized) {
            // Expand
            try {
                if (minimizedView?.isAttachedToWindow == true) {
                    windowManager?.removeView(minimizedView)
                }
            } catch (_: Exception) {}
            minimizedView = null

            windowManager?.addView(floatingView, expandedParams)
            isMinimized = false
        } else {
            // Minimize to cart bubble
            try {
                if (floatingView?.isAttachedToWindow == true) {
                    windowManager?.removeView(floatingView)
                }
            } catch (_: Exception) {}

            minimizedView = LayoutInflater.from(this).inflate(R.layout.layout_floating_shop_mini, null)

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE

            val miniParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 16
                y = 250
            }

            minimizedView!!.setOnClickListener {
                toggleMinimize()
            }

            windowManager?.addView(minimizedView, miniParams)
            isMinimized = true
        }
    }

    private fun setupDrag(dragTarget: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        dragTarget.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = expandedParams.x
                    initialY = expandedParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) isDragging = true
                    expandedParams.x = initialX + dx
                    expandedParams.y = initialY + dy
                    try {
                        if (floatingView?.isAttachedToWindow == true) {
                            windowManager?.updateViewLayout(floatingView, expandedParams)
                        }
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isDragging
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (floatingView?.isAttachedToWindow == true) windowManager?.removeView(floatingView)
        } catch (_: Exception) {}
        try {
            if (minimizedView?.isAttachedToWindow == true) windowManager?.removeView(minimizedView)
        } catch (_: Exception) {}
    }
}
