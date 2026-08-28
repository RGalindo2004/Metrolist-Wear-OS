package com.metrolist.music.utils

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

object GoogleDeviceAuth {
    private const val CLIENT_ID = "851522332635-69f8v11f18g7528euh092o93m7q9t699.apps.googleusercontent.com"
    private const val CLIENT_SECRET = "oc9mo_YvXzyvSpsfCH9B9nK6"
    private const val SCOPE = "https://www.googleapis.com/auth/youtube"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    @Serializable
    data class DeviceCodeResponse(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_url") val verificationUrl: String,
        @SerialName("expires_in") val expiresIn: Int,
        val interval: Int
    )

    @Serializable
    data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("expires_in") val expiresIn: Int? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        val error: String? = null
    )

    suspend fun requestDeviceCode(): Result<DeviceCodeResponse> = runCatching {
        client.submitForm(
            url = "https://oauth2.googleapis.com/device/code",
            formParameters = parameters {
                append("client_id", CLIENT_ID)
                append("scope", SCOPE)
            }
        ).body()
    }

    suspend fun pollToken(deviceCode: String): Result<TokenResponse> = runCatching {
        client.submitForm(
            url = "https://oauth2.googleapis.com/token",
            formParameters = parameters {
                append("client_id", CLIENT_ID)
                append("client_secret", CLIENT_SECRET)
                append("device_code", deviceCode)
                append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            }
        ).body()
    }
}
