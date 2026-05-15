package com.itlab.data.cloud

import android.content.Context
import android.content.Intent
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class AuthManager(
    private val auth: FirebaseAuth,
) {
    fun getSignInIntent(): Intent =
        AuthUI
            .getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(
                listOf(
                    AuthUI.IdpConfig.EmailBuilder().build(),
                    AuthUI.IdpConfig.GoogleBuilder().build(),
                ),
            ).setIsSmartLockEnabled(false)
            .build()

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    suspend fun signOut(context: Context) {
        AuthUI.getInstance().signOut(context).await()
    }
}
