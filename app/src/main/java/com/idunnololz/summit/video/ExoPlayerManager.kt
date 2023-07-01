package com.idunnololz.summit.video

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.SimpleExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.idunnololz.summit.preview.VideoType
import java.util.*
import kotlin.collections.HashMap

/**
 * Facilitates exoplayer reuse
 */
@SuppressLint("UnsafeOptInUsageError")
class ExoPlayerManager(
    private val context: Context
) {

    companion object {

        private val TAG = "ExoPlayerManager"

        private val managersMap = HashMap<LifecycleOwner, ExoPlayerManager>()

        private val players = LinkedList<SimpleExoPlayer>()

        private lateinit var context: Context

        /**
         * The amount to rewind a video if we are maintaining video position and transitioning screens.
         * The idea is that the transition might be laggy so rewind slightly to account for it.
         */
        const val CONVENIENCE_REWIND_TIME_MS = 500L

        fun initialize(context: Context) {
            this.context = context
        }

        fun get(lifecycleOwner: LifecycleOwner): ExoPlayerManager {
            return managersMap[lifecycleOwner] ?: ExoPlayerManager(context).also { manager ->
                Log.d(TAG, "No manager found for $lifecycleOwner. Creating one...")
                managersMap[lifecycleOwner] = manager
                lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {

                    override fun onResume(owner: LifecycleOwner) {
                        Log.d(TAG, "Fragment resumed. Restoring players")
                        managersMap[lifecycleOwner]?.restorePlayers()
                    }

                    override fun onPause(owner: LifecycleOwner) {
                        Log.d(TAG, "Fragment paused. Stopping players")
                        managersMap[lifecycleOwner]?.stop()
                    }

                    override fun onDestroy(owner: LifecycleOwner) {
                        Log.d(TAG, "Fragment destroyed. Cleaning up ExoPlayerManager instance")
                        //managersMap.remove(lifecycleOwner)?.destroy()
                        managersMap.remove(lifecycleOwner)?.viewDestroy()
                    }
                })
            }
        }

        fun destroyAll() {
            Log.d(TAG, "destroyAll()")
            managersMap.forEach { (owner, manager) ->
                manager.destroy()
            }
            managersMap.clear()

            players.forEach {
                it.release()
            }
            players.clear()
        }
    }

    private val allocatedPlayers = LinkedList<SimpleExoPlayer>()

    private val savedState = HashMap<SimpleExoPlayer, ExoPlayerConfig>()

    data class ExoPlayerConfig(
        val url: String,
        val videoType: VideoType,
        val playing: Boolean = true,
        val videoState: VideoState? = null
    )

    fun getPlayerForUrl(
        url: String,
        videoType: VideoType,
        videoState: VideoState?
    ): SimpleExoPlayer =
        getPlayer().also {
            val config = ExoPlayerConfig(
                url = url,
                videoType = videoType,
                videoState = videoState
            )
            setupPlayer(it, config)
            savedState[it] = config
        }

    fun release(player: Player?) {
        player ?: return

        Log.d(TAG, "Releasing one player.")

        player.stop()
        val removed = allocatedPlayers.remove(player)

        if (!removed) {
            Log.e(TAG, "Released a player that did not belong to this manager!")
        } else {
            players.add(player as SimpleExoPlayer)
        }
    }

    fun restorePlayers() {
        allocatedPlayers.forEach {
            val config = savedState[it] ?: return@forEach

            setupPlayer(it, config)
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping ${allocatedPlayers.size} players")
        allocatedPlayers.forEach {
            it.stop()

            val config = savedState[it] ?: return@forEach
            savedState[it] = config.copy(videoState = it.getVideoState(), playing = it.isPlaying)
        }
    }

    fun destroy() {
        Log.d(TAG, "Releasing ${allocatedPlayers.size} players")
        allocatedPlayers.forEach {
            it.release()
        }
        allocatedPlayers.clear()
    }

    fun viewDestroy() {
        Log.d(TAG, "View destroyed. Collecting ${allocatedPlayers.size} players")
        allocatedPlayers.forEach {
            it.stop()
        }
        players.addAll(allocatedPlayers)
        allocatedPlayers.clear()
    }

    fun pausePlayers() {
        allocatedPlayers.forEach {
            it.playWhenReady = false
        }
    }

    private fun getPlayer(): SimpleExoPlayer =
        if (players.isNotEmpty()) {
            players.pop()
        } else {
            SimpleExoPlayer.Builder(context)
                .build()
        }.also {
            allocatedPlayers.push(it)

            Log.d(
                TAG,
                "Allocating another player. Alloced: ${allocatedPlayers.size} Total: ${allocatedPlayers.size + players.size}"
            )
        }

    private fun setupPlayer(player: SimpleExoPlayer, config: ExoPlayerConfig) {
        // Create a data source factory.
        val dataSourceFactory: DataSource.Factory = DefaultHttpDataSource.Factory()
            .setUserAgent("summit")
        val uri = Uri.parse(config.url)
        val mediaItem = MediaItem.fromUri(uri)
        val mediaSource = when (config.videoType) {
            VideoType.UNKNOWN -> throw RuntimeException("Unknown video type")
            VideoType.DASH ->
                DashMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            VideoType.MP4 ->
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
        }

        // Prepare the player with the media source.
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true

        val videoState = config.videoState
        if (videoState != null) {
            player.seekTo(videoState.currentTime)
            player.audioComponent?.volume = videoState.volume
        } else {
            player.audioComponent?.volume = 0.0f
        }

        player.playWhenReady = config.playing
    }
}