package com.mounsokdara.zyphvert.ui.more

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GithubProfile(val login: String, val name: String?, val avatar: ImageBitmap?) {
    companion object {
        private var cached: GithubProfile? = null

        suspend fun load(login: String): GithubProfile? = cached ?: withContext(Dispatchers.IO) {
            runCatching {
                val j = JSONObject(get("https://api.github.com/users/$login").toString(Charsets.UTF_8))
                val bytes = j.optString("avatar_url").takeIf { it.isNotEmpty() }?.let { runCatching { get("$it&s=160") }.getOrNull() }
                GithubProfile(
                    login = j.optString("login").ifEmpty { login },
                    name = j.optString("name").trim().takeIf { it.isNotEmpty() && it != "null" },
                    avatar = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() },
                )
            }.getOrNull()?.also { cached = it }
        }

        private fun get(url: String): ByteArray {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "zyphvert-app")
                if (conn.responseCode != 200) throw java.io.IOException("HTTP ${conn.responseCode}")
                return conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
        }
    }
}
