package com.metrolist.music.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import com.metrolist.music.constants.AuthSyncConstants.DISCORD_AUTH_CALLBACK_PATH
import com.metrolist.music.constants.AuthSyncConstants.KEY_DISCORD_CODE
import com.metrolist.music.constants.AuthSyncConstants.KEY_DISCORD_ERROR
import com.metrolist.music.constants.AuthSyncConstants.KEY_DISCORD_STATE
import com.metrolist.music.discord.DiscordOAuthActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

class DiscordWearOAuthRelayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callback = intent?.data
        DiscordOAuthActivity.deliverCallback(
            code = callback?.getQueryParameter("code"),
            state = callback?.getQueryParameter("state"),
            error = callback?.getQueryParameter("error"),
        )
        if (callback?.getQueryParameter("state")?.startsWith("wear_") != true) {
            finish()
            return
        }
        val payload = JSONObject().apply {
            callback.getQueryParameter("code")?.let { put(KEY_DISCORD_CODE, it) }
            callback.getQueryParameter("state")?.let { put(KEY_DISCORD_STATE, it) }
            callback.getQueryParameter("error")?.let { put(KEY_DISCORD_ERROR, it) }
        }.toString().toByteArray()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Wearable.getNodeClient(this@DiscordWearOAuthRelayActivity)
                    .connectedNodes
                    .await()
                    .forEach { node ->
                        Wearable.getMessageClient(this@DiscordWearOAuthRelayActivity)
                            .sendMessage(node.id, DISCORD_AUTH_CALLBACK_PATH, payload)
                            .await()
                    }
            } catch (error: Throwable) {
                Timber.e(error, "Failed to relay Discord authorization to Wear OS")
            } finally {
                withContext(Dispatchers.Main) { finish() }
            }
        }
    }
}
