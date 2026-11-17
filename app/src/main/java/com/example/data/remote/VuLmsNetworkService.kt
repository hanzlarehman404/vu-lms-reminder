package com.example.data.remote

import android.util.Log
import com.example.data.model.ActivityEntity
import com.example.data.model.AppUpdateInfo
import com.example.data.model.ChallanEntity
import com.example.data.model.EnrolledSubject
import com.example.data.model.RemoteNotificationItem
import com.example.data.model.RemoteNotificationsFeed
import com.example.data.model.StudentProfile
import com.example.data.model.VuRawActivity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class LoginPageTokens(
    val viewState: String,
    val viewStateGenerator: String,
    val eventValidation: String
)

data class CalendarFetchResult(
    val activities: List<ActivityEntity>,
    val viewState: String,
    val viewStateGenerator: String
)

class VuLmsNetworkService(
    private val cookieJar: VuLmsCookieJar = VuLmsCookieJar.shared
) {
    private val client: OkHttpClient = OkHttpClient.Builder().apply {
        cookieJar(cookieJar)
        connectTimeout(25, TimeUnit.SECONDS)
        readTimeout(30, TimeUnit.SECONDS)
        writeTimeout(25, TimeUnit.SECONDS)
        followRedirects(true)
        followSslRedirects(true)
    }.build()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val activityListType = Types.newParameterizedType(List::class.java, VuRawActivity::class.java)
    private val activityAdapter = moshi.adapter<List<VuRawActivity>>(activityListType)
    private val updateAdapter = moshi.adapter(AppUpdateInfo::class.java)
    private val notificationsAdapter = moshi.adapter(RemoteNotificationsFeed::class.java)

    fun clearCookies() {
        cookieJar.clear()
    }

    fun hasSessionCookie(): Boolean {
        return cookieJar.hasSessionCookie()
    }

    fun syncCookiesToWebView() {
        cookieJar.syncToAndroidWebView()
    }

    suspend fun fetchLoginPage(): Result<LoginPageTokens> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LOGIN_PAGE_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Referer", SIGN_OUT_URL)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to connect to VU LMS (HTTP ${response.code})"))
                }

                val html = response.body?.string().orEmpty()
                val viewState = extractHiddenField(html, "__VIEWSTATE")
                val generator = extractHiddenField(html, "__VIEWSTATEGENERATOR")
                val eventValidation = extractHiddenField(html, "__EVENTVALIDATION")

                if (viewState.isEmpty()) {
                    return@withContext Result.failure(IOException("Could not extract security tokens from VU LMS page"))
                }

                Result.success(LoginPageTokens(viewState, generator, eventValidation))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching login page", e)
            Result.failure(e)
        }
    }

    suspend fun submitLogin(
        studentId: String,
        password: String,
        tokens: LoginPageTokens
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("__VIEWSTATE", tokens.viewState)
                .add("__VIEWSTATEGENERATOR", tokens.viewStateGenerator)
                .add("__EVENTVALIDATION", tokens.eventValidation)
                .add("txtStudentID", studentId.trim())
                .add("txtPassword", password)
                .add("ibtnLogin", "Sign In")
                .add("g-recaptcha-response", "")
                .add("action", "beta_LMS")
                .build()

            val request = Request.Builder()
                .url(LOGIN_PAGE_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", LOGIN_PAGE_URL)
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val html = response.body?.string().orEmpty()

                val isSuccess = html.contains("window.top.location.href", ignoreCase = true) ||
                        html.contains("Home.aspx", ignoreCase = true) ||
                        html.contains("Default.aspx", ignoreCase = true) ||
                        response.request.url.toString().contains("Home.aspx", ignoreCase = true)

                if (isSuccess) {
                    Result.success(true)
                } else {
                    val errorMsg = extractErrorMessage(html) ?: "Invalid Student ID or Password"
                    Result.failure(IOException(errorMsg))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting login", e)
            Result.failure(e)
        }
    }

    suspend fun fetchCalendarActivities(): Result<CalendarFetchResult> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(CALENDAR_PAGE_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://vulms.vu.edu.pk/home.aspx")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to fetch calendar (HTTP ${response.code})"))
                }

                val html = response.body?.string().orEmpty()

                if (html.contains("txtStudentID") && html.contains("txtPassword")) {
                    return@withContext Result.failure(IOException("Session expired. Please log in again."))
                }

                val viewState = extractHiddenField(html, "__VIEWSTATE")
                val generator = extractHiddenField(html, "__VIEWSTATEGENERATOR")

                val jsonPattern = Pattern.compile(
                    """var\s+JsonData\s*=\s*(\[\s*\{.*?\}\s*\])\s*;""",
                    Pattern.DOTALL or Pattern.CASE_INSENSITIVE
                )
                val matcher = jsonPattern.matcher(html)

                if (!matcher.find()) {
                    if (html.contains("var JsonData = []", ignoreCase = true) ||
                        html.contains("var JsonData = [];", ignoreCase = true)) {
                        return@withContext Result.success(CalendarFetchResult(emptyList(), viewState, generator))
                    }
                    return@withContext Result.failure(IOException("Could not find activity calendar data in VU LMS response"))
                }

                val rawJson = matcher.group(1).orEmpty()
                val parsedList = try {
                    activityAdapter.fromJson(rawJson) ?: emptyList()
                } catch (e: Exception) {
                    Log.w(TAG, "Moshi parse failed, falling back to manual regex", e)
                    parseJsonFallback(rawJson)
                }

                val entities = parsedList
                    .filterNot { raw ->
                        val t = raw.title?.lowercase().orEmpty()
                        val c = raw.coursecode?.lowercase().orEmpty()
                        t.contains("challan") || c.contains("challan") || c == "fee"
                    }
                    .map { it.toActivityEntity() }
                Result.success(CalendarFetchResult(entities, viewState, generator))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching activities", e)
            Result.failure(e)
        }
    }

    /**
     * POST to ActivityCalendar.aspx with tabHClassic to fetch the ToDo assignments, quizzes, GDBs, and practicals.
     * Returns a set of pending activity matching keys: "COURSE_CLEANTITLE"
     */
    suspend fun fetchClassicToDoActivities(
        viewState: String,
        viewStateGenerator: String
    ): Result<Set<String>> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("__EVENTTARGET", "ctl00\$MainContent\$tabHClassic")
                .add("__EVENTARGUMENT", "")
                .add("__VIEWSTATE", viewState)
                .add("__VIEWSTATEGENERATOR", viewStateGenerator)
                .add("ctl00\$MainContent\$hfActiveTab", "")
                .build()

            val request = Request.Builder()
                .url(CALENDAR_PAGE_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", CALENDAR_PAGE_URL)
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to fetch ToDo tab (HTTP ${response.code})"))
                }

                val html = response.body?.string().orEmpty()
                val pendingKeys = parseClassicToDoHtml(html)
                Result.success(pendingKeys)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching classic ToDo list", e)
            Result.failure(e)
        }
    }

    /**
     * GET https://vulms.vu.edu.pk/AccountBook/AccountBook.aspx to fetch all Challan records.
     */
    suspend fun fetchAccountBook(): Result<List<ChallanEntity>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(ACCOUNT_BOOK_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://vulms.vu.edu.pk/home.aspx")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to fetch account book (HTTP ${response.code})"))
                }

                val html = response.body?.string().orEmpty()
                val challans = parseAccountBookChallans(html)
                Result.success(challans)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching account book", e)
            Result.failure(e)
        }
    }

    suspend fun fetchAccountBookPaidChallans(): Result<Set<String>> = withContext(Dispatchers.IO) {
        val challanResult = fetchAccountBook()
        if (challanResult.isSuccess) {
            val list = challanResult.getOrNull().orEmpty()
            val paidSet = list.filter { it.isPaid }.map { it.challanNo }.toSet()
            Result.success(paidSet)
        } else {
            Result.failure(challanResult.exceptionOrNull() ?: IOException("Failed to fetch account book"))
        }
    }

    /**
     * GET https://vulms.vu.edu.pk/ProgressTimeline/ProgressTimeline.aspx to fetch enrolled subjects.
     */
    suspend fun fetchCurrentSubjects(): Result<List<EnrolledSubject>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(PROGRESS_TIMELINE_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://vulms.vu.edu.pk/home.aspx")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to fetch subjects (HTTP ${response.code})"))
                }

                val html = response.body?.string().orEmpty()
                val subjects = parseCurrentSubjectsHtml(html)
                Result.success(subjects)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching current subjects", e)
            Result.failure(e)
        }
    }

    /**
     * GET https://vulms.vu.edu.pk/StudentServices/CourseSelection/CourseSelectionClosed.aspx to fetch academic profile.
     * Primes session at StudentServices.aspx, adds browser headers, and falls back to course selection and profile pages.
     */
    suspend fun fetchStudentProfile(): Result<StudentProfile> = withContext(Dispatchers.IO) {
        val urlsToTry = listOf(
            COURSE_SELECTION_CLOSED_URL,
            "https://vulms.vu.edu.pk/StudentServices/CourseSelection/CourseSelection.aspx",
            "https://vulms.vu.edu.pk/StudentServices/StudentServices.aspx",
            COURSE_SELECTION_CLOSED_URL,
            "https://vulms.vu.edu.pk/Profile/StudentProfile.aspx",
            "https://vulms.vu.edu.pk/home.aspx"
        )

        var bestProfile: StudentProfile? = null

        for (targetUrl in urlsToTry) {
            try {
                val referer = if (targetUrl == COURSE_SELECTION_CLOSED_URL || targetUrl.contains("CourseSelection")) {
                    "https://vulms.vu.edu.pk/StudentServices/StudentServices.aspx"
                } else {
                    "https://vulms.vu.edu.pk/home.aspx"
                }

                val request = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/jxl,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Referer", referer)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseUrl = response.request.url.toString()
                        val html = response.body?.string().orEmpty()

                        // Detect login redirect or session expiry
                        if (responseUrl.contains("LMS_LP", ignoreCase = true) ||
                            html.contains("txtStudentID", ignoreCase = true) ||
                            html.contains("Session Expired", ignoreCase = true)
                        ) {
                            Log.w(TAG, "Student profile fetch: session expired or redirected to login ($responseUrl)")
                            return@withContext Result.failure(IOException("Session expired, authentication required"))
                        }

                        val profile = parseStudentProfileHtml(html)
                        if (profile.studentId.isNotEmpty() && profile.cgpa.isNotEmpty()) {
                            return@withContext Result.success(profile)
                        }
                        if (profile.studentId.isNotEmpty() || profile.studentName.isNotEmpty()) {
                            if (bestProfile == null || (bestProfile?.cgpa.isNullOrEmpty() && profile.cgpa.isNotEmpty())) {
                                bestProfile = profile
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching student profile from $targetUrl: ${e.message}")
            }
        }

        if (bestProfile != null && (bestProfile!!.studentId.isNotEmpty() || bestProfile!!.studentName.isNotEmpty() || bestProfile!!.cgpa.isNotEmpty())) {
            return@withContext Result.success(bestProfile!!)
        }

        Result.failure(IOException("Failed to fetch student academic profile from VU LMS (Session may have expired)"))
    }

    /**
     * GET https://vulms.vu.edu.pk/StudentServices/Registrar%20Office/PrintStudentECard.aspx to download E-Card PDF.
     * Validates %PDF- binary signature and primes PrintStudentECardView.aspx if ASP.NET returns HTML first.
     */
    suspend fun downloadStudentECard(outputFile: java.io.File): Result<java.io.File> = withContext(Dispatchers.IO) {
        try {
            fun executePdfRequest(): Pair<Int, Pair<String, ByteArray?>> {
                val request = Request.Builder()
                    .url(STUDENT_ECARD_URL)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/pdf,application/octet-stream;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .header("Referer", STUDENT_ECARD_VIEW_URL)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val contentType = response.header("Content-Type").orEmpty()
                    if (!response.isSuccessful) {
                        return response.code to (contentType to null)
                    }
                    return response.code to (contentType to response.body?.bytes())
                }
            }

            var (statusCode, payloadPair) = executePdfRequest()
            var contentType = payloadPair.first
            var bytes = payloadPair.second

            // If VU LMS returned HTML instead of application/pdf (e.g. ASP.NET E-Card view session not primed),
            // visit PrintStudentECardView.aspx first and retry PrintStudentECard.aspx once.
            if (bytes == null || !isValidPdfBytes(bytes)) {
                runCatching {
                    val viewReq = Request.Builder()
                        .url(STUDENT_ECARD_VIEW_URL)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Referer", "https://vulms.vu.edu.pk/home.aspx")
                        .get()
                        .build()
                    client.newCall(viewReq).execute().close()
                }
                val retry = executePdfRequest()
                statusCode = retry.first
                contentType = retry.second.first
                bytes = retry.second.second
            }

            if (bytes == null) {
                return@withContext Result.failure(IOException("Failed to download E-Card PDF (HTTP $statusCode)"))
            }

            if (!isValidPdfBytes(bytes)) {
                if (outputFile.exists() && !isValidPdfFile(outputFile)) {
                    runCatching { outputFile.delete() }
                }
                val kind = contentType.substringBefore(";").ifBlank { "non-PDF response" }
                return@withContext Result.failure(
                    IOException("VU LMS returned $kind instead of a valid E-Card PDF (session may have expired)")
                )
            }

            outputFile.parentFile?.mkdirs()
            val tempFile = java.io.File(outputFile.parentFile, "${outputFile.name}.tmp")
            tempFile.writeBytes(bytes)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            if (!tempFile.renameTo(outputFile)) {
                outputFile.writeBytes(bytes)
                runCatching { tempFile.delete() }
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading student E-Card", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches student profile picture from GridImageTemplate.aspx.
     * Extracts pure JPEG bytes between SOI (0xFF 0xD8) and EOI (0xFF 0xD9).
     */
    suspend fun fetchProfileAvatar(): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(PROFILE_IMAGE_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .header("Referer", "https://vulms.vu.edu.pk/home.aspx")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to fetch profile image (HTTP ${response.code})"))
                }
                val bytes = response.body?.bytes() ?: return@withContext Result.failure(IOException("Empty response body"))
                if (bytes.size < 4) {
                    return@withContext Result.failure(IOException("Response too small"))
                }

                // Locate JPEG Start of Image (0xFF, 0xD8)
                var startIndex = -1
                for (i in 0 until bytes.size - 1) {
                    if ((bytes[i].toInt() and 0xFF) == 0xFF && (bytes[i + 1].toInt() and 0xFF) == 0xD8) {
                        startIndex = i
                        break
                    }
                }

                if (startIndex == -1) {
                    return@withContext Result.failure(IOException("No JPEG SOI marker found"))
                }

                // Locate JPEG End of Image (0xFF, 0xD9)
                var endIndex = -1
                for (i in bytes.size - 1 downTo startIndex + 2) {
                    if ((bytes[i - 1].toInt() and 0xFF) == 0xFF && (bytes[i].toInt() and 0xFF) == 0xD9) {
                        endIndex = i + 1
                        break
                    }
                }

                val jpegBytes = if (endIndex > startIndex) {
                    bytes.copyOfRange(startIndex, endIndex)
                } else {
                    bytes.copyOfRange(startIndex, bytes.size)
                }

                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    Result.success(jpegBytes)
                } else {
                    Result.failure(IOException("Decoded image bounds invalid"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching profile image", e)
            Result.failure(e)
        }
    }

    /**
     * Checks https://raw.githubusercontent.com/hanzlarehman404/vu-lms-reminder/default/update.json
     */
    suspend fun checkAppUpdate(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        val urlsToTry = listOf(
            UPDATE_CHECK_URL,
            "https://raw.githubusercontent.com/hanzlarehman404/vu-lms-reminder/main/update.json",
            "https://raw.githubusercontent.com/hanzlarehman404/vu-lms-reminder/master/update.json"
        )
        var lastException: Exception? = null

        for (url in urlsToTry) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = response.body?.string().orEmpty()
                        val updateInfo = updateAdapter.fromJson(json)
                        if (updateInfo != null) {
                            return@withContext Result.success(updateInfo)
                        }
                        lastException = IOException("Invalid update.json format from GitHub")
                    } else {
                        lastException = IOException("GitHub returned HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Error querying update from $url: ${e.message}")
            }
        }
        Result.failure(lastException ?: IOException("Failed to reach GitHub release metadata."))
    }

    /**
     * Fetches https://raw.githubusercontent.com/hanzlarehman404/vu-lms-reminder/notifications/notifications.json
     */
    suspend fun fetchRemoteNotifications(): Result<List<RemoteNotificationItem>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(NOTIFICATIONS_FEED_URL)
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code} fetching notifications"))
                }
                val json = response.body?.string().orEmpty().trim()
                if (json.isEmpty()) {
                    return@withContext Result.success(emptyList())
                }
                val feed = notificationsAdapter.fromJson(json)
                if (feed != null) {
                    return@withContext Result.success(feed.notifications)
                }
                return@withContext Result.failure(IOException("Invalid notifications JSON format"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching remote notifications: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fetches the latest README.md directly from the GitHub repository.
     */
    suspend fun fetchReadmeMarkdown(): Result<String> = withContext(Dispatchers.IO) {
        val urlsToTry = listOf(
            README_DEFAULT_URL,
            "https://raw.githubusercontent.com/hanzlarehman404/vu-lms-reminder/main/README.md",
            "https://raw.githubusercontent.com/hanzlarehman404/vu-lms-reminder/dev/README.md",
            "https://raw.githubusercontent.com/hanzlarehman404/vu-lms-reminder/master/README.md"
        )
        var lastException: Exception? = null
        for (url in urlsToTry) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val markdown = response.body?.string().orEmpty().trim()
                        if (markdown.isNotEmpty()) {
                            return@withContext Result.success(markdown)
                        }
                    }
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Error fetching README from $url: ${e.message}")
            }
        }
        Result.failure(lastException ?: IOException("Unable to load README.md from GitHub."))
    }

    private fun parseClassicToDoHtml(html: String): Set<String> {
        val keys = mutableSetOf<String>()

        // 1. Assignments Table: #MainContent_gvAssignmentsToDo
        val rowPattern = Pattern.compile(
            """<tr>\s*<td[^>]*>(.*?)</td>\s*<td[^>]*>(.*?)</td>""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = rowPattern.matcher(html)
        while (matcher.find()) {
            val course = matcher.group(1)?.replace(Regex("<.*?>"), "")?.trim().orEmpty().uppercase()
            val title = matcher.group(2)?.replace(Regex("<.*?>"), "")?.trim().orEmpty().lowercase()

            if (course.isNotEmpty() && !course.equals("Course", ignoreCase = true)) {
                val cleanTitle = title.replace(" ", "")
                keys.add("${course}_$cleanTitle")
                // Also add generalized variant (e.g. without "#")
                keys.add("${course}_${cleanTitle.replace("#", "")}")
            }
        }

        return keys
    }

    fun parseAccountBookChallans(html: String): List<ChallanEntity> {
        val list = mutableListOf<ChallanEntity>()
        val indexSet = mutableSetOf<String>()

        val viewState = extractHiddenField(html, "__VIEWSTATE")
        val viewStateGenerator = extractHiddenField(html, "__VIEWSTATEGENERATOR")
        val previousPage = extractHiddenField(html, "__PREVIOUSPAGE")

        // Find all indices from lblDChallanType_X or lbl_X or hlPrint_X or hlPay_X
        val indexPattern = Pattern.compile("""id=["'][^"']*(?:lblDChallanType|lbl|hlPrint|hlPay)_(\d+)["']""", Pattern.CASE_INSENSITIVE)
        val indexMatcher = indexPattern.matcher(html)
        while (indexMatcher.find()) {
            indexSet.add(indexMatcher.group(1).orEmpty())
        }

        for (idx in indexSet.sortedBy { it.toIntOrNull() ?: 0 }) {
            val challanNo = extractSpanText(html, "lbl_$idx").ifEmpty { extractSpanText(html, "lblChallanNo_$idx") }
            val challanType = extractSpanText(html, "lblDChallanType_$idx").ifEmpty { "Tuition / Semester Fee" }
            val payable = extractSpanText(html, "lblPayableAmount_$idx")
            val paid = extractSpanText(html, "lblPaidAmount_$idx")
            val balance = extractSpanText(html, "lblrbalance_$idx")
            val dueDate = extractSpanText(html, "lblduedate_$idx")
            val paidDate = extractSpanText(html, "lblpaiddate_$idx")

            // Determine if paid
            val rowPattern = Pattern.compile(
                """(?:id=["'][^"']*grdaccountbook_pnl_${idx}["'][\s\S]*?(?=id=["'][^"']*grdaccountbook_pnl_|\Z)|<tr[^>]*>[\s\S]*?_${idx}["'][\s\S]*?</tr>)""",
                Pattern.CASE_INSENSITIVE
            )
            val rowMatcher = rowPattern.matcher(html)
            val rowHtml = if (rowMatcher.find()) rowMatcher.group(0).orEmpty() else ""

            val hasUnpaidMarker = rowHtml.contains("UnpaidDiv", ignoreCase = true) ||
                    paidDate.contains("unpaid", ignoreCase = true) ||
                    rowHtml.contains("title=\"Pay Now\"", ignoreCase = true)

            val isPaid = !hasUnpaidMarker && (
                    rowHtml.contains("title=\"Paid\"", ignoreCase = true) ||
                    rowHtml.contains("Paid.png", ignoreCase = true) ||
                    (paidDate.isNotBlank() && paidDate != "-" && (balance == "0" || balance.isEmpty()))
            )

            // Extract print voucher preview URL: ChallanPrintPreview.aspx?ref=...&fref=...
            var printUrl = ""
            val printPattern = Pattern.compile("""ChallanPrintPreview\.aspx\?ref=[^"'\s,)<>]+""", Pattern.CASE_INSENSITIVE)
            val pm1 = printPattern.matcher(rowHtml)
            if (pm1.find()) {
                val rel = pm1.group(0).orEmpty().replace("&amp;", "&").replace("&quot;", "").trimEnd(',', '"', '\'', ')')
                printUrl = "https://vulms.vu.edu.pk/AccountBook/$rel"
            } else {
                val printAroundIdx = Pattern.compile("""hlPrint_${idx}["'][\s\S]*?(ChallanPrintPreview\.aspx\?ref=[^"'\s,)<>]+)""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (printAroundIdx.find()) {
                    val rel = printAroundIdx.group(1).orEmpty().replace("&amp;", "&").replace("&quot;", "").trimEnd(',', '"', '\'', ')')
                    printUrl = "https://vulms.vu.edu.pk/AccountBook/$rel"
                } else if (challanNo.isNotEmpty()) {
                    printUrl = "https://vulms.vu.edu.pk/AccountBook/PrintChallan.aspx?ChallanNo=$challanNo"
                }
            }

            // Extract pay with card (IPG) URL: https://vulms.vu.edu.pk/IPG/Payment.aspx?ref=...
            var payCardUrl = ""
            val payUrlPattern = Pattern.compile("""https://vulms\.vu\.edu\.pk/IPG/Payment\.aspx\?ref=[^"'\s,)<>]+""", Pattern.CASE_INSENSITIVE)
            val payMatcher = payUrlPattern.matcher(rowHtml)
            if (payMatcher.find()) {
                payCardUrl = payMatcher.group(0).orEmpty().replace("&amp;", "&").replace("&quot;", "").trimEnd(',', '"', '\'', ')')
            } else {
                val payAroundIdx = Pattern.compile("""hlPay_${idx}["'][\s\S]*?(https://vulms\.vu\.edu\.pk/IPG/Payment\.aspx\?ref=[^"'\s,)<>]+)""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (payAroundIdx.find()) {
                    payCardUrl = payAroundIdx.group(1).orEmpty().replace("&amp;", "&").replace("&quot;", "").trimEnd(',', '"', '\'', ')')
                }
            }

            // Extract pay with card control name: e.g. ctl00$MainContent$grdaccountbook$ctl24$hlPay
            var payControlName = "ctl00${'$'}MainContent${'$'}grdaccountbook${'$'}ctl${idx}${'$'}hlPay"
            val ctrlPattern = Pattern.compile("""name=["'](ctl00\${'$'}MainContent\${'$'}grdaccountbook\${'$'}[^"']*hlPay[^"']*)["']""", Pattern.CASE_INSENSITIVE).matcher(rowHtml)
            if (ctrlPattern.find()) {
                payControlName = ctrlPattern.group(1).orEmpty()
            } else {
                val ctrlAroundIdx = Pattern.compile("""name=["'](ctl00\${'$'}MainContent\${'$'}grdaccountbook\${'$'}[^"']*hlPay[^"']*)["'][^>]*id=["'][^"']*hlPay_${idx}["']""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (ctrlAroundIdx.find()) {
                    payControlName = ctrlAroundIdx.group(1).orEmpty()
                }
            }

            if (challanNo.isNotEmpty() || payable.isNotEmpty()) {
                list.add(
                    ChallanEntity(
                        challanNo = challanNo.ifEmpty { "CH-$idx" },
                        challanType = challanType,
                        payableAmount = payable,
                        paidAmount = paid,
                        balance = balance,
                        dueDate = dueDate,
                        paidDate = paidDate,
                        isPaid = isPaid,
                        printUrl = printUrl,
                        payCardUrl = payCardUrl,
                        payControlName = payControlName,
                        viewState = viewState,
                        viewStateGenerator = viewStateGenerator,
                        previousPage = previousPage
                    )
                )
            }
        }

        // Fallback if ASP.NET span IDs were not matched
        if (list.isEmpty()) {
            val challanBlockPattern = Pattern.compile(
                """Challan No\.\s*(\d+)[\s\S]*?(?=Challan No\.|\Z)""",
                Pattern.CASE_INSENSITIVE
            )
            val matcher = challanBlockPattern.matcher(html)
            while (matcher.find()) {
                val challanId = matcher.group(1).orEmpty()
                val block = matcher.group(0).orEmpty()
                val isPaid = block.contains("title=\"Paid\"", ignoreCase = true) ||
                        block.contains("Paid.png", ignoreCase = true) ||
                        (block.contains("style=\"color:Green;\"", ignoreCase = true) && !block.contains("UnPaid", ignoreCase = true))

                val dueMatcher = Pattern.compile("""Due\s*Date[:\s]*([0-9A-Za-z\s-]+)""", Pattern.CASE_INSENSITIVE).matcher(block)
                val dueDate = if (dueMatcher.find()) dueMatcher.group(1).orEmpty().trim() else ""

                list.add(
                    ChallanEntity(
                        challanNo = challanId,
                        challanType = "VU Fee Challan",
                        payableAmount = "",
                        paidAmount = "",
                        balance = if (isPaid) "0" else "",
                        dueDate = dueDate,
                        paidDate = if (isPaid) "Paid" else "-",
                        isPaid = isPaid,
                        printUrl = "https://vulms.vu.edu.pk/AccountBook/PrintChallan.aspx?ChallanNo=$challanId"
                    )
                )
            }
        }

        return list
    }

    private fun parseCurrentSubjectsHtml(html: String): List<EnrolledSubject> {
        val subjects = mutableListOf<EnrolledSubject>()

        // Look for tab list anchor elements: <a ... class="nav-link ...">CS301-Data Structures</a>
        val anchorPattern = Pattern.compile(
            """<a[^>]*data-toggle=["']tab["'][^>]*>([^<]+)</a>""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = anchorPattern.matcher(html)
        while (matcher.find()) {
            val text = matcher.group(1)?.trim().orEmpty()
            if (text.isNotEmpty() && !text.equals("Home", ignoreCase = true)) {
                if (text.contains("-")) {
                    val code = text.substringBefore("-").trim().uppercase()
                    val title = text.substringAfter("-").trim()
                    subjects.add(EnrolledSubject(code, title))
                } else {
                    subjects.add(EnrolledSubject(text.uppercase(), text))
                }
            }
        }

        // Fallback: search nav-link text inside nav-tabs
        if (subjects.isEmpty()) {
            val navPattern = Pattern.compile(
                """<ul[^>]*class=["'][^"']*nav-tabs[^"']*["'][^>]*>([\s\S]*?)</ul>""",
                Pattern.CASE_INSENSITIVE
            )
            val navMatcher = navPattern.matcher(html)
            if (navMatcher.find()) {
                val navHtml = navMatcher.group(1).orEmpty()
                val itemMatcher = Pattern.compile("""<a[^>]*>([^<]+)</a>""", Pattern.CASE_INSENSITIVE).matcher(navHtml)
                while (itemMatcher.find()) {
                    val text = itemMatcher.group(1)?.trim().orEmpty()
                    if (text.isNotEmpty() && !text.equals("Home", ignoreCase = true)) {
                        val code = if (text.contains("-")) text.substringBefore("-").trim().uppercase() else text.uppercase()
                        val title = if (text.contains("-")) text.substringAfter("-").trim() else text
                        subjects.add(EnrolledSubject(code, title))
                    }
                }
            }
        }

        return subjects.distinctBy { it.code }
    }

    fun parseStudentProfileHtml(html: String): StudentProfile {
        if (html.isBlank()) return StudentProfile()

        // 1. First, parse all label-value pairs from the table:
        // Table in CourseSelectionClosed.aspx has rows:
        // <tr><td ... class="label" ...>LABEL</td> <td ... class="txt" ...>VALUE</td></tr>
        val tableData = mutableMapOf<String, String>()

        val labelTxtPattern = Pattern.compile(
            """<td[^>]*class=["'][^"']*label[^"']*["'][^>]*>([\s\S]*?)</td>\s*<td[^>]*class=["'][^"']*txt[^"']*["'][^>]*>([\s\S]*?)</td>""",
            Pattern.CASE_INSENSITIVE
        )
        val ltMatcher = labelTxtPattern.matcher(html)
        while (ltMatcher.find()) {
            val rawLabel = ltMatcher.group(1).orEmpty()
                .replace(Regex("<.*?>"), " ")
                .replace("&nbsp;", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .lowercase()
            val rawValue = ltMatcher.group(2).orEmpty()
                .replace(Regex("<.*?>"), " ")
                .replace("&nbsp;", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (rawLabel.isNotEmpty() && rawValue.isNotEmpty()) {
                tableData[rawLabel] = rawValue
            }
        }

        // Helper to extract by matching keywords in tableData
        fun findFromTableData(vararg keywords: String, notContains: String? = null): String {
            for ((k, v) in tableData) {
                if (notContains != null && k.contains(notContains.lowercase())) continue
                if (keywords.any { k.contains(it.lowercase()) }) {
                    return v
                }
            }
            return ""
        }

        // Fallback cell extractor if class="label" / "txt" are absent
        fun extractCell(labelRegex: String): String {
            val p = Pattern.compile(
                """<td[^>]*>(?:(?!<td)[\s\S])*?(?:$labelRegex)(?:(?!<td)[\s\S])*?</td>\s*<td[^>]*>([\s\S]*?)</td>""",
                Pattern.CASE_INSENSITIVE
            )
            val m = p.matcher(html)
            if (m.find()) {
                val clean = m.group(1).orEmpty()
                    .replace(Regex("<.*?>"), " ")
                    .replace("&nbsp;", " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (clean.isNotEmpty()) return clean
            }
            return ""
        }

        var studentId = findFromTableData("student id").ifEmpty { extractCell("""Student\s*ID""") }
        var name = findFromTableData("name", notContains = "semester").ifEmpty { extractCell("""\bName\b""") }
        if (name.isEmpty()) name = extractCell("""Student\s*Name""")
        var program = findFromTableData("study program", "program").ifEmpty { extractCell("""Study\s*program""") }
        var effectiveCgpa = findFromTableData("effective cgpa").ifEmpty { extractCell("""Effective\s*CGPA""") }
        if (effectiveCgpa.isEmpty()) {
            val effMatcher = Pattern.compile("""Effective\s*CGPA[:\s<>/a-zA-Z="'-]*?([0-4]\.\d{1,2})""", Pattern.CASE_INSENSITIVE).matcher(html)
            if (effMatcher.find()) {
                effectiveCgpa = effMatcher.group(1).orEmpty()
            }
        }

        var cgpa = findFromTableData("cgpa", notContains = "effective")
            .ifEmpty { extractCell("""(?<!Effective\s)\bCGPA\b""") }
            .ifEmpty { extractCell("""\bCGPA:""") }

        if (cgpa.isEmpty()) {
            val cgpaMatcher = Pattern.compile("""(?<!Effective\s)\bCGPA[:\s<>/a-zA-Z="'-]*?([0-4]\.\d{1,2})""", Pattern.CASE_INSENSITIVE).matcher(html)
            if (cgpaMatcher.find()) {
                cgpa = cgpaMatcher.group(1).orEmpty()
            }
        }
        if (cgpa.isEmpty()) {
            val generalCgpa = Pattern.compile("""\bCGPA[:\s]*([0-4]\.\d{1,2})""", Pattern.CASE_INSENSITIVE).matcher(html)
            if (generalCgpa.find()) {
                cgpa = generalCgpa.group(1).orEmpty()
            }
        }
        if (cgpa.isEmpty()) cgpa = effectiveCgpa
        if (effectiveCgpa.isEmpty()) effectiveCgpa = cgpa

        val earned = findFromTableData("credits earned", "credit earned", "earned").ifEmpty { extractCell("""Credits?\s*Earned""") }
        val exempted = findFromTableData("credits exempted", "credit exempted", "exempted").ifEmpty { extractCell("""Credits?\s*Exempted""") }
        val semNo = findFromTableData("current semester", "semester no")
            .ifEmpty { extractCell("""Current\s*Semester(?:\s*No)?""") }
            .replace(Regex("(?i)semester|sem|no|:"), "")
            .trim()

        // Parse Semester Name from span id MainContent_lblSemester or lblSemester1/2
        var semName = ""
        val semPattern = Pattern.compile("""id=["'][^"']*lblSemester\d*["'][^>]*>([^<]+)</span>""", Pattern.CASE_INSENSITIVE)
        val semMatcher = semPattern.matcher(html)
        if (semMatcher.find()) {
            semName = semMatcher.group(1)?.trim().orEmpty()
        }
        if (semName.isEmpty()) {
            val semPattern2 = Pattern.compile("""Course Selection\s*\(([^)]+)\)""", Pattern.CASE_INSENSITIVE)
            val sm2 = semPattern2.matcher(html)
            if (sm2.find()) {
                semName = sm2.group(1)?.replace(Regex("<.*?>"), " ")?.trim().orEmpty()
            }
        }

        // Top bar user link fallback: "HANZLA REHMAN<br />(bc250417449)"
        if (name.isEmpty() || studentId.isEmpty()) {
            val topBarPattern = Pattern.compile(
                """<span[^>]*class=["'][^"']*m-nav__link-text[^"']*["'][^>]*>([\s\S]*?)</span>""",
                Pattern.CASE_INSENSITIVE
            )
            val tbMatcher = topBarPattern.matcher(html)
            if (tbMatcher.find()) {
                val text = tbMatcher.group(1).orEmpty().replace(Regex("<.*?>"), "\n")
                val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isNotEmpty() && name.isEmpty()) name = lines[0]
                val idLine = lines.firstOrNull { it.contains("bc", ignoreCase = true) || it.contains("mc", ignoreCase = true) }
                if (idLine != null && studentId.isEmpty()) {
                    studentId = idLine.replace("(", "").replace(")", "").trim()
                }
            }
        }

        if (name.isEmpty()) {
            val userPicName = Pattern.compile("""<span[^>]*class=["'][^"']*m-card-user__name[^"']*["'][^>]*>([^<]+)</span>""", Pattern.CASE_INSENSITIVE).matcher(html)
            if (userPicName.find()) name = userPicName.group(1)?.trim().orEmpty()
        }

        if (studentId.isEmpty()) {
            val altIdPattern = Pattern.compile("""<img[^>]*alt=["'](bc\d+|mc\d+)[^"']*["']""", Pattern.CASE_INSENSITIVE).matcher(html)
            if (altIdPattern.find()) studentId = altIdPattern.group(1)?.trim().orEmpty()
        }

        if (program.isEmpty()) {
            val emailProg = Pattern.compile("""<a[^>]*class=["'][^"']*m-card-user__email[^"']*["'][^>]*>([^<]+)</a>""", Pattern.CASE_INSENSITIVE).matcher(html)
            if (emailProg.find()) program = emailProg.group(1)?.trim().orEmpty()
        }

        return StudentProfile(
            studentId = studentId,
            studentName = name,
            studyProgram = program,
            cgpa = cgpa,
            effectiveCgpa = effectiveCgpa,
            creditsEarned = earned,
            creditsExempted = exempted,
            currentSemesterNo = semNo,
            semesterName = semName
        )
    }

    private fun extractSpanText(html: String, spanIdSuffix: String): String {
        val p = Pattern.compile("""id=["'][^"']*${Pattern.quote(spanIdSuffix)}["'][^>]*>([^<]*)</span>""", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(html)
        return if (m.find()) m.group(1)?.trim().orEmpty() else ""
    }

    private fun extractHiddenField(html: String, fieldName: String): String {
        val p1 = Pattern.compile(
            """name=["']${Pattern.quote(fieldName)}["'][^>]*value=["']([^"']*)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val m1 = p1.matcher(html)
        if (m1.find()) return m1.group(1).orEmpty()

        val p2 = Pattern.compile(
            """value=["']([^"']*)["'][^>]*name=["']${Pattern.quote(fieldName)}["']""",
            Pattern.CASE_INSENSITIVE
        )
        val m2 = p2.matcher(html)
        if (m2.find()) return m2.group(1).orEmpty()

        return ""
    }

    private fun extractErrorMessage(html: String): String? {
        val patterns = listOf(
            Pattern.compile("""<span[^>]*id=["'][^"']*lblError[^"']*["'][^>]*>([^<]+)</span>""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""<div[^>]*class=["'][^"']*alert[^"']*["'][^>]*>([^<]+)</div>""", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in patterns) {
            val m = pattern.matcher(html)
            if (m.find()) {
                val txt = m.group(1)?.trim()
                if (!txt.isNullOrEmpty()) return txt
            }
        }
        return null
    }

    private fun parseJsonFallback(rawJson: String): List<VuRawActivity> {
        val list = mutableListOf<VuRawActivity>()
        val itemPattern = Pattern.compile("""\{([^{}]+)\}""", Pattern.DOTALL)
        val matcher = itemPattern.matcher(rawJson)
        while (matcher.find()) {
            val block = matcher.group(1).orEmpty()
            val title = extractJsonStringProp(block, "title")
            val course = extractJsonStringProp(block, "coursecode")
            val semester = extractJsonStringProp(block, "Semester")
            val start = extractJsonStringProp(block, "start")
            val end = extractJsonStringProp(block, "end")
            val url = extractJsonStringProp(block, "url")
            val expired = extractJsonStringProp(block, "IsExpired")
            val bg = extractJsonStringProp(block, "backgroundColor")
            val border = extractJsonStringProp(block, "borderColor")

            if (!title.isNullOrEmpty()) {
                list.add(
                    VuRawActivity(
                        title = title,
                        coursecode = course,
                        semester = semester,
                        start = start,
                        end = end,
                        url = url,
                        isExpired = expired,
                        backgroundColor = bg,
                        borderColor = border
                    )
                )
            }
        }
        return list
    }

    private fun extractJsonStringProp(block: String, key: String): String? {
        val p = Pattern.compile(""""${Pattern.quote(key)}"\s*:\s*"([^"]*)"""", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(block)
        return if (m.find()) m.group(1) else null
    }

    companion object {
        private const val TAG = "VuLmsNetworkService"
        private const val LOGIN_PAGE_URL = "https://vulms.vu.edu.pk/LMS_LP.aspx"
        private const val SIGN_OUT_URL = "https://vulms.vu.edu.pk/SignOut.aspx"
        private const val CALENDAR_PAGE_URL = "https://vulms.vu.edu.pk/ActivityCalendar/ActivityCalendar.aspx"
        private const val ACCOUNT_BOOK_URL = "https://vulms.vu.edu.pk/AccountBook/AccountBook.aspx"
        private const val PROGRESS_TIMELINE_URL = "https://vulms.vu.edu.pk/ProgressTimeline/ProgressTimeline.aspx"
        private const val COURSE_SELECTION_CLOSED_URL = "https://vulms.vu.edu.pk/StudentServices/CourseSelection/CourseSelectionClosed.aspx"
        private const val STUDENT_ECARD_URL = "https://vulms.vu.edu.pk/StudentServices/Registrar%20Office/PrintStudentECard.aspx"
        private const val STUDENT_ECARD_VIEW_URL = "https://vulms.vu.edu.pk/StudentServices/Registrar%20Office/PrintStudentECardView.aspx"
        private const val PROFILE_IMAGE_URL = "https://vulms.vu.edu.pk/Profile/GridImageTemplate.aspx"
        private const val UPDATE_CHECK_URL = "https://raw.githubusercontent.com/hanzlarehman404/vu-lms-reminder/default/update.json"
        const val NOTIFICATIONS_FEED_URL = "https://raw.githubusercontent.com/hanzlarehman404/vu-lms-reminder/notifications/notifications.json"
        const val README_DEFAULT_URL = "https://raw.githubusercontent.com/hanzlarehman404/vu-lms-reminder/default/README.md"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun isValidPdfBytes(bytes: ByteArray): Boolean {
            if (bytes.size < 100) return false
            var idx = 0
            // Skip optional UTF-8 BOM
            if (bytes.size >= 3 &&
                (bytes[0].toInt() and 0xFF) == 0xEF &&
                (bytes[1].toInt() and 0xFF) == 0xBB &&
                (bytes[2].toInt() and 0xFF) == 0xBF
            ) {
                idx = 3
            }
            while (idx < bytes.size && idx < 16) {
                val b = bytes[idx].toInt() and 0xFF
                if (b == 0x20 || b == 0x09 || b == 0x0A || b == 0x0D) {
                    idx++
                } else {
                    break
                }
            }
            if (idx + 5 > bytes.size) return false
            return bytes[idx] == '%'.code.toByte() &&
                bytes[idx + 1] == 'P'.code.toByte() &&
                bytes[idx + 2] == 'D'.code.toByte() &&
                bytes[idx + 3] == 'F'.code.toByte() &&
                bytes[idx + 4] == '-'.code.toByte()
        }

        fun isValidPdfFile(file: java.io.File): Boolean {
            if (!file.exists() || !file.isFile || file.length() < 100L) return false
            return try {
                val header = ByteArray(24)
                val readCount = file.inputStream().use { it.read(header) }
                if (readCount < 5) return false
                val probe = ByteArray(100)
                val copyLen = minOf(readCount, probe.size)
                System.arraycopy(header, 0, probe, 0, copyLen)
                isValidPdfBytes(probe)
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Shared OkHttpClient for Coil and general image loading with VU LMS browser headers.
         */
        val sharedImageOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .cookieJar(VuLmsCookieJar.shared)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    val newRequestBuilder = request.newBuilder()
                    if (host.contains("vu.edu.pk", ignoreCase = true)) {
                        newRequestBuilder.header("User-Agent", USER_AGENT)
                        newRequestBuilder.header("Referer", "https://vulms.vu.edu.pk/")
                        newRequestBuilder.header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    }
                    chain.proceed(newRequestBuilder.build())
                }
                .build()
        }
    }
}
