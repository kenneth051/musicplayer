package com.example.musicplayer.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.content.IntentCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.musicplayer.MainActivity

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    // Media3 only detects up to a double click (play/pause -> skip next) on the headset
    // button; triple click has to be counted ourselves to trigger the "previous" action.
    private val clickHandler = Handler(Looper.getMainLooper())
    private var pendingClickCount = 0
    private val resolveClicksRunnable = Runnable { resolveHeadsetClicks() }

    @OptIn(UnstableApi::class)
    private fun resolveHeadsetClicks() {
        val player = mediaSession?.player as? SmartForwardingPlayer
        val clickCount = pendingClickCount
        pendingClickCount = 0
        if (player == null) return

        when (clickCount) {
            1 -> if (player.playWhenReady) player.pause() else player.play()
            2 -> player.seekToNextMediaItem()
            else -> player.processPreviousAction()
        }
    }

    private fun registerHeadsetClick() {
        clickHandler.removeCallbacks(resolveClicksRunnable)
        pendingClickCount++
        clickHandler.postDelayed(resolveClicksRunnable, MULTI_CLICK_TIMEOUT_MS)
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val basePlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        val player = SmartForwardingPlayer(basePlayer)

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        setMediaNotificationProvider(notificationProvider)

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val availablePlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .build()

                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailablePlayerCommands(availablePlayerCommands)
                        .build()
                }

                @OptIn(UnstableApi::class)
                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    val keyEvent = IntentCompat.getParcelableExtra(
                        intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java
                    )
                    val isHeadsetKey = keyEvent != null &&
                        (keyEvent.keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                            keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

                    // Long-press repeats aren't part of click counting; let Media3 handle those.
                    if (!isHeadsetKey || keyEvent!!.repeatCount != 0) {
                        return false
                    }

                    registerHeadsetClick()
                    return true
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        clickHandler.removeCallbacks(resolveClicksRunnable)
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player?.playWhenReady == false || player?.mediaItemCount == 0) {
            stopSelf()
        }
    }

    private companion object {
        // Slightly more generous than the system double-tap timeout to leave room for a third click.
        const val MULTI_CLICK_TIMEOUT_MS = 500L
    }
}
