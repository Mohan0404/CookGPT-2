package com.example.cookgpt

import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prep_reminder)

        findViewById<android.view.View>(R.id.btn_back).setOnClickListener { finish() }

        val etRecipeName = findViewById<EditText>(R.id.et_recipe_name)
        val rbThaw = findViewById<RadioButton>(R.id.rb_thaw)
        tvSelectedDateTime = findViewById(R.id.tv_selected_date_time)
        val btnPick = findViewById<Button>(R.id.btn_pick_date_time)
        val btnSchedule = findViewById<Button>(R.id.btn_schedule)

        btnPick.setOnClickListener {
            showDateTimePicker()
        }

        btnSchedule.setOnClickListener {
            val recipeName = etRecipeName.text.toString()
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
    }

    private fun showDateTimePicker() {
        val currentCalendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                selectedCalendar.set(year, month, day, hour, minute)
                tvSelectedDateTime.text = "${day}/${month + 1}/${year} at ${String.format("%02d:%02d", hour, minute)}"
            }, currentCalendar.get(Calendar.HOUR_OF_DAY), currentCalendar.get(Calendar.MINUTE), true).show()
        }, currentCalendar.get(Calendar.YEAR), currentCalendar.get(Calendar.MONTH), currentCalendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun schedulePrepReminder(recipeName: String, prepHours: Int) {
        val cookingTimeMillis = selectedCalendar.timeInMillis
        val prepTimeMillis = cookingTimeMillis - TimeUnit.HOURS.toMillis(prepHours.toLong())
        val delay = prepTimeMillis - System.currentTimeMillis()

        if (delay <= 0) {
            Toast.makeText(this, "The prep time has already passed!", Toast.LENGTH_SHORT).show()
            return
        }

        val data = Data.Builder()
            .putString("title", "Prep Reminder: $recipeName")
            .putString("message", "Time to start prepping! You planned to cook at ${tvSelectedDateTime.text}")
            .build()

        val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)
        
        // Show confirmation notification immediately
        showConfirmationNotification(recipeName, tvSelectedDateTime.text.toString())
        
        Toast.makeText(this, "Reminder set for $prepHours hours before cooking!", Toast.LENGTH_LONG).show()
        finish()
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
