package tv.own.owntv.launcher

import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.SportsConfig
import tv.own.owntv.core.sports.TeamNames
import java.text.Normalizer

/**
 * Decides which programmes belong in "Für dich" and in the football group: the profile's teams above
 * everything, then its competitions, then any other football. Teams and competitions are the ones
 * chosen in Settings → Sports (My teams, Sports and leagues); their order there is their rank here.
 *
 * Names are compared as [TeamNames] tokens, the way core finds a match on a channel: accents folded,
 * "FC"/"SV" and numbers dropped, "München" = "Munich", "Deutschland" = "Germany". A team is found
 * when every token of one of its name variants is in the title.
 */
internal class FootballMatcher(
    private val teams: List<Interest>,
    private val competitions: List<Interest>,
) {

    enum class Kind { TEAM, COMPETITION, FOOTBALL }

    class Hit(val score: Int, val reason: String, val kind: Kind)

    /** One team or competition, with the token sets that name it. */
    class Interest(val name: String, val variants: List<Set<String>>) {
        fun foundIn(tokens: Set<String>): Boolean = variants.any { tokens.containsAll(it) }
    }

    /**
     * [title]'s place in "Für dich", or null. On a channel that is not a sports channel ([sportsChannel]
     * false: ARD, ZDF, TV 2 …) only a programme that reads as football counts, so "Deutschland sucht
     * den Superstar" is not a Germany match.
     */
    fun match(title: String, description: String?, sportsChannel: Boolean = true): Hit? {
        val t = TeamNames.tokens(title).toSet()
        if (t.any { it in OTHER_SPORTS }) return null
        val d = TeamNames.tokens(description.orEmpty()).toSet()
        if (!sportsChannel && !readsAsFootball(title, description, t, d)) return null
        teams.forEachIndexed { i, team -> if (team.foundIn(t)) return Hit(100 + rankBonus(i, 9), team.name, Kind.TEAM) }
        teams.forEachIndexed { i, team -> if (team.foundIn(d)) return Hit(70 + rankBonus(i, 9), team.name, Kind.TEAM) }
        competitions.forEachIndexed { i, c -> if (c.foundIn(t)) return Hit(45 + rankBonus(i, 5), c.name, Kind.COMPETITION) }
        competitions.forEachIndexed { i, c -> if (c.foundIn(d)) return Hit(30 + rankBonus(i, 5), c.name, Kind.COMPETITION) }
        val ft = fold(title)
        val fd = fold(description.orEmpty())
        if (FOOTBALL.any { containsPhrase(ft, it) || containsPhrase(fd, it) }) return Hit(15, "football", Kind.FOOTBALL)
        return null
    }

    private fun readsAsFootball(title: String, description: String?, t: Set<String>, d: Set<String>): Boolean =
        MATCH_PATTERN.containsMatchIn(title) || competitions.any { it.foundIn(t) || it.foundIn(d) } ||
            fold(title + " " + description.orEmpty()).let { all -> FOOTBALL.any { containsPhrase(all, it) } }

    private fun rankBonus(index: Int, max: Int) = (max - index).coerceAtLeast(0)

    companion object {

        /**
         * The matcher for [config]. Until the default teams were looked up once ([defaultTeams]), the
         * same teams are matched by name, so "Für dich" is right from the first start.
         */
        fun from(config: SportsConfig, defaultTeams: Boolean): FootballMatcher {
            val teamNames = if (config.teams.isEmpty() && defaultTeams) FootballSetup.DEFAULT_TEAMS.map { it.name }
            else config.teams.map { it.name }
            val leagues = config.leagues.ifEmpty { if (defaultTeams) FootballSetup.DEFAULT_LEAGUES else emptyList() }
            return FootballMatcher(
                teams = teamNames.map(::team),
                competitions = leagues.mapNotNull { key ->
                    val league = SportsCatalog.league(key) ?: return@mapNotNull null
                    if (league.sport != tv.own.owntv.core.sports.model.Sport.SOCCER) return@mapNotNull null
                    interest(league.name, listOf(league.name) + COMPETITION_ALIASES[key].orEmpty())
                },
            )
        }

        fun team(name: String): Interest = interest(name, listOf(name) + TEAM_ALIASES[key(name)].orEmpty())

        private fun interest(name: String, spellings: List<String>) = Interest(
            name,
            spellings.map { TeamNames.tokens(it).toSet() }.filter { it.isNotEmpty() && !(it.size == 1 && it.single() in GENERIC) }.distinct(),
        )

        private fun key(name: String) = TeamNames.tokens(name).joinToString(" ")

        /** Other spellings German guides use, by the team's name as ESPN writes it. */
        private val TEAM_ALIASES: Map<String, List<String>> by lazy {
            mapOf(
                "Hamburg SV" to listOf("HSV", "Hamburger SV"),
                "Borussia Dortmund" to listOf("BVB", "Dortmund"),
                "Bayern Munich" to listOf("FC Bayern", "Bayern"),
                "Liverpool" to listOf("LFC"),
                "Barcelona" to listOf("Barça"),
                "Germany" to listOf("DFB-Team", "DFB-Elf"),
                "Denmark" to listOf("Dänemark", "Danmark"),
                "Bayer Leverkusen" to listOf("Leverkusen"),
                "RB Leipzig" to listOf("Leipzig"),
                "Borussia Mönchengladbach" to listOf("Gladbach"),
                "Werder Bremen" to listOf("Werder"),
                "Eintracht Frankfurt" to listOf("SGE"),
                "Manchester City" to listOf("Man City"),
                "Manchester United" to listOf("Man United", "Man Utd"),
                "Paris Saint-Germain" to listOf("PSG"),
                "Internazionale" to listOf("Inter", "Inter Mailand"),
                "Atlético Madrid" to listOf("Atletico"),
                "Netherlands" to listOf("Holland"),
            ).mapKeys { key(it.key) }
        }

        /** German names of the competitions, by catalogue key; the catalogue's own name always counts. */
        private val COMPETITION_ALIASES = mapOf(
            "soccer/uefa.champions" to listOf("Königsklasse"),
            "soccer/fifa.worldq.uefa" to listOf("WM-Qualifikation", "WM Quali"),
            "soccer/uefa.euro" to listOf("Europameisterschaft", "EM-Qualifikation", "EM Quali"),
            "soccer/fifa.world" to listOf("Weltmeisterschaft", "WM"),
            "soccer/fifa.friendly" to listOf("Länderspiel", "Testspiel"),
            "soccer/esp.1" to listOf("Primera División"),
            "soccer/eng.1" to listOf("Premier League"),
        )

        /** Single words that name no team on their own ("City", "Real"). */
        private val GENERIC = setOf("city", "united", "utd", "real", "team", "sport", "sports", "liga", "league", "cup")

        /** A title with one of these is another sport, whatever team names it carries ("Handball: HSV Hamburg"). */
        private val OTHER_SPORTS: Set<String> by lazy {
            listOf(
                "handball", "basketball", "basket", "eishockey", "hockey", "volleyball", "tennis", "golf", "formel",
                "motogp", "radsport", "darts", "snooker", "nfl", "nba", "nhl", "boxen", "boxing", "ufc", "biathlon",
                "skispringen", "leichtathletik", "rugby", "cricket", "futsal", "esports", "efootball",
            ).flatMapTo(HashSet()) { TeamNames.tokens(it) }
        }

        /** "HSV – Werder", "Liverpool vs. Arsenal", "Deutschland gegen Dänemark". */
        private val MATCH_PATTERN = Regex("\\S\\s+(-|–|—|vs\\.?|v|gegen)\\s+\\S", RegexOption.IGNORE_CASE)

        private val FOOTBALL = listOf(
            "futbol", "fussball", "football", "soccer", "jornada", "spieltag", "matchday",
        )

        /** Words in a channel name or group that mark a sports channel worth reading the guide of. */
        private val SPORTS_CHANNEL = listOf(
            "sport", "sports", "dazn", "laliga", "liga", "bein", "espn", "eleven", "movistar", "futbol",
            "football", "champions", "gol", "tnt", "eurosport", "arena", "sky", "canal", "real madrid",
            "barca", "rmtv", "uefa", "magenta", "fussball", "viaplay", "lfctv", "mutv",
        )

        /** Free channels that show national-team matches; whole words only ("ard" is in "standard"). */
        private val NATIONAL_CHANNEL = listOf("ard", "das erste", "zdf", "rtl", "tv2", "tv 2", "dr1", "dr 1", "orf", "srf")

        fun isSportsChannel(name: String): Boolean {
            val folded = fold(name)
            return SPORTS_CHANNEL.any { containsPhrase(folded, fold(it)) || folded.contains(it) }
        }

        fun isNationalChannel(name: String): Boolean {
            val folded = fold(name)
            return NATIONAL_CHANNEL.any { containsPhrase(folded, it) }
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
}
