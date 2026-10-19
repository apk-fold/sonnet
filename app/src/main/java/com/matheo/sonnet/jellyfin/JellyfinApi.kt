package com.matheo.sonnet.jellyfin

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.matheo.sonnet.BuildConfig
import com.matheo.sonnet.SonnetApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Petit client REST Jellyfin (10.9+ / 10.11). Pas de SDK : HttpURLConnection + org.json,
 * déjà présents dans Android, donc zéro dépendance de plus.
 */
class JellyfinApi(server: String, val token: String) {

    val baseUrl: String = server.trim().trimEnd('/')

    val authorization: String =
        "MediaBrowser Client=\"Sonnet\", Device=\"${clean(Build.MODEL)}\", " +
            "DeviceId=\"$deviceId\", Version=\"${BuildConfig.VERSION_NAME}\", Token=\"$token\""

    val headers: Map<String, String> get() = mapOf("Authorization" to authorization)

    fun url(path: String): String = when {
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("/") -> baseUrl + path
        else -> "$baseUrl/$path"
    }

    // ---------------------------------------------------------------- HTTP

    suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        val text = request("GET", path, null)
        if (text.isNullOrBlank()) JSONObject() else JSONObject(text)
    }

    suspend fun getArray(path: String): JSONArray = withContext(Dispatchers.IO) {
        val text = request("GET", path, null)
        if (text.isNullOrBlank()) JSONArray() else JSONArray(text)
    }

    suspend fun post(path: String, body: JSONObject? = null): JSONObject? = withContext(Dispatchers.IO) {
        val text = request("POST", path, body?.toString() ?: "")
        if (text.isNullOrBlank()) null else runCatching { JSONObject(text) }.getOrNull()
    }

    suspend fun delete(path: String) {
        withContext(Dispatchers.IO) { request("DELETE", path, null) }
    }

    suspend fun bitmap(path: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open("GET", path)
            try {
                if (conn.responseCode !in 200..299) null
                else conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun open(method: String, path: String): HttpURLConnection {
        val conn = URL(url(path)).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Authorization", authorization)
        conn.setRequestProperty("Accept", "application/json")
        return conn
    }

    private fun request(method: String, path: String, body: String?): String? {
        val conn = open(method, path)
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                throw IOException("Jellyfin HTTP $code sur $path ${err?.take(160).orEmpty()}")
            }
            if (code == 204) return null
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ------------------------------------------------------------ Endpoints

    suspend fun me(): JSONObject = get("/Users/Me")

    suspend fun item(userId: String, itemId: String): ItemInfo =
        ItemInfo.parse(get("/Items/$itemId?userId=$userId"))

    suspend fun playbackInfo(itemId: String, userId: String, body: JSONObject): JSONObject =
        post("/Items/$itemId/PlaybackInfo?userId=$userId", body)
            ?: throw IOException("Réponse PlaybackInfo vide")

    /** Segments (intro, générique, récap…) — Jellyfin 10.10+ avec un plugin fournisseur. */
    suspend fun segments(itemId: String): List<MediaSegment> = runCatching {
        get("/MediaSegments/$itemId").optJSONArray("Items").objects().mapNotNull {
            val start = it.longOrNull("StartTicks") ?: return@mapNotNull null
            val end = it.longOrNull("EndTicks") ?: return@mapNotNull null
            if (end <= start) null else MediaSegment(it.str("Type") ?: "Unknown", start.ticksToMs(), end.ticksToMs())
        }
    }.getOrDefault(emptyList())

    suspend fun episodes(seriesId: String, userId: String, seasonId: String? = null): List<EpisodeInfo> {
        val q = buildString {
            append("/Shows/").append(seriesId).append("/Episodes?userId=").append(userId)
            append("&fields=Overview")
            if (seasonId != null) append("&seasonId=").append(seasonId)
        }
        return get(q).optJSONArray("Items").objects().map { EpisodeInfo.parse(it) }
    }

    suspend fun userViews(userId: String): List<LibraryView> =
        get("/UserViews?userId=$userId").optJSONArray("Items").objects().map {
            LibraryView(
                id = it.optString("Id"),
                name = it.str("Name") ?: "Bibliothèque",
                collectionType = it.str("CollectionType"),
                primaryTag = it.optJSONObject("ImageTags")?.str("Primary"),
                serverId = it.str("ServerId"),
            )
        }

    suspend fun reportStart(body: JSONObject) { post("/Sessions/Playing", body) }
    suspend fun reportProgress(body: JSONObject) { post("/Sessions/Playing/Progress", body) }
    suspend fun reportStopped(body: JSONObject) { post("/Sessions/Playing/Stopped", body) }

    suspend fun stopEncoding(playSessionId: String) = runCatching {
        delete("/Videos/ActiveEncodings?deviceId=${Uri.encode(deviceId)}&playSessionId=${Uri.encode(playSessionId)}")
    }

    suspend fun closeLiveStream(liveStreamId: String) = runCatching {
        post("/LiveStreams/Close?liveStreamId=${Uri.encode(liveStreamId)}")
    }

    // ---------------------------------------------------------------- URLs

    fun imageUrl(itemId: String, type: String = "Primary", tag: String? = null, maxWidth: Int = 600): String =
        buildString {
            append(baseUrl).append("/Items/").append(itemId).append("/Images/").append(type)
            append("?maxWidth=").append(maxWidth).append("&quality=90")
            if (tag != null) append("&tag=").append(tag)
        }

    fun chapterImageUrl(itemId: String, index: Int, maxWidth: Int = 400): String =
        "$baseUrl/Items/$itemId/Images/Chapter/$index?maxWidth=$maxWidth&quality=85"

    fun trickplayTilePath(itemId: String, mediaSourceId: String, width: Int, tile: Int): String =
        "/Videos/$itemId/Trickplay/$width/$tile.jpg?mediaSourceId=$mediaSourceId"

    companion object {
        @delegate:SuppressLint("HardwareIds")
        val deviceId: String by lazy {
            Settings.Secure.getString(SonnetApp.instance.contentResolver, Settings.Secure.ANDROID_ID)
                ?.let { "sonnet-$it" } ?: "sonnet-android"
        }

        private fun clean(s: String) = s.replace("\"", "").replace(",", " ").ifBlank { "Android" }
    }
}
