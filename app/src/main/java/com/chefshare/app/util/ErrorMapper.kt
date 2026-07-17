package com.chefshare.app.util

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

/**
 * Turns a raw exception into a short, friendly message suitable for a Toast.
 *
 * This is the single translation point for the app's crash-prevention rule: repositories catch
 * every Firebase failure and hand the ViewModel a human-readable string instead of a stack trace.
 */
fun Throwable.toUserMessage(): String = when (this) {
    is FirebaseNetworkException ->
        "Network error. Check your connection and try again."
    is FirebaseAuthInvalidCredentialsException ->
        "Incorrect email or password."
    is FirebaseAuthUserCollisionException ->
        "An account with this email already exists."
    is FirebaseAuthWeakPasswordException ->
        "Password is too weak. Use at least 6 characters."
    is FirebaseAuthInvalidUserException ->
        "No account found for this email."
    else ->
        localizedMessage ?: "Something went wrong. Please try again."
}
