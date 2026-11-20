package com.vinyl.player

import android.graphics.Bitmap
import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

data class Track(
    val title: String, val artist: String, val cover: Bitmap?,
    val durationMs: Long, val positionMs: Long, val stamp: Long, val playing: Boolean
)

object Media {
    val track = MutableStateFlow<Track?>(null)
    var controller: MediaController? = null
    fun play() = controller?.transportControls?.play()
    fun pause() = controller?.transportControls?.pause()
    fun next() = controller?.transportControls?.skipToNext()
    fun prev() = controller?.transportControls?.skipToPrevious()
}

object Lrc {
    private val re = Regex("""\[(\d+):(\d+(?:\.\d+)?)](.*)""")
    fun parse(s: String): List<Pair<Long, String>> = s.lines().mapNotNull {
        re.matchEntire(it.trim())?.let { m ->
            val t = ((m.groupValues[1].toLong() * 60 + m.groupValues[2].toDouble()) * 1000).toLong()
            t to m.groupValues[3].trim()
        }
    }

    fun fetch(title: String, artist: String, durMs: Long): List<Pair<Long, String>> = try {
        fun e(s: String) = URLEncoder.encode(s, "UTF-8")
        val u = "https://lrclib.net/api/get?track_name=${e(title)}&artist_name=${e(artist)}" +
            if (durMs > 0) "&duration=${durMs / 1000}" else ""
        val c = URL(u).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "VinylPlayer/1.0")
        c.connectTimeout = 8000; c.readTimeout = 8000
        if (c.responseCode != 200) emptyList()
        else parse(JSONObject(c.inputStream.bufferedReader().readText()).optString("syncedLyrics", ""))
    } catch (e: Exception) { emptyList() }
}
