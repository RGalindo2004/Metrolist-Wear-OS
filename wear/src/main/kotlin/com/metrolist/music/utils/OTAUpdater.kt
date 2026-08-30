/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.metrolist.music.wear.BuildConfig
import com.metrolist.music.core.R
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File

object OTAUpdater {
    private const val GITHUB_API_URL = "https://api.github.com/repos/RGalindo2004/Metrolist-Wear-OS/releases/latest"
    private val client = HttpClient()

    suspend fun checkAndUpdate(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching {
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
                        if (asset.getString("name") == "wear-debug.apk") {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }

                    if (downloadUrl != null) {
                        if (!checkInstallPermission(context)) return@runCatching
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, R.string.ota_downloading, Toast.LENGTH_SHORT).show()
                        }
                        downloadAndInstall(context, downloadUrl)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.ota_latest_version, Toast.LENGTH_SHORT).show()
                    }
                }
            }.onFailure { e ->
                Timber.e(e, "OTA Update failed")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.ota_error, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkInstallPermission(context: Context): Boolean {
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

    private suspend fun downloadAndInstall(context: Context, url: String) {
        val bytes = client.get(url).bodyAsBytes()
        val file = File(context.externalCacheDir ?: context.cacheDir, "update.apk")
        file.writeBytes(bytes)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
