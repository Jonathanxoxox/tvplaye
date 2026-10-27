package tv.own.owntv.launcher

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
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
     * They start in [channels]' order, so the caller puts the likeliest first. [onProgress], when given,
     * gets the guides read so far about every [PROGRESS_MS] while the reads run, so a screen can show
     * what is found before the last channel has answered.
     */
    suspend fun guides(
        channels: List<ChannelEntity>,
        onProgress: (suspend (Map<Long, EpgNowNext>) -> Unit)? = null,
        read: suspend (ChannelEntity) -> EpgNowNext?,
    ): Map<Long, EpgNowNext> {
        val families = channels.groupBy { EpgMatcher.normalizeForEpg(it.name).ifEmpty { it.id.toString() } }.values
        val gate = Semaphore(PARALLEL_READS)
        val keyed = ConcurrentHashMap<String, EpgNowNext>()
        val own = ConcurrentHashMap<Long, EpgNowNext>()

        fun assemble(): Map<Long, EpgNowNext> {
            val out = HashMap<Long, EpgNowNext>()
            for (family in families) {
                val familyGuide = family.firstNotNullOfOrNull { ch -> key(ch)?.let(keyed::get) }
                for (ch in family) (key(ch)?.let(keyed::get) ?: familyGuide ?: own[ch.id])?.let { out[ch.id] = it }
            }
            return out
        }

        val progressLock = Mutex()
        var lastProgress = System.currentTimeMillis()
        suspend fun progress() {
            if (onProgress == null || System.currentTimeMillis() - lastProgress < PROGRESS_MS) return
            if (!progressLock.tryLock()) return
            try {
                if (System.currentTimeMillis() - lastProgress >= PROGRESS_MS) {
                    lastProgress = System.currentTimeMillis()
                    onProgress(assemble())
                }
            } finally {
                progressLock.unlock()
            }
        }

        // First every guide id once.
        val byKey = channels.filter { key(it) != null }.distinctBy { key(it) }.take(MAX_GUIDE_READS)
        coroutineScope {
            byKey.map { ch ->
                async {
                    gate.withPermit { read(ch) }?.let { keyed[key(ch)!!] = it }
                    progress()
                }
            }.awaitAll()
        }
        // Then families that have no guide id at all: the provider is asked by the channel itself.
        val unread = families.filter { family -> family.none { ch -> key(ch)?.let(keyed::get) != null } }
            .map { family -> family.filter { key(it) == null } }
            .filter { it.isNotEmpty() }
            .take((MAX_GUIDE_READS - byKey.size).coerceAtLeast(0))
        coroutineScope {
            unread.map { family ->
                async {
                    for (ch in family) {
                        val g = gate.withPermit { read(ch) } ?: continue
                        family.forEach { own[it.id] = g }
                        break
                    }
                    progress()
                }
            }.awaitAll()
        }
        return assemble()
    }

    private fun key(ch: ChannelEntity): String? = ch.epgChannelId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    private const val MAX_SCAN = 20_000
    private const val MAX_SPORTS_CHANNELS = 5_000
    /** Guides read per scan; variants that share a guide id cost one read. */
    private const val MAX_GUIDE_READS = 800
    private const val PARALLEL_READS = 8
    private const val PROGRESS_MS = 700L
}
