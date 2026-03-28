package com.example.cookgpt.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.cookgpt.R
import com.example.cookgpt.data.CustomRecipeRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.io.File

class CreateRecipeActivity : AppCompatActivity() {

    // Views
    private lateinit var ivDishPhoto: ImageView
    private lateinit var btnAddPhoto: MaterialButton
    private lateinit var etTitle: TextInputEditText
    private lateinit var etIngredients: TextInputEditText
    private lateinit var etSteps: TextInputEditText
    private lateinit var spinnerCuisine: Spinner
    private lateinit var etPrepTime: TextInputEditText
    private lateinit var etCookTime: TextInputEditText
    private lateinit var btnSave: MaterialButton
    private lateinit var progressBar: ProgressBar

    private var selectedImageUri: Uri? = null
    private var cameraImageUri: Uri? = null
    private val repository = CustomRecipeRepository()

    private val cuisineTags = listOf(
        "Indian", "Chinese", "Italian", "Mexican",
        "American", "Mediterranean", "Thai", "Japanese", "Other"
    )

    // ── Activity result launchers ─────────────────────────────────────────

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { setPhotoPreview(it) }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraImageUri?.let { setPhotoPreview(it) }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera() else
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_recipe)

        supportActionBar?.title = "Create Recipe"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        bindViews()
        setupCuisineSpinner()
        setupClickListeners()
    }

    private fun bindViews() {
        ivDishPhoto    = findViewById(R.id.ivDishPhoto)
        btnAddPhoto    = findViewById(R.id.btnAddPhoto)
        etTitle        = findViewById(R.id.etRecipeTitle)
        etIngredients  = findViewById(R.id.etIngredients)
        etSteps        = findViewById(R.id.etSteps)
        spinnerCuisine = findViewById(R.id.spinnerCuisine)
        etPrepTime     = findViewById(R.id.etPrepTime)
        etCookTime     = findViewById(R.id.etCookTime)
        btnSave        = findViewById(R.id.btnSaveRecipe)
        progressBar    = findViewById(R.id.progressBarSave)
    }

    private fun setupCuisineSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, cuisineTags)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCuisine.adapter = adapter
    }

    private fun setupClickListeners() {
        btnAddPhoto.setOnClickListener { showPhotoSourceDialog() }
        btnSave.setOnClickListener { validateAndSave() }
    }

    // ── Photo handling ────────────────────────────────────────────────────

    private fun showPhotoSourceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Add dish photo")
            .setItems(arrayOf("Take a photo", "Choose from gallery")) { _, which ->
                if (which == 0) checkCameraPermissionAndLaunch() else galleryLauncher.launch("image/*")
            }
            .show()
    }

    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> launchCamera()
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val photoFile = File(cacheDir, "recipe_photo_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            photoFile
        )
        cameraImageUri?.let { cameraLauncher.launch(it) }
    }

    private fun setPhotoPreview(uri: Uri) {
        selectedImageUri = uri
        Glide.with(this).load(uri).centerCrop().into(ivDishPhoto)
        ivDishPhoto.visibility = View.VISIBLE
        btnAddPhoto.text = "Change photo"
    }

    // ── Validation and save ───────────────────────────────────────────────

    private fun validateAndSave() {
        val title       = etTitle.text?.toString()?.trim() ?: ""
        val ingredients = etIngredients.text?.toString()?.trim() ?: ""
        val steps       = etSteps.text?.toString()?.trim() ?: ""
        val cuisine     = spinnerCuisine.selectedItem?.toString() ?: "Other"
        val prepTime    = etPrepTime.text?.toString()?.toIntOrNull() ?: 0
        val cookTime    = etCookTime.text?.toString()?.toIntOrNull() ?: 0

        when {
            title.isBlank() -> {
                etTitle.error = "Title is required"
                etTitle.requestFocus()
                return
            }
            ingredients.isBlank() -> {
                etIngredients.error = "Add at least one ingredient"
                etIngredients.requestFocus()
                return
            }
            steps.isBlank() -> {
                etSteps.error = "Add at least one step"
                etSteps.requestFocus()
                return
            }
            cookTime <= 0 -> {
                etCookTime.error = "Enter a valid cook time"
                etCookTime.requestFocus()
                return
            }
        }

        saveRecipe(title, ingredients, steps, cuisine, prepTime, cookTime)
    }

    private fun saveRecipe(
        title: String, ingredients: String, steps: String,
        cuisine: String, prepTime: Int, cookTime: Int
    ) {
        setLoading(true)
        lifecycleScope.launch {
            val result = repository.saveRecipe(
                context         = this@CreateRecipeActivity,
                title           = title,
                ingredients     = ingredients,
                steps           = steps,
                cuisine         = cuisine,
                prepTimeMinutes = prepTime,
                cookTimeMinutes = cookTime,
                imageUri        = selectedImageUri
            )
            setLoading(false)
            if (result.isSuccess) {
                Toast.makeText(this@CreateRecipeActivity, "Recipe saved!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(
                    this@CreateRecipeActivity,
                    "Failed to save: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnSave.isEnabled = !loading
        btnAddPhoto.isEnabled = !loading
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
