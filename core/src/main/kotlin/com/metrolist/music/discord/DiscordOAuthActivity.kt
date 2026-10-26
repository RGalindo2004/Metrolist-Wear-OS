package com.metrolist.music.discord

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import timber.log.Timber

data class AuthCodeResult(val code: String, val state: String)

class DiscordOAuthActivity : Activity() {

    companion object {
        private const val TAG = "DiscordSvc"

        @Volatile
        private var deferred: CompletableDeferred<AuthCodeResult>? = null

        fun newDeferred(): CompletableDeferred<AuthCodeResult> {
            val d = CompletableDeferred<AuthCodeResult>()
            deferred = d
            return d
        }

        suspend fun awaitCode(timeoutMs: Long = 120_000L): AuthCodeResult {
            val d = deferred ?: throw CancellationException("No pending authorization")
            return withTimeout(timeoutMs) { d.await() }
        }

        fun cancelPending() {
            deferred?.let { d ->
                if (!d.isCompleted) {
                    d.completeExceptionally(CancellationException("Authorization cancelled by user"))
                }
            }
        }

        fun deliverCallback(code: String?, state: String?, error: String?) {
            when {
                error != null -> deferred?.completeExceptionally(
                    DiscordAuthException.UserCancelled("Authorization denied: $error")
                )
                code == null -> deferred?.completeExceptionally(
                    DiscordAuthException.InvalidGrant("Missing authorization code")
                )
                else -> deferred?.complete(AuthCodeResult(code = code, state = state.orEmpty()))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(TAG).i("OAuthActivity: onCreate with intent=%s", intent?.action)

        val uri = intent?.data ?: run {
            Timber.tag(TAG).w("OAuthActivity: no URI in intent")
            finish()
            return
        }

        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")

        deliverCallback(code, state, error)
        finish()
    }
}
