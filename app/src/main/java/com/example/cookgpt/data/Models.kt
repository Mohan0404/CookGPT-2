package com.example.cookgpt.data

import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties
import java.io.Serializable

/**
 * SavedRecipe — Firebase deserialization rules:
 *  1. Must be a plain class (NOT data class) — data class cannot have multiple constructors cleanly.
 *  2. Must have a explicit no-arg constructor.
 *  3. Every field must be a `var` with a default value — Firebase uses the Kotlin-generated
 *     getters (getXxx) and setters (setXxx) via Java reflection. Do NOT use @JvmField —
 *     that removes the getters/setters and breaks deserialization silently (returns null).
 */
@Keep
@IgnoreExtraProperties
class SavedRecipe : Serializable {
    var id: Int = 0
    var title: String = ""
    var image: String = ""
    var ingredients: String = ""
    var description: String = ""

    /** Required by Firebase Realtime Database — called via reflection during deserialization */
    constructor()

    /** Used inside the app when building a recipe from API response */
    constructor(id: Int, title: String, image: String, ingredients: String, description: String) {
        this.id = id
        this.title = title
        this.image = image
        this.ingredients = ingredients
        this.description = description
    }
}

@Keep
data class RecipeResponse(
    val results: List<Recipe> = emptyList()
)

@Keep
data class Recipe(
    val id: Int = 0,
    val title: String = "",
    val image: String = "",
    val nutrition: NutritionWrapper? = null
) : Serializable

@Keep
data class RecipeDetail(
    val id: Int = 0,
    val title: String = "",
    val image: String = "",
    val summary: String = "",
    val extendedIngredients: List<Ingredient> = emptyList(),
    val instructions: String? = null,
    val nutrition: NutritionWrapper? = null
)

@Keep
data class Ingredient(
    val original: String = ""
)

// ── Nutrition / Ingredient Search models (Task 11) ─────────────────────────

@Keep
data class IngredientSearchResponse(
    val results: List<IngredientResult> = emptyList()
)

@Keep
data class IngredientResult(
    val id: Int = 0,
    val name: String = "",
    val image: String = ""
)

@Keep
data class IngredientInfo(
    val id: Int = 0,
    val name: String = "",
    val image: String = "",
    val nutrition: NutritionWrapper? = null
)

@Keep
data class NutritionWrapper(
    val nutrients: List<Nutrient> = emptyList()
) : Serializable

@Keep
data class Nutrient(
    val name: String = "",
    val amount: Double = 0.0,
    val unit: String = ""
) : Serializable

// ── Instructions Models ──────────────────────────────────────────────────

@Keep
data class AnalyzedInstruction(
    val name: String = "",
    val steps: List<InstructionStep> = emptyList()
)

@Keep
data class InstructionStep(
    val number: Int = 0,
    val step: String = ""
)
