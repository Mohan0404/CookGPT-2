package com.example.cookgpt.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CustomRecipeRepository {

    companion object {
        private const val TAG = "CustomRecipeRepository"
        private const val MAX_IMAGE_DIMENSION = 800
        private const val JPEG_QUALITY = 70
    }

    private val auth     = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val storage  = FirebaseStorage.getInstance()

    private val uid: String?
        get() = auth.currentUser?.uid

    // ── Read: observe all recipes for current user as a Flow ──────────────

    fun observeMyRecipes(): Flow<List<CustomRecipe>> = callbackFlow {
        val currentUid = uid ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val ref = database.getReference("users/$currentUid/custom_recipes")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val recipes = snapshot.children.mapNotNull {
                        it.getValue(CustomRecipe::class.java)
                    }.sortedByDescending { it.createdAt }
                    trySend(recipes)
                } catch (e: Exception) {
                    Log.e(TAG, "Parsing failed: ${e.message}")
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "DB read cancelled: ${error.message}")
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Write: save a new custom recipe (with optional image upload) ───────

    suspend fun saveRecipe(
        context: Context,
        title: String,
        ingredients: String,
        steps: String,
        cuisine: String,
        prepTimeMinutes: Int,
        cookTimeMinutes: Int,
        imageUri: Uri?
    ): Result<String> {
        val currentUid = uid ?: return Result.failure(Exception("User not logged in"))

        return try {
            val ref = database.getReference("users/$currentUid/custom_recipes")
            val recipeId = ref.push().key
                ?: return Result.failure(Exception("Could not generate recipe ID"))

            // Upload image if provided
            val imageUrl = if (imageUri != null) {
                uploadImage(context, currentUid, recipeId, imageUri)
            } else {
                ""
            }

            val recipe = CustomRecipe(
                id              = recipeId,
                title           = title.trim(),
                imageUrl        = imageUrl,
                ingredients     = ingredients.trim(),
                steps           = steps.trim(),
                cuisine         = cuisine,
                prepTimeMinutes = prepTimeMinutes,
                cookTimeMinutes = cookTimeMinutes,
                createdAt       = System.currentTimeMillis(),
                uid             = currentUid
            )

            saveToDatabase(ref, recipeId, recipe)
            Result.success(recipeId)
        } catch (e: Exception) {
            Log.e(TAG, "saveRecipe failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Delete ─────────────────────────────────────────────────────────────

    suspend fun deleteRecipe(recipeId: String, imageUrl: String): Result<Unit> {
        val currentUid = uid ?: return Result.failure(Exception("User not logged in"))
        return try {
            // Delete from database
            val dbRef = database.getReference("users/$currentUid/custom_recipes/$recipeId")
            deleteFromDatabase(dbRef)

            // Delete image from Storage if present
            if (imageUrl.isNotBlank()) {
                try {
                    val storageRef = storage.getReferenceFromUrl(imageUrl)
                    deleteFromStorage(storageRef)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not delete image (non-fatal): ${e.message}")
                    // Non-fatal — recipe is already deleted from DB
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteRecipe failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private suspend fun uploadImage(
        context: Context,
        uid: String,
        recipeId: String,
        uri: Uri
    ): String = suspendCancellableCoroutine { cont ->
        try {
            // Compress image before upload
            val compressed = compressImage(context, uri)
            val storageRef = storage.reference
                .child("recipe_images/$uid/$recipeId.jpg")

            storageRef.putBytes(compressed)
                .continueWithTask { task ->
                    if (!task.isSuccessful) throw task.exception!!
                    storageRef.downloadUrl
                }
                .addOnSuccessListener { uri ->
                    cont.resume(uri.toString())
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        } catch (e: Exception) {
            cont.resumeWithException(e)
        }
    }

    private fun compressImage(context: Context, uri: Uri): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open image URI")
        val original = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        // Scale down if needed
        val scaled = if (original.width > MAX_IMAGE_DIMENSION || original.height > MAX_IMAGE_DIMENSION) {
            val ratio = MAX_IMAGE_DIMENSION.toFloat() / maxOf(original.width, original.height)
            val w = (original.width * ratio).toInt()
            val h = (original.height * ratio).toInt()
            Bitmap.createScaledBitmap(original, w, h, true)
        } else {
            original
        }

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return out.toByteArray()
    }

    private suspend fun saveToDatabase(
        ref: com.google.firebase.database.DatabaseReference,
        recipeId: String,
        recipe: CustomRecipe
    ) = suspendCancellableCoroutine<Unit> { cont ->
        ref.child(recipeId).setValue(recipe)
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private suspend fun deleteFromDatabase(
        ref: com.google.firebase.database.DatabaseReference
    ) = suspendCancellableCoroutine<Unit> { cont ->
        ref.removeValue()
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private suspend fun deleteFromStorage(
        ref: com.google.firebase.storage.StorageReference
    ) = suspendCancellableCoroutine<Unit> { cont ->
        ref.delete()
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
