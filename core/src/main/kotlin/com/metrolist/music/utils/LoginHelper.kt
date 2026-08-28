/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.content.Intent
import com.metrolist.innertube.YouTube
import com.metrolist.music.constants.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LoginHelper {
    suspend fun finalizeLogin(
        context: Context,
        cookie: String? = null,
        bearerToken: String? = null,
        refreshToken: String? = null,
        visitorData: String,
        dataSyncId: String,
        authUser: String,
        autoRestart: Boolean = true,
    ): Result<com.metrolist.innertube.models.AccountInfo> = withContext(Dispatchers.IO) {
        runCatching {
            YouTube.cookie = cookie
            YouTube.bearerToken = bearerToken
            YouTube.visitorData = visitorData
            YouTube.dataSyncId = dataSyncId
            YouTube.authUser = authUser

            val accountInfo = YouTube.accountInfo().getOrThrow()
            
            val saved = context.safeDataStoreEdit { settings ->
                if (cookie != null) settings[InnerTubeCookieKey] = cookie
                if (bearerToken != null) settings[InnerTubeBearerTokenKey] = bearerToken
                if (refreshToken != null) settings[InnerTubeRefreshTokenKey] = refreshToken
                settings[VisitorDataKey] = visitorData
                settings[DataSyncIdKey] = dataSyncId
                settings[InnerTubeAuthUserKey] = authUser
                settings[AccountNameKey] = accountInfo.name
                settings[AccountEmailKey] = accountInfo.email.orEmpty()
                settings[AccountChannelHandleKey] = accountInfo.channelHandle.orEmpty()
                settings[AccountPhotoKey] = accountInfo.thumbnailUrl.orEmpty()
            }
            if (!saved) throw Exception("Failed to persist account data")

            if (autoRestart) {
                withContext(Dispatchers.Main) {
                    context.packageManager
                        .getLaunchIntentForPackage(context.packageName)
                        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
                        ?.let(context::startActivity)
                    Runtime.getRuntime().exit(0)
                }
            }
            accountInfo
        }
    }
}
