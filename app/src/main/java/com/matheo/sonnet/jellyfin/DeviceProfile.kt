package com.matheo.sonnet.jellyfin

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaFormat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Profil d'appareil envoyé à Jellyfin (même démarche que l'appli officielle Jellyfin Android) :
 *  - les décodeurs vidéo réellement présents sur le téléphone (HEVC, AV1, VP9… + leurs profils) ;
 *  - les décodeurs audio logiciels FFmpeg embarqués (AC3, E-AC3, DTS, TrueHD, FLAC, Opus…) ;
 *  - uniquement les conteneurs qu'ExoPlayer sait réellement lire (pas d'AVI, par exemple).
 * Le serveur peut ainsi lire un maximum de fichiers en "direct play" et ne transcode que si nécessaire.
 */
object DeviceProfile {

    // ---------------------------------------------------------------- Décodeurs de l'appareil

    private class Dev(val video: Map<String, Set<String>>, val audio: Set<String>)

    private val dev: Dev by lazy {
        val video = HashMap<String, MutableSet<String>>()
        val audio = HashSet<String>()
        runCatching {
            for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
                if (info.isEncoder) continue
                for (mime in info.supportedTypes) {
                    val vName = videoName(mime)
                    val aName = audioName(mime)
                    when {
                        vName != null -> {
                            val set = video.getOrPut(vName) { HashSet() }
                            runCatching {
                                for (pl in info.getCapabilitiesForType(mime).profileLevels) {
                                    videoProfile(vName, pl.profile)?.let(set::add)
                                }
                            }
                        }
                        aName != null -> audio += aName
                    }
                }
            }
        }
        Dev(video, audio)
    }

    private fun videoName(mime: String): String? = when (mime) {
        MediaFormat.MIMETYPE_VIDEO_MPEG2 -> "mpeg2video"
        MediaFormat.MIMETYPE_VIDEO_H263 -> "h263"
        MediaFormat.MIMETYPE_VIDEO_MPEG4 -> "mpeg4"
        MediaFormat.MIMETYPE_VIDEO_AVC -> "h264"
        MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION -> "hevc"
        MediaFormat.MIMETYPE_VIDEO_VP8 -> "vp8"
        MediaFormat.MIMETYPE_VIDEO_VP9 -> "vp9"
        MediaFormat.MIMETYPE_VIDEO_AV1 -> "av1"
        else -> null
    }

    private fun audioName(mime: String): String? = when (mime) {
        MediaFormat.MIMETYPE_AUDIO_AAC -> "aac"
        MediaFormat.MIMETYPE_AUDIO_AC3 -> "ac3"
        MediaFormat.MIMETYPE_AUDIO_EAC3 -> "eac3"
        MediaFormat.MIMETYPE_AUDIO_FLAC -> "flac"
        MediaFormat.MIMETYPE_AUDIO_MPEG -> "mp3"
        MediaFormat.MIMETYPE_AUDIO_OPUS -> "opus"
        MediaFormat.MIMETYPE_AUDIO_VORBIS -> "vorbis"
        else -> null
    }

    private fun videoProfile(codec: String, p: Int): String? = when (codec) {
        "h264" -> when (p) {
            CodecProfileLevel.AVCProfileBaseline -> "baseline"
            CodecProfileLevel.AVCProfileMain -> "main"
            CodecProfileLevel.AVCProfileExtended -> "extended"
            CodecProfileLevel.AVCProfileHigh -> "high"
            CodecProfileLevel.AVCProfileHigh10 -> "high 10"
            CodecProfileLevel.AVCProfileHigh422 -> "high 422"
            CodecProfileLevel.AVCProfileHigh444 -> "high 444"
            CodecProfileLevel.AVCProfileConstrainedBaseline -> "constrained baseline"
            CodecProfileLevel.AVCProfileConstrainedHigh -> "constrained high"
            else -> null
        }
        "hevc" -> when (p) {
            CodecProfileLevel.HEVCProfileMain -> "Main"
            CodecProfileLevel.HEVCProfileMain10 -> "Main 10"
            CodecProfileLevel.HEVCProfileMain10HDR10 -> "Main 10 HDR 10"
            CodecProfileLevel.HEVCProfileMain10HDR10Plus -> "Main 10 HDR 10 Plus"
            CodecProfileLevel.HEVCProfileMainStill -> "Main Still"
            else -> null
        }
        "vp9" -> when (p) {
            CodecProfileLevel.VP9Profile0 -> "Profile 0"
            CodecProfileLevel.VP9Profile1 -> "Profile 1"
            CodecProfileLevel.VP9Profile2, CodecProfileLevel.VP9Profile2HDR -> "Profile 2"
            CodecProfileLevel.VP9Profile3, CodecProfileLevel.VP9Profile3HDR -> "Profile 3"
            else -> null
        }
        else -> null
    }

    // ---------------------------------------------------------------- Ce qu'ExoPlayer sait lire

    /** Conteneurs lus par ExoPlayer, avec les codecs vidéo / audio qu'on y accepte. */
    private class Container(val name: String, val video: List<String>, val audio: List<String>)

    private val pcm = listOf("pcm_s8", "pcm_s16be", "pcm_s16le", "pcm_s24le", "pcm_s32le", "pcm_f32le", "pcm_alaw", "pcm_mulaw")

    /**
     * Audio lu SANS le matériel du téléphone : décodeurs logiciels FFmpeg embarqués dans l'appli
     * (AC3, E-AC3, DTS, TrueHD, ALAC, FLAC…) — toujours annoncés, comme le fait l'appli officielle.
     */
    private val forcedAudio = pcm + listOf("aac", "ac3", "eac3", "dts", "mlp", "truehd", "alac", "flac", "mp3", "mp2", "mp1", "opus", "vorbis")

    private val containers = listOf(
        Container("mp4", listOf("mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1", "vp9"), listOf("mp1", "mp2", "mp3", "aac", "alac", "ac3", "eac3", "opus", "flac")),
        Container("mkv", listOf("mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1", "vp8", "vp9"), pcm + listOf("mp1", "mp2", "mp3", "aac", "vorbis", "opus", "flac", "alac", "ac3", "eac3", "dts", "mlp", "truehd")),
        Container("webm", listOf("vp8", "vp9", "av1"), listOf("vorbis", "opus")),
        Container("mpegts", listOf("mpeg2video", "mpeg4", "h264", "hevc"), pcm + listOf("mp1", "mp2", "mp3", "aac", "ac3", "eac3", "dts", "mlp", "truehd")),
        Container("flv", listOf("mpeg4", "h264"), listOf("mp3", "aac")),
        Container("3gp", listOf("h263", "mpeg4", "h264", "hevc"), listOf("aac", "flac")),
    )

    private val audioOnly = listOf(
        "mp3" to listOf("mp3"), "ogg" to listOf("vorbis", "opus", "flac"), "wav" to pcm,
        "aac" to listOf("aac"), "flac" to listOf("flac"), "m4a" to listOf("aac", "alac", "mp3"), "opus" to listOf("opus"),
    )

    private fun videoOk(c: String) = c == "h264" || dev.video.containsKey(c)
    private fun audioOk(c: String) = c in forcedAudio || c in dev.audio

    val videoCodecs: List<String> by lazy {
        containers.flatMap { it.video }.distinct().filter(::videoOk)
    }
    val audioCodecs: List<String> by lazy {
        (containers.flatMap { it.audio } + audioOnly.flatMap { it.second }).distinct().filter(::audioOk)
    }

    // ---------------------------------------------------------------- Profil

    /**
     * @param safe mode de dernier recours : aucun direct play, transcodage H.264 + AAC stéréo.
     */
    fun build(maxBitrateBps: Long, safe: Boolean = false): JSONObject {
        val directPlay = JSONArray()
        val containerProfiles = JSONArray()
        val codecProfiles = JSONArray()

        if (!safe) {
            for (c in containers) {
                val v = c.video.filter(::videoOk)
                val a = c.audio.filter(::audioOk)
                if (v.isEmpty()) continue
                directPlay.put(
                    JSONObject().put("Type", "Video").put("Container", c.name)
                        .put("VideoCodec", v.joinToString(",")).put("AudioCodec", a.joinToString(","))
                )
                containerProfiles.put(JSONObject().put("Type", "Video").put("Container", c.name))
                // restreint chaque codec vidéo aux profils que le décodeur matériel gère réellement (HEVC Main 10…)
                for (codec in v) {
                    val profiles = dev.video[codec].orEmpty()
                    if (profiles.isEmpty()) continue
                    codecProfiles.put(
                        JSONObject().put("Type", "Video").put("Container", c.name).put("Codec", codec)
                            .put("ApplyConditions", JSONArray())
                            .put(
                                "Conditions", JSONArray().put(
                                    JSONObject().put("Condition", "EqualsAny").put("Property", "VideoProfile")
                                        .put("Value", profiles.joinToString("|")).put("IsRequired", false)
                                )
                            )
                    )
                }
            }
            for ((name, codecs) in audioOnly) {
                val a = codecs.filter(::audioOk)
                if (a.isEmpty()) continue
                directPlay.put(JSONObject().put("Type", "Audio").put("Container", name).put("AudioCodec", a.joinToString(",")))
                containerProfiles.put(JSONObject().put("Type", "Audio").put("Container", name))
            }
        }

        val transcodeAudio = if (safe) "aac" else "aac,mp3,ac3,eac3"
        val transcoding = JSONArray()
            .put(
                JSONObject()
                    .put("Type", "Video").put("Container", "ts").put("Protocol", "hls").put("Context", "Streaming")
                    .put("VideoCodec", "h264").put("AudioCodec", transcodeAudio)
                    .put("MaxAudioChannels", if (safe) "2" else "6")
                    .put("MinSegments", 1).put("BreakOnNonKeyFrames", true)
            )
            .put(
                JSONObject()
                    .put("Type", "Audio").put("Container", "mp3").put("Protocol", "http").put("Context", "Streaming")
                    .put("AudioCodec", "mp3")
            )

        val subtitles = JSONArray()
        // Intégrés au fichier : lus directement par ExoPlayer
        listOf("srt", "subrip", "ass", "ssa", "vtt", "webvtt", "pgssub", "pgs", "dvdsub", "dvbsub", "mov_text", "ttml")
            .forEach { subtitles.put(JSONObject().put("Format", it).put("Method", "Embed")) }
        // Fichiers externes / sous-titres texte pendant un transcodage
        listOf("srt", "subrip", "vtt", "webvtt", "ass", "ssa", "ttml")
            .forEach { subtitles.put(JSONObject().put("Format", it).put("Method", "External")) }

        return JSONObject()
            .put("Name", "Sonnet (Android)")
            .put("MaxStreamingBitrate", maxBitrateBps)
            .put("MaxStaticBitrate", maxBitrateBps)
            .put("MusicStreamingTranscodingBitrate", 384_000)
            .put("DirectPlayProfiles", directPlay)
            .put("TranscodingProfiles", transcoding)
            .put("ContainerProfiles", containerProfiles)
            .put("CodecProfiles", codecProfiles)
            .put("SubtitleProfiles", subtitles)
    }

    fun describe(): String =
        "Vidéo : ${videoCodecs.joinToString(", ")}\nAudio : ${audioCodecs.joinToString(", ")}\nConteneurs : ${containers.joinToString(", ") { it.name }}"
}
