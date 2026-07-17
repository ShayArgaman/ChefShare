package com.chefshare.app.ui.feed

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.chefshare.app.R
import com.chefshare.app.data.model.Recipe
import com.chefshare.app.data.repository.AuthRepository
import com.chefshare.app.databinding.ActivityFeedBinding
import com.chefshare.app.ui.addrecipe.AddRecipeActivity
import com.chefshare.app.ui.auth.LoginActivity
import com.chefshare.app.ui.detail.RecipeDetailActivity
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar

/**
 * Community Feed screen.
 *
 * Renders the live recipe list, wires search + category filtering + the like button to the
 * [FeedViewModel], and navigates to Add Recipe (FAB). The Activity stays "dumb": it only observes
 * LiveData and forwards user intents to the ViewModel.
 */
class FeedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeedBinding
    private val viewModel: FeedViewModel by viewModels()
    private val authRepository = AuthRepository()
    private lateinit var recipeAdapter: RecipeAdapter
    private var currentRecipes: List<Recipe> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        setupCategoryChips()
        setupFab()
        setupSwipeRefresh()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_profile -> {
                    showProfileSummary()
                    true
                }
                R.id.action_sign_out -> {
                    signOut()
                    true
                }
                else -> false
            }
        }
    }

    /** Quick profile/favorites summary in a toast. */
    private fun showProfileSummary() {
        val message = getString(
            R.string.profile_summary,
            viewModel.currentUserName,
            viewModel.favoritesCount()
        )
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun setupRecyclerView() {
        // Pass the current UID so hearts render correctly; forward taps to the ViewModel/navigation.
        recipeAdapter = RecipeAdapter(
            currentUserId = viewModel.currentUserId,
            onRecipeClick = { recipe -> openRecipeDetail(recipe) },
            onLikeClick = { recipe -> viewModel.toggleLike(recipe) }
        )
        binding.recyclerRecipes.layoutManager = LinearLayoutManager(this)
        binding.recyclerRecipes.adapter = recipeAdapter
    }

    private fun setupSearch() {
        // Local, instant filtering as the user types.
        binding.editSearch.doAfterTextChanged { text ->
            viewModel.setSearchQuery(text?.toString().orEmpty())
        }
    }

    private fun setupCategoryChips() {
        // singleSelection + selectionRequired guarantee exactly one chip is always checked.
        binding.chipGroupCategories.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(checkedId)
            viewModel.setCategory(chip?.text?.toString() ?: FeedViewModel.CATEGORY_ALL)
        }
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddRecipeActivity::class.java))
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.retry() }
    }

    private fun observeViewModel() {
        // Live list -> adapter. ListAdapter + DiffUtil animates only what changed.
        viewModel.recipes.observe(this) { recipes ->
            currentRecipes = recipes
            recipeAdapter.submitList(recipes)
            updateEmptyState()
        }

        // Loading: centered spinner only for the initial (empty) load; always stop the pull spinner.
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.isVisible = loading && currentRecipes.isEmpty()
            if (!loading) binding.swipeRefresh.isRefreshing = false
            updateEmptyState()
        }

        // Feed-load error -> Snackbar with a Retry action (crash-safe recovery).
        viewModel.loadError.observe(this) { message ->
            if (message != null) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.action_retry) { viewModel.retry() }
                    .show()
                viewModel.onLoadErrorHandled()
            }
        }

        // One-off action failure (e.g. a like couldn't be saved) -> plain Snackbar.
        viewModel.actionMessage.observe(this) { message ->
            if (message != null) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                viewModel.onActionMessageHandled()
            }
        }
    }

    /**
     * Show the empty view only when we're NOT loading and the list is empty (avoids a flash during
     * the initial load). The message adapts: "no matches" when recipes exist but the filter/search
     * hides them, otherwise "the feed is empty".
     */
    private fun updateEmptyState() {
        val loading = viewModel.isLoading.value == true
        val isEmpty = currentRecipes.isEmpty() && !loading
        binding.textEmpty.isVisible = isEmpty
        if (isEmpty) {
            val messageRes = when {
                viewModel.isFavoritesTab -> R.string.feed_favorites_empty
                viewModel.hasLoadedRecipes() -> R.string.feed_no_results
                else -> R.string.feed_empty
            }
            binding.textEmpty.setText(messageRes)
        }
    }

    /** Open the detail screen, passing just the recipe id (safe String extra). */
    private fun openRecipeDetail(recipe: Recipe) {
        startActivity(RecipeDetailActivity.newIntent(this, recipe.id))
    }

    /** Sign out and return to Login, clearing the feed from the back stack. */
    private fun signOut() {
        authRepository.signOut()
        val intent = Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}
