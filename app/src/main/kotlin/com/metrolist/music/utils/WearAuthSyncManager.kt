/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.metrolist.music.constants.*
import com.metrolist.music.constants.AuthSyncConstants.AUTH_SYNC_PATH
import com.metrolist.music.constants.AuthSyncConstants.KEY_ACCOUNT_EMAIL
import com.metrolist.music.constants.AuthSyncConstants.KEY_ACCOUNT_HANDLE
import com.metrolist.music.constants.AuthSyncConstants.KEY_ACCOUNT_NAME
import com.metrolist.music.constants.AuthSyncConstants.KEY_AUTH_USER
import com.metrolist.music.constants.AuthSyncConstants.KEY_COOKIE
import com.metrolist.music.constants.AuthSyncConstants.KEY_DATA_SYNC_ID
import com.metrolist.music.constants.AuthSyncConstants.KEY_TIMESTAMP
import com.metrolist.music.constants.AuthSyncConstants.KEY_VISITOR_DATA
import com.metrolist.music.constants.AuthSyncConstants.KEY_DISCORD_TOKEN
import com.metrolist.music.discord.DiscordTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class WearAuthSyncManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val dataClient by lazy { Wearable.getDataClient(context) }

    fun startSync() {
        // YT Music Auth Sync
        scope.launch(Dispatchers.IO) {
            combine(
                context.dataStore.data.map { it[InnerTubeCookieKey] }.distinctUntilChanged(),
                context.dataStore.data.map { it[VisitorDataKey] }.distinctUntilChanged(),
                context.dataStore.data.map { it[DataSyncIdKey] }.distinctUntilChanged(),
                context.dataStore.data.map { it[InnerTubeAuthUserKey] }.distinctUntilChanged(),
                context.dataStore.data.map { it[AccountNameKey] }.distinctUntilChanged(),
                context.dataStore.data.map { it[AccountEmailKey] }.distinctUntilChanged(),
                context.dataStore.data.map { it[AccountChannelHandleKey] }.distinctUntilChanged()
            ) { values ->
                DiscordTokenStore.init(context)
                val discordToken = DiscordTokenStore.retrieve()
                AuthData(
                    cookie = values[0] as String?,
                    visitorData = values[1] as String?,
                    dataSyncId = values[2] as String?,
                    authUser = values[3] as String?,
                    accountName = values[4] as String?,
                    accountEmail = values[5] as String?,
                    accountHandle = values[6] as String?,
                    discordToken = discordToken
                )
            }.collect { authData ->
                syncToWearable(authData)
            }
        }
    }

    private suspend fun syncToWearable(authData: AuthData) {
        if (authData.cookie == null && authData.discordToken == null) {
            Timber.d("WearAuthSyncManager: No cookie or discord token to sync")
        }

        try {
            val request = PutDataMapRequest.create(AUTH_SYNC_PATH).apply {
                authData.cookie?.let { dataMap.putString(KEY_COOKIE, it) }
                authData.visitorData?.let { dataMap.putString(KEY_VISITOR_DATA, it) }
                authData.dataSyncId?.let { dataMap.putString(KEY_DATA_SYNC_ID, it) }
                authData.authUser?.let { dataMap.putString(KEY_AUTH_USER, it) }
                authData.accountName?.let { dataMap.putString(KEY_ACCOUNT_NAME, it) }
                authData.accountEmail?.let { dataMap.putString(KEY_ACCOUNT_EMAIL, it) }
                authData.accountHandle?.let { dataMap.putString(KEY_ACCOUNT_HANDLE, it) }
                authData.discordToken?.let { dataMap.putString(KEY_DISCORD_TOKEN, it) }
                dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                setUrgent()
            }.asPutDataRequest()

            dataClient.putDataItem(request).await()
            Timber.d("WearAuthSyncManager: Auth data synced successfully to Wearable Data Layer")
        } catch (e: Exception) {
            Timber.e(e, "WearAuthSyncManager: Failed to sync auth data to Wearable")
        }
    }

    private data class AuthData(
        val cookie: String?,
        val visitorData: String?,
        val dataSyncId: String?,
        val authUser: String?,
        val accountName: String?,
        val accountEmail: String?,
        val accountHandle: String?,
        val discordToken: String?
    )
}
