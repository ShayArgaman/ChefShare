package com.chefshare.app.ui.addrecipe

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.chefshare.app.R
import com.chefshare.app.databinding.ActivityAddRecipeBinding
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar

/**
 * Add Recipe screen. Collects the form, lets the user pick a gallery photo, and forwards a validated
 * publish request to the [AddRecipeViewModel]. The Activity only renders state and handles input.
 */
class AddRecipeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddRecipeBinding
    private val viewModel: AddRecipeViewModel by viewModels()

    // Modern photo picker (no storage permission needed). Null result = user backed out -> ignore.
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setImageUri(uri)
            showImagePreview(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddRecipeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        setupListeners()
        observeViewModel()

        // Restore the preview after rotation (the ViewModel kept the picked URI).
        viewModel.selectedImageUri?.let { showImagePreview(it) }
    }

    private fun setupListeners() {
        binding.cardImagePicker.setOnClickListener { launchPicker() }
        binding.buttonAddIngredient.setOnClickListener { addTypedIngredient() }

        // "Done" on the ingredient field also adds it.
        binding.editIngredient.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTypedIngredient()
                true
            } else {
                false
            }
        }

        binding.buttonPublish.setOnClickListener { onPublishClicked() }
    }

    private fun observeViewModel() {
        viewModel.ingredients.observe(this) { renderIngredientChips(it) }
        viewModel.uiState.observe(this) { render(it) }
    }

    private fun launchPicker() {
        pickImageLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    /** Add whatever is typed in the ingredient field, then clear it. */
    private fun addTypedIngredient() {
        val text = binding.editIngredient.text?.toString().orEmpty()
        if (text.isBlank()) return
        viewModel.addIngredient(text)
        binding.editIngredient.text?.clear()
    }

    /** Rebuild the ingredient chips from the ViewModel list; each chip's X removes it. */
    private fun renderIngredientChips(ingredients: List<String>) {
        binding.chipGroupIngredients.removeAllViews()
        val enabled = viewModel.uiState.value !is AddRecipeUiState.Loading
        ingredients.forEach { ingredient ->
            val chip = Chip(this).apply {
                text = ingredient
                isCheckable = false
                isClickable = false
                isEnabled = enabled
                isCloseIconVisible = true
                setOnCloseIconClickListener { viewModel.removeIngredient(ingredient) }
            }
            binding.chipGroupIngredients.addView(chip)
        }
    }

    /** Validate the form, then hand off to the ViewModel to run the upload/save chain. */
    private fun onPublishClicked() {
        val title = binding.editTitle.text?.toString()?.trim().orEmpty()
        val instructions = binding.editInstructions.text?.toString()?.trim().orEmpty()
        val category = selectedCategory()
        val ingredients = viewModel.ingredients.value.orEmpty()

        binding.tilTitle.error = null
        binding.tilInstructions.error = null

        when {
            title.isEmpty() -> {
                binding.tilTitle.error = getString(R.string.error_title_required)
                return
            }
            ingredients.isEmpty() -> {
                showMessage(getString(R.string.error_ingredients_required))
                return
            }
            instructions.isEmpty() -> {
                binding.tilInstructions.error = getString(R.string.error_instructions_required)
                return
            }
            category.isEmpty() -> {
                showMessage(getString(R.string.error_category_required))
                return
            }
        }

        viewModel.publish(title, instructions, category)
    }

    /** Text of the single selected category chip, or "" if none is chosen. */
    private fun selectedCategory(): String {
        val checkedId = binding.chipGroupCategory.checkedChipId
        if (checkedId == View.NO_ID) return ""
        return binding.chipGroupCategory.findViewById<Chip>(checkedId).text.toString()
    }

    /** Render the single UI state. */
    private fun render(state: AddRecipeUiState) {
        when (state) {
            is AddRecipeUiState.Idle -> setLoadingUi(false, null)

            is AddRecipeUiState.Loading -> setLoadingUi(true, state)

            is AddRecipeUiState.Success -> {
                Toast.makeText(this, R.string.toast_recipe_published, Toast.LENGTH_SHORT).show()
                // The Feed is still on the back stack and live-updates, so finish() is enough.
                finish()
            }

            is AddRecipeUiState.Error -> {
                setLoadingUi(false, null)
                showMessage(state.message)
                viewModel.onErrorHandled()
            }
        }
    }

    /** Show/hide progress and lock inputs so the user can't double-submit while publishing. */
    private fun setLoadingUi(loading: Boolean, state: AddRecipeUiState.Loading?) {
        setInputsEnabled(!loading)
        binding.textUploadStatus.isVisible = loading
        binding.progressUpload.isVisible = loading
        binding.buttonPublish.isEnabled = !loading

        if (loading && state != null) {
            when (state.phase) {
                AddRecipeUiState.Phase.UPLOADING -> {
                    val pct = state.progress.coerceIn(0, 100)
                    binding.progressUpload.isIndeterminate = false
                    binding.progressUpload.progress = pct
                    binding.textUploadStatus.text = getString(R.string.uploading_photo_pct, pct)
                }
                AddRecipeUiState.Phase.PUBLISHING -> {
                    binding.progressUpload.isIndeterminate = true
                    binding.textUploadStatus.setText(R.string.publishing)
                }
            }
        }
    }

    /** Enable/disable every input (fields, picker, chips, buttons). */
    private fun setInputsEnabled(enabled: Boolean) {
        binding.cardImagePicker.isEnabled = enabled
        binding.editTitle.isEnabled = enabled
        binding.editIngredient.isEnabled = enabled
        binding.buttonAddIngredient.isEnabled = enabled
        binding.editInstructions.isEnabled = enabled
        binding.buttonPublish.isEnabled = enabled
        setChildrenEnabled(binding.chipGroupCategory, enabled)
        setChildrenEnabled(binding.chipGroupIngredients, enabled)
    }

    private fun setChildrenEnabled(group: ViewGroup, enabled: Boolean) {
        for (i in 0 until group.childCount) {
            group.getChildAt(i).isEnabled = enabled
        }
    }

    private fun showImagePreview(uri: Uri) {
        Glide.with(this).load(uri).centerCrop().into(binding.imagePreview)
        binding.imagePreview.isVisible = true
        binding.imagePlaceholder.isVisible = false
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
