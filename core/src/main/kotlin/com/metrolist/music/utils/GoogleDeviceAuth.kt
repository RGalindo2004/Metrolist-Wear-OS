package com.metrolist.music.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

object GoogleDeviceAuth {
    // Official YouTube on TV Client ID
    private const val CLIENT_ID = "207374026362-sc9vj1sh3mfhdv8p77id6v669b9u866n.apps.googleusercontent.com"
    private const val SCOPE = "https://www.googleapis.com/auth/youtube"

    private val jsonSerializer = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(jsonSerializer)
        }
    }

    @Serializable
    data class DeviceCodeResponse(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_url") val verificationUrl: String,
        @SerialName("expires_in") val expiresIn: Int,
        val interval: Int = 5
    )

    @Serializable
    data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("expires_in") val expiresIn: Int? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null
    )

    suspend fun requestDeviceCode(): Result<DeviceCodeResponse> = runCatching {
        val response = client.submitForm(
            url = "https://oauth2.googleapis.com/device/code",
            formParameters = parameters {
                append("client_id", CLIENT_ID)
                append("scope", SCOPE)
            }
        ) {
            header("User-Agent", "com.google.android.youtube.tv/2.0 (Android TV)")
        }
        
        val bodyText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            Timber.tag("GoogleAuth").e("Request error: $bodyText")
            val errorMessage = runCatching { 
                jsonSerializer.decodeFromString<TokenResponse>(bodyText).error ?: bodyText 
            }.getOrDefault(bodyText)
            throw Exception("Google Error ${response.status.value}: $errorMessage")
        }

        jsonSerializer.decodeFromString<DeviceCodeResponse>(bodyText)
    }

    suspend fun pollToken(deviceCode: String): Result<TokenResponse> = runCatching {
        val response = client.submitForm(
            url = "https://oauth2.googleapis.com/token",
            formParameters = parameters {
                append("client_id", CLIENT_ID)
                append("device_code", deviceCode)
                append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            }
        ) {
            header("User-Agent", "com.google.android.youtube.tv/2.0 (Android TV)")
        }
        
        val bodyText = response.bodyAsText()
        jsonSerializer.decodeFromString<TokenResponse>(bodyText)
    }
}
