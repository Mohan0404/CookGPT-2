package com.example.cookgpt

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class User(
    @PrimaryKey val id: Int = 1, // We only have one user profile
    val name: String = "",
    val age: Int = 0,
    val weight: Float = 0f,
    val height: Float = 0f,
    val gender: String = "",
    val goal: String = "",
    val preferences: String = "" // Allergies and restrictions
)
