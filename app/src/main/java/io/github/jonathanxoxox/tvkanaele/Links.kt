package io.github.jonathanxoxox.tvkanaele

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import io.github.jonathanxoxox.tvkanaele.core.IptvChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Links, die der Launcher beim Klick auf eine Kachel öffnet. Sie enthalten nur den Senderschlüssel,
 * keine Stream-Adresse, damit die Zugangsdaten nicht im TvProvider landen.
 */
object Links {
    private const val SCHEME = "tvkanaele"

    fun play(channelKey: String): Uri =
        Uri.Builder().scheme(SCHEME).authority("play").appendQueryParameter("ch", channelKey).build()

    fun openApp(): Uri = Uri.Builder().scheme(SCHEME).authority("open").build()
}

/** Startet einen Sender mit dem in den Einstellungen gewählten Player. */
object Launcher {
    private const val TAG = "Launcher"

    fun tiviMateInstalled(context: Context) = appIntent(context, Settings.TIVIMATE_PACKAGE) != null

    /** Ob TiviMate Stream-Links (ACTION_VIEW mit video/*) annimmt. Einmal am Fernseher prüfen lohnt sich. */
    fun tiviMateAcceptsLinks(context: Context): Boolean {
        val probe = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("http://example.com/live.ts"), "video/*")
            .setPackage(Settings.TIVIMATE_PACKAGE)
        return context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
    }

    fun play(context: Context, channel: IptvChannel, mode: Settings.Player = Settings(context).player): Boolean {
        val url = Uri.parse(channel.streamUrl)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(url, "video/*")
            .putExtra("title", channel.name)
            .apply { channel.userAgent?.let { putExtra("User-Agent", it) } }
        return when (mode) {
            Settings.Player.TIVIMATE_LINK ->
                start(context, Intent(view).setPackage(Settings.TIVIMATE_PACKAGE)) || builtin(context, channel)
            Settings.Player.TIVIMATE_APP ->
                appIntent(context, Settings.TIVIMATE_PACKAGE)?.let { start(context, it) } == true || builtin(context, channel)
            Settings.Player.BUILTIN -> builtin(context, channel)
            Settings.Player.EXTERNAL ->
                start(context, Intent.createChooser(view, context.getString(R.string.choose_player))) || builtin(context, channel)
        }
    }

    fun openTiviMate(context: Context): Boolean =
        appIntent(context, Settings.TIVIMATE_PACKAGE)?.let { start(context, it) } == true

    private fun builtin(context: Context, channel: IptvChannel) =
        start(context, Intent(context, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_CHANNEL, channel.key))

    private fun appIntent(context: Context, pkg: String): Intent? =
        context.packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: context.packageManager.getLaunchIntentForPackage(pkg)

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "Keine App für $intent", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "Start verweigert: $intent", e)
        false
    }
}

/** Unsichtbare Zwischenstation: nimmt den Klick aus dem Launcher an und startet den Sender. */
class PlayActivity : Activity() {
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        val key = data?.getQueryParameter("ch")
        if (data?.authority != "play" || key == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        scope.launch {
            val channel = withContext(Dispatchers.IO) { Sync.store(applicationContext).loadChannels().firstOrNull { it.key == key } }
            if (channel == null) {
                Toast.makeText(this@PlayActivity, R.string.channel_gone, Toast.LENGTH_LONG).show()
                startActivity(Intent(this@PlayActivity, MainActivity::class.java))
            } else {
                Launcher.play(this@PlayActivity, channel)
            }
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
