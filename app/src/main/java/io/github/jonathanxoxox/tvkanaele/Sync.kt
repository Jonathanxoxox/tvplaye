package io.github.jonathanxoxox.tvkanaele

import android.content.Context
import android.util.Log
import android.util.Xml
import io.github.jonathanxoxox.tvkanaele.core.ChannelMapper
import io.github.jonathanxoxox.tvkanaele.core.Guide
import io.github.jonathanxoxox.tvkanaele.core.GuideStore
import io.github.jonathanxoxox.tvkanaele.core.IptvChannel
import io.github.jonathanxoxox.tvkanaele.core.M3uParser
import io.github.jonathanxoxox.tvkanaele.core.Matcher
import io.github.jonathanxoxox.tvkanaele.core.Programme
import io.github.jonathanxoxox.tvkanaele.core.Suggestion
import io.github.jonathanxoxox.tvkanaele.core.Text
import io.github.jonathanxoxox.tvkanaele.core.XmltvParser
import io.github.jonathanxoxox.tvkanaele.core.XtreamParser
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Ablauf einer Aktualisierung:
 * 1. alle 6 Stunden (oder auf Knopfdruck) Playlist und EPG laden,
 * 2. den EPG nach Favoriten und Interessen filtern (auch nach jeder Änderung der Einstellungen),
 * 3. Vorschläge berechnen und als Kanäle auf den Startbildschirm schreiben (alle 15 Minuten).
 */
object Sync {
    private const val TAG = "Sync"
    private const val FULL_INTERVAL = 6 * 3_600_000L
    private const val HOUR = 3_600_000L
    private const val MAX_SUGGESTIONS = 15
    private const val MAX_WATCH_NEXT = 3

    private val lock = Any()

    fun store(context: Context) = GuideStore(File(context.filesDir, "guide"))
    private fun epgDir(context: Context) = File(context.filesDir, "epg")

    fun run(context: Context, forceDownload: Boolean): String = synchronized(lock) {
        val settings = Settings(context)
        if (!settings.isConfigured) return "Noch keine Playlist eingetragen"
        val store = store(context)
        val parts = mutableListOf<String>()

        val now = System.currentTimeMillis()
        if (forceDownload || now - settings.lastFullSync > FULL_INTERVAL || store.loadChannels().isEmpty()) {
            val channels = downloadAll(context, settings)
            store.saveChannels(channels)
            settings.lastFullSync = now
            settings.needsReparse = true
            parts += "${channels.size} Sender"
        }
        if (settings.needsReparse) {
            val count = reparse(context, settings, store)
            settings.needsReparse = false
            parts += "$count Sendungen gemerkt"
        }
        parts += publish(context, settings, store.load())
        return parts.joinToString(", ")
    }

    private fun downloadAll(context: Context, settings: Settings): List<IptvChannel> {
        val ua = settings.userAgent
        val epgUrls = mutableListOf<String>()
        val channels = when (settings.source) {
            Settings.Source.M3U -> {
                val playlist = Http.getText(settings.m3uUrl, ua).lineSequence().let { M3uParser.parse(it) }
                epgUrls += playlist.epgUrls
                playlist.channels
            }
            Settings.Source.XTREAM -> {
                val account = settings.xtreamAccount()
                val streams = Http.getText(account.liveStreamsUrl(), ua)
                if (!streams.trimStart().startsWith("[")) throw IllegalStateException("Xtream-Anmeldung abgelehnt")
                val categories = runCatching { Http.getText(account.liveCategoriesUrl(), ua) }.getOrNull()
                epgUrls += account.epgUrl()
                XtreamParser.parse(account, streams, categories)
            }
        }
        if (channels.isEmpty()) throw IllegalStateException("Die Playlist enthält keine Sender")

        val manual = Text.splitList(settings.epgUrl)
        val urls = (manual.ifEmpty { epgUrls }).distinct()
        val dir = epgDir(context).apply { mkdirs() }
        val keep = mutableSetOf<String>()
        var failures = 0
        urls.forEachIndexed { i, url ->
            val file = File(dir, "epg_$i.xml")
            try {
                Http.download(url, file, ua)
                keep += file.name
            } catch (e: Exception) {
                Log.w(TAG, "EPG ${redact(url)} nicht geladen", e)
                failures++
                if (file.exists()) keep += file.name // alten Stand behalten
            }
        }
        dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        if (urls.isNotEmpty() && failures == urls.size && keep.isEmpty()) {
            Log.w(TAG, "Kein EPG verfügbar")
        }
        return channels
    }

