package tv.own.owntv.launcher

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import tv.own.owntv.R
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.epg.EpgMatcher
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.launcher.LauncherDeepLink
import tv.own.owntv.core.live.LiveEpgReader
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The app's own rows on the launcher home screen (Projectivity, Google TV, the stock launcher):
 * "Für dich" with football on now or soon, the user's teams first, and "Favoriten" with what the
 * favourite channels show right now. A tile opens the channel straight in the player. Every row,
 * core's "Zuletzt gesehen" included (see [decorateRecent]), shows the same tile: the channel logo
 * large, what is on below it.
 *
 * Written to the system TvProvider, where launchers read them. Rows are replaced as a whole, new
 * tiles first and old ones after, so a row never flashes empty.
 */
@SuppressLint("RestrictedApi")
internal class LauncherRows(private val context: Context) {

    private val resolver get() = context.contentResolver
    private val time = DateFormat.getTimeInstance(DateFormat.SHORT)

    private class Tile(
        val id: String,
        val title: String,
        val description: String,
        val poster: Uri?,
        val intent: Uri,
        val type: Int,
        val live: Boolean,
        val start: Long?,
        val end: Long?,
    )

    private class Pick(val channel: ChannelEntity, val programme: XtEpgEntry, val score: Int, val hit: FootballMatcher.Hit)

