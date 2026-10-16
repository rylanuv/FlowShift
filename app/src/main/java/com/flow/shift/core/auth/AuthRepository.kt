package com.flow.shift.core.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.flow.shift.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth
) {
    private val _currentUser = MutableStateFlow<FirebaseUser?>(firebaseAuth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        _currentUser.value = auth.currentUser
    }

    init {
        firebaseAuth.addAuthStateListener(authStateListener)
    }

    suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser> {
        val activity = activityContext.findActivity() ?: return Result.failure(
            IllegalStateException("An Activity context is required for Google Sign-In")
        )

        val webClientId = getWebClientId(activityContext)
        if (webClientId.isBlank() || webClientId == "YOUR_WEB_CLIENT_ID") {
            return Result.failure(
                IllegalStateException("Google Sign-In is not configured. Please set your Web Client ID in strings.xml (google_web_client_id) or Firebase Console.")
            )
        }

        return try {
            val credentialManager = CredentialManager.create(activity)
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId = webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = firebaseAuth.signInWithCredential(authCredential).awaitTask()
                val user = authResult.user ?: throw IllegalStateException("User authentication failed: null user returned")
                _currentUser.value = user
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Unsupported credential type returned: ${credential.type}"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> {
        return try {
            firebaseAuth.signOut()
            _currentUser.value = null
            try {
                val credentialManager = CredentialManager.create(context)
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                // Ignore credential clearing errors on sign out
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(): Result<Unit> {
        val user = firebaseAuth.currentUser ?: return Result.failure(IllegalStateException("No user is currently signed in"))
        return try {
            user.delete().awaitTask()
            _currentUser.value = null
            try {
                val credentialManager = CredentialManager.create(context)
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                // Ignore credential clearing errors on account deletion
            }
            Result.success(Unit)
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            Result.failure(IllegalStateException("For security reasons, please sign out and sign in again before deleting your account."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getWebClientId(context: Context): String {
        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) {
                val generatedId = context.getString(resId)
                if (generatedId.isNotBlank() && generatedId != "YOUR_WEB_CLIENT_ID") return generatedId
            }
            val fallbackId = context.getString(R.string.google_web_client_id)
            if (fallbackId.isNotBlank() && fallbackId != "YOUR_WEB_CLIENT_ID") fallbackId else ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                continuation.resumeWith(Result.success(result))
            }
            addOnFailureListener { exception ->
                continuation.resumeWith(Result.failure(exception))
            }
            addOnCanceledListener {
                continuation.cancel()
            }
        }
}
