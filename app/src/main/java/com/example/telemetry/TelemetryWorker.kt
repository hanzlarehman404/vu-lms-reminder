package com.example.telemetry

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class TelemetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_EVENT_NAME = "event_name"
        internal const val MAX_RETRY_ATTEMPTS = 4
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 10_000
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val eventName = inputData.getString(KEY_EVENT_NAME) ?: "daily_checkin"
        val baseUrl = BuildConfig.TELEMETRY_BASE_URL.trimEnd('/')
        val apiKey = BuildConfig.INGEST_API_KEY

        if (baseUrl.isBlank() || apiKey.isBlank()) {
            return@withContext Result.failure()
        }

        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            return@withContext Result.failure()
        }

        val payloadJson = runCatching {
            DeviceInfoCollector.collectPayload(applicationContext, eventName).toString()
        }.getOrElse {
            return@withContext Result.failure()
        }

        return@withContext postAndClassify(
            endpointUrl = "$baseUrl/api/v1/collect",
            apiKey = apiKey,
            payloadJson = payloadJson,
            attemptCount = runAttemptCount,
            onSuccess = { TelemetryManager.markSuccess(applicationContext) }
        )
    }

    internal fun postAndClassify(
        endpointUrl: String,
        apiKey: String,
        payloadJson: String,
        attemptCount: Int,
        onSuccess: () -> Unit
    ): Result {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(endpointUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-API-Key", apiKey)
            }

            conn.outputStream.use { os ->
                os.write(payloadJson.toByteArray(Charsets.UTF_8))
            }

            val statusCode = conn.responseCode
            classifyHttpResponse(
                statusCode = statusCode,
                attemptCount = attemptCount,
                onSuccess = onSuccess
            )
        } catch (_: Throwable) {
            if (attemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    internal fun classifyHttpResponse(
        statusCode: Int,
        attemptCount: Int,
        onSuccess: () -> Unit
    ): Result {
        return when (statusCode) {
            in 200..299 -> {
                onSuccess()
                Result.success()
            }
            429, in 500..599 -> {
                if (attemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
            }
            else -> Result.failure()
        }
    }
}
