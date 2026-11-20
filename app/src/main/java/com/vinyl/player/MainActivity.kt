package com.vinyl.player

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate as dsRotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Root() }
    }
}

@Composable
fun Root() {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            enabled = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
            delay(1000)
        }
    }
    val track by Media.track.collectAsState()
    Box(Modifier.fillMaxSize().background(Color(0xFF07050C)), contentAlignment = Alignment.Center) {
        when {
            !enabled -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text("Allow notification access so the app can see what's playing.",
                    color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Text("Open settings", color = Color.Black, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(Color(0xFFD38CFF))
                        .clickable { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                        .padding(horizontal = 28.dp, vertical = 14.dp))
            }
            track == null -> Text("Play a song in Spotify, YouTube Music...", color = Color.Gray)
            else -> PlayerScreen(track!!)
        }
    }
}

@Composable
fun PlayerScreen(t: Track) {
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(t) {
        while (true) {
            pos = if (t.playing) t.positionMs + (SystemClock.elapsedRealtime() - t.stamp) else t.positionMs
            delay(150)
        }
    }
    val lyrics by produceState(emptyList<Pair<Long, String>>(), t.title, t.artist) {
        value = emptyList()
        value = withContext(Dispatchers.IO) { Lrc.fetch(t.title, t.artist, t.durationMs) }
    }
    val glow = remember(t.title, t.cover) {
        t.cover?.let { Color(Palette.from(it).generate().getVibrantColor(0xFF9B30FF.toInt())) } ?: Color(0xFF9B30FF)
    }
    val idx = lyrics.indexOfLast { it.first <= pos }
    val cur = if (idx >= 0) lyrics[idx].second else ""
    val nxt = lyrics.getOrNull(idx + 1)?.second ?: ""

    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(t.playing) {
        var last = 0L
        while (t.playing) withFrameNanos { n ->
            if (last != 0L) angle = (angle + (n - last) / 1e9f * 36f) % 360f
            last = n
        }
    }
    val arm by animateFloatAsState(if (t.playing) 0f else -28f, label = "arm")

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(glow.copy(alpha = .55f), Color.Transparent), radius = 1100f,
                center = Offset(300f, 700f))))
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(glow.copy(alpha = .30f), Color.Transparent), radius = 900f,
                center = Offset(900f, 1800f))))
        Column(Modifier.fillMaxSize().padding(24.dp).statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.size(320.dp)) {
                Box(Modifier.size(290.dp).align(Alignment.BottomStart).rotate(angle).clip(CircleShape)) {
                    if (t.cover != null) Image(t.cover.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Box(Modifier.fillMaxSize().background(glow))
                    Canvas(Modifier.fillMaxSize()) {
                        for (i in 1..6) drawCircle(Color.Black.copy(alpha = .10f), size.minDimension / 2 * (1 - i * .1f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                        drawCircle(Color(0xFFE6B3FF), size.minDimension * .09f)
                        drawCircle(Color.Black, size.minDimension * .02f)
                    }
                }
                Canvas(Modifier.fillMaxSize()) {
                    val pivot = Offset(size.width * .93f, size.height * .06f)
                    dsRotate(arm, pivot) {
                        val head = Offset(size.width * .60f, size.height * .50f)
                        drawLine(Color.White, pivot, head, strokeWidth = 7f, cap = StrokeCap.Round)
                        drawCircle(Color.White, 16f, head)
                    }
                    drawCircle(Color.White, 22f, pivot)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(t.artist.uppercase(), color = glow.copy(alpha = 1f).compositeOver(Color.White), fontSize = 12.sp, letterSpacing = 2.sp)
            Text(t.title, color = Color(0xFFF0CCFF), fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Text(cur.ifBlank { if (lyrics.isEmpty()) "" else "♪" }, color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(nxt, color = Color.White.copy(alpha = .4f), fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            val frac = if (t.durationMs > 0) (pos.toFloat() / t.durationMs).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .15f))) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color(0xFFD38CFF)))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(pos), color = Color.Gray, fontSize = 11.sp)
                Text(fmt(t.durationMs), color = Color.Gray, fontSize = 11.sp)
            }
            Row(Modifier.padding(vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("⏮", color = Color.White, fontSize = 26.sp, modifier = Modifier.clickable { Media.prev() })
                Text(if (t.playing) "⏸" else "▶", color = Color.White, fontSize = 30.sp,
                    modifier = Modifier.clip(CircleShape).background(Color(0xFFB04CFF))
                        .clickable { if (t.playing) Media.pause() else Media.play() }.padding(18.dp))
                Text("⏭", color = Color.White, fontSize = 26.sp, modifier = Modifier.clickable { Media.next() })
            }
        }
    }
}

private fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }
