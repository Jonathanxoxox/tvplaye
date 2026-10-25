package io.github.jonathanxoxox.tvkanaele.core

/** Was den Nutzer interessiert, so wie es in den Einstellungen steht. */
data class Interests(
    val teams: List<String> = emptyList(),
    val leagues: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val excludes: List<String> = emptyList(),
    val preferredChannels: List<String> = emptyList(),
    /** Auch Fußballspiele ohne Lieblingsteam vorschlagen (niedrige Priorität). */
    val includeAllFootball: Boolean = true,
)

/**
 * Bewertet eine Sendung nach den Interessen, unabhängig von der Uhrzeit.
 * Teams und Ligen werden über eingebaute Schreibvarianten erkannt (Barça, FC Barcelona, Barca …).
 */
class Matcher(interests: Interests) {

    data class Hit(val score: Int, val reasons: List<String>, val strong: Boolean)

    private class Term(val label: String, val aliases: List<String>)

    private val teams = interests.teams.map { resolve(it, Aliases.TEAMS) }
    private val leagues = interests.leagues.map { resolve(it, Aliases.LEAGUES) }
    private val keywords = interests.keywords.map { Term(it, listOf(Text.fold(it))) }
    private val excludes = interests.excludes.map { Text.fold(it) }.filter { it.isNotEmpty() }
    private val includeAllFootball = interests.includeAllFootball
    private val allLeagueAliases = Aliases.LEAGUES.flatten().map { Text.fold(it) }

    fun match(p: Programme): Hit? {
        val head = Text.fold(listOfNotNull(p.title, p.subtitle).joinToString(" "))
        if (excludes.any { Text.containsPhrase(head, it) }) return null
        val desc = Text.fold(p.description.orEmpty())
        val cats = Text.fold(p.categories.joinToString(" "))

        var score = 0
        val reasons = mutableListOf<String>()
        var strong = false

        var teamHits = 0
        for (t in teams) {
            val inHead = t.aliases.any { Text.containsPhrase(head, it) }
            val inDesc = !inHead && t.aliases.any { Text.containsPhrase(desc, it) }
            if (inHead || inDesc) {
                teamHits++
                score += if (inHead) TEAM else TEAM_DESC
                reasons += t.label
                strong = true
            }
        }
        if (teamHits >= 2) score += DERBY

        for (k in keywords) {
            val inHead = k.aliases.any { Text.containsPhrase(head, it) }
            if (inHead || k.aliases.any { Text.containsPhrase(desc, it) }) {
                score += if (inHead) KEYWORD else KEYWORD_DESC
                reasons += k.label
                strong = true
            }
        }

        val all = "$head $cats $desc"
        for (l in leagues) {
            if (l.aliases.any { Text.containsPhrase(all, it) }) {
                score += LEAGUE
                reasons += l.label
            }
        }

        if (score == 0 && includeAllFootball && isFootballMatch(p, head, cats, desc)) {
            score += FOOTBALL
            reasons += "Fußball"
        }
        if (score == 0) return null

        if (isLive(p, head)) score += LIVE
        return Hit(score, reasons.distinct(), strong)
    }

    private fun isFootballMatch(p: Programme, head: String, cats: String, desc: String): Boolean {
        val footballish = FOOTBALL_WORDS.any { Text.containsPhrase(cats, it) || Text.containsPhrase(head, it) } ||
            allLeagueAliases.any { Text.containsPhrase(head, it) || Text.containsPhrase(cats, it) } ||
            (FOOTBALL_WORDS.any { Text.containsPhrase(desc, it) } && cats.contains("sport"))
        return footballish && MATCH_PATTERN.containsMatchIn(p.title + " " + p.subtitle.orEmpty())
    }

    private fun isLive(p: Programme, head: String) =
        Text.containsPhrase(head, "live") || p.title.contains("(L)") || Text.containsPhrase(head, "en vivo") ||
            Text.containsPhrase(head, "en directo")

    private fun resolve(term: String, groups: List<List<String>>): Term {
        val folded = Text.fold(term)
        val group = groups.firstOrNull { g -> g.any { Text.fold(it) == folded } }
        val aliases = (group.orEmpty() + term).map { Text.fold(it) }.filter { it.isNotEmpty() }.distinct()
        return Term(term, aliases)
    }

    companion object {
        const val TEAM = 100
        const val TEAM_DESC = 50
        const val DERBY = 50
        const val KEYWORD = 60
        const val KEYWORD_DESC = 30
        const val LEAGUE = 40
        const val FOOTBALL = 15
        const val LIVE = 20

        private val FOOTBALL_WORDS = listOf("fussball", "football", "soccer", "futbol", "calcio", "voetbal", "futebol")
        private val MATCH_PATTERN = Regex("""\S\s+(vs\.?|v|-|–|—)\s+\S""", RegexOption.IGNORE_CASE)
    }
}
