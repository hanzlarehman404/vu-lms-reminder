package com.example.telemetry

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object DeviceInfoCollector {

    // Constant salt across versions so device_hash stays deterministic per install
    private const val APP_SALT = "vulms_device_telemetry_salt_v1"

    @SuppressLint("HardwareIds")
    fun getDeviceHash(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty().ifBlank { "unknown_android_id" }

        return sha256Hex("$androidId:$APP_SALT")
    }

    internal fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    internal fun formatUtcIso8601(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMillis))
    }

    fun collectPayload(context: Context, eventName: String): JSONObject {
        val appContext = context.applicationContext
        val pm = appContext.packageManager
        val pkgName = appContext.packageName

        val pkgInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkgName, 0)
            }
        }.getOrNull()

        val installSource = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(pkgName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(pkgName)
            }
        }.getOrNull()

        val isDebug = runCatching {
            (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        }.getOrDefault(false)

        return JSONObject().apply {
            put("device_hash", getDeviceHash(appContext))
            put("event_name", eventName)
            put("session_id", UUID.randomUUID().toString())
            put("client_timestamp", formatUtcIso8601(System.currentTimeMillis()))

            putOpt("app_version_name", pkgInfo?.versionName)
            putOpt("app_version_code", pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it).toInt() })
            putOpt("install_source", installSource)
            putOpt("first_install_time", pkgInfo?.firstInstallTime?.let { formatUtcIso8601(it) })
            putOpt("last_update_time", pkgInfo?.lastUpdateTime?.let { formatUtcIso8601(it) })
            put("is_debug_build", isDebug)

            put("manufacturer", Build.MANUFACTURER)
            put("brand", Build.BRAND)
            put("model", Build.MODEL)
            put("os_version", Build.VERSION.RELEASE)
            put("sdk_int", Build.VERSION.SDK_INT)
            put("is_emulator", isEmulator())

            put("country_locale", Locale.getDefault().country)
            put("timezone", TimeZone.getDefault().id)

            put("extra_device_info", JSONObject())
            put("payload", JSONObject())
        }
    }

    private fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.HARDWARE.contains("goldfish")
            || Build.HARDWARE.contains("ranchu")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.PRODUCT.contains("sdk_gphone")
            || Build.PRODUCT.contains("vbox86p")
            || Build.PRODUCT.contains("emulator")
    }
}
