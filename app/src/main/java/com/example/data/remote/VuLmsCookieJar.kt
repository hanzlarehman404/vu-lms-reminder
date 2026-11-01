package com.example.data.remote

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
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cookieStore[host] ?: return emptyList()
        val now = System.currentTimeMillis()
        synchronized(cookies) {
            // Remove expired cookies
            cookies.removeAll { it.expiresAt < now }
            return cookies.toList()
        }
    }

    fun clear() {
        cookieStore.clear()
    }

    fun hasSessionCookie(): Boolean {
        return cookieStore.values.flatten().any {
            it.name.contains("Session", ignoreCase = true) || it.name.contains("ASP", ignoreCase = true)
        }
    }
}
