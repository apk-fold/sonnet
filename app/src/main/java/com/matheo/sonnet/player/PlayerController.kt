package com.matheo.sonnet.player

import android.content.Context
import android.graphics.Bitmap
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.SystemClock
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.matheo.sonnet.BuildConfig
import com.matheo.sonnet.data.PlayerPrefs
import com.matheo.sonnet.jellyfin.ChapterInfo
import com.matheo.sonnet.jellyfin.DeviceProfile
import com.matheo.sonnet.jellyfin.EpisodeInfo
import com.matheo.sonnet.jellyfin.ItemInfo
import com.matheo.sonnet.jellyfin.JellyfinApi
import com.matheo.sonnet.jellyfin.MediaSegment
import com.matheo.sonnet.jellyfin.MediaStreamInfo
import com.matheo.sonnet.jellyfin.PlaybackSource
import com.matheo.sonnet.jellyfin.TrickplayInfo
import com.matheo.sonnet.jellyfin.longOrNull
import com.matheo.sonnet.jellyfin.msToTicks
import com.matheo.sonnet.jellyfin.objects
import com.matheo.sonnet.jellyfin.str
import com.matheo.sonnet.jellyfin.ticksToMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException

/** Scope qui survit à l'activité : le "Stopped" doit partir même quand on ferme le lecteur. */
private val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Cerveau du lecteur Sonnet : ExoPlayer + API Jellyfin (PlaybackInfo, suivi de lecture,
 * segments intro/générique, épisodes, vignettes de recherche, pistes, qualité, minuteur…).
 * Tout l'état exposé est observable par Compose.
 */
