package com.xovrix.quickvoicereminder.network

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.xovrix.quickvoicereminder.BuildConfig
import com.xovrix.quickvoicereminder.ai.ParsedTaskResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

class BackendApiClient private constructor(context: Context) {
    private val baseUrl = BuildConfig.BACKEND_BASE_URL.trim().trimEnd('/')
    private val clientId = context.getSharedPreferences("backend_client", Context.MODE_PRIVATE)
        .let { preferences ->
            preferences.getString("client_id", null) ?: UUID.randomUUID().toString().also {
                preferences.edit().putString("client_id", it).apply()
            }
        }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val parsedTaskAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(ParsedTaskResult::class.java)

    suspend fun parseReminder(
        input: String,
        clientParsed: ParsedTaskResult
    ): ParsedTaskResult? = withContext(Dispatchers.IO) {
        post(
            "/v1/ai/parse",
            JSONObject()
                .put("input", input)
                .put("currentDateTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .put("timeZone", ZoneId.systemDefault().id)
                .put("clientParsed", JSONObject(parsedTaskAdapter.toJson(clientParsed)))
        )?.let { parsedTaskAdapter.fromJson(it) }?.copy(isAiParsed = true)
    }

    private fun post(path: String, json: JSONObject): String? {
        if (baseUrl.isBlank()) return null
        if (!BuildConfig.DEBUG && !baseUrl.startsWith("https://")) return null
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("X-Client-ID", clientId)
            .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        }.getOrNull()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        @Volatile
        private var instance: BackendApiClient? = null

        fun get(context: Context): BackendApiClient = instance ?: synchronized(this) {
            instance ?: BackendApiClient(context.applicationContext).also { instance = it }
        }
    }
}
