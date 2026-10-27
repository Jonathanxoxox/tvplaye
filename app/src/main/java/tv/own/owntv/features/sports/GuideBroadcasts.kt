package tv.own.owntv.features.sports

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.core.live.LiveEpgReader
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchTier
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.launcher.FootballMatcher
import tv.own.owntv.launcher.FootballScan

/**
 * A football match's channels found the way "Für dich" finds them, beside core's Find channels.
 *
 * Core reads only the stored guide, and only titles that spell a side as ESPN does. A playlist whose
 * guide comes from the provider's short EPG has nothing stored, and "Hamburger SV" is not ESPN's
 * "Hamburg SV": the match page then said "Nicht auf deinen Sendern" while "Für dich" showed the match on
 * Sky. Here every sports channel's guide is read as the Live TV list reads it, and a programme on at
 * kick-off that names the match ([FootballMatcher.Fixture]) lists its channel as a guide hit.
 */
internal object GuideBroadcasts {

    private class Snapshot(val profileId: Long, val at: Long, val channels: List<ChannelEntity>, val guides: Map<Long, EpgNowNext>)

    private val lock = Mutex()
    @Volatile private var snapshot: Snapshot? = null
    private val found = HashMap<String, List<WatchOption>>()

    /** [match]'s channels from the guides; reads them when the last read is older than [MAX_AGE_MS]. */
    suspend fun find(match: Match): List<WatchOption> {
        if (match.sport != Sport.SOCCER) return emptyList()
        val s = lock.withLock { read() } ?: return emptyList()
        val fixture = FootballMatcher.fixture(match)
        val kickOff = match.startMs
        val options = s.channels.flatMap { ch ->
            val g = s.guides[ch.id] ?: return@flatMap emptyList()
            (listOfNotNull(g.now, g.next) + g.upcoming).distinctBy { it.startMs }
                .filter { p ->
                    p.startMs in (kickOff - BEFORE_MS)..(kickOff + AFTER_MS) && p.stopMs > kickOff && fixture.shownIn(p.title)
                }
                .take(1).map { WatchOption(ch, WatchTier.GUIDE, it.title) }
        }
        synchronized(found) { found[match.key] = options }
        return options
    }

    /** What the last [find] for [match] gave, without reading anything. */
    fun cached(match: Match): List<WatchOption> = synchronized(found) { found[match.key].orEmpty() }

    /** Core's options and [guide]'s in one list, one row per channel, best tier first. */
    fun merge(core: List<WatchOption>, guide: List<WatchOption>): List<WatchOption> {
        if (guide.isEmpty()) return core
        val out = LinkedHashMap<Long, WatchOption>()
        (core + guide).sortedBy { it.tier.ordinal }.forEach { out.putIfAbsent(it.channel.id, it) }
        return out.values.toList()
    }

    private suspend fun read(): Snapshot? {
        val koin = GlobalContext.get()
        val settings = koin.get<SettingsRepository>()
        val profile = withTimeoutOrNull(15_000) {
            activeProfileSources(settings, koin.get<SourceDao>()).first { it.profileId >= 0 }
        } ?: return null
        val now = System.currentTimeMillis()
        snapshot?.takeIf { it.profileId == profile.profileId && now - it.at < MAX_AGE_MS }?.let { return it }
        val sources = profile.sourceIdsFor(MediaType.LIVE).ifEmpty { return null }
        val channelDao = koin.get<ChannelDao>()
        val epgReader = koin.get<LiveEpgReader>()
        val custom = koin.get<CustomizationStore>().observe(profile.profileId, MediaType.LIVE).first()
        val offset = settings.epgOffsetMinutes.first()
        val favorites = channelDao.snapshotFavoritesManual(profile.profileId, ContentOrderEntity.FAV_CONTEXT, sources, MAX_FAVORITES)
        val channels = FootballScan.channels(channelDao, sources, favorites).all
            .filter { CustomizeKeys.channel(it) !in custom.hiddenItems }
        val guides = FootballScan.guides(channels) { ch -> runCatching { epgReader.nowNext(ch, custom, offset) }.getOrNull() }
        return Snapshot(profile.profileId, now, channels, guides).also { snapshot = it }
    }

    /** A guide read is reused this long; a short-EPG read asks the provider. */
    private const val MAX_AGE_MS = 10 * 60_000L
    private const val MAX_FAVORITES = 40
    /** A programme starting this long before kick-off (Sky's build-up) to this long after counts. */
    private const val BEFORE_MS = 3 * 60 * 60_000L
    private const val AFTER_MS = 30 * 60_000L
}
