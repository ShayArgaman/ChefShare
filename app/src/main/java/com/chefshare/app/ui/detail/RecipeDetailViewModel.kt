package com.chefshare.app.ui.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chefshare.app.data.model.Recipe
import com.chefshare.app.data.repository.AuthRepository
import com.chefshare.app.data.repository.RecipeRepository
import com.chefshare.app.util.Resource
import kotlinx.coroutines.launch

/**
 * Drives the Recipe Detail screen for a given [recipeId].
 *
 * It observes the single recipe document LIVE, so the heart reflects the real like state and any
 * change (from this screen or the feed) shows up immediately.
 */
class RecipeDetailViewModel(private val recipeId: String) : ViewModel() {

    private val recipeRepository = RecipeRepository()
    private val authRepository = AuthRepository()

    val currentUserId: String? = authRepository.currentUserId

    // Latest live recipe state (Loading -> Success/Error).
    private val _recipe = MutableLiveData<Resource<Recipe>>(Resource.Loading)
    val recipe: LiveData<Resource<Recipe>> = _recipe

    // Transient like-failure message -> Snackbar (consumed after handling).
    private val _actionMessage = MutableLiveData<String?>()
    val actionMessage: LiveData<String?> = _actionMessage

    // Keep the last loaded recipe so toggleLike knows the current like state.
    private var currentRecipe: Recipe? = null

    init {
        viewModelScope.launch {
            recipeRepository.observeRecipe(recipeId).collect { result ->
                if (result is Resource.Success) currentRecipe = result.data
                _recipe.value = result
            }
        }
    }

    /** Atomically like/unlike; the live listener updates the heart afterwards. */
    fun toggleLike() {
        val uid = currentUserId ?: return
        val recipe = currentRecipe ?: return
        viewModelScope.launch {
            val result = recipeRepository.toggleLike(recipe.id, uid, recipe.isLikedBy(uid))
            if (result is Resource.Error) _actionMessage.value = result.message
        }
    }

    fun onActionMessageHandled() {
        _actionMessage.value = null
    }
}

/** Supplies the recipeId to [RecipeDetailViewModel] (which needs it at construction). */
class RecipeDetailViewModelFactory(
    private val recipeId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RecipeDetailViewModel(recipeId) as T
}
