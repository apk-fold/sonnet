package com.matheo.sonnet.jellyfin

import org.json.JSONArray
import org.json.JSONObject

/** 1 tick Jellyfin = 100 ns. */
const val TICKS_PER_MS = 10_000L

fun Long.ticksToMs(): Long = this / TICKS_PER_MS
fun Long.msToTicks(): Long = this * TICKS_PER_MS

/** optString renvoie "null" pour une valeur JSON null : on normalise. */
fun JSONObject.str(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

fun JSONObject.intOrNull(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name)

fun JSONObject.longOrNull(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name)

fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}

data class MediaStreamInfo(
    val index: Int,
    val type: String,
    val codec: String?,
    val language: String?,
    val title: String,
    val isExternal: Boolean,
    val isDefault: Boolean,
    val isForced: Boolean,
    val isTextSubtitle: Boolean,
    val deliveryMethod: String?,
    val deliveryUrl: String?,
    val channels: Int?,
    val width: Int?,
    val height: Int?,
    val bitrate: Long?,
    val videoRange: String?,
) {
    companion object {
        fun parse(o: JSONObject): MediaStreamInfo {
            val type = o.str("Type") ?: "Unknown"
            val lang = o.str("Language")
            val title = o.str("DisplayTitle")
                ?: listOfNotNull(o.str("Title"), lang, o.str("Codec")?.uppercase()).joinToString(" · ")
                    .ifEmpty { "Piste ${o.optInt("Index")}" }
            return MediaStreamInfo(
                index = o.optInt("Index", -1),
                type = type,
                codec = o.str("Codec"),
                language = lang,
                title = title,
                isExternal = o.optBoolean("IsExternal", false),
                isDefault = o.optBoolean("IsDefault", false),
                isForced = o.optBoolean("IsForced", false),
                isTextSubtitle = o.optBoolean("IsTextSubtitleStream", false),
                deliveryMethod = o.str("DeliveryMethod"),
                deliveryUrl = o.str("DeliveryUrl"),
                channels = o.intOrNull("Channels"),
                width = o.intOrNull("Width"),
                height = o.intOrNull("Height"),
                bitrate = o.longOrNull("BitRate"),
                videoRange = o.str("VideoRangeType") ?: o.str("VideoRange"),
            )
        }
    }
}

data class MediaSegment(val type: String, val startMs: Long, val endMs: Long) {
    val label: String
        get() = when (type) {
            "Intro" -> "Passer l'intro"
            "Outro" -> "Passer le générique"
            "Recap" -> "Passer le récap"
            "Preview" -> "Passer l'aperçu"
            "Commercial" -> "Passer la pub"
            else -> "Passer"
        }

    val skippedLabel: String
        get() = when (type) {
            "Intro" -> "Intro passée"
            "Outro" -> "Générique passé"
            "Recap" -> "Récap passé"
            "Preview" -> "Aperçu passé"
            "Commercial" -> "Pub passée"
            else -> "Segment passé"
        }
}

data class ChapterInfo(val name: String, val startMs: Long, val index: Int, val hasImage: Boolean)

data class TrickplayInfo(
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbnailCount: Int,
    val intervalMs: Long,
)

data class EpisodeInfo(
    val id: String,
    val name: String,
    val indexNumber: Int?,
    val seasonNumber: Int?,
    val seasonId: String?,
    val runtimeMs: Long,
    val played: Boolean,
    val playedPercentage: Float,
    val resumeMs: Long,
    val primaryTag: String?,
    val overview: String?,
) {
    val code: String
        get() = buildString {
            if (seasonNumber != null) append("S").append(seasonNumber)
            if (indexNumber != null) append("E").append(indexNumber)
        }

    companion object {
        fun parse(o: JSONObject): EpisodeInfo {
            val userData = o.optJSONObject("UserData")
            return EpisodeInfo(
                id = o.optString("Id"),
                name = o.str("Name") ?: "Épisode",
                indexNumber = o.intOrNull("IndexNumber"),
                seasonNumber = o.intOrNull("ParentIndexNumber"),
                seasonId = o.str("SeasonId"),
                runtimeMs = (o.longOrNull("RunTimeTicks") ?: 0L).ticksToMs(),
                played = userData?.optBoolean("Played", false) ?: false,
                playedPercentage = (userData?.optDouble("PlayedPercentage", 0.0) ?: 0.0).toFloat(),
                resumeMs = (userData?.longOrNull("PlaybackPositionTicks") ?: 0L).ticksToMs(),
                primaryTag = o.optJSONObject("ImageTags")?.str("Primary"),
                overview = o.str("Overview"),
            )
        }
    }
}

