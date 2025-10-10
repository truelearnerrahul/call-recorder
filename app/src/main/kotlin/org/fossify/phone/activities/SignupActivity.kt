package org.fossify.phone.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.phone.R
import org.fossify.phone.databinding.ActivitySignupBinding
import org.fossify.phone.helpers.AuthHelper
import org.fossify.phone.helpers.GoogleSignInHelper
import org.fossify.phone.network.GoogleAuthRequest
import org.fossify.phone.network.RetrofitClient
import org.fossify.phone.network.SignupRequest
import org.fossify.phone.network.Token
import retrofit2.Response

class SignupActivity : SimpleActivity() {
    private val binding by viewBinding(ActivitySignupBinding::inflate)
    private lateinit var googleSignInHelper: GoogleSignInHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupGoogleSignIn()
        setupViews()
        setupClickListeners()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)

        if (requestCode == GoogleSignInHelper.RC_GOOGLE_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                googleSignInHelper.handleSignInResult(resultData)
            } else {
                showLoading(false)
                toast("Google Sign-In cancelled")
            }
        }
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


    private fun setupGoogleSignIn() {
        googleSignInHelper = GoogleSignInHelper(
            context = this,
            onSuccess = { idToken ->
                Log.d("SignupActivity", "Google token received, calling backend...")
                signupWithGoogle(idToken)
            },
            onError = { error ->
                Log.e("SignupActivity", "Google Sign-In error: $error")
                runOnUiThread {
                    toast(error)
                    showLoading(false)
                }
            }
        )
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

    private fun attemptGoogleSignup() {
        Log.d("SignupActivity", "Google Sign-Up button clicked")
        showLoading(true)

        // Add small delay to ensure UI updates
        binding.root.postDelayed({
            try {
                googleSignInHelper.signIn(this@SignupActivity)
            } catch (e: Exception) {
                Log.e("SignupActivity", "Failed to start Google Sign-In: ${e.message}")
                showLoading(false)
                toast("Failed to start Google Sign-In")
            }
        }, 100)
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

    private fun handleSignupSuccess(response: Response<Token>) {
        response.body()?.let { token ->
            Log.d("SignupActivity", "Signup successful, saving auth data")
            saveAuthData(token)
            toast(R.string.signup_successful)

            // Navigate to MainActivity (Analytics tab) instead of going back to LoginActivity
            val intent = Intent(this@SignupActivity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } ?: run {
            Log.e("SignupActivity", "Signup failed: response body is null")
            toast(R.string.signup_failed)
        }
    }

    private fun handleSignupError(response: Response<Token>) {
        Log.e("SignupActivity", "Signup error: ${response.code()} - ${response.message()}")
        when (response.code()) {
            400 -> toast(R.string.email_already_exists)
            else -> toast("${getString(R.string.signup_failed)}: ${response.message()}")
        }
    }

    private fun handleSignupException(e: Exception) {
        Log.e("SignupActivity", "Signup exception: ${e.message}")
        toast("${getString(R.string.signup_failed)}: ${e.localizedMessage}")
    }

    private fun showLoading(show: Boolean) {
        binding.apply {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
            signupButton.isEnabled = !show
            googleSignupButton.isEnabled = !show
        }
    }

    private fun signupWithGoogle(idToken: String) {
        lifecycleScope.launch {
            try {
                Log.d("SignupActivity", "Sending Google token to backend...")
                val response = RetrofitClient.getApiService().googleAuth(
                    GoogleAuthRequest(idToken)
                )

                if (response.isSuccessful) {
                    Log.d("SignupActivity", "Google signup successful")
                    handleSignupSuccess(response)
                } else {
                    Log.e("SignupActivity", "Google signup failed: ${response.code()}")
                    handleSignupError(response)
                }
            } catch (e: Exception) {
                Log.e("SignupActivity", "Google signup exception: ${e.message}")
                handleSignupException(e)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun saveAuthData(token: Token) {
        AuthHelper.saveAuthData(
            context = this,
            token = token.access_token,
            email = token.customer.email,
            name = token.customer.name,
            userId = token.customer.id
        )
        Log.d("SignupActivity", "Auth data saved for user: ${token.customer.email}")
    }

    private fun isEmailValid(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}
