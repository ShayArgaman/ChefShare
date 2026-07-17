package com.chefshare.app.ui.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.chefshare.app.R
import com.chefshare.app.data.model.Recipe
import com.chefshare.app.databinding.ActivityRecipeDetailBinding
import com.chefshare.app.databinding.ItemIngredientBinding
import com.chefshare.app.databinding.ItemStepBinding
import com.chefshare.app.util.Resource
import com.google.android.material.snackbar.Snackbar

/**
 * Recipe Detail screen.
 *
 * We pass ONLY the recipe id through the Intent (a plain String extra) — no Parcelable marshalling,
 * so there's nothing that can fail to un-marshal — then load the full recipe live from Firestore.
 */
class RecipeDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeDetailBinding

    // Read the id from the Intent; blank if the extra is missing.
    private val recipeId: String by lazy { intent.getStringExtra(EXTRA_RECIPE_ID).orEmpty() }

    private val viewModel: RecipeDetailViewModel by viewModels {
        RecipeDetailViewModelFactory(recipeId)
    }

    // Static content (title/photo/ingredients/steps) is bound once; the heart updates every emission.
    private var staticBound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Guard: without a valid id there's nothing to show — leave gracefully instead of crashing.
        if (recipeId.isBlank()) {
            Toast.makeText(this, R.string.recipe_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonLike.setOnClickListener { viewModel.toggleLike() }
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.recipe.observe(this) { state ->
            when (state) {
                is Resource.Loading -> binding.progressBar.isVisible = true
                is Resource.Success -> renderRecipe(state.data)
                is Resource.Error -> {
                    // Missing doc / permission / network — show the message and back out.
                    binding.progressBar.isVisible = false
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }

        viewModel.actionMessage.observe(this) { message ->
            if (message != null) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                viewModel.onActionMessageHandled()
            }
        }
    }

    private fun renderRecipe(recipe: Recipe) {
        binding.progressBar.isVisible = false
        binding.scrollContent.isVisible = true
        binding.buttonLike.isVisible = true

        // Bind the parts that don't change on a like toggle only once (avoids re-inflating lists).
        if (!staticBound) {
            bindStaticContent(recipe)
            staticBound = true
        }
        bindHeart(recipe)
    }

    private fun bindStaticContent(recipe: Recipe) {
        binding.textTitle.text = recipe.title
        binding.textAuthor.text = getString(R.string.by_author, recipe.authorName)

        // Category tag only if present.
        if (recipe.category.isBlank()) {
            binding.textCategory.isVisible = false
        } else {
            binding.textCategory.isVisible = true
            binding.textCategory.text = recipe.category
        }

        // Hero image — placeholder covers null/empty URLs and load errors, so it never crashes.
        Glide.with(this)
            .load(recipe.imageUrl)
            .placeholder(R.drawable.bg_recipe_placeholder)
            .error(R.drawable.bg_recipe_placeholder)
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(binding.imageHero)

        renderIngredients(recipe.ingredients)
        renderSteps(recipe.instructions)
    }

    /** Inflate one bullet row per ingredient into the container. */
    private fun renderIngredients(ingredients: List<String>) {
        binding.containerIngredients.removeAllViews()
        ingredients.forEach { ingredient ->
            val row = ItemIngredientBinding.inflate(
                layoutInflater, binding.containerIngredients, false
            )
            row.textIngredient.text = ingredient
            binding.containerIngredients.addView(row.root)
        }
    }

    /**
     * Split the free-text instructions into steps (one per non-blank line), strip any leading
     * "1." / "2)" the author typed, and render an auto-numbered card per step.
     */
    private fun renderSteps(instructions: String) {
        binding.containerInstructions.removeAllViews()
        val steps = instructions
            .split("\n")
            .map { it.trim().replace(LEADING_NUMBER, "") }
            .filter { it.isNotEmpty() }

        steps.forEachIndexed { index, step ->
            val row = ItemStepBinding.inflate(
                layoutInflater, binding.containerInstructions, false
            )
            row.textStepNumber.text = (index + 1).toString()
            row.textStepText.text = step
            binding.containerInstructions.addView(row.root)
        }
    }

    /** Reflect the current like state (filled + red when liked, outline otherwise). */
    private fun bindHeart(recipe: Recipe) {
        val liked = recipe.isLikedBy(viewModel.currentUserId)
        binding.buttonLike.setImageResource(
            if (liked) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
        val tint = ContextCompat.getColor(this, if (liked) R.color.error else R.color.on_surface)
        binding.buttonLike.setColorFilter(tint)
    }

    companion object {
        private const val EXTRA_RECIPE_ID = "extra_recipe_id"

        // Strips a leading enumeration like "1. " or "2) " from a typed instruction line.
        private val LEADING_NUMBER = Regex("^\\d+[.)]\\s*")

        /** Build the Intent that opens this screen for [recipeId]. */
        fun newIntent(context: Context, recipeId: String): Intent =
            Intent(context, RecipeDetailActivity::class.java)
                .putExtra(EXTRA_RECIPE_ID, recipeId)
    }
}
