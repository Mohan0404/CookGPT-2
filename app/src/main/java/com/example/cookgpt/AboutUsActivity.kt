package com.example.cookgpt

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView

class AboutUsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_about_us)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<android.widget.ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        val scrollTo = intent.getStringExtra("scrollTo")
        val scrollView = findViewById<NestedScrollView>(R.id.scroll_view)
        
        scrollView.post {
            when (scrollTo) {
                "team" -> {
                    val teamSection = findViewById<View>(R.id.section_team)
                    scrollView.smoothScrollTo(0, teamSection.top)
                }
                "project" -> {
                    val projectSection = findViewById<View>(R.id.section_project)
                    scrollView.smoothScrollTo(0, projectSection.top)
                }
            }
        }
    }
}