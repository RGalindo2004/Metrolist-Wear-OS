/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.google.android.gms.wearable.Wearable
import com.metrolist.music.constants.AuthSyncConstants.DOWNLOAD_WEAR_APK_PATH
import com.metrolist.music.core.R
import com.metrolist.music.wear.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

object OTAUpdater {
    private const val GITHUB_API_URL = "https://api.github.com/repos/RGalindo2004/Metrolist-Wear-OS/releases/latest"
    private val client = HttpClient()

    suspend fun checkAndUpdate(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val response = client.get(GITHUB_API_URL).bodyAsText()
                val json = JSONObject(response)
                val latestVersion = json.getString("tag_name").removePrefix("v")
                val body = json.optString("body", "")
                val latestVersionCode = "VersionCode:\\s*(\\d+)".toRegex()
                    .find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0

                val currentVersion = BuildConfig.VERSION_NAME
                val currentVersionCode = BuildConfig.VERSION_CODE

                val hasUpdate = if (latestVersionCode > 0 && currentVersionCode > 0) {
                    latestVersionCode > currentVersionCode
                } else {
                    Updater.compareVersions(latestVersion, currentVersion) > 0
                }

                if (hasUpdate) {
                    val assets = json.getJSONArray("assets")
                    var downloadUrl: String? = null

                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (!name.endsWith(".apk")) continue

                        val isDebugBuild = BuildConfig.DEBUG
                        if (isDebugBuild && name.contains("DEBUG", ignoreCase = true)) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        } else if (!isDebugBuild && !name.contains("DEBUG", ignoreCase = true)) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }

                    if (downloadUrl == null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.getString("name")
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.getString("browser_download_url")
                                break
                            }
                        }
                    }

                    if (downloadUrl != null) {
                        if (checkStoragePermission(context) && checkInstallPermission(context)) {
                            val connectedNodes = try {
                                Wearable.getNodeClient(context).connectedNodes.await()
                            } catch (_: Exception) {
                                emptyList()
                            }

                            if (connectedNodes.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, R.string.requesting_download_from_phone, Toast.LENGTH_SHORT).show()
                                }
                                val node = connectedNodes.first()
                                Wearable.getMessageClient(context).sendMessage(
                                    node.id,
                                    DOWNLOAD_WEAR_APK_PATH,
                                    downloadUrl.toByteArray(Charsets.UTF_8)
                                ).await()
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Se requiere conectar el celular para actualizar", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.ota_latest_version, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "OTA Update failed")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.ota_error, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun checkStoragePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true // Scoped storage
        val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Please grant storage permissions to update", Toast.LENGTH_LONG).show()
            }
            return false
        }
        return true
    }

    fun checkInstallPermission(context: Context): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return false
        }
        return true
    }

    fun installApk(context: Context, file: File) {
        if (!checkInstallPermission(context)) return

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

}
