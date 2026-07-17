package com.chefshare.app.util

/**
 * A tiny wrapper the repository layer returns to the ViewModels so every network result carries
 * an explicit state. This is central to our crash-prevention strategy: the UI always receives
 * either data or a friendly message — it never has to catch raw exceptions itself.
 */
sealed class Resource<out T> {
    /** The operation succeeded and produced [data]. */
    data class Success<out T>(val data: T) : Resource<T>()

    /** The operation failed; [message] is safe to show to the user in a Toast. */
    data class Error(val message: String) : Resource<Nothing>()

    /** The operation is in progress (used to drive progress spinners). */
    data object Loading : Resource<Nothing>()
}
