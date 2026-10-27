package tv.own.owntv.launcher

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.EpgMatcher
import tv.own.owntv.core.live.EpgNowNext
import java.util.concurrent.ConcurrentHashMap

/**
 * The channels that may show football and what they show now and next: the profile's favourites, every
 * sports channel and the free channels that carry national-team matches. Read through core's
 * `LiveEpgReader.nowNext`, so a channel whose guide comes only from the provider's short EPG (never
 * stored in the guide table) is read too. "Für dich", the football group and a match page's
 * "Übertragungen" all read it.
 */
internal object FootballScan {

    class Channels(val favorites: List<ChannelEntity>, val all: List<ChannelEntity>, val sportsIds: Set<Long>)

    /** [favorites] first, then the sports and national channels of [sources], in playlist order. */
    suspend fun channels(channelDao: ChannelDao, sources: List<Long>, favorites: List<ChannelEntity>): Channels {
        // Each name is folded once: the playlist can hold tens of thousands of channels.
        val sportsIds = HashSet<Long>()
        val scanned = channelDao.snapshotAll(sources, MAX_SCAN).filter { ch ->
            val name = FootballMatcher.fold(ch.name)
            when {
                FootballMatcher.isSportsChannelFolded(name) -> { sportsIds += ch.id; true }
                else -> FootballMatcher.isNationalChannelFolded(name)
            }
        }
        return Channels(favorites, (favorites + scanned).distinctBy { it.id }.take(MAX_SPORTS_CHANNELS), sportsIds)
    }

    /**
     * Each channel's guide, by channel id. Variants of one channel share a guide: by their guide id, read
     * once, and by name, so "Sky Sport Bundesliga 1", its HD, FHD, UHD and backup feeds and another
     * country's copy are one family. A variant without a guide of its own shows its family's, so every
     * variant of a match is found. Reads run a few at a time: a short-EPG read is a request to the provider.
     */
    suspend fun guides(channels: List<ChannelEntity>, read: suspend (ChannelEntity) -> EpgNowNext?): Map<Long, EpgNowNext> {
        val families = channels.groupBy { EpgMatcher.normalizeForEpg(it.name).ifEmpty { it.id.toString() } }.values
        val gate = Semaphore(PARALLEL_READS)
        var budget = MAX_GUIDE_READS

        // First every guide id once.
        val byKey = channels.filter { key(it) != null }.distinctBy { key(it) }.take(budget)
        budget -= byKey.size
        val keyed = coroutineScope {
            byKey.map { ch -> async { key(ch)!! to gate.withPermit { read(ch) } } }.awaitAll()
        }.filter { it.second != null }.associate { it.first to it.second!! }

        val out = ConcurrentHashMap<Long, EpgNowNext>()
        val unread = ArrayList<List<ChannelEntity>>()
        for (family in families) {
            val familyGuide = family.firstNotNullOfOrNull { ch -> key(ch)?.let(keyed::get) }
            for (ch in family) (key(ch)?.let(keyed::get) ?: familyGuide)?.let { out[ch.id] = it }
            if (familyGuide == null) unread += family.filter { key(it) == null }
        }
        // Then families that have no guide id at all: the provider is asked by the channel itself.
        val ownReads = unread.filter { it.isNotEmpty() }.take(budget.coerceAtLeast(0))
        coroutineScope {
            ownReads.map { family ->
                async {
                    for (ch in family) {
                        val g = gate.withPermit { read(ch) } ?: continue
                        family.forEach { out[it.id] = g }
                        break
                    }
                }
            }.awaitAll()
        }
        return out
    }

    private fun key(ch: ChannelEntity): String? = ch.epgChannelId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    private const val MAX_SCAN = 20_000
    private const val MAX_SPORTS_CHANNELS = 5_000
    /** Guides read per scan; variants that share a guide id cost one read. */
    private const val MAX_GUIDE_READS = 800
    private const val PARALLEL_READS = 8
}
