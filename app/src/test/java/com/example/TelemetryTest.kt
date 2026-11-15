package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.telemetry.DeviceInfoCollector
import com.example.telemetry.TelemetryManager
import com.example.telemetry.TelemetryWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TelemetryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("telemetry_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `device_hash is deterministic 64-char lowercase hex sha256`() {
        val hash1 = DeviceInfoCollector.getDeviceHash(context)
        val hash2 = DeviceInfoCollector.getDeviceHash(context)
        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)
        assertTrue(hash1.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun `formatUtcIso8601 produces valid UTC ISO timestamp`() {
        val formatted = DeviceInfoCollector.formatUtcIso8601(0L)
        assertEquals("1970-01-01T00:00:00Z", formatted)
    }

    @Test
    fun `collectPayload includes essential install and usage fields and omits invasive fields`() {
        val payload = DeviceInfoCollector.collectPayload(context, "first_open")

        assertEquals("first_open", payload.getString("event_name"))
        assertTrue(payload.getString("device_hash").matches(Regex("^[0-9a-f]{64}$")))
        assertTrue(payload.getString("session_id").isNotBlank())
        assertTrue(payload.getString("client_timestamp").endsWith("Z"))
        assertTrue(payload.has("app_version_code"))
        assertTrue(payload.has("manufacturer"))
        assertTrue(payload.has("brand"))
        assertTrue(payload.has("model"))
        assertTrue(payload.has("os_version"))
        assertTrue(payload.has("sdk_int"))
        assertTrue(payload.has("country_locale"))
        assertTrue(payload.has("timezone"))
        assertTrue(payload.has("is_emulator"))
        assertTrue(payload.has("is_debug_build"))
        assertTrue(payload.has("extra_device_info"))
        assertTrue(payload.has("payload"))

        // Verify optional invasive fields are omitted
        assertFalse(payload.has("carrier_name"))
        assertFalse(payload.has("battery_pct"))
        assertFalse(payload.has("is_rooted"))
        assertFalse(payload.has("free_ram_mb"))
        assertFalse(payload.has("build_fingerprint"))
    }

    @Test
    fun `determineEventName follows first_open app_updated and 24h daily_checkin schedule`() {
        val dayMs = 24L * 60L * 60L * 1000L
        val baseTime = 1_700_000_000_000L

        assertEquals(
            "first_open",
            TelemetryManager.determineEventName(
                reportedVersionCode = -1,
                currentVersionCode = 2,
                lastSuccessMs = 0L,
                nowMs = baseTime
            )
        )

        assertEquals(
            "app_updated",
            TelemetryManager.determineEventName(
                reportedVersionCode = 2,
                currentVersionCode = 3,
                lastSuccessMs = baseTime - 1_000L,
                nowMs = baseTime
            )
        )

        assertNull(
            TelemetryManager.determineEventName(
                reportedVersionCode = 2,
                currentVersionCode = 2,
                lastSuccessMs = baseTime - (dayMs - 60_000L),
                nowMs = baseTime
            )
        )

        assertEquals(
            "daily_checkin",
            TelemetryManager.determineEventName(
                reportedVersionCode = 2,
                currentVersionCode = 2,
                lastSuccessMs = baseTime - dayMs,
                nowMs = baseTime
            )
        )
    }

    @Test
    fun `TelemetryWorker handles 202 retry limits and unreachable server safely without crashing`() {
        val worker = TestListenableWorkerBuilder<TelemetryWorker>(context).build()

        val successCalled = AtomicBoolean(false)
        assertEquals(
            ListenableWorker.Result.success(),
            worker.classifyHttpResponse(202, attemptCount = 0) { successCalled.set(true) }
        )
        assertTrue(successCalled.get())

        val retryCalled = AtomicBoolean(false)
        assertEquals(
            ListenableWorker.Result.retry(),
            worker.classifyHttpResponse(503, attemptCount = 1) { retryCalled.set(true) }
        )
        assertFalse(retryCalled.get())

        assertEquals(
            ListenableWorker.Result.retry(),
            worker.classifyHttpResponse(429, attemptCount = 2) {}
        )

        // Bounded retry cap when server stays down
        assertEquals(
            ListenableWorker.Result.failure(),
            worker.classifyHttpResponse(503, attemptCount = 4) {}
        )

        // Permanent client/payload/key errors fail immediately without retry
        assertEquals(
            ListenableWorker.Result.failure(),
            worker.classifyHttpResponse(401, attemptCount = 0) {}
        )
        assertEquals(
            ListenableWorker.Result.failure(),
            worker.classifyHttpResponse(422, attemptCount = 0) {}
        )

        // Unreachable endpoint fails silently and returns bounded retry / failure
        val unreachableRetry = worker.postAndClassify(
            endpointUrl = "http://127.0.0.1:1/api/v1/collect",
            apiKey = "dummy",
            payloadJson = "{}",
            attemptCount = 0,
            onSuccess = {}
        )
        assertEquals(ListenableWorker.Result.retry(), unreachableRetry)

        val unreachableCap = worker.postAndClassify(
            endpointUrl = "http://127.0.0.1:1/api/v1/collect",
            apiKey = "dummy",
            payloadJson = "{}",
            attemptCount = 4,
            onSuccess = {}
        )
        assertEquals(ListenableWorker.Result.failure(), unreachableCap)
    }
}
