package com.chefshare.app.data

/**
 * Central place for Firestore collection / field names and Storage folders.
 * Keeping these as constants avoids scattered "magic strings" and typos across the repositories.
 */
object FirestorePaths {
    // Firestore
    const val COLLECTION_RECIPES = "recipes"
    const val FIELD_TIMESTAMP = "timestamp"
    const val FIELD_LIKED_BY = "likedByUsers"

    // Cloud Storage — recipe photos live under recipes/{UUID}.jpg
    const val STORAGE_RECIPE_IMAGES = "recipes"
}
