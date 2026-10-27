package tv.own.owntv.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.SportsConfig
import tv.own.owntv.core.sports.model.Competitor
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.core.sports.model.Team

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
    fun talkShowsAndMagazinesAreNotMatches() {
        // Das Erste, 26 Oct 2026: the reminder that asked for the remote.
        assertNull(
            hit(
                "Hart aber fair - Pflege zu Hause: Lässt die Politik Angehörige allein?",
                "Millionen Menschen in Deutschland pflegen Angehörige, in Hamburg wie in Bayern.",
                sportsChannel = false,
            ),
        )
        assertNull(hit("Maischberger - Deutschland am Limit", "Talk mit Gästen aus Politik und Wirtschaft", sportsChannel = false))
        assertNull(hit("Tatort", "Ein Fall in Hamburg. Der Kommissar ist Fußball-Fan.", sportsChannel = false))
        assertNull(hit("Sky Sport News - Das Magazin", "Alles zur Bundesliga"))
        assertNull(hit("Die Simpsons", "Homer und Bart in Liverpool"))
    }

    @Test
    fun matchesOnSportsChannels() {
        assertEquals(FootballMatcher.Kind.COMPETITION, hit("Live LaLiga 26/27: FC Sevilla - CA Osasuna")?.kind)
        assertEquals(FootballMatcher.Kind.FOOTBALL, hit("Live Ligue 1: Paris - Lyon")?.kind)
        assertNull(hit("Paris - Lyon", sportsChannel = false))
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
        assertTrue(FootballMatcher.isSportsChannel("DE: DAZN 2 HD"))
        assertTrue(!FootballMatcher.isSportsChannel("DE: Sky Krimi HD"))
        assertTrue(!FootballMatcher.isSportsChannel("DE: RTL ZWEI HD"))
        assertTrue(FootballMatcher.isNationalChannel("DE: Das Erste HD"))
        assertTrue(FootballMatcher.isNationalChannel("DK: TV2 Sport"))
        assertTrue(!FootballMatcher.isNationalChannel("DE: Standard Kanal"))
    }

    private fun fixture(home: Team, away: Team, league: String = "ger.dfb_pokal", leagueName: String = "German Cup") =
        FootballMatcher.fixture(
            Match(
                id = "1", sport = Sport.SOCCER, feed = league, leagueSlug = league, leagueName = leagueName,
                startMs = 0L, state = MatchState.PRE, statusText = "", title = "",
                competitors = listOf(Competitor(home, home = true, winner = null, score = null), Competitor(away, home = false, winner = null, score = null)),
            ),
        )

    private val hsv = Team("127", "Hamburg SV", "Hamburg", "HSV", location = "Hamburg")
    private val frankfurt = Team("125", "Eintracht Frankfurt", "Frankfurt", "SGE", location = "Frankfurt")

    @Test
    fun matchPageFindsGermanGuideTitles() {
        val f = fixture(hsv, frankfurt)
        assertTrue(f.shownIn("Live DFB-Pokal: Hamburger SV - Eintracht Frankfurt"))
        assertTrue(f.shownIn("Hamburger SV - Eintracht Frankfurt"))
        assertTrue(f.shownIn("HSV - SGE"))
        assertTrue(f.shownIn("DFB-Pokal: HSV - Frankfurt"))
        assertTrue(f.shownIn("Hamburg – Frankfurt"))
        assertTrue(f.shownIn("Live DFB-Pokal: Hamburger SV"))
    }

    @Test
    fun matchPageSkipsOtherMatchesAndSports() {
        val f = fixture(hsv, frankfurt)
        assertFalse(f.shownIn("Handball: HSV Hamburg - Füchse Berlin"))
        assertFalse(f.shownIn("Sky Sport News"))
        assertFalse(f.shownIn("Hamburg Journal"))
        assertFalse(f.shownIn("DFB-Pokal: Bayern - Köln"))
        assertFalse(f.shownIn("Werder Bremen - VfL Wolfsburg"))
    }
}