data class ItemInfo(
    val id: String,
    val name: String,
    val type: String,
    val seriesName: String?,
    val seriesId: String?,
    val seasonId: String?,
    val indexNumber: Int?,
    val seasonNumber: Int?,
    val productionYear: Int?,
    val overview: String?,
    val runtimeMs: Long,
    val chapters: List<ChapterInfo>,
    /** mediaSourceId -> meilleure résolution de vignettes disponible */
    val trickplay: Map<String, TrickplayInfo>,
    val hasBackdrop: Boolean,
) {
    val isEpisode: Boolean get() = type == "Episode" && seriesId != null

    val displayTitle: String
        get() = if (isEpisode) seriesName ?: name else name

    val displaySubtitle: String
        get() = when {
            isEpisode -> buildString {
                if (seasonNumber != null) append("S").append(seasonNumber)
                if (indexNumber != null) append("E").append(indexNumber)
                if (isNotEmpty()) append(" · ")
                append(name)
            }
            productionYear != null -> productionYear.toString()
            else -> ""
        }

    companion object {
        fun parse(o: JSONObject): ItemInfo {
            val chapters = o.optJSONArray("Chapters").objects().mapIndexed { i, c ->
                ChapterInfo(
                    name = c.str("Name") ?: "Chapitre ${i + 1}",
                    startMs = (c.longOrNull("StartPositionTicks") ?: 0L).ticksToMs(),
                    index = i,
                    hasImage = c.str("ImageTag") != null,
                )
            }
            val trick = mutableMapOf<String, TrickplayInfo>()
            o.optJSONObject("Trickplay")?.let { all ->
                all.keys().forEach { sourceId ->
                    val byWidth = all.optJSONObject(sourceId) ?: return@forEach
                    var best: TrickplayInfo? = null
                    byWidth.keys().forEach { w ->
                        val t = byWidth.optJSONObject(w) ?: return@forEach
                        val info = TrickplayInfo(
                            width = t.optInt("Width"),
                            height = t.optInt("Height"),
                            tileWidth = t.optInt("TileWidth", 1).coerceAtLeast(1),
                            tileHeight = t.optInt("TileHeight", 1).coerceAtLeast(1),
                            thumbnailCount = t.optInt("ThumbnailCount"),
                            intervalMs = t.optLong("Interval", 10_000L).coerceAtLeast(1L),
                        )
                        // On vise ~320 px de large : net sans être lourd
                        val current = best
                        if (info.width > 0 && (current == null ||
                                kotlin.math.abs(info.width - 320) < kotlin.math.abs(current.width - 320))
                        ) best = info
                    }
                    best?.let { trick[sourceId] = it }
                }
            }
            return ItemInfo(
                id = o.optString("Id"),
                name = o.str("Name") ?: "Sans titre",
                type = o.str("Type") ?: "Video",
                seriesName = o.str("SeriesName"),
                seriesId = o.str("SeriesId"),
                seasonId = o.str("SeasonId"),
                indexNumber = o.intOrNull("IndexNumber"),
                seasonNumber = o.intOrNull("ParentIndexNumber"),
                productionYear = o.intOrNull("ProductionYear"),
                overview = o.str("Overview"),
                runtimeMs = (o.longOrNull("RunTimeTicks") ?: 0L).ticksToMs(),
                chapters = chapters,
                trickplay = trick,
                hasBackdrop = (o.optJSONArray("BackdropImageTags")?.length() ?: 0) > 0,
            )
        }
    }
}

/** Source choisie pour la lecture, après PlaybackInfo. */
data class PlaybackSource(
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String?,
    val url: String,
    val isHls: Boolean,
    /** "DirectPlay" | "DirectStream" | "Transcode" */
    val playMethod: String,
    val container: String?,
    val streams: List<MediaStreamInfo>,
    val defaultAudioIndex: Int?,
    val defaultSubtitleIndex: Int?,
    val liveStreamId: String?,
    val bitrate: Long?,
)

data class LibraryView(
    val id: String,
    val name: String,
    val collectionType: String?,
    val primaryTag: String?,
    val serverId: String?,
)
