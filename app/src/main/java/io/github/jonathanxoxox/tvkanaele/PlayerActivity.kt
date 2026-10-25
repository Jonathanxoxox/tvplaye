package io.github.jonathanxoxox.tvkanaele

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import io.github.jonathanxoxox.tvkanaele.core.Guide
import io.github.jonathanxoxox.tvkanaele.core.IptvChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Einfacher eingebauter Player (Media3/ExoPlayer) für HLS und MPEG-TS.
 * Hoch/Runter schaltet durch die Favoriten, OK zeigt, was läuft.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {
    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var info: TextView

    private var guide: Guide? = null
    private var zapList: List<IptvChannel> = emptyList()
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        view = findViewById(R.id.player_view)
        info = findViewById(R.id.player_info)
        view.useController = false

        val key = intent.getStringExtra(EXTRA_CHANNEL)
        scope.launch {
            val g = withContext(Dispatchers.IO) { Sync.store(applicationContext).load() }
            guide = g
            val current = g.channelsByKey[key]
            if (current == null) {
                finish()
                return@launch
            }
            val favorites = Settings(this@PlayerActivity).favorites.mapNotNull { g.channelsByKey[it] }
            zapList = if (favorites.any { it.key == current.key }) favorites else listOf(current) + favorites
            index = zapList.indexOfFirst { it.key == current.key }.coerceAtLeast(0)
            play(zapList[index])
        }
    }

    private fun play(channel: IptvChannel) {
        player?.release()
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(channel.userAgent ?: Settings(this).userAgent.ifBlank { Http.DEFAULT_USER_AGENT })
            .apply { channel.referrer?.let { setDefaultRequestProperties(mapOf("Referer" to it)) } }
        val p = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
        player = p
        view.player = p
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                showInfo(getString(R.string.player_error, channel.name, error.errorCodeName), 8_000)
            }
        })
        p.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        p.playWhenReady = true
        p.prepare()
        showInfo(describe(channel), 4_000)
    }

    private fun describe(channel: IptvChannel): String {
        val now = System.currentTimeMillis()
        val fav = guide?.favorites(listOf(channel.key), now)?.firstOrNull()
        val lines = mutableListOf(channel.name)
        fav?.now?.let { lines += "${TIME.format(it.start)}–${TIME.format(it.end)}  ${it.title}" }
        fav?.next?.let { lines += getString(R.string.next_prefix, TIME.format(it.start), it.title) }
        return lines.joinToString("\n")
    }

    private fun showInfo(text: String, millis: Long) {
        info.text = text
        info.visibility = View.VISIBLE
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ info.visibility = View.GONE }, millis)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> zap(-1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> zap(1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO ->
                zapList.getOrNull(index)?.let { showInfo(describe(it), 5_000) }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun zap(step: Int) {
        if (zapList.size < 2) return
        index = (index + step + zapList.size) % zapList.size
        play(zapList[index])
    }

    override fun onStop() {
        super.onStop()
        // Live-TV im Hintergrund weiterlaufen zu lassen, verschwendet nur Bandbreite.
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CHANNEL = "channel"
        private val TIME = SimpleDateFormat("HH:mm", Locale.GERMANY)
    }
}
