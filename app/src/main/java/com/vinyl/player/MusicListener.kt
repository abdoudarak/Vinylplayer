package com.vinyl.player

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService

class MusicListener : NotificationListenerService() {
    private var msm: MediaSessionManager? = null
    private var ctrl: MediaController? = null
    private val cn by lazy { ComponentName(this, MusicListener::class.java) }

    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = push()
        override fun onPlaybackStateChanged(state: PlaybackState?) = push()
        override fun onSessionDestroyed() = pick(msm?.getActiveSessions(cn))
    }
    private val sl = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    override fun onListenerConnected() {
        msm = getSystemService(MediaSessionManager::class.java)
        msm?.addOnActiveSessionsChangedListener(sl, cn)
        pick(msm?.getActiveSessions(cn))
    }

    override fun onListenerDisconnected() {
        msm?.removeOnActiveSessionsChangedListener(sl)
        ctrl?.unregisterCallback(cb)
    }

    private fun pick(list: List<MediaController>?) {
        ctrl?.unregisterCallback(cb)
        ctrl = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        ctrl?.registerCallback(cb)
        Media.controller = ctrl
        push()
    }

    private fun push() {
        val c = ctrl ?: run { Media.track.value = null; return }
        val m = c.metadata ?: run { Media.track.value = null; return }
        val s = c.playbackState
        val cover = m.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        Media.track.value = Track(
            title = m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown",
            artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            cover = cover,
            durationMs = m.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = s?.position ?: 0L,
            stamp = s?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            playing = s?.state == PlaybackState.STATE_PLAYING
        )
    }
}
