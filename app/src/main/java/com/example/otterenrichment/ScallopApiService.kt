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

    @FormUrlEncoded
    @POST("command")
    suspend fun sendCommand(@Field("command") command: String): Response<String>

    @FormUrlEncoded
    @POST("update-time")
    suspend fun updateTime(@Field("timestamp") timestamp: Long): Response<String>

    @GET("list-files")
    suspend fun listFiles(): Response<String>

    @GET("request-download")
    suspend fun requestDownload(@Query("file") fileName: String): Response<ResponseBody>

    @POST("delete-files")
    suspend fun deleteFiles(@Body fileNames: Map<String, List<String>>): Response<String>
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
