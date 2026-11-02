package com.example.data.remote

import android.util.Log
import com.example.data.model.ActivityEntity
import com.example.data.model.AppUpdateInfo
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
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
    private val cookieJar: VuLmsCookieJar = VuLmsCookieJar()
) {
    private val client: OkHttpClient = OkHttpClient.Builder().apply {
        cookieJar(cookieJar)
        connectTimeout(25, TimeUnit.SECONDS)
        readTimeout(30, TimeUnit.SECONDS)
        writeTimeout(25, TimeUnit.SECONDS)
        followRedirects(true)
        followSslRedirects(true)
        configureSsl(this)
    }.build()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val activityListType = Types.newParameterizedType(List::class.java, VuRawActivity::class.java)
    private val activityAdapter = moshi.adapter<List<VuRawActivity>>(activityListType)
    private val updateAdapter = moshi.adapter(AppUpdateInfo::class.java)

    fun clearCookies() {
        cookieJar.clear()
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

                val entities = parsedList.map { it.toActivityEntity() }
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
     * GET https://vulms.vu.edu.pk/AccountBook/AccountBook.aspx to extract paid challan IDs.
     */
    suspend fun fetchAccountBookPaidChallans(): Result<Set<String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(ACCOUNT_BOOK_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://vulms.vu.edu.pk/GradeBook/GradeBook.aspx")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to fetch account book (HTTP ${response.code})"))
                }

                val html = response.body?.string().orEmpty()
                val paidChallans = parseAccountBookPaidChallans(html)
                Result.success(paidChallans)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching account book", e)
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
                    }
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Error querying update from $url: ${e.message}")
            }
        }
        Result.failure(lastException ?: IOException("Failed to check for updates. Kindly try again later or contact the developer."))
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

    private fun parseAccountBookPaidChallans(html: String): Set<String> {
        val paidChallans = mutableSetOf<String>()

        // Look for challan blocks: Challan No. XXXXXXXX
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

            if (isPaid && challanId.isNotEmpty()) {
                paidChallans.add(challanId)
            }
        }

        return paidChallans
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
        private const val PROFILE_IMAGE_URL = "https://vulms.vu.edu.pk/Profile/GridImageTemplate.aspx"
        private const val UPDATE_CHECK_URL = "https://raw.githubusercontent.com/hanzlarehman404/vu-lms-reminder/default/update.json"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private val trustAllCerts: Array<TrustManager> = arrayOf(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )

        private val sslSocketFactory = try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            sslContext.socketFactory
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create custom SSLContext", e)
            null
        }

        fun configureSsl(builder: OkHttpClient.Builder): OkHttpClient.Builder {
            if (sslSocketFactory != null) {
                builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            }
            return builder
        }

        /**
         * Shared OkHttpClient for Coil and general image loading.
         * Bypasses the untrusted certificate chain on vulms.vu.edu.pk and adds required browser headers.
         */
        val sharedImageOkHttpClient: OkHttpClient by lazy {
            val builder = OkHttpClient.Builder()
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
            configureSsl(builder).build()
        }
    }
}
