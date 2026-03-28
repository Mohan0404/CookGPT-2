package com.example.cookgpt.data

import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties

/**
 * CustomRecipe — Firebase Realtime Database model.
 *
 * Rules:
 * - Must be a plain class (not data class) for Firebase deserialization.
 * - Must have a no-arg constructor.
 * - All fields must be var with defaults.
 * - No @JvmField — Firebase needs getters/setters via reflection.
 */
@Keep
@IgnoreExtraProperties
class CustomRecipe {
    var id: String = ""
    var title: String = ""
    var imageUrl: String = ""        // Firebase Storage download URL (empty if no photo)
    var ingredients: String = ""     // newline-separated list
    var steps: String = ""           // newline-separated steps
    var cuisine: String = ""         // one of the fixed cuisine tags
    var prepTimeMinutes: Int = 0
    var cookTimeMinutes: Int = 0
    var createdAt: Long = 0L         // System.currentTimeMillis() at save time
    var uid: String = ""             // owner's Firebase Auth UID

    constructor()

    constructor(
        id: String,
        title: String,
        imageUrl: String,
        ingredients: String,
        steps: String,
        cuisine: String,
        prepTimeMinutes: Int,
        cookTimeMinutes: Int,
        createdAt: Long,
        uid: String
    ) {
        this.id = id
        this.title = title
        this.imageUrl = imageUrl
        this.ingredients = ingredients
        this.steps = steps
        this.cuisine = cuisine
        this.prepTimeMinutes = prepTimeMinutes
        this.cookTimeMinutes = cookTimeMinutes
        this.createdAt = createdAt
        this.uid = uid
    }
}
