package org.fossify.phone.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

class GoogleSignInHelper(
    private val context: Context,
    private val onSuccess: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val ANDROID_CLIENT_ID = "302525311837-0v09mgle1g03ivetn6tak5spj7icttei.apps.googleusercontent.com"

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(ANDROID_CLIENT_ID)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    fun signIn(activity: Activity) {
        try {
            Log.d("GoogleSignIn", "Starting Google Sign-In...")
            val signInIntent = googleSignInClient.signInIntent
            activity.startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)
        } catch (e: Exception) {
            Log.e("GoogleSignIn", "Failed to start sign-in: ${e.message}")
            onError("Failed to start Google Sign-In: ${e.message}")
        }
    }

    fun handleSignInResult(data: Intent?) {
        try {
            Log.d("GoogleSignIn", "Handling sign-in result")

            if (data == null) {
                onError("No data received from Google Sign-In")
                return
            }

            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken

            if (idToken != null) {
                Log.d("GoogleSignIn", "Google Sign-In successful! Token length: ${idToken.length}")
                Log.d("GoogleSignIn", "User email: ${account.email}")
                onSuccess(idToken)
            } else {
                Log.e("GoogleSignIn", "Google token is null")
                onError("Google token is null")
            }
        } catch (e: ApiException) {
            Log.e("GoogleSignIn", "Google Sign-In failed: ${e.statusCode} - ${e.message}")
            handleApiException(e)
        } catch (e: Exception) {
            Log.e("GoogleSignIn", "Unexpected error: ${e.message}")
            onError("Unexpected error: ${e.message}")
        }
    }

    private fun handleApiException(exception: ApiException) {
        val errorMessage = when (exception.statusCode) {
            CommonStatusCodes.INTERNAL_ERROR -> "Internal error, please try again"
            CommonStatusCodes.NETWORK_ERROR -> "Network error, check your connection"
            CommonStatusCodes.SIGN_IN_REQUIRED -> "Sign-in required"
            CommonStatusCodes.INVALID_ACCOUNT -> "Invalid account"
            CommonStatusCodes.DEVELOPER_ERROR -> "Developer error - check configuration"
            else -> "Error code: ${exception.statusCode}"
        }
        onError(errorMessage)
    }

    companion object {
        const val RC_GOOGLE_SIGN_IN = 1001
    }
}
