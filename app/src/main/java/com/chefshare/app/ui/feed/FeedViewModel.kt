package com.chefshare.app.ui.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chefshare.app.data.model.Recipe
import com.chefshare.app.data.repository.AuthRepository
import com.chefshare.app.data.repository.RecipeRepository
import com.chefshare.app.util.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Drives the Community Feed.
 *
 * Data flow:
 *  - The repository exposes the 'recipes' collection as a LIVE stream (a Firestore
 *    addSnapshotListener bridged to a Flow). We collect it here, so the feed updates in real time
 *    whenever anyone adds a recipe or toggles a like — no manual refresh needed.
 *  - Category (chips) and search (text) filtering happen locally on the last snapshot, so they are
 *    instant and cost zero extra reads.
 */
class FeedViewModel : ViewModel() {

    private val recipeRepository = RecipeRepository()
    private val authRepository = AuthRepository()

    /** Needed to render heart state and to like/unlike as the current user. */
    val currentUserId: String? = authRepository.currentUserId

    /** Display name for the toolbar profile summary. */
    val currentUserName: String get() = authRepository.currentUserName

    // The latest full snapshot from Firestore + the current filters.
    private var allRecipes: List<Recipe> = emptyList()
    private var selectedCategory: String = CATEGORY_ALL
    private var searchQuery: String = ""

    // Handle to the collection coroutine so "Retry" can cancel and re-subscribe.
    private var listenJob: Job? = null

    // Filtered list handed to the adapter.
    private val _recipes = MutableLiveData<List<Recipe>>(emptyList())
    val recipes: LiveData<List<Recipe>> = _recipes

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Feed-load failure -> shown as a Snackbar with a "Retry" action (consumed after handling).
    private val _loadError = MutableLiveData<String?>()
    val loadError: LiveData<String?> = _loadError

    // Transient messages (e.g. a like that failed) -> plain Snackbar (consumed after handling).
    private val _actionMessage = MutableLiveData<String?>()
    val actionMessage: LiveData<String?> = _actionMessage

    init {
        startListening()
    }

    /** Subscribe to the live recipe stream. Safe to call again (cancels any previous subscription). */
    fun startListening() {
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            recipeRepository.observeRecipes().collect { result ->
                when (result) {
                    is Resource.Loading -> _isLoading.value = true
                    is Resource.Success -> {
                        _isLoading.value = false
                        _loadError.value = null
                        allRecipes = result.data
                        applyFilters()
                    }
                    is Resource.Error -> {
                        // A Firestore listener error is terminal, so we surface it and let the
                        // user re-subscribe via Retry.
                        _isLoading.value = false
                        _loadError.value = result.message
                    }
                }
            }
        }
    }

    /** Re-subscribe after an error (wired to the Snackbar's Retry action and pull-to-refresh). */
    fun retry() = startListening()

    /** Chip selection -> filter locally. */
    fun setCategory(category: String) {
        selectedCategory = category
        applyFilters()
    }

    /** Search text -> filter locally. */
    fun setSearchQuery(query: String) {
        searchQuery = query.trim()
        applyFilters()
    }

    /** Re-derive the visible list from the last snapshot + the active category + search text. */
    private fun applyFilters() {
        _recipes.value = allRecipes.filter { recipe ->
            val matchesCategory = when (selectedCategory) {
                CATEGORY_ALL -> true
                // Favorites tab: keep only recipes this user has liked. isLikedBy is null-safe.
                CATEGORY_FAVORITES -> recipe.isLikedBy(currentUserId)
                else -> recipe.category.equals(selectedCategory, ignoreCase = true)
            }
            val matchesSearch =
                searchQuery.isEmpty() ||
                    recipe.title.contains(searchQuery, ignoreCase = true) ||
                    recipe.authorName.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    /** True when the "Favorites" chip is the active filter (drives the empty-state message). */
    val isFavoritesTab: Boolean get() = selectedCategory == CATEGORY_FAVORITES

    /** How many recipes the current user has favorited (for the toolbar profile summary). */
    fun favoritesCount(): Int = allRecipes.count { it.isLikedBy(currentUserId) }

    /** True when recipes exist but the active filter/search hides them all (for a clearer empty message). */
    fun hasLoadedRecipes(): Boolean = allRecipes.isNotEmpty()

    /**
     * Like / unlike a recipe.
     *
     * We hand the repository an atomic arrayUnion/arrayRemove on likedByUsers, so simultaneous likes
     * from different devices can't clobber each other. We don't manually mutate the UI here: the live
     * listener re-emits the updated document (Firestore fires it optimistically for the local write),
     * so the heart flips practically instantly.
     */
    fun toggleLike(recipe: Recipe) {
        val uid = currentUserId ?: return // not signed in -> ignore
        val currentlyLiked = recipe.isLikedBy(uid)
        viewModelScope.launch {
            val result = recipeRepository.toggleLike(recipe.id, uid, currentlyLiked)
            if (result is Resource.Error) _actionMessage.value = result.message
        }
    }

    fun onLoadErrorHandled() {
        _loadError.value = null
    }

    fun onActionMessageHandled() {
        _actionMessage.value = null
    }

    companion object {
        const val CATEGORY_ALL = "All"
        const val CATEGORY_FAVORITES = "Favorites"
    }
}
