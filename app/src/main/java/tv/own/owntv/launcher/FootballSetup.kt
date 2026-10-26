package tv.own.owntv.launcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.SportsSource
import tv.own.owntv.core.sports.TeamNames
import tv.own.owntv.core.sports.model.Sport

/**
 * Jonathan's football, put into Settings → Sports once: the leagues he watches and his teams, HSV
 * first. Everything stays editable there (Sports and leagues, My teams); "Für dich", the football group
 * and the kick-off reminders read it from the profile's sports settings.
 *
 * Followed teams carry ESPN's ids, so they are looked up in the leagues' team lists (the same call the
 * My teams search makes). Without network the lookup is tried again on the next start; until it has
 * worked once, [FootballMatcher.from] matches the same teams by name.
 */
internal object FootballSetup {

    /** A default team: its name as ESPN writes it, and where to look for it. */
    class DefaultTeam(val name: String, val leagues: List<String>)

    val DEFAULT_TEAMS = listOf(
        DefaultTeam("Hamburg SV", listOf("soccer/ger.1", "soccer/ger.2")),
        DefaultTeam("Borussia Dortmund", listOf("soccer/ger.1")),
        DefaultTeam("Bayern Munich", listOf("soccer/ger.1")),
        DefaultTeam("Liverpool", listOf("soccer/eng.1")),
        DefaultTeam("Germany", listOf("soccer/uefa.nations", "soccer/fifa.worldq.uefa", "soccer/uefa.euro")),
        DefaultTeam("Denmark", listOf("soccer/uefa.nations", "soccer/fifa.worldq.uefa", "soccer/uefa.euro")),
        DefaultTeam("Barcelona", listOf("soccer/esp.1")),
    )

    val DEFAULT_LEAGUES = listOf(
        "soccer/ger.1", "soccer/uefa.champions", "soccer/ger.dfb_pokal", "soccer/eng.1", "soccer/esp.1",
        "soccer/uefa.nations", "soccer/fifa.worldq.uefa", "soccer/uefa.euro", "soccer/fifa.world", "soccer/fifa.friendly",
    )

    private const val PREFS = "tvplayer"
    private const val LEAGUES_DONE = "football_leagues_v1"
    private const val TEAMS_DONE = "football_teams_v1"
    const val GROUP_ON = "football_group_on"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The football group in the channel list ("Fußball jetzt & heute"). On unless switched off. */
    fun groupOn(context: Context) = prefs(context).getBoolean(GROUP_ON, true)

    fun set(context: Context, key: String, on: Boolean) {
        prefs(context).edit().putBoolean(key, on).apply()
        LauncherRowsWorker.refreshSoon(context, delaySeconds = 1)
    }

    /** True while the default teams have not been looked up yet: match them by name meanwhile. */
    fun useDefaultTeams(context: Context) = !prefs(context).getBoolean(TEAMS_DONE, false)

    /** Once per install, after a profile exists; a profile that already follows something is left alone. */
    suspend fun seed(context: Context) {
        val prefs = prefs(context)
        if (prefs.getBoolean(LEAGUES_DONE, false) && prefs.getBoolean(TEAMS_DONE, false)) return
        val koin = GlobalContext.get()
        val settings = koin.get<SettingsRepository>()
        val profileId = settings.activeProfileId.first { it >= 0L }
        val config = settings.activeSportsConfig.first()

        if (!prefs.getBoolean(LEAGUES_DONE, false)) {
            if (config.leagues.isEmpty()) settings.updateSportsConfig(profileId) { it.copy(leagues = DEFAULT_LEAGUES) }
            prefs.edit().putBoolean(LEAGUES_DONE, true).apply()
        }
        if (prefs.getBoolean(TEAMS_DONE, false)) return
        if (config.teams.isNotEmpty()) {
            prefs.edit().putBoolean(TEAMS_DONE, true).apply()
            return
        }

        val source = koin.get<SportsSource>()
        val lists = HashMap<String, List<tv.own.owntv.core.sports.model.Team>>()
        suspend fun teamsOf(key: String) = lists.getOrPut(key) {
            SportsCatalog.league(key)?.let { league -> runCatching { source.teams(league) }.getOrDefault(emptyList()) }.orEmpty()
        }
        val found = DEFAULT_TEAMS.mapNotNull { wanted ->
            val tokens = TeamNames.tokens(wanted.name).toSet()
            wanted.leagues.firstNotNullOfOrNull { key ->
                val teams = teamsOf(key)
                teams.firstOrNull { TeamNames.tokens(it.name).toSet() == tokens } ?: teams.firstOrNull { TeamNames.tokens(it.name).containsAll(tokens) }
            }?.let { FollowedTeam(Sport.SOCCER, it.id, it.name, it.abbreviation) }
        }.distinctBy { it.key }
        if (found.isEmpty()) {
            Log.i(TAG, "default teams not found yet (no network?), trying again on the next start")
            return
        }
        settings.updateSportsConfig(profileId) { c -> if (c.teams.isEmpty()) c.copy(teams = found) else c }
        prefs.edit().putBoolean(TEAMS_DONE, true).apply()
        LauncherRowsWorker.refreshSoon(context, delaySeconds = 1)
    }

    private const val TAG = "FootballSetup"
}
