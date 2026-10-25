package io.github.jonathanxoxox.tvkanaele

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import io.github.jonathanxoxox.tvkanaele.core.FavoriteNow
import io.github.jonathanxoxox.tvkanaele.core.Suggestion
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Schreibt die Zeilen "Für dich" und "Favoriten" sowie "Als Nächstes ansehen" in den TvProvider.
 * Launcher wie der Projectivity Launcher oder Google TV lesen sie von dort.
 */
@SuppressLint("RestrictedApi")
class ChannelPublisher(private val context: Context) {

    private val resolver get() = context.contentResolver

    private class Tile(
        val id: String,
        val title: String,
        val description: String,
        val poster: Uri?,
        val logo: Uri?,
        val intent: Uri,
        val type: Int,
        val live: Boolean,
        val start: Long?,
        val end: Long?,
    )

    @WorkerThread
    fun publishSuggestions(suggestions: List<Suggestion>, tiles: TileRenderer, now: Long) {
        val channelId = ensureChannel(KEY_FOR_YOU, context.getString(R.string.channel_for_you), R.drawable.channel_for_you)
        val items = suggestions.map { s ->
            val p = s.programme
            val running = p.isRunning(now)
            val label = if (running) context.getString(R.string.now_prefix) else TIME.format(p.start)
            val extra = if (s.alternatives > 0) " · " + context.getString(R.string.more_channels, s.alternatives) else ""
            Tile(
                id = "s:${s.channel.key}:${p.start}",
                title = "$label: ${p.title}",
                description = "${s.channel.name} · ${TIME.format(p.start)}–${TIME.format(p.end)} · ${s.reasons.joinToString(", ")}$extra",
                poster = tiles.tile("s:${s.channel.key}:${p.start}", s.channel, p, null, now),
                logo = s.channel.logo?.let { Uri.parse(it) },
                intent = Links.play(s.channel.key),
                type = TvContractCompat.PreviewPrograms.TYPE_EVENT,
                live = running,
                start = p.start,
                end = p.end,
            )
        }.ifEmpty {
            listOf(
                Tile(
                    id = "empty", title = context.getString(R.string.nothing_now),
                    description = context.getString(R.string.nothing_now_desc),
                    poster = null, logo = null, intent = Links.openApp(),
                    type = TvContractCompat.PreviewPrograms.TYPE_CLIP, live = false, start = null, end = null,
                )
            )
        }
        replacePrograms(channelId, items)
    }

    @WorkerThread
    fun publishFavorites(favorites: List<FavoriteNow>, tiles: TileRenderer, now: Long) {
        val channelId = ensureChannel(KEY_FAVORITES, context.getString(R.string.channel_favorites), R.drawable.channel_favorites)
        val items = favorites.map { f ->
            val current = f.now
            val description = listOfNotNull(
                current?.let { "${TIME.format(it.start)}–${TIME.format(it.end)}" },
                f.next?.let { context.getString(R.string.next_prefix, TIME.format(it.start), it.title) },
            ).joinToString(" · ")
            Tile(
                id = "f:${f.channel.key}",
                title = current?.let { "${f.channel.name}: ${it.title}" } ?: f.channel.name,
                description = description,
                poster = tiles.tile("f:${f.channel.key}", f.channel, current, null, now),
                logo = f.channel.logo?.let { Uri.parse(it) },
                intent = Links.play(f.channel.key),
                type = TvContractCompat.PreviewPrograms.TYPE_CHANNEL,
                live = current != null,
                start = current?.start,
                end = current?.end,
            )
        }
        replacePrograms(channelId, items)
    }

    @WorkerThread
    fun removeChannel(key: String) {
        val id = channelIds()[key] ?: return
        resolver.delete(TvContractCompat.buildChannelUri(id), null, null)
    }

