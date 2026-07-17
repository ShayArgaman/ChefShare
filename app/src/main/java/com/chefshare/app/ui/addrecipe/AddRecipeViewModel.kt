package com.chefshare.app.ui.addrecipe

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chefshare.app.data.model.Recipe
import com.chefshare.app.data.repository.AuthRepository
import com.chefshare.app.data.repository.RecipeRepository
import com.chefshare.app.util.Resource
import kotlinx.coroutines.launch

/**
 * Drives the Add Recipe screen: holds the in-progress ingredient list + picked image, and runs the
 * publish chain (upload photo -> get URL -> write Firestore document) while reporting a single
 * [AddRecipeUiState].
 *
 * State survives rotation (ViewModel), so the ingredient chips and chosen photo persist automatically.
 */
class AddRecipeViewModel : ViewModel() {

    private val recipeRepository = RecipeRepository()
    private val authRepository = AuthRepository()

    // Ingredients added so far (rendered as removable chips).
    private val _ingredients = MutableLiveData<List<String>>(emptyList())
    val ingredients: LiveData<List<String>> = _ingredients

    // The picked gallery image (null until the user chooses one — an image is optional).
    var selectedImageUri: Uri? = null
        private set

    private val _uiState = MutableLiveData<AddRecipeUiState>(AddRecipeUiState.Idle)
    val uiState: LiveData<AddRecipeUiState> = _uiState

    /** Add a trimmed, non-blank, non-duplicate ingredient. */
    fun addIngredient(raw: String) {
        val ingredient = raw.trim()
        if (ingredient.isEmpty()) return
        val current = _ingredients.value.orEmpty()
        if (current.any { it.equals(ingredient, ignoreCase = true) }) return
        _ingredients.value = current + ingredient
    }

    /** Remove an ingredient (from a chip's X). */
    fun removeIngredient(ingredient: String) {
        _ingredients.value = _ingredients.value.orEmpty().filterNot { it == ingredient }
    }

    /** Remember the gallery image the user picked (or clear it if null). */
    fun setImageUri(uri: Uri?) {
        selectedImageUri = uri
    }

    /**
     * Publish the recipe. Inputs are already validated by the Activity, so here we just run the
     * network chain. All Firebase failures come back as Resource.Error (friendly messages) — the
     * whole chain can never throw to the UI.
     */
    fun publish(title: String, instructions: String, category: String) {
        val uid = authRepository.currentUserId
        if (uid == null) {
            // Guard: shouldn't happen (we require login), but never crash if it does.
            _uiState.value = AddRecipeUiState.Error(SIGNED_OUT_MESSAGE)
            return
        }
        val ingredientList = _ingredients.value.orEmpty()

        viewModelScope.launch {
            // Step 1: upload the photo (if any), reporting progress.
            var imageUrl = ""
            val uri = selectedImageUri
            if (uri != null) {
                _uiState.value = AddRecipeUiState.Loading(AddRecipeUiState.Phase.UPLOADING, 0)
                val uploadResult = recipeRepository.uploadRecipeImage(uri) { percent ->
                    _uiState.value = AddRecipeUiState.Loading(AddRecipeUiState.Phase.UPLOADING, percent)
                }
                when (uploadResult) {
                    is Resource.Success -> imageUrl = uploadResult.data
                    is Resource.Error -> {
                        _uiState.value = AddRecipeUiState.Error(uploadResult.message)
                        return@launch
                    }
                    else -> Unit
                }
            }

            // Step 2 + 3: build the Recipe (with author info + timestamp) and save it.
            _uiState.value = AddRecipeUiState.Loading(AddRecipeUiState.Phase.PUBLISHING, -1)
            val recipe = Recipe(
                title = title.trim(),
                category = category,
                ingredients = ingredientList,
                instructions = instructions.trim(),
                imageUrl = imageUrl,
                authorId = uid,
                authorName = authRepository.currentUserName,
                likedByUsers = emptyList(),
                timestamp = System.currentTimeMillis()
            )

            _uiState.value = when (val result = recipeRepository.addRecipe(recipe)) {
                is Resource.Success -> AddRecipeUiState.Success
                is Resource.Error -> AddRecipeUiState.Error(result.message)
                else -> AddRecipeUiState.Error(GENERIC_MESSAGE)
            }
        }
    }

    /** Reset back to Idle after the Activity has shown an error (re-enables inputs). */
    fun onErrorHandled() {
        _uiState.value = AddRecipeUiState.Idle
    }

    companion object {
        private const val SIGNED_OUT_MESSAGE = "You must be signed in to publish."
        private const val GENERIC_MESSAGE = "Something went wrong. Please try again."
    }
}
