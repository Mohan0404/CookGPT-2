package com.example.cookgpt

import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.*
import java.util.concurrent.TimeUnit

class PrepReminderActivity : AppCompatActivity() {

    private var selectedCalendar = Calendar.getInstance()
    private lateinit var tvSelectedDateTime: TextView
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var llReminders: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prep_reminder)

        prefs = getSharedPreferences("prep_reminders", Context.MODE_PRIVATE)

        findViewById<android.view.View>(R.id.btn_back).setOnClickListener { finish() }

        val etRecipeName = findViewById<EditText>(R.id.et_recipe_name)
        val rbThaw       = findViewById<RadioButton>(R.id.rb_thaw)
        tvSelectedDateTime = findViewById(R.id.tv_selected_date_time)
        llReminders        = findViewById(R.id.ll_scheduled_reminders)

        findViewById<Button>(R.id.btn_pick_date_time).setOnClickListener { showDateTimePicker() }

        findViewById<Button>(R.id.btn_schedule).setOnClickListener {
            val recipeName = etRecipeName.text.toString().trim()
            if (recipeName.isEmpty()) {
                Toast.makeText(this, "Please enter recipe name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (tvSelectedDateTime.text == "Not selected") {
                Toast.makeText(this, "Please select date and time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val prepHours = if (rbThaw.isChecked) 24 else 4
            schedulePrepReminder(recipeName, prepHours)
        }

        loadScheduledReminders()
    }

    override fun onResume() {
        super.onResume()
        loadScheduledReminders()
    }

    private fun showDateTimePicker() {
        val now = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                selectedCalendar.set(year, month, day, hour, minute)
                tvSelectedDateTime.text = "$day/${month + 1}/$year at ${String.format("%02d:%02d", hour, minute)}"
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun schedulePrepReminder(recipeName: String, prepHours: Int) {
        val cookingTimeMillis = selectedCalendar.timeInMillis
        val prepTimeMillis    = cookingTimeMillis - TimeUnit.HOURS.toMillis(prepHours.toLong())
        val delay             = prepTimeMillis - System.currentTimeMillis()

        if (delay <= 0) {
            Toast.makeText(this, "The prep time has already passed!", Toast.LENGTH_SHORT).show()
            return
        }

        val data = Data.Builder()
            .putString("title",   "Prep Reminder: $recipeName")
            .putString("message", "Time to start prepping! You planned to cook at ${tvSelectedDateTime.text}")
            .build()

        val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)

        // TASK 7: Persist the WorkManager ID and label so we can list + cancel it later
        val workId       = workRequest.id.toString()
        val reminderLabel = "$recipeName — ${tvSelectedDateTime.text}"
        prefs.edit()
            .putString("reminder_${recipeName}_id",    workId)
            .putString("reminder_${recipeName}_label", reminderLabel)
            .apply()
        Log.d("PrepReminder", "Scheduled reminder '$reminderLabel' id=$workId delay=${delay}ms")

        showConfirmationNotification(recipeName, tvSelectedDateTime.text.toString())
        Toast.makeText(this, "Reminder set for $prepHours hours before cooking!", Toast.LENGTH_LONG).show()
        loadScheduledReminders()
    }

    // TASK 7: Load and display all scheduled reminders as cards with Cancel button
    private fun loadScheduledReminders() {
        llReminders.removeAllViews()
        val allKeys = prefs.all.keys.filter { it.endsWith("_id") }

        if (allKeys.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No scheduled reminders"
            tv.setPadding(0, 8, 0, 8)
            llReminders.addView(tv)
            return
        }

        allKeys.forEach { idKey ->
            val labelKey = idKey.replace("_id", "_label")
            val workId   = prefs.getString(idKey, null) ?: return@forEach
            val label    = prefs.getString(labelKey, "Unknown reminder") ?: "Unknown reminder"

            val cardView = LayoutInflater.from(this)
                .inflate(R.layout.item_reminder_card, llReminders, false)
            cardView.findViewById<TextView>(R.id.tv_reminder_label).text = label
            cardView.findViewById<Button>(R.id.btn_cancel_reminder).setOnClickListener {
                cancelReminder(idKey, labelKey, workId, label)
                llReminders.removeView(cardView)
                loadScheduledReminders() // refresh
            }
            llReminders.addView(cardView)
        }
    }

    private fun cancelReminder(idKey: String, labelKey: String, workId: String, label: String) {
        WorkManager.getInstance(this).cancelWorkById(UUID.fromString(workId))
        prefs.edit().remove(idKey).remove(labelKey).apply()
        Log.d("PrepReminder", "Cancelled reminder: $label (id=$workId)")
        Toast.makeText(this, "Reminder cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun showConfirmationNotification(recipeName: String, dateTime: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, "COOKGPT_CHANNEL")
            .setSmallIcon(R.drawable.ic_chef_hat)
            .setContentTitle("Reminder Scheduled")
            .setContentText("We'll remind you to prep '$recipeName' before $dateTime.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
