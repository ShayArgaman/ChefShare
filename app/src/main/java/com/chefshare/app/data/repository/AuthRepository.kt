package com.chefshare.app.data.repository

import com.chefshare.app.util.Resource
import com.chefshare.app.util.toUserMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.tasks.await

/**
 * Repository for Firebase Authentication (Email/Password).
 *
 * Every network call is wrapped in try/catch and returns a [Resource], so callers (ViewModels)
 * never deal with raw exceptions — this is the crash-prevention rule applied at the data layer.
 *
 * The FirebaseAuth instance is injected with a default so ViewModels can just call `AuthRepository()`,
 * while tests can pass a fake.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    /** UID of the signed-in user, or null if nobody is signed in. */
    val currentUserId: String? get() = auth.currentUser?.uid

    /** Display name of the signed-in user (falls back to the email prefix, then a generic label). */
    val currentUserName: String
        get() = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: "Chef"

    /** True when a user session already exists (used by the launcher to pick the start screen). */
    fun isLoggedIn(): Boolean = auth.currentUser != null

    /**
     * Signs an existing user in with email/password.
     * Returns Success(user) or Error(friendly message).
     */
    suspend fun signIn(email: String, password: String): Resource<FirebaseUser> = try {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val user = result.user
        if (user != null) Resource.Success(user)
        else Resource.Error("Sign in failed. Please try again.")
    } catch (e: Exception) {
        // Any Firebase/network failure becomes a friendly, user-facing message.
        Resource.Error(e.toUserMessage())
    }

    /**
     * Creates a new account and stores the chosen display name on the Firebase user profile
     * (so it can be denormalized onto each recipe as authorName later).
     */
    suspend fun signUp(name: String, email: String, password: String): Resource<FirebaseUser> = try {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user
        if (user != null) {
            // Attach the display name to the freshly created profile.
            user.updateProfile(userProfileChangeRequest { displayName = name.trim() }).await()
            Resource.Success(user)
        } else {
            Resource.Error("Sign up failed. Please try again.")
        }
    } catch (e: Exception) {
        Resource.Error(e.toUserMessage())
    }

    /** Sends a password-reset email. Kept for the "Forgot password?" link in the login design. */
    suspend fun sendPasswordReset(email: String): Resource<Unit> = try {
        auth.sendPasswordResetEmail(email.trim()).await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.toUserMessage())
    }

    /** Signs the current user out. Local, synchronous, cannot fail. */
    fun signOut() = auth.signOut()
}