    /** Filtert die gespeicherten EPG-Dateien: alles von Favoriten, sonst nur passende Sendungen. */
    private fun reparse(context: Context, settings: Settings, store: GuideStore): Int {
        val channels = store.loadChannels()
        val matcher = Matcher(settings.interests())
        val favorites = settings.favorites.toSet()
        val now = System.currentTimeMillis()
        val from = now - 6 * HOUR
        val to = now + 30 * HOUR

        val names = mutableMapOf<String, List<String>>()
        val programmes = mutableListOf<Programme>()
        // Die Zuordnung Sender → EPG braucht die Kanalnamen aus dem EPG; die stehen vor den Sendungen,
        // deshalb wird beim Lesen erst gefiltert, sobald ein Kanal bekannt ist.
        epgDir(context).listFiles()?.sortedBy { it.name }?.forEach { file ->
            try {
                openMaybeGzip(file).use { input ->
                    var favoriteEpgIds: Set<String>? = null
                    var mappedEpgIds: Set<String>? = null
                    val parser = XmltvParser(Xml.newPullParser())
                    val result = parser.parse(input, from, to) { p ->
                        if (mappedEpgIds == null) {
                            val map = ChannelMapper.map(channels, names + parser.namesSoFar())
                            mappedEpgIds = map.values.toSet()
                            favoriteEpgIds = favorites.mapNotNull { map[it] }.toSet()
                        }
                        p.epgChannel in mappedEpgIds!! &&
                            (p.epgChannel in favoriteEpgIds!! || matcher.match(p) != null)
                    }
                    names += result.channelNames
                    programmes += result.programmes
                }
            } catch (e: Exception) {
                Log.w(TAG, "EPG ${file.name} unlesbar", e)
            }
        }
        val map = ChannelMapper.map(channels, names)
        store.saveGuide(map, programmes.filter { it.epgChannel in map.values })
        return programmes.size
    }

    private fun openMaybeGzip(file: File): InputStream {
        val input = BufferedInputStream(file.inputStream(), 64 * 1024)
        input.mark(2)
        val gz = input.read() == 0x1f && input.read() == 0x8b
        input.reset()
        return if (gz) BufferedInputStream(GZIPInputStream(input, 64 * 1024)) else input
    }

    fun suggestions(settings: Settings, guide: Guide, now: Long = System.currentTimeMillis()): List<Suggestion> {
        val matcher = Matcher(settings.interests())
        val favorites = settings.favorites.toSet()
        val preferred = settings.interests().preferredChannels
        val soon = guide.suggestions(matcher, favorites, preferred, now, settings.lookaheadHours * HOUR, MAX_SUGGESTIONS)
        if (soon.isNotEmpty()) return soon
        // Nichts Passendes in den nächsten Stunden: die nächsten Treffer des Tages zeigen.
        return guide.suggestions(matcher, favorites, preferred, now, 24 * HOUR, 5)
    }

    private fun publish(context: Context, settings: Settings, guide: Guide): String {
        val now = System.currentTimeMillis()
        val publisher = ChannelPublisher(context)
        val tiles = TileRenderer(context, enabled = settings.drawTiles)

        val suggestions = suggestions(settings, guide, now)
        publisher.publishSuggestions(suggestions, tiles, now)

        val favorites = guide.favorites(settings.favorites, now)
        if (favorites.isEmpty()) publisher.removeChannel(ChannelPublisher.KEY_FAVORITES)
        else publisher.publishFavorites(favorites, tiles, now)

        if (settings.watchNextEnabled) {
            publisher.syncWatchNext(
                suggestions.filter { it.programme.isRunning(now) && it.score >= Matcher.TEAM }.take(MAX_WATCH_NEXT),
                tiles, now,
            )
        } else {
            publisher.syncWatchNext(emptyList(), tiles, now)
        }
        tiles.cleanup()
        return "${suggestions.size} Vorschläge, ${favorites.size} Favoriten"
    }
}
