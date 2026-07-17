package com.chefshare.app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chefshare.app.data.repository.AuthRepository
import com.chefshare.app.util.Resource
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

/**
 * Drives the Login/Signup screen.
 *
 * The Activity only observes LiveData and renders — all auth work happens here and in the
 * repository. Auth results are exposed as [Resource] so the UI uniformly handles
 * Loading / Success / Error without ever touching a raw exception.
 */
class LoginViewModel : ViewModel() {

    // The repository uses default Firebase instances, so no factory is needed.
    private val authRepository = AuthRepository()

    // Auth result stream. Nullable + a "handled" reset so a Success/Error isn't re-delivered
    // after a configuration change (e.g. rotation) and we don't navigate/toast twice.
    private val _authState = MutableLiveData<Resource<FirebaseUser>?>()
    val authState: LiveData<Resource<FirebaseUser>?> = _authState

    // Separate stream for the "Forgot password?" reset result.
    private val _resetState = MutableLiveData<Resource<Unit>?>()
    val resetState: LiveData<Resource<Unit>?> = _resetState

    // Current mode: false = Login, true = Signup. The Activity re-renders on change.
    private val _isSignUpMode = MutableLiveData(false)
    val isSignUpMode: LiveData<Boolean> = _isSignUpMode

    /** Convenience read of the current mode. */
    val isSignUp: Boolean get() = _isSignUpMode.value == true

    /** Flip between Login and Signup. */
    fun toggleMode() {
        _isSignUpMode.value = !isSignUp
    }

    /**
     * Runs sign-in or sign-up depending on the current mode.
     * Emits Loading first, then the repository's Success/Error.
     * (Input is validated in the Activity before this is called.)
     */
    fun authenticate(name: String, email: String, password: String) {
        _authState.value = Resource.Loading
        viewModelScope.launch {
            _authState.value =
                if (isSignUp) authRepository.signUp(name, email, password)
                else authRepository.signIn(email, password)
        }
    }

    /** Sends a password-reset email for the "Forgot password?" link. */
    fun resetPassword(email: String) {
        _resetState.value = Resource.Loading
        viewModelScope.launch {
            _resetState.value = authRepository.sendPasswordReset(email)
        }
    }

    /** Called by the Activity once it has navigated/toasted, so the event isn't replayed. */
    fun onAuthStateHandled() {
        _authState.value = null
    }

    fun onResetStateHandled() {
        _resetState.value = null
    }
}