    suspend fun refresh() {
        val koin = GlobalContext.get()
        val settings = koin.get<SettingsRepository>()
        val channelDao = koin.get<ChannelDao>()
        val epgReader = koin.get<LiveEpgReader>()
        val profile = withTimeoutOrNull(15_000) {
            activeProfileSources(settings, koin.get<SourceDao>()).first { it.profileId >= 0 }
        } ?: return
        val sources = profile.sourceIdsFor(MediaType.LIVE).ifEmpty { listOf(-1L) }
        val custom = koin.get<CustomizationStore>().observe(profile.profileId, MediaType.LIVE).first()
        val offset = settings.epgOffsetMinutes.first()
        val now = System.currentTimeMillis()
        val tiles = LauncherTiles(context)

        val favorites = channelDao.snapshotFavoritesManual(profile.profileId, ContentOrderEntity.FAV_CONTEXT, sources, MAX_FAVORITES)
        val favoriteIds = favorites.map { it.id }.toSet()
        val guides = HashMap<Long, tv.own.owntv.core.live.EpgNowNext?>()
        suspend fun guide(ch: ChannelEntity) = guides.getOrPut(ch.id) { runCatching { epgReader.nowNext(ch, custom, offset) }.getOrNull() }

        // First, as it is the cheapest: at most ten channels.
        runCatching { decorateRecent(profile.profileId, channelDao, ::guide, tiles, now) }
            .onFailure { android.util.Log.w(TAG, "recent row not decorated", it) }

        // Favourites first: they also count for "Für dich", and their guide is read once.
        val favoriteTiles = favorites.map { ch ->
            val g = guide(ch)
            val current = g?.now?.takeIf { it.startMs <= now && it.stopMs > now }
            val description = listOfNotNull(
                current?.let { range(it) },
                g?.next?.let { context.getString(R.string.tvplayer_next_at, time.format(Date(it.startMs))) + " " + it.title },
            ).joinToString(SEPARATOR)
            Tile(
                id = "f:${ch.id}",
                title = current?.let { "${ch.name}: ${it.title}" } ?: ch.name,
                description = description,
                poster = tiles.tile("f:${ch.id}:${current?.startMs}", content(ch, current, now)),
                intent = playUri(ch),
                type = TvContractCompat.PreviewPrograms.TYPE_CHANNEL,
                live = current != null,
                start = current?.startMs,
                end = current?.stopMs,
            )
        }

        val sportsConfig = settings.activeSportsConfig.first()
        val matcher = FootballMatcher.from(sportsConfig, FootballSetup.useDefaultTeams(context))
        // Each name is folded once: the playlist can hold tens of thousands of channels.
        val sportsIds = HashSet<Long>()
        val scanned = channelDao.snapshotAll(sources, MAX_SCAN).filter { ch ->
            val name = FootballMatcher.fold(ch.name)
            when {
                FootballMatcher.isSportsChannelFolded(name) -> { sportsIds += ch.id; true }
                else -> FootballMatcher.isNationalChannelFolded(name)
            }
        }
        val candidates = (favorites + scanned).distinctBy { it.id }.take(MAX_SPORTS_CHANNELS)
        // Variants of one channel share a guide: by their guide id, read once, and by name, so "Sky Sport
        // Bundesliga 1", its HD, FHD, UHD and backup feeds and another country's copy are one family. A
        // variant without a guide of its own shows its family's, so every variant of a match is found.
        val byGuideId = HashMap<String, tv.own.owntv.core.live.EpgNowNext?>()
        var reads = 0
        suspend fun sharedGuide(ch: ChannelEntity): tv.own.owntv.core.live.EpgNowNext? {
            if (guides.containsKey(ch.id)) return guides[ch.id]
            val key = ch.epgChannelId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            if (key != null && byGuideId.containsKey(key)) return byGuideId[key]
            if (reads >= MAX_GUIDE_READS) return null
            reads++
            return guide(ch).also { if (key != null) byGuideId[key] = it }
        }
        val families = candidates.groupBy { EpgMatcher.normalizeForEpg(it.name).ifEmpty { it.id.toString() } }.values
        // Today's football: up to midnight, and at least the launcher row's look-ahead.
        val horizon = maxOf(now + LOOKAHEAD_MS, endOfDay(now))
        val picks = ArrayList<Pick>()
        // Variants repeat the same programmes, so each one is matched once.
        val hits = HashMap<Triple<String, String?, Boolean>, FootballMatcher.Hit?>()
        for (family in families) {
            val own = family.associate { ch -> ch.id to if (ch.epgChannelId.isNullOrBlank()) null else sharedGuide(ch) }
            var familyGuide = own.values.firstOrNull { it != null }
            for (ch in family) {
                val g = own[ch.id] ?: familyGuide ?: sharedGuide(ch)?.also { familyGuide = it } ?: continue
                val programmes = listOfNotNull(g.now, g.next) + g.upcoming.orEmpty()
                val sportsChannel = ch.id in favoriteIds || ch.id in sportsIds
                for (p in programmes.distinctBy { it.startMs }) {
                    if (p.stopMs <= now || p.startMs >= horizon) continue
                    val hit = hits.getOrPut(Triple(p.title, p.description, sportsChannel)) { matcher.match(p.title, p.description, sportsChannel) } ?: continue
                    val bonus = (if (ch.id in favoriteIds) 10 else 0) + timeScore(p, now) + qualityScore(ch.name)
                    picks += Pick(ch, p, hit.score + bonus, hit)
                }
            }
        }
        // The same match on several channels (HD, FHD, backup …) becomes one tile on the best of them.
        val groups = picks.groupBy { FootballMatcher.fold(it.programme.title) + "@" + it.programme.startMs / FIVE_MINUTES }.values
        val matches = groups.map { group -> group.maxBy { it.score } to group.map { it.channel.id }.distinct().size - 1 }
            .sortedWith(compareByDescending<Pair<Pick, Int>> { it.first.score }.thenBy { it.first.programme.startMs })
        val forYou = matches.filter { it.first.programme.startMs < now + LOOKAHEAD_MS }.take(MAX_FOR_YOU)

        // The channel group: running matches first (best first), then today's in kick-off order. Every
        // variant of a match is listed, right below each other, the best one leading.
        val groupChannels = groups
            .map { group ->
                group.filter { it.hit.kind != FootballMatcher.Kind.FOOTBALL || it.channel.id in favoriteIds || it.channel.id in sportsIds }
                    .sortedByDescending { it.score }
            }
            .filter { it.isNotEmpty() }
            .sortedWith(
                compareBy<List<Pick>> { it.first().programme.startMs > now }
                    .thenBy { if (it.first().programme.startMs <= now) -it.first().score.toLong() else it.first().programme.startMs }
                    .thenByDescending { it.first().score },
            )
            .flatten().map { it.channel.id }.distinct().take(MAX_GROUP)
        runCatching { FootballGroup.update(context, profile.profileId, groupChannels) }
            .onFailure { android.util.Log.w(TAG, "football group not updated", it) }
        runCatching { FootballReminders.clear(context, profile.profileId) }
            .onFailure { android.util.Log.w(TAG, "old football reminders not cleared", it) }

        val forYouTiles = forYou.map { (pick, alternatives) ->
            val p = pick.programme
            val running = p.startMs <= now
            val label = if (running) context.getString(R.string.tvplayer_tile_now) else time.format(Date(p.startMs))
            val more = if (alternatives > 0) SEPARATOR + context.resources.getQuantityString(R.plurals.tvplayer_more_channels, alternatives, alternatives) else ""
            Tile(
                id = "s:${pick.channel.id}:${p.startMs}",
                title = "$label: ${p.title}",
                description = pick.channel.name + SEPARATOR + range(p) + more,
                poster = tiles.tile("s:${pick.channel.id}:${p.startMs}", content(pick.channel, p, now)),
                intent = playUri(pick.channel),
                type = TvContractCompat.PreviewPrograms.TYPE_EVENT,
                live = running,
                start = p.startMs,
                end = p.stopMs,
            )
        }.ifEmpty {
            listOf(
                Tile(
                    id = "empty", title = context.getString(R.string.tvplayer_nothing_now),
                    description = context.getString(R.string.tvplayer_nothing_now_desc),
                    poster = null, intent = OPEN_APP,
                    type = TvContractCompat.PreviewPrograms.TYPE_CLIP, live = false, start = null, end = null,
                )
            )
        }

        publish(KEY_FOR_YOU, context.getString(R.string.tvplayer_row_for_you), forYouTiles, LOGO_FOR_YOU)
        if (favoriteTiles.isEmpty()) removeChannel(KEY_FAVORITES)
        else publish(KEY_FAVORITES, context.getString(R.string.tvplayer_row_favorites), favoriteTiles, LOGO_FAVORITES)
        tiles.cleanup()
    }

