package com.example.cookgpt

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class CookGPTApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Force light mode globally
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
