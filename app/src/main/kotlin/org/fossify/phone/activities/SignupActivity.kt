package org.fossify.phone.activities

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.phone.R
import org.fossify.phone.databinding.ActivitySignupBinding
import org.fossify.phone.network.RetrofitClient
import org.fossify.phone.network.SignupRequest
import retrofit2.Response

class SignupActivity : SimpleActivity() {
    private val binding by viewBinding(ActivitySignupBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupViews()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.toolbar, NavigationIcon.Arrow)
        updateTextColors(binding.root)
    }

    private fun setupViews() {
        binding.apply {
            updateMaterialActivityViews(signupCoordinator, signupNestedScrollView, useTransparentNavigation = true, useTopSearchMenu = false)
            setupMaterialScrollListener(signupNestedScrollView, toolbar)
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            signupButton.setOnClickListener {
                attemptSignup()
            }

            googleSignupButton.setOnClickListener {
                attemptGoogleSignup()
            }

            loginLink.setOnClickListener {
                startActivity(Intent(this@SignupActivity, LoginActivity::class.java))
            }
        }
    }

    private fun attemptSignup() {
        val firstName = binding.firstNameEditText.text.toString().trim()
        val lastName = binding.lastNameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()
        val confirmPassword = binding.confirmPasswordEditText.text.toString().trim()

        // Validate input
        if (TextUtils.isEmpty(firstName) || TextUtils.isEmpty(lastName) ||
            TextUtils.isEmpty(email) || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
            toast(R.string.please_fill_all_fields)
            return
        }

        if (!isEmailValid(email)) {
            toast(R.string.invalid_email)
            return
        }

        if (password != confirmPassword) {
            toast(R.string.passwords_do_not_match)
            return
        }

        if (password.length < 6) {
            toast("Password must be at least 6 characters long")
            return
        }

        // Show progress and disable button
        showLoading(true)

        // Make API call
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService().signup(
                    SignupRequest(
                        email = email,
                        password = password,
                        first_name = firstName,
                        last_name = lastName
                    )
                )

                if (response.isSuccessful) {
                    handleSignupSuccess(response)
                } else {
                    handleSignupError(response)
                }
            } catch (e: Exception) {
                handleSignupException(e)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun handleSignupSuccess(response: Response<org.fossify.phone.network.Token>) {
        response.body()?.let { token ->
            // Store token and user info (you might want to use SharedPreferences or a more secure storage)
            toast(R.string.signup_successful)

            // Navigate back to main activity or wherever appropriate
            finish()
        } ?: run {
            toast(R.string.signup_failed)
        }
    }

    private fun handleSignupError(response: Response<org.fossify.phone.network.Token>) {
        when (response.code()) {
            400 -> toast(R.string.email_already_exists)
            else -> toast("${R.string.signup_failed}: ${response.message()}")
        }
    }

    private fun handleSignupException(e: Exception) {
        toast("${R.string.signup_failed}: ${e.localizedMessage}")
    }

    private fun showLoading(show: Boolean) {
        binding.apply {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
            signupButton.isEnabled = !show
            googleSignupButton.isEnabled = !show
        }
    }

    private fun attemptGoogleSignup() {
        toast("Google signup feature coming soon!")
        // TODO: Implement Google Sign-In
        // You would typically use Google Sign-In API here
        // For now, just show a placeholder message
    }

    private fun isEmailValid(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}
