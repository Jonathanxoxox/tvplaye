package io.github.jonathanxoxox.tvkanaele.core

/** Ordnet Playlist-Sender den EPG-Kanälen zu: zuerst über tvg-id, sonst über den Namen. */
object ChannelMapper {
    fun map(channels: List<IptvChannel>, epgNames: Map<String, List<String>>): Map<String, String> {
        val byIdLower = epgNames.keys.associateBy { it.lowercase() }
        val byName = mutableMapOf<String, String>()
        epgNames.forEach { (id, names) ->
            (names + id).forEach { n -> Text.channelBaseName(n).takeIf { it.isNotEmpty() }?.let { byName.putIfAbsent(it, id) } }
        }
        val result = mutableMapOf<String, String>()
        for (c in channels) {
            val id = c.tvgId?.let { byIdLower[it.lowercase()] }
                ?: c.tvgName?.let { byName[Text.channelBaseName(it)] }
                ?: byName[Text.channelBaseName(c.name)]
                ?: c.tvgId?.takeIf { epgNames.isEmpty() }
            if (id != null) result[c.key] = id
        }
        return result
    }
}

data class Suggestion(
    val channel: IptvChannel,
    val programme: Programme,
    val score: Int,
    val reasons: List<String>,
    /** Wie viele weitere Sender dieselbe Sendung zeigen (HD/FHD/Backup …). */
    val alternatives: Int,
)

data class FavoriteNow(val channel: IptvChannel, val now: Programme?, val next: Programme?)

/** Daten, aus denen Vorschläge entstehen: Sender, Zuordnung zum EPG und gefilterte Sendungen. */
class Guide(
    val channels: List<IptvChannel>,
    /** Senderschlüssel → EPG-Kanal-ID */
    val epgMap: Map<String, String>,
    val programmes: List<Programme>,
) {
    val channelsByKey: Map<String, IptvChannel> = channels.associateBy { it.key }
    private val channelsByEpg: Map<String, List<IptvChannel>> =
        epgMap.entries.groupBy({ it.value }, { channelsByKey[it.key] }).mapValues { e -> e.value.filterNotNull() }
    private val programmesByEpg: Map<String, List<Programme>> =
        programmes.groupBy { it.epgChannel }.mapValues { e -> e.value.sortedBy { it.start } }

    fun channelsFor(epgId: String): List<IptvChannel> = channelsByEpg[epgId].orEmpty()

    fun favorites(keys: List<String>, now: Long): List<FavoriteNow> = keys.mapNotNull { key ->
        val channel = channelsByKey[key] ?: return@mapNotNull null
        val list = epgMap[key]?.let { programmesByEpg[it] }.orEmpty()
        val current = list.firstOrNull { it.isRunning(now) }
        val next = list.firstOrNull { it.start >= (current?.end ?: now) }
        FavoriteNow(channel, current, next)
    }

    /**
     * Sendungen, die gerade laufen oder innerhalb von [lookaheadMs] beginnen, nach Interesse sortiert.
     * Läuft dieselbe Sendung auf mehreren Sendern, gewinnt der passendste (Favorit, bevorzugte Sender, Qualität).
     */
    fun suggestions(
        matcher: Matcher,
        favorites: Set<String>,
        preferred: List<String>,
        now: Long,
        lookaheadMs: Long,
        limit: Int,
    ): List<Suggestion> {
        val preferredFolded = preferred.map { Text.fold(it) }.filter { it.isNotEmpty() }

        class Candidate(val channel: IptvChannel, val programme: Programme, val hit: Matcher.Hit, val channelScore: Int)

        val events = mutableMapOf<String, MutableList<Candidate>>()
        for (p in programmes) {
            if (p.end <= now || p.start >= now + lookaheadMs) continue
            val channels = channelsFor(p.epgChannel)
            if (channels.isEmpty()) continue
            val hit = matcher.match(p) ?: continue
            val eventKey = Text.fold(p.title) + "@" + (p.start / FIVE_MINUTES)
            for (c in channels) {
                events.getOrPut(eventKey) { mutableListOf() } += Candidate(c, p, hit, channelScore(c, favorites, preferredFolded))
            }
        }

        return events.values.map { candidates ->
            val best = candidates.maxWith(compareBy<Candidate> { it.channelScore }.thenBy { -candidates.indexOf(it) })
            val p = best.programme
            val favoriteBonus = if (best.channel.key in favorites) FAVORITE_BONUS else 0
            Suggestion(
                channel = best.channel,
                programme = p,
                score = best.hit.score + timeScore(p, now) + favoriteBonus,
                reasons = best.hit.reasons,
                alternatives = candidates.map { it.channel.key }.distinct().size - 1,
            )
        }.sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.programme.start })
            .take(limit)
    }

    private fun timeScore(p: Programme, now: Long): Int {
        val minutes = (p.start - now) / 60_000
        return when {
            p.isRunning(now) -> 50
            minutes <= 15 -> 40
            minutes <= 60 -> 25
            else -> 10
        }
    }

    private fun channelScore(c: IptvChannel, favorites: Set<String>, preferred: List<String>): Int {
        val text = Text.fold("${c.group} ${c.name}")
        var score = 0
        if (c.key in favorites) score += 100
        preferred.forEach { if (Text.containsPhrase(text, it)) score += 20 }
        val words = text.split(' ').toSet()
        score += when {
            "fhd" in words || "1080p" in words -> 4
            "hd" in words -> 3
            "uhd" in words || "4k" in words -> 2
            else -> 0
        }
        if ("backup" in words) score -= 5
        return score
    }

    companion object {
        private const val FIVE_MINUTES = 5 * 60_000L
        private const val FAVORITE_BONUS = 10
    }
}
