package com.example.data.remote

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class VuLmsCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val current = cookieStore.getOrPut(host) { mutableListOf() }
        synchronized(current) {
            for (newCookie in cookies) {
                current.removeAll { it.name == newCookie.name }
                current.add(newCookie)
            }
        }
        syncHostToAndroidWebView(url.toString(), cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cookieStore[host] ?: return emptyList()
        val now = System.currentTimeMillis()
        synchronized(cookies) {
            cookies.removeAll { it.expiresAt < now }
            return cookies.toList()
        }
    }

    fun clear() {
        cookieStore.clear()
        try {
            CookieManager.getInstance()?.removeAllCookies(null)
            CookieManager.getInstance()?.flush()
        } catch (_: Throwable) {
        }
    }

    fun hasSessionCookie(): Boolean {
        return cookieStore.values.flatten().any {
            it.name.contains("Session", ignoreCase = true) ||
                it.name.contains("ASP", ignoreCase = true) ||
                it.name.contains("Auth", ignoreCase = true)
        }
    }

    fun syncToAndroidWebView() {
        try {
            val cm = CookieManager.getInstance() ?: return
            cm.setAcceptCookie(true)
            val now = System.currentTimeMillis()
            for ((host, cookies) in cookieStore) {
                val baseUrl = "https://$host/"
                synchronized(cookies) {
                    cookies.removeAll { it.expiresAt < now }
                    for (cookie in cookies) {
                        val header = buildString {
                            append(cookie.name).append("=").append(cookie.value)
                            append("; path=").append(cookie.path)
                            if (cookie.secure) append("; Secure")
                        }
                        cm.setCookie(baseUrl, header)
                    }
                }
            }
            cm.flush()
        } catch (_: Throwable) {
        }
    }

    private fun syncHostToAndroidWebView(url: String, cookies: List<Cookie>) {
        try {
            val cm = CookieManager.getInstance() ?: return
            cm.setAcceptCookie(true)
            for (cookie in cookies) {
                val header = buildString {
                    append(cookie.name).append("=").append(cookie.value)
                    append("; path=").append(cookie.path)
                    if (cookie.secure) append("; Secure")
                }
                cm.setCookie(url, header)
            }
            cm.flush()
        } catch (_: Throwable) {
        }
    }

    companion object {
        val shared = VuLmsCookieJar()
    }
}
