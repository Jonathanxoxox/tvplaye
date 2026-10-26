package tv.own.owntv.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.SportsConfig
import tv.own.owntv.core.sports.model.Sport

class FootballMatcherTest {

    private val config = SportsConfig(
        leagues = FootballSetup.DEFAULT_LEAGUES,
        teams = listOf(
            FollowedTeam(Sport.SOCCER, "127", "Hamburg SV", "HSV"),
            FollowedTeam(Sport.SOCCER, "124", "Borussia Dortmund", "DOR"),
            FollowedTeam(Sport.SOCCER, "132", "Bayern Munich", "MUN"),
            FollowedTeam(Sport.SOCCER, "364", "Liverpool", "LIV"),
            FollowedTeam(Sport.SOCCER, "481", "Germany", "GER"),
            FollowedTeam(Sport.SOCCER, "479", "Denmark", "DEN"),
            FollowedTeam(Sport.SOCCER, "83", "Barcelona", "BAR"),
        ),
    )
    private val matcher = FootballMatcher.from(config, defaultTeams = false)

    private fun hit(title: String, description: String? = null, sportsChannel: Boolean = true) =
        matcher.match(title, description, sportsChannel)

    @Test
    fun germanGuideSpellingsFindTheTeams() {
        assertEquals("Hamburg SV", hit("Hamburger SV - Werder Bremen")?.reason)
        assertEquals("Hamburg SV", hit("Bundesliga: HSV – 1. FC Köln")?.reason)
        assertEquals("Borussia Dortmund", hit("BVB gegen Schalke")?.reason)
        assertEquals("Bayern Munich", hit("FC Bayern München - VfB Stuttgart")?.reason)
        assertEquals("Liverpool", hit("Premier League: Liverpool FC vs. Arsenal")?.reason)
        assertEquals("Barcelona", hit("LaLiga: FC Barça - Real Madrid")?.reason)
    }

    @Test
    fun nationalTeamsInGerman() {
        val h = hit("Länderspiel: Deutschland – Dänemark")
        assertNotNull(h)
        assertEquals(FootballMatcher.Kind.TEAM, h!!.kind)
        assertEquals("Germany", h.reason)
        assertEquals("Denmark", hit("WM-Qualifikation: Dänemark - Schottland")?.reason)
    }

    @Test
    fun firstTeamRanksHighest() {
        val hsv = hit("HSV - Werder Bremen")!!.score
        val barca = hit("Barcelona - Sevilla")!!.score
        assertTrue(hsv > barca)
    }

    @Test
    fun teamBeatsCompetitionBeatsFootball() {
        val team = hit("Bayern - Leipzig")!!
        val competition = hit("UEFA Champions League: Inter - Benfica")!!
        val other = hit("Fußball: Regionalliga Nord")!!
        assertEquals(FootballMatcher.Kind.COMPETITION, competition.kind)
        assertEquals(FootballMatcher.Kind.FOOTBALL, other.kind)
        assertTrue(team.score > competition.score)
        assertTrue(competition.score > other.score)
    }

    @Test
    fun teamInDescriptionCountsLess() {
        val title = hit("Bayern - Leipzig")!!.score
        val description = hit("Bundesliga live", "Heute: Bayern München gegen RB Leipzig")!!
        assertEquals(FootballMatcher.Kind.TEAM, description.kind)
        assertTrue(description.score < title)
    }

    @Test
    fun otherSportsAreLeftOut() {
        assertNull(hit("Handball-Bundesliga: HSV Hamburg - THW Kiel"))
        assertNull(hit("Basketball: FC Bayern - Alba Berlin"))
        assertNull(hit("Eishockey: Deutschland - Dänemark"))
    }

    @Test
    fun aGeneralChannelNeedsFootball() {
        assertNull(hit("Deutschland sucht den Superstar", sportsChannel = false))
        assertNull(hit("Bayern erleben", sportsChannel = false))
        assertEquals("Germany", hit("Deutschland - Dänemark", "Länderspiel live", sportsChannel = false)?.reason)
        assertEquals("Germany", hit("Fußball: Deutschland gegen Frankreich", sportsChannel = false)?.reason)
    }

    @Test
    fun unfollowedTeamsOnlyCountAsFootball() {
        val h = hit("Fußball: 1860 München - Ingolstadt")
        assertEquals(FootballMatcher.Kind.FOOTBALL, h?.kind)
        assertNull(hit("Tagesschau"))
    }

    @Test
    fun defaultTeamsWorkBeforeTheLookup() {
        val defaults = FootballMatcher.from(SportsConfig(), defaultTeams = true)
        assertEquals("Hamburg SV", defaults.match("HSV - St. Pauli", null)?.reason)
        assertEquals(FootballMatcher.Kind.COMPETITION, defaults.match("Champions League: Arsenal - PSV", null)?.kind)
        // Once looked up, an empty choice stays empty: only football in general is left.
        val none = FootballMatcher.from(SportsConfig(), defaultTeams = false)
        assertEquals(FootballMatcher.Kind.FOOTBALL, none.match("Fußball: HSV - St. Pauli", null)?.kind)
    }

    @Test
    fun channelKinds() {
        assertTrue(FootballMatcher.isSportsChannel("DE: Sky Sport Bundesliga 1 FHD"))
        assertTrue(FootballMatcher.isNationalChannel("DE: Das Erste HD"))
        assertTrue(FootballMatcher.isNationalChannel("DK: TV2 Sport"))
        assertTrue(!FootballMatcher.isNationalChannel("DE: Standard Kanal"))
    }
}
