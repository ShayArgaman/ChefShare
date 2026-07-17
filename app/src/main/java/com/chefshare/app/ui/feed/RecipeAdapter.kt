package com.chefshare.app.ui.feed

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.chefshare.app.R
import com.chefshare.app.data.model.Recipe
import com.chefshare.app.databinding.ItemRecipeBinding

/**
 * RecyclerView adapter for recipe cards.
 *
 * Uses [ListAdapter] + [DiffUtil]: instead of notifyDataSetChanged() (which rebinds everything and
 * kills scroll/animations), DiffUtil computes the exact changed rows off the main thread, so live
 * updates from Firestore animate smoothly and only the affected cards rebind.
 *
 * @param currentUserId used to show the correct heart state (filled if this user liked the recipe).
 * @param onRecipeClick card tap -> open details.
 * @param onLikeClick    heart tap -> like/unlike.
 */
class RecipeAdapter(
    private val currentUserId: String?,
    private val onRecipeClick: (Recipe) -> Unit,
    private val onLikeClick: (Recipe) -> Unit
) : ListAdapter<Recipe, RecipeAdapter.RecipeViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemRecipeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecipeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecipeViewHolder(
        private val binding: ItemRecipeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe) {
            val context = binding.root.context

            binding.textTitle.text = recipe.title
            binding.textAuthor.text = context.getString(R.string.by_author, recipe.authorName)

            // Load the photo with Glide; a gray placeholder shows while loading / on error,
            // and a cross-fade keeps the swap smooth.
            Glide.with(binding.imageRecipe)
                .load(recipe.imageUrl)
                .placeholder(R.drawable.bg_recipe_placeholder)
                .error(R.drawable.bg_recipe_placeholder)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.imageRecipe)

            // Heart reflects whether THIS user's UID is in likedByUsers.
            val liked = recipe.isLikedBy(currentUserId)
            binding.buttonLike.setImageResource(
                if (liked) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            )
            val tintColor = ContextCompat.getColor(
                context,
                if (liked) R.color.error else R.color.on_surface
            )
            binding.buttonLike.setColorFilter(tintColor)

            // Whole card opens details; the heart handles its own tap without triggering the card.
            binding.root.setOnClickListener { onRecipeClick(recipe) }
            binding.buttonLike.setOnClickListener { onLikeClick(recipe) }
        }
    }

    companion object {
        // Identity by document id; content equality via the data class (includes likedByUsers),
        // so a like/unlike is detected and only that card re-binds.
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Recipe>() {
            override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe): Boolean =
                oldItem == newItem
        }
    }
}
