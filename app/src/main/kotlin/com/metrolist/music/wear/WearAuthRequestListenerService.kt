/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.wear

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.metrolist.music.core.R
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.metrolist.music.constants.*
import com.metrolist.music.constants.AuthSyncConstants.AUTH_REQUEST_PATH
import com.metrolist.music.constants.AuthSyncConstants.OPEN_LOGIN_PATH
import com.metrolist.music.constants.AuthSyncConstants.AUTH_SYNC_PATH
import com.metrolist.music.constants.AuthSyncConstants.KEY_ACCOUNT_EMAIL
import com.metrolist.music.constants.AuthSyncConstants.KEY_ACCOUNT_HANDLE
import com.metrolist.music.constants.AuthSyncConstants.KEY_ACCOUNT_NAME
import com.metrolist.music.constants.AuthSyncConstants.KEY_AUTH_USER
import com.metrolist.music.constants.AuthSyncConstants.KEY_COOKIE
import com.metrolist.music.constants.AuthSyncConstants.KEY_DATA_SYNC_ID
import com.metrolist.music.constants.AuthSyncConstants.KEY_TIMESTAMP
import com.metrolist.music.constants.AuthSyncConstants.KEY_VISITOR_DATA
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

class WearAuthRequestListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == AUTH_REQUEST_PATH) {
            Timber.d("WearAuthRequestListenerService: Received auth sync request")
            
            scope.launch {
                // Mostrar aviso en el móvil
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, R.string.sending_account_to_watch, Toast.LENGTH_SHORT).show()
                }

                try {
                    val settings = dataStore.data.first()
                    val cookie = settings[InnerTubeCookieKey]
                    val visitorData = settings[VisitorDataKey]
                    val dataSyncId = settings[DataSyncIdKey]
                    val authUser = settings[InnerTubeAuthUserKey]
                    val accountName = settings[AccountNameKey]
                    val accountEmail = settings[AccountEmailKey]
                    val accountHandle = settings[AccountChannelHandleKey]

                    if (cookie == null) {
                        Timber.d("WearAuthRequestListenerService: No cookie found to sync")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, R.string.login_first_on_mobile, Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    val request = PutDataMapRequest.create(AUTH_SYNC_PATH).apply {
                        dataMap.putString(KEY_COOKIE, cookie)
                        visitorData?.let { dataMap.putString(KEY_VISITOR_DATA, it) }
                        dataSyncId?.let { dataMap.putString(KEY_DATA_SYNC_ID, it) }
                        authUser?.let { dataMap.putString(KEY_AUTH_USER, it) }
                        accountName?.let { dataMap.putString(KEY_ACCOUNT_NAME, it) }
                        accountEmail?.let { dataMap.putString(KEY_ACCOUNT_EMAIL, it) }
                        accountHandle?.let { dataMap.putString(KEY_ACCOUNT_HANDLE, it) }
                        dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                        setUrgent()
                    }.asPutDataRequest()

                    // Forzar que se envíe inmediatamente
                    Wearable.getDataClient(this@WearAuthRequestListenerService).putDataItem(request).await()
                    Timber.d("WearAuthRequestListenerService: Auth data pushed successfully")
                } catch (e: Exception) {
                    Timber.e(e, "WearAuthRequestListenerService: Failed to push auth data")
                }
            }
        } else if (messageEvent.path == OPEN_LOGIN_PATH) {
            Timber.d("WearAuthRequestListenerService: Received open login request")
            
            scope.launch(Dispatchers.Main) {
                Toast.makeText(applicationContext, R.string.opening_login_on_phone, Toast.LENGTH_SHORT).show()
            }

            val intent = Intent(this, com.metrolist.music.MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                data = Uri.parse("https://metrolist.cc/login")
            }

            // Fallback notification for Android 10+ background activity start restrictions
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, "updates")
                .setSmallIcon(R.drawable.login)
                .setContentTitle(getString(R.string.login))
                .setContentText(getString(R.string.opening_login_on_phone))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(2001, notification)

            try {
                startActivity(intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start login activity from background")
            }
        }
    }
}
