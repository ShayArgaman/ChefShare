package com.chefshare.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.chefshare.app.R
import com.chefshare.app.databinding.ActivityLoginBinding
import com.chefshare.app.ui.feed.FeedActivity
import com.chefshare.app.util.Resource
import com.google.android.material.snackbar.Snackbar

/**
 * Single-screen Login/Signup. The screen swaps between the two modes in-place (no second Activity):
 * the ViewModel holds the mode + auth state as LiveData, and this Activity just renders and reacts.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    // `by viewModels()` gives a ViewModel that survives rotation, scoped to this Activity.
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()
    }

    /** Wire up clicks and the keyboard "Done" action. */
    private fun setupListeners() {
        binding.buttonSubmit.setOnClickListener { submit() }

        // Toggle prompt (e.g. "Sign Up" / "Sign In") flips the mode in the ViewModel.
        binding.textToggleAction.setOnClickListener { viewModel.toggleMode() }

        // "Forgot password?" triggers a reset email using whatever is typed in the email field.
        binding.textForgotPassword.setOnClickListener { onForgotPassword() }

        // Pressing "Done" on the password keyboard submits the form.
        binding.editPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
    }

    /** Observe the three LiveData streams and render accordingly. */
    private fun observeViewModel() {
        // Re-render the whole screen when the mode changes.
        viewModel.isSignUpMode.observe(this) { isSignUp -> renderMode(isSignUp) }

        // Auth result: drive the spinner, navigate on success, toast on error.
        viewModel.authState.observe(this) { state ->
            when (state) {
                is Resource.Loading -> setLoading(true)
                is Resource.Success -> {
                    setLoading(false)
                    viewModel.onAuthStateHandled() // consume before navigating so it can't re-fire on rotation
                    goToFeed()
                }
                is Resource.Error -> {
                    setLoading(false)
                    showMessage(state.message)
                    viewModel.onAuthStateHandled() // consume so it isn't replayed on rotation
                }
                null -> Unit
            }
        }

        // Password-reset result feedback.
        viewModel.resetState.observe(this) { state ->
            when (state) {
                is Resource.Success -> {
                    showMessage(getString(R.string.reset_email_sent))
                    viewModel.onResetStateHandled()
                }
                is Resource.Error -> {
                    showMessage(state.message)
                    viewModel.onResetStateHandled()
                }
                else -> Unit
            }
        }
    }

    /**
     * Adjust the UI for Login vs Signup: the name field and "Forgot password?" only exist in the
     * relevant mode, and the button / subtitle / toggle texts change.
     */
    private fun renderMode(isSignUp: Boolean) {
        binding.tilName.isVisible = isSignUp
        binding.textForgotPassword.isVisible = !isSignUp

        binding.buttonSubmit.text =
            getString(if (isSignUp) R.string.action_sign_up else R.string.action_sign_in)
        binding.textSubtitle.text =
            getString(if (isSignUp) R.string.subtitle_sign_up else R.string.subtitle_sign_in)
        binding.textTogglePrompt.text =
            getString(if (isSignUp) R.string.prompt_have_account else R.string.prompt_no_account)
        binding.textToggleAction.text =
            getString(if (isSignUp) R.string.action_sign_in else R.string.action_sign_up)

        clearErrors()
    }

    /** Validate inputs, then hand off to the ViewModel. */
    private fun submit() {
        val name = binding.editName.text?.toString()?.trim().orEmpty()
        val email = binding.editEmail.text?.toString()?.trim().orEmpty()
        val password = binding.editPassword.text?.toString().orEmpty()

        if (!validateInputs(name, email, password)) return
        viewModel.authenticate(name, email, password)
    }

    /** Inline field validation. Returns true only if everything is valid. */
    private fun validateInputs(name: String, email: String, password: String): Boolean {
        clearErrors()

        if (viewModel.isSignUp && name.isEmpty()) {
            binding.tilName.error = getString(R.string.error_name_required)
            return false
        }
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_email_required)
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_email_invalid)
            return false
        }
        if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.error_password_length)
            return false
        }
        return true
    }

    private fun clearErrors() {
        binding.tilName.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null
    }

    /** Send a reset email; requires a valid-looking address in the email field first. */
    private fun onForgotPassword() {
        val email = binding.editEmail.text?.toString()?.trim().orEmpty()
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_enter_email_first)
            return
        }
        viewModel.resetPassword(email)
    }

    /** Show the spinner over the button and lock inputs so the user can't double-submit. */
    private fun setLoading(loading: Boolean) {
        binding.progressBar.isVisible = loading
        binding.buttonSubmit.isInvisible = loading

        binding.editName.isEnabled = !loading
        binding.editEmail.isEnabled = !loading
        binding.editPassword.isEnabled = !loading
        binding.buttonSubmit.isEnabled = !loading
        binding.textToggleAction.isEnabled = !loading
        binding.textForgotPassword.isEnabled = !loading
    }

    /** On success, go to the feed and finish so Back can't return to the login screen. */
    private fun goToFeed() {
        startActivity(Intent(this, FeedActivity::class.java))
        finish()
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