    /**
     * "Zuletzt gesehen" is OwnTV core's row (TvHomeRepository), published with the bare channel logo as
     * art. Its programmes keep their place, id and link; only the art and the description are replaced,
     * by the tile the other rows use, so the row also shows what is on. The core writes the bare logo
     * back when it republishes the row (on zapping, while the app is in front); the next refresh, when
     * the app is left, draws the tiles again.
     */
    private suspend fun decorateRecent(
        profileId: Long,
        channelDao: ChannelDao,
        guide: suspend (ChannelEntity) -> tv.own.owntv.core.live.EpgNowNext?,
        tiles: LauncherTiles,
        now: Long,
    ) {
        val channelId = channelIds()[coreRecentKey(profileId)] ?: return
        val programmes = ArrayList<Pair<Long, LauncherDeepLink.Live>>()
        resolver.query(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            arrayOf(TvContractCompat.PreviewPrograms._ID, TvContractCompat.PreviewPrograms.COLUMN_INTENT_URI), null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val intent = c.getString(1) ?: continue
                val link = runCatching { Intent.parseUri(intent, Intent.URI_INTENT_SCHEME).data }.getOrNull()
                (LauncherDeepLink.parse(link) as? LauncherDeepLink.Live)?.let { programmes += c.getLong(0) to it }
            }
        }
        if (programmes.isEmpty()) return
        val recent = channelDao.recentlyWatched(profileId, MAX_RECENT).first()
        for ((programmeId, link) in programmes) {
            val ch = recent.firstOrNull { it.sourceId == link.sourceId && it.remoteId == link.remoteId }
                ?: recent.firstOrNull { it.sourceId == link.sourceId && it.name == link.name }
                ?: continue
            val g = guide(ch)
            val current = g?.now?.takeIf { it.startMs <= now && it.stopMs > now }
            val poster = tiles.tile("r:${ch.id}:${current?.startMs}", content(ch, current, now)) ?: continue
            val values = ContentValues().apply {
                put(TvContractCompat.PreviewPrograms.COLUMN_POSTER_ART_URI, poster.toString())
                put(TvContractCompat.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                put(TvContractCompat.PreviewPrograms.COLUMN_SHORT_DESCRIPTION, current?.let { "${it.title}$SEPARATOR${range(it)}" } ?: ch.name)
            }
            runCatching { resolver.update(TvContractCompat.buildPreviewProgramUri(programmeId), values, null, null) }
        }
    }

