package com.metrolist.music.wear

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.metrolist.music.discord.DiscordAuthorizationRequest
import com.metrolist.music.discord.DiscordRpcManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await

object WearDiscordAuthCoordinator {
    private const val PREFERENCES_NAME = "discord_wear_auth"
    private const val STATE_KEY = "state"
    private const val VERIFIER_KEY = "verifier"

    private val _waitingForPhone = MutableStateFlow(false)
    val waitingForPhone = _waitingForPhone.asStateFlow()

    suspend fun start(context: Context) {
        DiscordRpcManager.init(context)
        val request = DiscordRpcManager.createAuthorizationRequest(statePrefix = "wear_")
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(STATE_KEY, request.state)
            .putString(VERIFIER_KEY, request.verifier)
            .apply()
        _waitingForPhone.value = true

        try {
            RemoteActivityHelper(context).startRemoteActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    data = request.url.toUri()
                    addCategory(Intent.CATEGORY_BROWSABLE)
                },
            ).await()
        } catch (error: Throwable) {
            clearPending(context)
            throw error
        }
    }

    fun complete(
        context: Context,
        code: String?,
        returnedState: String?,
        error: String?,
        onComplete: (Boolean) -> Unit,
    ) {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val expectedState = preferences.getString(STATE_KEY, null)
        val verifier = preferences.getString(VERIFIER_KEY, null)
        clearPending(context)

        if (error != null || code == null || returnedState == null || expectedState == null || verifier == null) {
            onComplete(false)
            return
        }

        DiscordRpcManager.init(context)
        DiscordRpcManager.completeAuthorization(
            request = DiscordAuthorizationRequest("", expectedState, verifier),
            code = code,
            returnedState = returnedState,
            onComplete = onComplete,
        )
    }

    private fun clearPending(context: Context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        _waitingForPhone.value = false
    }
}
