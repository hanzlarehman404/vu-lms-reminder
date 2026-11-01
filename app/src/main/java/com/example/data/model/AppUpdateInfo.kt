package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AppUpdateInfo(
    @Json(name = "version") val version: String = "1.0.0",
    @Json(name = "versionCode") val versionCode: Int = 1,
    @Json(name = "minimumSupportedVersion") val minimumSupportedVersion: Int = 1,
    @Json(name = "forceUpdate") val forceUpdate: Boolean = false,
    @Json(name = "releaseName") val releaseName: String = "",
    @Json(name = "releaseNotes") val releaseNotes: List<String> = emptyList(),
    @Json(name = "downloadUrl") val downloadUrl: String = "",
    @Json(name = "publishedAt") val publishedAt: String = ""
)