    private fun content(ch: ChannelEntity, p: XtEpgEntry?, now: Long): LauncherTiles.Content {
        val running = p != null && p.startMs <= now && p.stopMs > now
        return LauncherTiles.Content(
            channelName = ch.name,
            logoUrl = ch.displayLogoUrl,
            title = p?.title,
            badge = p?.let { if (running) context.getString(R.string.tvplayer_tile_live) else startLabel(it.startMs, now) },
            live = running,
            timeRange = p?.let { range(it) },
            progress = p?.takeIf { running }?.let { (now - it.startMs).toFloat() / (it.stopMs - it.startMs).coerceAtLeast(1) },
        )
    }

    private fun range(p: XtEpgEntry) =
        context.getString(R.string.content_live_time_range_plain, time.format(Date(p.startMs)), time.format(Date(p.stopMs)))

    private fun startLabel(start: Long, now: Long): String {
        val minutes = (start - now) / 60_000
        if (minutes in 0..59) {
            val m = minutes.coerceAtLeast(1).toInt()
            return context.resources.getQuantityString(R.plurals.tvplayer_tile_in_minutes, m, m)
        }
        val day = Calendar.getInstance().apply { timeInMillis = start }.get(Calendar.DAY_OF_YEAR)
        val today = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_YEAR)
        val clock = time.format(Date(start))
        return if (day == today) clock else context.getString(R.string.tvplayer_tile_tomorrow, clock)
    }

    private fun endOfDay(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun timeScore(p: XtEpgEntry, now: Long): Int {
        val minutes = (p.startMs - now) / 60_000
        return when {
            p.startMs <= now -> 50
            minutes <= 15 -> 40
            minutes <= 60 -> 25
            else -> 10
        }
    }

    private fun qualityScore(name: String): Int {
        val words = FootballMatcher.fold(name).split(' ').toSet()
        var score = when {
            "fhd" in words || "1080p" in words -> 4
            "hd" in words -> 3
            "uhd" in words || "4k" in words -> 2
            else -> 0
        }
        if ("backup" in words) score -= 5
        return score
    }

    private fun publish(key: String, displayName: String, items: List<Tile>, logo: String) {
        val channelId = ensureChannel(key, displayName, logo)
        val oldIds = ArrayList<Long>()
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
            // No logo URI: launchers draw it small over the tile's corner, and the tile shows it large.
            t.poster?.let { builder.setPosterArtUri(it) }
            t.start?.let { builder.setStartTimeUtcMillis(it) }
            t.end?.let { builder.setEndTimeUtcMillis(it) }
            resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, builder.build().toContentValues())
        }
        oldIds.forEach { resolver.delete(TvContractCompat.buildPreviewProgramUri(it), null, null) }
    }

    private fun channelIds(): Map<String, Long> {
        val result = HashMap<String, Long>()
        resolver.query(TvContractCompat.Channels.CONTENT_URI, CHANNEL_PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val providerId = c.getString(1) ?: continue
                result[providerId] = c.getLong(0)
            }
        }
        return result
    }

    private fun removeChannel(key: String) {
        val id = channelIds()[key] ?: return
        resolver.delete(TvContractCompat.buildChannelUri(id), null, null)
    }

    private fun ensureChannel(key: String, displayName: String, logo: String): Long {
        channelIds()[key]?.let { id ->
            // Keep the name current (an older build may have used another one).
            val values = Channel.Builder().setType(TvContractCompat.Channels.TYPE_PREVIEW).setDisplayName(displayName)
                .setAppLinkIntentUri(OPEN_APP).setInternalProviderId(key).build().toContentValues()
            runCatching { resolver.update(TvContractCompat.buildChannelUri(id), values, null, null) }
            return id
        }
        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(displayName)
            .setAppLinkIntentUri(OPEN_APP)
            .setInternalProviderId(key)
            .build()
        val uri = resolver.insert(TvContractCompat.Channels.CONTENT_URI, channel.toContentValues())
            ?: error(key)
        val id = ContentUris.parseId(uri)
        ChannelLogoUtils.storeChannelLogo(context, id, rowLogo(logo))
        // Only the app's first row is shown without asking; launchers let the user switch on the rest.
        TvContractCompat.requestChannelBrowsable(context, id)
        return id
    }

    /** The row's icon: a symbol on the accent blue, drawn rather than shipped as a file. */
    private fun rowLogo(symbol: String): Bitmap {
        val size = 160
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x2F, 0x6F, 0xE4) })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 84f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        canvas.drawText(symbol, size / 2f, size / 2f - (p.descent() + p.ascent()) / 2, p)
        return bmp
    }

    companion object {
        const val KEY_FOR_YOU = "tvplayer_for_you"
        const val KEY_FAVORITES = "tvplayer_favorites"
        const val PARAM_CHANNEL = "tvplayer_channel"
        const val PARAM_SOURCE = "tvplayer_source"
        private const val SCHEME = "owntv"
        private const val HOST_OPEN = "open"
        private const val SEPARATOR = " · "
        private const val LOGO_FOR_YOU = "⚽"
        private const val LOGO_FAVORITES = "★"
        private const val MAX_FAVORITES = 40
        /** Twice core's ten: it skips hidden channels before taking its ten. */
        private const val MAX_RECENT = 20
        private const val TAG = "LauncherRows"
        private const val MAX_FOR_YOU = 15
        private const val MAX_GROUP = 250
        private const val MAX_SCAN = 20_000
        private const val MAX_SPORTS_CHANNELS = 5_000
        /** Guides read per refresh; variants that share a guide id cost one read. */
        private const val MAX_GUIDE_READS = 800
        private const val LOOKAHEAD_MS = 6 * 3_600_000L
        private const val FIVE_MINUTES = 5 * 60_000L
        private val OPEN_APP: Uri = Uri.Builder().scheme(SCHEME).authority(HOST_OPEN).build()

        private val CHANNEL_PROJECTION = arrayOf(
            TvContractCompat.Channels._ID,
            TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID,
        )

        /** The internal id core gives its "Zuletzt gesehen" row (TvHomeRepository.platformInternalId). */
        private fun coreRecentKey(profileId: Long): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("$profileId|${MediaType.LIVE.name}|recent-live".toByteArray(Charsets.UTF_8))
            return "owntv:recent_live:" + digest.joinToString("") { "%02x".format(Locale.ROOT, it) }
        }

        /** owntv://open?tvplayer_channel=…&tvplayer_source=…, read back by [channelFrom]. */
        fun playUri(ch: ChannelEntity): Uri = Uri.Builder().scheme(SCHEME).authority(HOST_OPEN)
            .appendQueryParameter(PARAM_CHANNEL, ch.id.toString())
            .appendQueryParameter(PARAM_SOURCE, ch.sourceId.toString())
            .build()

        /** The (source, channel id) a launcher tile asked for, or null for any other link. */
        fun channelFrom(uri: Uri?): Pair<Long, Long>? {
            if (uri == null || uri.scheme != SCHEME) return null
            val id = uri.getQueryParameter(PARAM_CHANNEL)?.toLongOrNull() ?: return null
            val source = uri.getQueryParameter(PARAM_SOURCE)?.toLongOrNull() ?: return null
            return source to id
        }
    }
}