@OptIn(UnstableApi::class)
class PlayerController(private val context: Context, initialPrefs: PlayerPrefs) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var prefs: PlayerPrefs = initialPrefs
        private set

    // ------------------------------------------------------------- ExoPlayer

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Sonnet/${BuildConfig.VERSION_NAME} (Android)")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)

    val player: ExoPlayer = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON),
    )
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory)))
        // Gros tampons (comme l'appli officielle Jellyfin) : lecture fluide des gros fichiers en réseau
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(50_000, 120_000, 2_500, 5_000)
                .build()
        )
        .setUsePlatformDiagnostics(false)
        .setSeekBackIncrementMs(initialPrefs.seekBackSec * 1000L)
        .setSeekForwardIncrementMs(initialPrefs.seekForwardSec * 1000L)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    // ---------------------------------------------------------- État exposé

    var item by mutableStateOf<ItemInfo?>(null); private set
    var source by mutableStateOf<PlaybackSource?>(null); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set

    var isPlaying by mutableStateOf(false); private set
    var buffering by mutableStateOf(false); private set
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set
    var bufferedMs by mutableLongStateOf(0L); private set
    var videoAspect by mutableFloatStateOf(16f / 9f); private set
    var videoWidth by mutableIntStateOf(0); private set
    var videoHeight by mutableIntStateOf(0); private set
    var cues by mutableStateOf<List<Cue>>(emptyList()); private set
    var speed by mutableFloatStateOf(1f); private set

    var selectedAudioIndex by mutableStateOf<Int?>(null); private set
    var selectedSubtitleIndex by mutableIntStateOf(-1); private set

    var chapters by mutableStateOf<List<ChapterInfo>>(emptyList()); private set
    var segments by mutableStateOf<List<MediaSegment>>(emptyList()); private set
    var activeSegment by mutableStateOf<MediaSegment?>(null); private set
    var toast by mutableStateOf<String?>(null); private set

    var seasonEpisodes by mutableStateOf<List<EpisodeInfo>>(emptyList()); private set
    var nextEpisode by mutableStateOf<EpisodeInfo?>(null); private set
    var previousEpisode by mutableStateOf<EpisodeInfo?>(null); private set
    var upNextVisible by mutableStateOf(false); private set
    var upNextSecondsLeft by mutableIntStateOf(0); private set

    /** Minuteur de mise en veille : 0 = désactivé, -1 = fin de l'épisode, sinon fin en elapsedRealtime. */
    var sleepTimerEnd by mutableLongStateOf(0L); private set
    var sleepRemainingMs by mutableLongStateOf(0L); private set

    var maxBitrateMbps by mutableIntStateOf(initialPrefs.maxBitrateMbps); private set
    var forceTranscode by mutableStateOf(initialPrefs.forceTranscode); private set

    val audioStreams: List<MediaStreamInfo> get() = source?.streams?.filter { it.type == "Audio" }.orEmpty()
    val subtitleStreams: List<MediaStreamInfo> get() = source?.streams?.filter { it.type == "Subtitle" }.orEmpty()
    val videoStream: MediaStreamInfo? get() = source?.streams?.firstOrNull { it.type == "Video" }

    // ------------------------------------------------------------ Interne

    private var api: JellyfinApi? = null
    private var userId: String? = null
    private var allEpisodes: List<EpisodeInfo> = emptyList()
    private var pendingTracks: Pair<Int?, Int?>? = null
    private var triedTranscodeFallback = false
    private var triedSafeFallback = false
    /** Dernier recours : transcodage H.264 + AAC stéréo, sans copie de flux, débit plafonné. */
    private var safeMode = false
    private var startReported = false
    private var lastProgressReport = 0L
    private var upNextDeadline = 0L
    private var upNextDismissedFor: String? = null
    private val skippedSegments = mutableSetOf<MediaSegment>()
    private var loudness: LoudnessEnhancer? = null
    private var tickerJob: Job? = null
    private var loadJob: Job? = null
    private var toastJob: Job? = null
    private val thumbCache = LruCache<Int, Bitmap>(6)

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            reportProgress(if (playing) "Unpause" else "Pause")
        }

        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) {
                loading = false
                refreshDuration()
            }
            if (state == Player.STATE_ENDED) onEnded()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
                videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            cues = cueGroup.cues
        }

        override fun onTracksChanged(tracks: Tracks) {
            val pending = pendingTracks ?: return
            if (tracks.groups.isEmpty()) return
            pendingTracks = null
            applyInitialTracks(pending.first, pending.second)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            applyAudioBoost(prefs.audioBoostDb)
        }

        override fun onPlayerError(e: PlaybackException) {
            val s = source
            if (s != null && s.playMethod != "Transcode" && !triedTranscodeFallback) {
                // Codec ou conteneur non lisible en direct : on retente en transcodage, sans rien demander.
                triedTranscodeFallback = true
                forceTranscode = true
                showToast("Lecture directe impossible, passage en transcodage…")
                reload(forceTranscode = true)
            } else if (s != null && !safeMode && !triedSafeFallback) {
                // Le transcodage normal échoue aussi : dernier essai en mode compatible (H.264 + AAC stéréo).
                triedSafeFallback = true
                safeMode = true
                forceTranscode = true
                showToast("Nouvel essai en mode compatible…")
                reload(forceTranscode = true)
            } else {
                error = describeError(e, s)
                loading = false
            }
        }
    }

    /** Message d'erreur lisible : code, cause, statut HTTP, méthode de lecture. Jamais l'URL ni le jeton. */
    private fun describeError(e: PlaybackException, s: PlaybackSource?): String {
        val parts = mutableListOf("Erreur de lecture : ${e.errorCodeName}")
        var cause: Throwable? = e.cause
        var depth = 0
        while (cause != null && depth < 3) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                parts += "Le serveur a répondu HTTP ${cause.responseCode}"
            } else {
                val m = cause.message?.take(140)
                if (!m.isNullOrBlank()) parts += "${cause.javaClass.simpleName} : ${m.replace(Regex("api_key=[^&\\s]+"), "api_key=…")}"
            }
            cause = cause.cause
            depth++
        }
        if (s != null) parts += "Méthode : ${s.playMethod}" + (s.container?.let { " · conteneur $it" } ?: "") + (if (safeMode) " · mode compatible" else "")
        return parts.joinToString("\n")
    }

    init {
        player.addListener(listener)
        speed = initialPrefs.defaultSpeed
    }

    // ------------------------------------------------------------- Public

    fun start(request: PlayRequest) {
        // Nouvelle vidéo lancée pendant qu'une autre joue (PiP, onNewIntent) : on clôt la précédente
        if (source != null) reportStopped()
        val sameServer = api?.baseUrl == request.server.trimEnd('/') && api?.token == request.token
        if (!sameServer) {
            api = JellyfinApi(request.server, request.token)
            userId = null
        }
        triedTranscodeFallback = false
        triedSafeFallback = false
        safeMode = false
        load(
            itemId = request.itemId,
            startMs = request.startTicks.ticksToMs(),
            mediaSourceId = request.mediaSourceId,
            audioIndex = request.audioIndex,
            subtitleIndex = request.subtitleIndex,
            sameItem = false,
            // Le web vient d'envoyer son propre "Stopped" : on le laisse passer avant notre "Playing".
            reportDelayMs = 1500L,
        )
    }

    fun updatePrefs(newPrefs: PlayerPrefs) {
        val old = prefs
        prefs = newPrefs
        if (old.audioBoostDb != newPrefs.audioBoostDb) applyAudioBoost(newPrefs.audioBoostDb)
    }

    fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
            player.play()
        } else if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceIn(0L, safeDuration())
        player.seekTo(target)
        positionMs = target
        reportProgress("TimeUpdate", force = true)
    }

    fun seekTo(ms: Long) {
        val target = ms.coerceIn(0L, safeDuration())
        player.seekTo(target)
        positionMs = target
        reportProgress("TimeUpdate", force = true)
    }

    fun changeSpeed(value: Float) {
        speed = value
        player.setPlaybackSpeed(value)
        reportProgress("PlaybackRateChange", force = true)
    }

    fun skipActiveSegment() {
        val seg = activeSegment ?: return
        skippedSegments.add(seg)
        activeSegment = null
        seekTo(seg.endMs)
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerEnd = when {
            minutes == 0 -> 0L
            minutes < 0 -> -1L
            else -> SystemClock.elapsedRealtime() + minutes * 60_000L
        }
        sleepRemainingMs = if (minutes > 0) minutes * 60_000L else 0L
        showToast(
            when {
                minutes == 0 -> "Minuteur désactivé"
                minutes < 0 -> "Pause à la fin de l'épisode"
                else -> "Pause dans $minutes min"
            }
        )
    }

    fun dismissUpNext() {
        upNextVisible = false
        upNextDismissedFor = nextEpisode?.id
    }

    fun playNext() {
        val next = nextEpisode ?: return
        playEpisode(next, fromStart = true)
    }

    fun playPrevious() {
        val prev = previousEpisode ?: return
        playEpisode(prev, fromStart = true)
    }

    fun playEpisode(episode: EpisodeInfo, fromStart: Boolean = false) {
        reportStopped()
        triedTranscodeFallback = false
        triedSafeFallback = false
        safeMode = false
        val start = if (fromStart || episode.played) 0L else episode.resumeMs
        load(episode.id, start, null, null, null, sameItem = false, reportDelayMs = 0L)
    }

    fun selectAudio(stream: MediaStreamInfo) {
        val s = source ?: return
        if (s.playMethod != "Transcode" && selectExoTrack(C.TRACK_TYPE_AUDIO, stream)) {
            selectedAudioIndex = stream.index
            reportProgress("AudioTrackChange", force = true)
            return
        }
        reload(audioIndex = stream.index)
    }

    fun selectSubtitle(stream: MediaStreamInfo?) {
        if (stream == null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            selectedSubtitleIndex = -1
            reportProgress("SubtitleTrackChange", force = true)
            return
        }
        val s = source ?: return
        val method = stream.deliveryMethod.orEmpty()
        val exoCanShow = method.equals("External", true) ||
            (s.playMethod != "Transcode" && (method.isEmpty() || method.equals("Embed", true)))
        if (exoCanShow && selectExoTrack(C.TRACK_TYPE_TEXT, stream)) {
            selectedSubtitleIndex = stream.index
            reportProgress("SubtitleTrackChange", force = true)
            return
        }
        // Sous-titres image (PGS…) en transcodage : Jellyfin les incruste
        reload(subtitleIndex = stream.index)
    }

    fun setQuality(mbps: Int, transcode: Boolean) {
        maxBitrateMbps = mbps
        forceTranscode = transcode
        reload(forceTranscode = transcode)
    }

    /** Vignette de prévisualisation (trickplay Jellyfin 10.9+) pour une position donnée. */
    suspend fun thumbnailAt(positionMs: Long): Bitmap? {
        val s = source ?: return null
        val info: TrickplayInfo = item?.trickplay?.get(s.mediaSourceId)
            ?: item?.trickplay?.values?.firstOrNull() ?: return null
        val a = api ?: return null
        if (info.thumbnailCount <= 0) return null
        val perTile = info.tileWidth * info.tileHeight
        val index = (positionMs / info.intervalMs).toInt().coerceIn(0, info.thumbnailCount - 1)
        val tile = index / perTile
        val tileBitmap = thumbCache.get(tile) ?: a.bitmap(
            a.trickplayTilePath(s.itemId, s.mediaSourceId, info.width, tile)
        )?.also { thumbCache.put(tile, it) } ?: return null
        val inTile = index % perTile
        val x = (inTile % info.tileWidth) * info.width
        val y = (inTile / info.tileWidth) * info.height
        if (x + info.width > tileBitmap.width || y + info.height > tileBitmap.height) return null
        return Bitmap.createBitmap(tileBitmap, x, y, info.width, info.height)
    }

    fun chapterImageUrl(chapter: ChapterInfo): String? {
        val a = api ?: return null
        val id = item?.id ?: return null
        return if (chapter.hasImage) a.chapterImageUrl(id, chapter.index) else null
    }

    fun episodeImageUrl(ep: EpisodeInfo): String? {
        val a = api ?: return null
        return a.imageUrl(ep.id, "Primary", ep.primaryTag, 480)
    }

    fun retry() {
        val current = item ?: return
        error = null
        triedTranscodeFallback = false
        triedSafeFallback = false
        safeMode = false
        load(current.id, player.currentPosition.coerceAtLeast(positionMs), source?.mediaSourceId,
            selectedAudioIndex, selectedSubtitleIndex, sameItem = true, reportDelayMs = 0L)
    }

    fun showToast(message: String) {
        toast = message
        toastJob?.cancel()
        toastJob = scope.launch {
            delay(2200)
            toast = null
        }
    }

    fun release() {
        reportStopped()
        tickerJob?.cancel()
        loadJob?.cancel()
        runCatching { loudness?.release() }
        loudness = null
        player.removeListener(listener)
        player.release()
        scope.cancel()
    }

    // ----------------------------------------------------------- Chargement

    private fun reload(
        audioIndex: Int? = selectedAudioIndex,
        subtitleIndex: Int? = selectedSubtitleIndex,
        forceTranscode: Boolean = this.forceTranscode,
    ) {
        val current = item ?: return
        val oldSession = source?.playSessionId
        val position = player.currentPosition
        val a = api
        if (a != null && oldSession != null && source?.playMethod == "Transcode") {
            reportScope.launch { a.stopEncoding(oldSession) }
        }
        load(current.id, position, source?.mediaSourceId, audioIndex, subtitleIndex,
            sameItem = true, reportDelayMs = 0L, forceTranscode = forceTranscode)
    }

    private fun load(
        itemId: String,
        startMs: Long,
        mediaSourceId: String?,
        audioIndex: Int?,
        subtitleIndex: Int?,
        sameItem: Boolean,
        reportDelayMs: Long,
        forceTranscode: Boolean = this.forceTranscode,
    ) {
        val a = api ?: return
        loadJob?.cancel()
        loading = true
        error = null
        if (!sameItem) {
            startReported = false
            upNextVisible = false
            activeSegment = null
            skippedSegments.clear()
            thumbCache.evictAll()
        }
        loadJob = scope.launch {
            try {
                val uid = userId ?: a.me().getString("Id").also { userId = it }
                val info = if (sameItem && item?.id == itemId) item!! else a.item(uid, itemId)
                item = info
                if (!sameItem) {
                    chapters = info.chapters
                    loadExtras(a, uid, info)
                }

                var bitrate = if (maxBitrateMbps > 0) maxBitrateMbps * 1_000_000L else 200_000_000L
                if (safeMode) bitrate = minOf(bitrate, 20_000_000L)
                val body = JSONObject()
                    .put("UserId", uid)
                    .put("StartTimeTicks", startMs.msToTicks())
                    .put("IsPlayback", true)
                    .put("AutoOpenLiveStream", true)
                    .put("MaxStreamingBitrate", bitrate)
                    .put("EnableDirectPlay", !forceTranscode && !safeMode)
                    .put("EnableDirectStream", !forceTranscode && !safeMode)
                    .put("EnableTranscoding", true)
                    .put("AllowVideoStreamCopy", !forceTranscode && !safeMode)
                    .put("AllowAudioStreamCopy", !safeMode)
                    .put("DeviceProfile", DeviceProfile.build(bitrate, safeMode))
                mediaSourceId?.let { body.put("MediaSourceId", it) }
                audioIndex?.let { body.put("AudioStreamIndex", it) }
                subtitleIndex?.let { body.put("SubtitleStreamIndex", it) }

                val response = a.playbackInfo(itemId, uid, body)
                response.str("ErrorCode")?.let { throw IOException("Jellyfin refuse la lecture ($it)") }
                val playback = pickSource(a, itemId, response, mediaSourceId, forceTranscode)
                source = playback

                httpFactory.setDefaultRequestProperties(a.headers)
                val mediaItem = buildMediaItem(a, info, playback, subtitleIndex)

                val resumeAt = if (!sameItem && startMs > 0 && prefs.resumeRewindSec > 0)
                    (startMs - prefs.resumeRewindSec * 1000L).coerceAtLeast(0L) else startMs

                pendingTracks = (audioIndex ?: playback.defaultAudioIndex) to
                    (subtitleIndex ?: playback.defaultSubtitleIndex ?: -1)
                selectedAudioIndex = audioIndex ?: playback.defaultAudioIndex
                selectedSubtitleIndex = subtitleIndex ?: playback.defaultSubtitleIndex ?: -1

                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverrides()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
                player.setMediaItem(mediaItem, resumeAt)
                player.setPlaybackSpeed(speed)
                player.prepare()
                player.playWhenReady = true
                positionMs = resumeAt
                startTicker()

                if (!startReported) {
                    startReported = true
                    reportScope.launch {
                        delay(reportDelayMs)
                        runCatching { a.reportStart(progressBody("TimeUpdate", resumeAt)) }
                    }
                } else {
                    reportProgress("TimeUpdate", force = true)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                loading = false
                error = e.message ?: "Impossible de lancer la lecture"
            }
        }
    }

    private fun pickSource(
        a: JellyfinApi,
        itemId: String,
        response: JSONObject,
        wantedSourceId: String?,
        forceTranscode: Boolean,
    ): PlaybackSource {
        val sources = response.optJSONArray("MediaSources").objects()
        val ms = sources.firstOrNull { it.str("Id") == wantedSourceId } ?: sources.firstOrNull()
            ?: throw IOException("Aucune source vidéo disponible")
        val msId = ms.str("Id") ?: itemId
        val playSession = response.str("PlaySessionId")
        val liveStreamId = ms.str("LiveStreamId")
        val streams = ms.optJSONArray("MediaStreams").objects().map { MediaStreamInfo.parse(it) }
        val transcodingUrl = ms.str("TranscodingUrl")
        val canDirect = !forceTranscode &&
            (ms.optBoolean("SupportsDirectPlay", false) || ms.optBoolean("SupportsDirectStream", false))

        val supportsPlay = ms.optBoolean("SupportsDirectPlay", false)
        val supportsStream = ms.optBoolean("SupportsDirectStream", false)
        val protocol = ms.str("Protocol")
        val container = ms.str("Container")
        val key = "&api_key=" + Uri.encode(a.token)
        val common = buildString {
            append("&mediaSourceId=").append(Uri.encode(msId))
            append("&deviceId=").append(Uri.encode(JellyfinApi.deviceId))
            playSession?.let { append("&playSessionId=").append(Uri.encode(it)) }
            liveStreamId?.let { append("&liveStreamId=").append(Uri.encode(it)) }
            ms.str("ETag")?.let { append("&Tag=").append(Uri.encode(it)) }
        }

        val url: String
        var isHls = false
        val method: String
        if (!forceTranscode && supportsPlay && protocol.equals("Http", true) && !ms.str("Path").isNullOrBlank()) {
            // Source distante (ex. flux HTTP) : on lit directement son adresse
            url = ms.str("Path")!!
            method = "DirectPlay"
        } else if (!forceTranscode && supportsPlay) {
            url = a.url("/Videos/$itemId/stream?static=true") + common + key
            method = "DirectPlay"
        } else if (!forceTranscode && supportsStream && !container.isNullOrBlank()) {
            // Remux côté serveur : pas de "static", conteneur explicite
            url = a.url("/Videos/$itemId/stream.${container.substringBefore(',')}") + "?" + common.removePrefix("&") + key
            method = "DirectStream"
        } else if (transcodingUrl != null) {
            val t = a.url(transcodingUrl)
            url = if (t.contains("api_key=", true)) t else t + (if (t.contains('?')) "&" else "?") + key.removePrefix("&")
            isHls = transcodingUrl.contains(".m3u8", ignoreCase = true) ||
                ms.str("TranscodingSubProtocol").equals("hls", ignoreCase = true)
            method = "Transcode"
        } else {
            url = a.url("/Videos/$itemId/stream?static=true") + common + key
            method = "DirectPlay"
        }
        return PlaybackSource(
            itemId = itemId,
            mediaSourceId = msId,
            playSessionId = playSession,
            url = url,
            isHls = isHls,
            playMethod = method,
            container = ms.str("Container"),
            streams = streams,
            defaultAudioIndex = ms.optInt("DefaultAudioStreamIndex", -1).takeIf { it >= 0 },
            defaultSubtitleIndex = if (ms.has("DefaultSubtitleStreamIndex") && !ms.isNull("DefaultSubtitleStreamIndex"))
                ms.optInt("DefaultSubtitleStreamIndex") else null,
            liveStreamId = liveStreamId,
            bitrate = ms.longOrNull("Bitrate"),
        )
    }

    private fun buildMediaItem(a: JellyfinApi, info: ItemInfo, s: PlaybackSource, selectedSub: Int?): MediaItem {
        val subtitleConfigs = s.streams
            .filter { it.type == "Subtitle" && it.deliveryMethod.equals("External", true) && it.deliveryUrl != null }
            .map { st ->
                val deliveryUrl = st.deliveryUrl!!
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(a.url(deliveryUrl)))
                    .setId("jf-${st.index}")
                    .setMimeType(subtitleMime(deliveryUrl, st.codec))
                    .setLanguage(st.language)
                    .setLabel(st.title)
                    .setSelectionFlags(if (st.index == selectedSub) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            }
        val metadata = MediaMetadata.Builder()
            .setTitle(info.displayTitle)
            .setSubtitle(info.displaySubtitle)
            .setArtist(info.displaySubtitle.ifBlank { null })
            .setArtworkUri(Uri.parse(a.imageUrl(info.id, "Primary", null, 600)))
            .build()
        return MediaItem.Builder()
            .setUri(s.url)
            .setMediaId(s.itemId)
            .setMimeType(if (s.isHls) MimeTypes.APPLICATION_M3U8 else null)
            .setSubtitleConfigurations(subtitleConfigs)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun subtitleMime(url: String, codec: String?): String {
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        val key = ext.ifEmpty { codec?.lowercase().orEmpty() }
        return when (key) {
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    private fun loadExtras(a: JellyfinApi, uid: String, info: ItemInfo) {
        segments = emptyList()
        seasonEpisodes = emptyList()
        nextEpisode = null
        previousEpisode = null
        scope.launch {
            val fromApi = a.segments(info.id)
            segments = fromApi.ifEmpty { segmentsFromChapters(info) }
        }
        if (info.isEpisode) {
            scope.launch {
                runCatching {
                    val seriesId = info.seriesId!!
                    if (allEpisodes.none { it.id == info.id }) {
                        allEpisodes = a.episodes(seriesId, uid)
                    }
                    val idx = allEpisodes.indexOfFirst { it.id == info.id }
                    nextEpisode = allEpisodes.getOrNull(idx + 1).takeIf { idx >= 0 }
                    previousEpisode = allEpisodes.getOrNull(idx - 1).takeIf { idx > 0 }
                    seasonEpisodes = allEpisodes.filter { it.seasonId != null && it.seasonId == info.seasonId }
                        .ifEmpty { allEpisodes }
                }
            }
        } else {
            allEpisodes = emptyList()
        }
    }

    /** Sans plugin de segments : on devine l'intro / le générique à partir des chapitres. */
    private fun segmentsFromChapters(info: ItemInfo): List<MediaSegment> {
        val intro = Regex("(?i)^(intro|opening|op|générique|generique|ouverture)\\b")
        val outro = Regex("(?i)^(outro|ending|ed|credits|crédits|générique de fin|generique de fin)\\b")
        val list = info.chapters
        return list.mapIndexedNotNull { i, c ->
            val end = list.getOrNull(i + 1)?.startMs ?: info.runtimeMs
            val type = when {
                outro.containsMatchIn(c.name.trim()) -> "Outro"
                intro.containsMatchIn(c.name.trim()) -> "Intro"
                else -> null
            }
            if (type != null && end > c.startMs) MediaSegment(type, c.startMs, end) else null
        }
    }

    // ------------------------------------------------------------- Pistes

    private fun applyInitialTracks(audioIndex: Int?, subtitleIndex: Int?) {
        val s = source ?: return
        if (audioIndex != null && s.playMethod != "Transcode") {
            audioStreams.firstOrNull { it.index == audioIndex }?.let { selectExoTrack(C.TRACK_TYPE_AUDIO, it) }
        }
        if (subtitleIndex == null || subtitleIndex < 0) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            selectedSubtitleIndex = -1
        } else {
            val st = subtitleStreams.firstOrNull { it.index == subtitleIndex }
            if (st != null) {
                val method = st.deliveryMethod.orEmpty()
                if (method.equals("External", true) || s.playMethod != "Transcode") {
                    selectExoTrack(C.TRACK_TYPE_TEXT, st)
                }
            }
        }
    }

    private fun selectExoTrack(type: Int, stream: MediaStreamInfo): Boolean {
        val groups = player.currentTracks.groups.filter { it.type == type }
        if (groups.isEmpty()) return false
        val tag = "jf-${stream.index}"
        fun Tracks.Group.isSideLoaded(): Boolean =
            mediaTrackGroup.id.contains("jf-") || (0 until length).any { getTrackFormat(it).id?.contains("jf-") == true }

        val target: Tracks.Group? = if (stream.deliveryMethod.equals("External", true)) {
            groups.firstOrNull { g ->
                g.mediaTrackGroup.id.contains(tag) || (0 until g.length).any { g.getTrackFormat(it).id?.contains(tag) == true }
            }
        } else {
            val embedded = groups.filter { !it.isSideLoaded() }
            val containerId = (stream.index + 1).toString()
            embedded.firstOrNull { g ->
                (0 until g.length).any {
                    val id = g.getTrackFormat(it).id
                    id == containerId || id?.endsWith(":$containerId") == true
                }
            } ?: run {
                val sameType = (if (type == C.TRACK_TYPE_AUDIO) audioStreams else subtitleStreams)
                    .filter { !it.isExternal }
                    .sortedBy { it.index }
                val pos = sameType.indexOfFirst { it.index == stream.index }
                if (pos >= 0 && sameType.size == embedded.size) embedded[pos] else null
            }
        }
        if (target == null) return false
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(target.mediaTrackGroup, 0))
            .build()
        return true
    }

    // -------------------------------------------------------- Boucle d'état

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                tick()
                delay(250)
            }
        }
    }

    private fun tick() {
        positionMs = player.currentPosition
        bufferedMs = player.bufferedPosition
        refreshDuration()
        val pos = positionMs

        // Intro / générique / récap
        val seg = segments.firstOrNull { pos >= it.startMs && pos < it.endMs - 800 }
        if (seg != null && seg !in skippedSegments) {
            when (prefs.skipSegmentsMode) {
                PlayerPrefs.SKIP_AUTO -> {
                    skippedSegments.add(seg)
                    showToast(seg.skippedLabel)
                    player.seekTo(seg.endMs)
                }
                PlayerPrefs.SKIP_BUTTON -> activeSegment = seg
                else -> activeSegment = null
            }
        } else {
            activeSegment = null
        }

        // Épisode suivant
        val next = nextEpisode
        val duration = durationMs
        if (next != null && prefs.autoplayNext && duration > 0 && upNextDismissedFor != next.id) {
            val inOutro = seg?.type == "Outro"
            val nearEnd = duration - pos <= maxOf(prefs.nextCountdownSec * 1000L + 5_000L, 25_000L)
            if ((inOutro || nearEnd) && !upNextVisible && player.playWhenReady) {
                upNextVisible = true
                upNextDeadline = SystemClock.elapsedRealtime() + prefs.nextCountdownSec * 1000L
            }
            if (upNextVisible) {
                val left = ((upNextDeadline - SystemClock.elapsedRealtime()) / 1000L).toInt().coerceAtLeast(0)
                upNextSecondsLeft = left
                if (left == 0 && player.isPlaying && sleepTimerEnd != -1L) {
                    upNextVisible = false
                    playNext()
                    return
                }
                if (!player.isPlaying) upNextDeadline += 250
            }
        } else if (upNextVisible && (next == null || duration - pos > 60_000L)) {
            upNextVisible = false
        }

        // Minuteur de veille
        val end = sleepTimerEnd
        if (end > 0) {
            val left = end - SystemClock.elapsedRealtime()
            sleepRemainingMs = left.coerceAtLeast(0L)
            if (left <= 0) {
                sleepTimerEnd = 0L
                player.pause()
                showToast("Minuteur terminé — bonne nuit")
            }
        }

        // Suivi Jellyfin toutes les 10 s
        if (player.isPlaying) reportProgress("TimeUpdate")
    }

    private fun onEnded() {
        upNextVisible = false
        if (sleepTimerEnd == -1L) {
            sleepTimerEnd = 0L
            showToast("Minuteur terminé — bonne nuit")
            reportStopped()
            return
        }
        val next = nextEpisode
        if (next != null && prefs.autoplayNext && upNextDismissedFor != next.id) {
            playNext()
        } else {
            reportStopped()
        }
    }

    private fun refreshDuration() {
        val d = player.duration
        durationMs = if (d != C.TIME_UNSET && d > 0) d else item?.runtimeMs ?: 0L
    }

    private fun safeDuration(): Long {
        val d = durationMs
        return if (d > 0) d else Long.MAX_VALUE / 2
    }

    private fun applyAudioBoost(db: Int) {
        runCatching { loudness?.release() }
        loudness = null
        if (db <= 0) return
        val session = player.audioSessionId
        if (session == C.AUDIO_SESSION_ID_UNSET) return
        loudness = runCatching {
            LoudnessEnhancer(session).apply {
                setTargetGain(db * 100)
                enabled = true
            }
        }.getOrNull()
    }

    // ------------------------------------------------------- Suivi serveur

    private fun progressBody(event: String?, position: Long = player.currentPosition): JSONObject {
        val s = source
        return JSONObject().apply {
            put("ItemId", s?.itemId ?: item?.id)
            put("MediaSourceId", s?.mediaSourceId)
            put("PlaySessionId", s?.playSessionId)
            put("PositionTicks", position.msToTicks())
            put("IsPaused", !player.playWhenReady)
            put("IsMuted", false)
            put("CanSeek", true)
            put("PlayMethod", s?.playMethod ?: "DirectPlay")
            put("PlaybackRate", speed.toDouble())
            selectedAudioIndex?.let { put("AudioStreamIndex", it) }
            put("SubtitleStreamIndex", selectedSubtitleIndex)
            s?.liveStreamId?.let { put("LiveStreamId", it) }
            event?.let { put("EventName", it) }
        }
    }

    private fun reportProgress(event: String, force: Boolean = false) {
        val a = api ?: return
        if (source == null || !startReported) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastProgressReport < 10_000L) return
        lastProgressReport = now
        val body = progressBody(event)
        reportScope.launch { runCatching { a.reportProgress(body) } }
    }

    private fun reportStopped() {
        val a = api ?: return
        val s = source ?: return
        if (!startReported) return
        startReported = false
        val body = JSONObject()
            .put("ItemId", s.itemId)
            .put("MediaSourceId", s.mediaSourceId)
            .put("PlaySessionId", s.playSessionId)
            .put("PositionTicks", player.currentPosition.msToTicks())
        s.liveStreamId?.let { body.put("LiveStreamId", it) }
        reportScope.launch {
            runCatching { a.reportStopped(body) }
            s.liveStreamId?.let { a.closeLiveStream(it) }
        }
    }
}
