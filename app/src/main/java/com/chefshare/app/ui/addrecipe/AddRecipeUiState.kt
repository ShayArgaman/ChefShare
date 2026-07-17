package com.chefshare.app.ui.addrecipe

/**
 * Single UI state for the Add Recipe screen, observed by the Activity.
 *
 * The publish flow moves Idle -> Loading(UPLOADING, %) -> Loading(PUBLISHING) -> Success | Error.
 */
sealed class AddRecipeUiState {

    /** Nothing in flight; inputs enabled. */
    data object Idle : AddRecipeUiState()

    /**
     * A publish is in progress; inputs are disabled.
     * @param phase    which step we're on (photo upload vs. saving the document).
     * @param progress upload percentage 0..100, or -1 for an indeterminate step.
     */
    data class Loading(val phase: Phase, val progress: Int) : AddRecipeUiState()

    /** Recipe saved — the Activity toasts, navigates back to the feed, and finishes. */
    data object Success : AddRecipeUiState()

    /** Something failed; [message] is a friendly, user-facing string for a Snackbar. */
    data class Error(val message: String) : AddRecipeUiState()

    enum class Phase { UPLOADING, PUBLISHING }
}
