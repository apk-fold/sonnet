package com.matheo.sonnet.player

import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** Ce que l'interface web nous transmet quand tu appuies sur "Lecture". */
data class PlayRequest(
    val server: String,
    val token: String,
    val itemId: String,
    val startTicks: Long,
    val mediaSourceId: String?,
    val audioIndex: Int?,
    val subtitleIndex: Int?,
) {
    fun toIntent(context: Context): Intent = Intent(context, PlayerActivity::class.java).apply {
        putExtra(EXTRA_SERVER, server)
        putExtra(EXTRA_TOKEN, token)
        putExtra(EXTRA_ITEM, itemId)
        putExtra(EXTRA_START, startTicks)
        mediaSourceId?.let { putExtra(EXTRA_SOURCE, it) }
        audioIndex?.let { putExtra(EXTRA_AUDIO, it) }
        subtitleIndex?.let { putExtra(EXTRA_SUB, it) }
    }

    companion object {
        private const val EXTRA_SERVER = "sonnet.server"
        private const val EXTRA_TOKEN = "sonnet.token"
        private const val EXTRA_ITEM = "sonnet.item"
        private const val EXTRA_START = "sonnet.start"
        private const val EXTRA_SOURCE = "sonnet.source"
        private const val EXTRA_AUDIO = "sonnet.audio"
        private const val EXTRA_SUB = "sonnet.sub"

        fun fromIntent(intent: Intent?): PlayRequest? {
            if (intent == null) return null
            val server = intent.getStringExtra(EXTRA_SERVER) ?: return null
            val token = intent.getStringExtra(EXTRA_TOKEN) ?: return null
            val item = intent.getStringExtra(EXTRA_ITEM) ?: return null
            return PlayRequest(
                server = server,
                token = token,
                itemId = item,
                startTicks = intent.getLongExtra(EXTRA_START, 0L),
                mediaSourceId = intent.getStringExtra(EXTRA_SOURCE),
                audioIndex = if (intent.hasExtra(EXTRA_AUDIO)) intent.getIntExtra(EXTRA_AUDIO, -1) else null,
                subtitleIndex = if (intent.hasExtra(EXTRA_SUB)) intent.getIntExtra(EXTRA_SUB, -1) else null,
            )
        }

        /** JSON envoyé par le script injecté dans Jellyfin web. */
        fun fromJson(json: String, fallbackServer: String): PlayRequest? = runCatching {
            val o = JSONObject(json)
            val token = o.optString("token").takeIf { it.isNotBlank() && it != "null" } ?: return null
            val item = o.optString("itemId").takeIf { it.isNotBlank() && it != "null" } ?: return null
            val server = o.optString("server").takeIf { it.startsWith("http") } ?: fallbackServer
            PlayRequest(
                server = server.trimEnd('/'),
                token = token,
                itemId = item,
                startTicks = o.optLong("startTicks", 0L).coerceAtLeast(0L),
                mediaSourceId = o.optString("mediaSourceId").takeIf { it.isNotBlank() && it != "null" },
                audioIndex = if (o.has("audio") && !o.isNull("audio")) o.optInt("audio") else null,
                subtitleIndex = if (o.has("sub") && !o.isNull("sub")) o.optInt("sub") else null,
            )
        }.getOrNull()
    }
}
