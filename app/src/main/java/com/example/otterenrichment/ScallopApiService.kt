package com.example.otterenrichment

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/**
 * Retrofit API interface for ESP32 device communication
 */
interface ScallopApi {

    @GET("api/status")
    suspend fun getStatus(): Response<StatusResponse>

    @FormUrlEncoded
    @POST("api/time")
    suspend fun updateTime(@Field("time") time: Long): Response<ResponseBody>

    @GET("api/files")
    suspend fun listFiles(): Response<ResponseBody>

    @GET("api/download")
    suspend fun requestDownload(@Query("file") fileName: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/collect")
    suspend fun startCollection(@Field("duration_ms") durationMs: Long): Response<ResponseBody>

    @POST("api/sleep")
    suspend fun sleep(): Response<ResponseBody>

    @Headers("Connection: close")
    @FormUrlEncoded
    @POST("api/delete")
    suspend fun deleteFile(@Field("file") fileName: String): Response<ResponseBody>
}

/**
 * Singleton object for API service
 */
object ScallopApiService {
    private var currentBaseUrl = "http://scallop.local/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var retrofit = Retrofit.Builder()
        .baseUrl(currentBaseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    var api: ScallopApi = retrofit.create(ScallopApi::class.java)
        private set

    fun setBaseUrlFromSsid(ssid: String) {
        val cleanSsid = ssid.replace("\"", "")
        val newUrl = "http://$cleanSsid.local/"

        if (newUrl != currentBaseUrl) {
            updateBaseUrl(newUrl)
        }
    }

    private fun updateBaseUrl(newUrl: String) {
        currentBaseUrl = newUrl
        retrofit = Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(ScallopApi::class.java)
    }

    fun getBaseUrl(): String {
        return currentBaseUrl
    }
}

/**
 * Data models
 */
data class ScallopFile(
    val name: String,
    val size: Long,
    var isSelected: Boolean = false
)

data class StatusResponse(
    val voltage: Float
)
