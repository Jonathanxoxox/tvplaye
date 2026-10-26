package tv.own.owntv.launcher

import java.text.Normalizer

/**
 * Decides which programmes belong in "Für dich": football first, the user's teams above everything,
 * then the big competitions. Titles are compared folded (no case, accents or punctuation), so
 * "Barça", "BARCA" and "barca" are the same word.
 */
internal object FootballMatcher {

    class Hit(val score: Int, val reason: String)

    private val TEAMS = listOf(
        "real madrid", "barcelona", "barca", "fc barcelona", "clasico", "el clasico",
    )
    private val LEAGUES = listOf(
        "laliga", "la liga", "champions league", "liga de campeones", "copa del rey", "supercopa",
        "europa league", "conference league", "bundesliga", "dfb pokal", "premier league", "serie a",
        "ligue 1", "uefa", "fifa", "nations league", "club world cup",
    )
    private val FOOTBALL = listOf(
        "futbol", "fussball", "football", "soccer", "jornada", "spieltag", "matchday",
    )

    /** Words in a channel name or group that mark a sports channel worth reading the guide of. */
    private val SPORTS_CHANNEL = listOf(
        "sport", "sports", "dazn", "laliga", "liga", "bein", "espn", "eleven", "movistar", "futbol",
        "football", "champions", "gol", "tnt", "eurosport", "arena", "sky", "canal", "real madrid",
        "barca", "rmtv", "uefa", "magenta", "fussball",
    )

    fun isSportsChannel(name: String): Boolean {
        val folded = fold(name)
        return SPORTS_CHANNEL.any { containsPhrase(folded, fold(it)) || folded.contains(it) }
    }

    fun match(title: String, description: String?): Hit? {
        val t = fold(title)
        val d = fold(description.orEmpty())
        TEAMS.firstOrNull { containsPhrase(t, it) }?.let { return Hit(100, it) }
        TEAMS.firstOrNull { containsPhrase(d, it) }?.let { return Hit(70, it) }
        LEAGUES.firstOrNull { containsPhrase(t, fold(it)) }?.let { return Hit(45, it) }
        LEAGUES.firstOrNull { containsPhrase(d, fold(it)) }?.let { return Hit(30, it) }
        if (FOOTBALL.any { containsPhrase(t, it) || containsPhrase(d, it) }) return Hit(15, "football")
        return null
    }

    /**
     * Whether the focused channel shows football right now, for the Flutlichtnacht scene: the running
     * programme decides when the guide has one, otherwise the channel's name or group (a "LaLiga TV" or
     * a "Fußball" group).
     */
    fun isFootballFocus(title: String?, description: String?, channelName: String?, groupName: String?): Boolean {
        if (groupName != null && match(groupName, null) != null) return true
        if (!title.isNullOrBlank()) return match(title, description) != null
        return channelName != null && match(channelName, null) != null
    }

    private val MARKS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    fun fold(s: String): String {
        val lower = s.lowercase().replace("ß", "ss")
        val noAccents = MARKS.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
        return NON_ALNUM.replace(noAccents, " ").trim()
    }

    private fun containsPhrase(folded: String, phrase: String): Boolean =
        phrase.isNotEmpty() && " $folded ".contains(" $phrase ")
}
