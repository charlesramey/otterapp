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

    @FormUrlEncoded
    @POST("api/delete")
    suspend fun deleteFiles(@Field("files") files: List<String>): Response<ResponseBody>
}

/**
 * Singleton object for API service
 */
object ScallopApiService {
    private const val BASE_URL = "http://scallop.local/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: ScallopApi = retrofit.create(ScallopApi::class.java)
}

/**
 * Data models
 */
data class ScallopFile(
    val name: String,
    val size: Long,
    var isSelected: Boolean = false
)

data class DeviceStatus(
    val isConnected: Boolean = false,
    val voltage: Float = 0f,
    val sdUsagePercent: Float = 0f,
    val firmwareVersion: String = "Unknown"
)

data class StatusResponse(
    val voltage: Float
)

data class FilesResponse(
    val files: List<FileItem>,
    val sd_usage_percent: Float = 0f
)

data class FileItem(
    val name: String,
    val size: Long = 0
)
