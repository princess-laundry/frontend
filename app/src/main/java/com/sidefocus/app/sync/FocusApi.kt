package com.sidefocus.app.sync

import com.sidefocus.app.data.AppJson
import com.sidefocus.app.data.MIN_AXIS_THRESHOLD_VERSION
import com.sidefocus.core.SessionUpload
import com.sidefocus.core.Thresholds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.*
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable data class DeviceRegistration(@SerialName("device_uuid") val deviceUuid: String)
@Serializable data class AuthResponse(@SerialName("access_token") val accessToken: String)
@Serializable data class UploadResponse(@SerialName("session_id") val sessionId: Long)
@Serializable data class ConfigResponse(
    val version: Int, @SerialName("t1_ratio") val headRatio: Double,
    @SerialName("t2_angle") val leanDegrees: Double, @SerialName("stable_sec") val stableSec: Double,
    @SerialName("analysis_fps") val analysisFps: Int
) {
    fun thresholds(): Thresholds {
        require(version >= MIN_AXIS_THRESHOLD_VERSION) { "서버의 자세 기준 설정을 v2 이상으로 업데이트해 주세요" }
        return Thresholds(
            version = version,
            headRatio = headRatio,
            leanDegrees = leanDegrees,
            stableMs = (stableSec * 1000).toLong(),
            analysisFps = analysisFps
        )
    }
}

interface FocusApi {
    @POST("auth/device") suspend fun register(@Body device: DeviceRegistration): Response<AuthResponse>
    @GET("config") suspend fun config(@Header("Authorization") token: String): Response<ConfigResponse>
    @POST("sessions") suspend fun upload(@Header("Authorization") token: String, @Body session: SessionUpload): Response<UploadResponse>
    @DELETE("users/me") suspend fun deleteUser(@Header("Authorization") token: String): Response<Unit>
}

interface TokenStore {
    fun read(endpoint: String): String?
    fun write(endpoint: String, token: String)
    fun clear()
}

class ApiFailure(val status: Int) : IOException("HTTP $status") {
    val retryable get() = status == 408 || status == 429 || status >= 500
}

class SyncClient(private val endpoint: String, private val deviceId: String, private val tokens: TokenStore, private val api: FocusApi = createApi(endpoint)) {
    private suspend fun token(refresh: Boolean = false): String {
        if (!refresh) tokens.read(endpoint)?.let { return it }
        val response = api.register(DeviceRegistration(deviceId))
        if (!response.isSuccessful) throw ApiFailure(response.code())
        val value = response.body()?.accessToken?.takeIf { it.isNotBlank() } ?: throw IOException("Empty auth response")
        tokens.write(endpoint, value)
        return value
    }
    private suspend fun <T> authenticated(action: suspend (String) -> Response<T>): Response<T> {
        var response = action("Bearer ${token()}")
        if (response.code() == 401) response = action("Bearer ${token(refresh = true)}")
        if (!response.isSuccessful) throw ApiFailure(response.code())
        return response
    }
    suspend fun config(): Thresholds = authenticated { api.config(it) }.body()?.thresholds() ?: throw IOException("Missing config")
    suspend fun upload(session: SessionUpload): Long = authenticated { api.upload(it, session) }.body()?.sessionId ?: throw IOException("Missing session id")
    suspend fun deleteUser() { authenticated { api.deleteUser(it) }; tokens.clear() }
}

fun createApi(endpoint: String): FocusApi = Retrofit.Builder().baseUrl(endpoint)
    .client(OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build())
    .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType())).build().create(FocusApi::class.java)
