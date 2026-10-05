/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.wear

import android.net.Uri
import android.widget.Toast
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.metrolist.innertube.YouTube
import com.metrolist.music.constants.*
import com.metrolist.music.constants.AuthSyncConstants.AUTH_SYNC_PATH
import com.metrolist.music.constants.AuthSyncConstants.WEAR_UPDATE_CHANNEL_PATH
import com.metrolist.music.constants.AuthSyncConstants.KEY_ACCOUNT_EMAIL
import com.metrolist.music.constants.AuthSyncConstants.KEY_ACCOUNT_HANDLE
import com.metrolist.music.constants.AuthSyncConstants.KEY_ACCOUNT_NAME
import com.metrolist.music.constants.AuthSyncConstants.KEY_AUTH_USER
import com.metrolist.music.constants.AuthSyncConstants.KEY_COOKIE
import com.metrolist.music.constants.AuthSyncConstants.KEY_DATA_SYNC_ID
import com.metrolist.music.constants.AuthSyncConstants.KEY_VISITOR_DATA
import com.metrolist.music.core.R
import com.metrolist.music.utils.OTAUpdater
import com.metrolist.music.utils.safeDataStoreEdit
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

class WearAuthListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.dataItem.uri.path == AUTH_SYNC_PATH) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val cookie = dataMap.getString(KEY_COOKIE)
                val visitorData = dataMap.getString(KEY_VISITOR_DATA)
                val dataSyncId = dataMap.getString(KEY_DATA_SYNC_ID)
                val authUser = dataMap.getString(KEY_AUTH_USER)
                val accountName = dataMap.getString(KEY_ACCOUNT_NAME)
                val accountEmail = dataMap.getString(KEY_ACCOUNT_EMAIL)
                val accountHandle = dataMap.getString(KEY_ACCOUNT_HANDLE)

                if (cookie != null) {
                    YouTube.cookie = cookie
                    scope.launch {
                        safeDataStoreEdit { settings ->
                            settings[InnerTubeCookieKey] = cookie
                            visitorData?.let { settings[VisitorDataKey] = it }
                            dataSyncId?.let { settings[DataSyncIdKey] = it }
                            authUser?.let { settings[InnerTubeAuthUserKey] = it }
                            accountName?.let { settings[AccountNameKey] = it }
                            accountEmail?.let { settings[AccountEmailKey] = it }
                            accountHandle?.let { settings[AccountChannelHandleKey] = it }
                        }
                        Timber.d("WearAuthListenerService: Auth data updated from mobile")
                    }
                }
            }
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path == WEAR_UPDATE_CHANNEL_PATH) {
            Timber.d("WearAuthListenerService: Received update channel from mobile")
            val channelClient = Wearable.getChannelClient(this)
            val file = File(externalCacheDir ?: cacheDir, "update.apk")
            if (file.exists()) file.delete()
            val fileUri = Uri.fromFile(file)

            scope.launch {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, R.string.ota_downloading, Toast.LENGTH_SHORT).show()
                }
                try {
                    channelClient.receiveFile(channel, fileUri, false).await()
                    Timber.d("WearAuthListenerService: Received update APK from mobile (${file.length()} bytes)")
                    withContext(Dispatchers.Main) {
                        OTAUpdater.installApk(applicationContext, file)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "WearAuthListenerService: Failed to receive update APK from mobile")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, getString(R.string.ota_error, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
