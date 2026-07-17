package com.chefshare.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude

/**
 * A single shared recipe — the core document stored in the Firestore "recipes" collection.
 *
 * IMPORTANT (Firestore requirement): every property MUST have a default value so that Firestore
 * can construct the object with its no-argument constructor when reading a document. Do not remove
 * the defaults.
 *
 * @property id           Firestore document id. Filled automatically by @DocumentId on reads;
 *                        never written back into the document body.
 * @property title        Recipe name shown on cards and the detail hero.
 * @property category      Category used by the feed's filter chips (e.g. Breakfast, Lunch, Dinner, Dessert).
 * @property ingredients  Ingredient lines (one string per ingredient) — rendered as a checklist.
 * @property instructions Free-text / numbered preparation steps.
 * @property imageUrl     Download URL of the photo stored in Firebase Cloud Storage.
 * @property authorId     UID of the user who created the recipe.
 * @property authorName   Display name of the author (denormalized so the feed needs no extra query).
 * @property likedByUsers UIDs that favorited this recipe. Kept as an array so "favorites" work with a
 *                        single atomic arrayUnion/arrayRemove — no join collection or complex query.
 * @property timestamp    Creation time in epoch millis; used to order the feed newest-first.
 */
data class Recipe(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val category: String = "",
    val ingredients: List<String> = emptyList(),
    val instructions: String = "",
    val imageUrl: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val likedByUsers: List<String> = emptyList(),
    val timestamp: Long = 0L
) {
    /** Number of likes. @Exclude keeps Firestore from trying to persist this derived value. */
    @get:Exclude
    val likeCount: Int
        get() = likedByUsers.size

    /** True if the given user has favorited this recipe. Also excluded from Firestore writes. */
    @Exclude
    fun isLikedBy(userId: String?): Boolean = userId != null && likedByUsers.contains(userId)
}
