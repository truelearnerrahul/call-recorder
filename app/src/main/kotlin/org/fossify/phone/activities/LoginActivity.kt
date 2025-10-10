package org.fossify.phone.activities

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.phone.R
import org.fossify.phone.databinding.ActivityLoginBinding
import org.fossify.phone.helpers.AuthHelper
import org.fossify.phone.helpers.GoogleSignInHelper
import org.fossify.phone.network.GoogleAuthRequest
import org.fossify.phone.network.RetrofitClient
import org.fossify.phone.network.Token
import retrofit2.Response

class LoginActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityLoginBinding::inflate)
    private lateinit var googleSignInHelper: GoogleSignInHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        debugCurrentConfiguration()
        setupGoogleSignIn()
        setupViews()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.toolbar, NavigationIcon.Arrow)
        updateTextColors(binding.root)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)

        if (requestCode == GoogleSignInHelper.RC_GOOGLE_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                googleSignInHelper.handleSignInResult(resultData)
            } else {
                showLoading(false)
                Toast.makeText(this, "Google Sign-In cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun debugCurrentConfiguration() {
        try {
            Log.d("GoogleDebug", "=== Google Sign-In Debug Info ===")

            // Check package name
            val packageName = packageName
            Log.d("GoogleDebug", "App package name: $packageName")
//            Log.d("GoogleDebug", "BuildConfig.APPLICATION_ID: ${BuildConfig.APPLICATION_ID}")

            // Check signature
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            val signatures = info.signatures
            Log.d("GoogleDebug", "Number of signatures: ${signatures.size}")

            // Check Google Play Services
            val googleApiAvailability = GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
            Log.d("GoogleDebug", "Google Play Services available: ${resultCode == ConnectionResult.SUCCESS}")

            Log.d("GoogleDebug", "=== End Debug Info ===")

        } catch (e: Exception) {
            Log.e("GoogleDebug", "Debug error: ${e.message}")
        }
    }

    private fun setupGoogleSignIn() {
        googleSignInHelper = GoogleSignInHelper(
            context = this,
            onSuccess = { idToken ->
                Log.d("LoginActivity", "Google token received, calling backend...")
                loginWithGoogle(idToken)
            },
            onError = { error ->
                Log.e("LoginActivity", "Google Sign-In error: $error")
                runOnUiThread {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    showLoading(false)
                }
            }
        )
    }

    private fun setupViews() {
        binding.apply {
            updateMaterialActivityViews(loginCoordinator, loginNestedScrollView, useTransparentNavigation = true, useTopSearchMenu = false)
            setupMaterialScrollListener(loginNestedScrollView, toolbar)
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            loginButton.setOnClickListener {
                attemptLogin()
            }

            googleLoginButton.setOnClickListener {
                Log.d("LoginActivity", "Google Sign-In button clicked")
                showLoading(true)

                // Add small delay to ensure UI updates
                binding.root.postDelayed({
                    try {
                        googleSignInHelper.signIn(this@LoginActivity)
                    } catch (e: Exception) {
                        Log.e("LoginActivity", "Failed to start Google Sign-In: ${e.message}")
                        showLoading(false)
                        Toast.makeText(this@LoginActivity, "Failed to start Google Sign-In", Toast.LENGTH_SHORT).show()
                    }
                }, 100)
            }

            signupLink.setOnClickListener {
                startActivity(Intent(this@LoginActivity, SignupActivity::class.java))
            }
        }
    }

    private fun loginWithGoogle(idToken: String) {
        lifecycleScope.launch {
            try {
                Log.d("LoginActivity", "Sending Google token to backend...")
                val response = RetrofitClient.getApiService().googleAuth(
                    GoogleAuthRequest(idToken)
                )

                if (response.isSuccessful) {
                    Log.d("LoginActivity", "Google login successful")
                    handleLoginSuccess(response)
                } else {
                    Log.e("LoginActivity", "Google login failed: ${response.code()}")
                    handleLoginError(response)
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "Google login exception: ${e.message}")
                handleLoginException(e)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun attemptLogin() {
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()

        // Validate input
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            toast(R.string.please_fill_all_fields)
            return
        }

        if (!isEmailValid(email)) {
            toast(R.string.invalid_email)
            return
        }

        // Show progress and disable button
        showLoading(true)

        // Make API call
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService().login(email, password)

                if (response.isSuccessful) {
                    handleLoginSuccess(response)
                } else {
                    handleLoginError(response)
                }
            } catch (e: Exception) {
                handleLoginException(e)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun handleLoginSuccess(response: Response<Token>) {
        response.body()?.let { token ->
            Log.d("LoginActivity", "Login successful, saving auth data")
            saveAuthData(token)
            toast(R.string.login_successful)
            finish()
        } ?: run {
            Log.e("LoginActivity", "Login failed: response body is null")
            toast(R.string.login_failed)
        }
    }

    private fun handleLoginError(response: Response<Token>) {
        Log.e("LoginActivity", "Login error: ${response.code()} - ${response.message()}")
        when (response.code()) {
            401 -> toast(R.string.invalid_credentials)
            else -> toast("${getString(R.string.login_failed)}: ${response.message()}")
        }
    }

    private fun handleLoginException(e: Exception) {
        Log.e("LoginActivity", "Login exception: ${e.message}")
        toast("${getString(R.string.login_failed)}: ${e.localizedMessage}")
    }

    private fun showLoading(show: Boolean) {
        binding.apply {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
            loginButton.isEnabled = !show
            googleLoginButton.isEnabled = !show
        }
    }

    private fun isEmailValid(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun saveAuthData(token: Token) {
        AuthHelper.saveAuthData(
            context = this,
            token = token.access_token,
            email = token.customer.email,
            name = token.customer.name,
            userId = token.customer.id
        )
        Log.d("LoginActivity", "Auth data saved for user: ${token.customer.email}")
    }
}
