package com.example.cookgpt.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class RecipeDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "Recipes.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_NAME = "saved_recipes"
        private const val COL_ID = "id"
        private const val COL_USER_ID = "user_id"
        private const val COL_TITLE = "title"
        private const val COL_IMAGE = "image"
        private const val COL_INGREDIENTS = "ingredients"
        private const val COL_DESCRIPTION = "description"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTable = ("CREATE TABLE " + TABLE_NAME + " ("
                + COL_ID + " INTEGER,"
                + COL_USER_ID + " TEXT,"
                + COL_TITLE + " TEXT,"
                + COL_IMAGE + " TEXT,"
                + COL_INGREDIENTS + " TEXT,"
                + COL_DESCRIPTION + " TEXT,"
                + "PRIMARY KEY (" + COL_ID + ", " + COL_USER_ID + "))")
        db?.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertRecipe(recipe: SavedRecipe, userId: String): Long {
        val db = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(COL_ID, recipe.id)
        contentValues.put(COL_USER_ID, userId)
        contentValues.put(COL_TITLE, recipe.title)
        contentValues.put(COL_IMAGE, recipe.image)
        contentValues.put(COL_INGREDIENTS, recipe.ingredients)
        contentValues.put(COL_DESCRIPTION, recipe.description)

        return db.insertWithOnConflict(TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun isRecipeSaved(id: Int, userId: String): Boolean {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME WHERE $COL_ID = ? AND $COL_USER_ID = ?", arrayOf(id.toString(), userId))
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    fun deleteRecipe(id: Int, userId: String): Int {
        val db = this.writableDatabase
        return db.delete(TABLE_NAME, "$COL_ID = ? AND $COL_USER_ID = ?", arrayOf(id.toString(), userId))
    }

    fun clearAllRecipes() {
        val db = this.writableDatabase
        db.delete(TABLE_NAME, null, null)
    }

    fun getAllSavedRecipes(userId: String): List<SavedRecipe> {
        val recipeList = mutableListOf<SavedRecipe>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME WHERE $COL_USER_ID = ?", arrayOf(userId))

        if (cursor.moveToFirst()) {
            do {
                val recipe = SavedRecipe(
                    cursor.getInt(0),
                    cursor.getString(2),
                    cursor.getString(3),
                    cursor.getString(4),
                    cursor.getString(5)
                )
                recipeList.add(recipe)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return recipeList
    }
}