    @WorkerThread
    fun channelIds(): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        resolver.query(TvContractCompat.Channels.CONTENT_URI, CHANNEL_PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val providerId = c.getString(1) ?: continue
                result[providerId] = c.getLong(0)
            }
        }
        return result
    }

    private fun ensureChannel(key: String, displayName: String, logoRes: Int): Long {
        channelIds()[key]?.let { return it }
        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(displayName)
            .setAppLinkIntentUri(Links.openApp())
            .setInternalProviderId(key)
            .build()
        val uri = resolver.insert(TvContractCompat.Channels.CONTENT_URI, channel.toContentValues())
            ?: throw IllegalStateException("Kanal $key konnte nicht angelegt werden")
        val id = ContentUris.parseId(uri)
        BitmapFactory.decodeResource(context.resources, logoRes)?.let { ChannelLogoUtils.storeChannelLogo(context, id, it) }
        // Wirkt nur beim ersten Kanal einer App ohne Rückfrage; weitere Kanäle im Launcher einschalten.
        TvContractCompat.requestChannelBrowsable(context, id)
        return id
    }

    private fun replacePrograms(channelId: Long, items: List<Tile>) {
        val oldIds = mutableListOf<Long>()
        resolver.query(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            arrayOf(TvContractCompat.PreviewPrograms._ID), null, null, null,
        )?.use { c -> while (c.moveToNext()) oldIds += c.getLong(0) }

        items.forEachIndexed { index, t ->
            val builder = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(t.type)
                .setTitle(t.title)
                .setDescription(t.description)
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                .setIntentUri(t.intent)
                .setInternalProviderId(t.id)
                .setContentId(t.id)
                .setLive(t.live)
                .setWeight(items.size - index)
            t.poster?.let { builder.setPosterArtUri(it) }
            t.logo?.let { builder.setLogoUri(it) }
            t.start?.let { builder.setStartTimeUtcMillis(it) }
            t.end?.let { builder.setEndTimeUtcMillis(it) }
            resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, builder.build().toContentValues())
        }
        // Erst nach dem Einfügen löschen, damit die Zeile nie kurz leer aussieht.
        oldIds.forEach { resolver.delete(TvContractCompat.buildPreviewProgramUri(it), null, null) }
    }

    /**
     * Legt die wichtigsten laufenden Spiele in "Als Nächstes ansehen". Was der Nutzer dort
     * entfernt hat, kommt nicht wieder; vorbei Gelaufenes verschwindet.
     */
    @WorkerThread
    fun syncWatchNext(suggestions: List<Suggestion>, tiles: TileRenderer, now: Long) {
        data class Existing(val rowId: Long, val browsable: Boolean)

        val existing = mutableMapOf<String, Existing>()
        resolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, WATCH_NEXT_PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val providerId = c.getString(1)
                if (providerId == null) {
                    resolver.delete(TvContractCompat.buildWatchNextProgramUri(c.getLong(0)), null, null)
                    continue
                }
                existing[providerId] = Existing(c.getLong(0), c.getInt(2) != 0)
            }
        }

        val wanted = mutableSetOf<String>()
        suggestions.forEachIndexed { index, s ->
            val p = s.programme
            val id = "w:${s.channel.key}:${p.start}"
            wanted += id
            val old = existing[id]
            if (old != null && !old.browsable) return@forEachIndexed
            val program = WatchNextProgram.Builder()
                .setType(TvContractCompat.WatchNextPrograms.TYPE_EVENT)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEW)
                .setLastEngagementTimeUtcMillis(now - index * 60_000L)
                .setTitle("${context.getString(R.string.now_prefix)}: ${p.title}")
                .setDescription(s.channel.name)
                .setPosterArtAspectRatio(TvContractCompat.WatchNextPrograms.ASPECT_RATIO_16_9)
                .setIntentUri(Links.play(s.channel.key))
                .setInternalProviderId(id)
                .setContentId(id)
                .setLive(true)
                .setStartTimeUtcMillis(p.start)
                .setEndTimeUtcMillis(p.end)
                .apply { tiles.tile(id, s.channel, p, null, now)?.let { setPosterArtUri(it) } }
                .build()
            if (old == null) {
                resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())
            } else {
                resolver.update(TvContractCompat.buildWatchNextProgramUri(old.rowId), program.toContentValues(), null, null)
            }
        }
        // Vorbei Gelaufenes entfernen. Vom Nutzer Ausgeblendetes bleibt als Merker, solange das Spiel läuft.
        existing.filterKeys { it !in wanted }.values.forEach {
            resolver.delete(TvContractCompat.buildWatchNextProgramUri(it.rowId), null, null)
        }
    }

    companion object {
        const val KEY_FOR_YOU = "for_you"
        const val KEY_FAVORITES = "favorites"
        private val TIME = SimpleDateFormat("HH:mm", Locale.GERMANY)

        private val CHANNEL_PROJECTION = arrayOf(
            TvContractCompat.Channels._ID,
            TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID,
        )
        private val WATCH_NEXT_PROJECTION = arrayOf(
            TvContractCompat.WatchNextPrograms._ID,
            TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
            TvContractCompat.WatchNextPrograms.COLUMN_BROWSABLE,
        )
    }
}
