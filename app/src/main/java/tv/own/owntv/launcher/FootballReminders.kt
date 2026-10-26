package tv.own.owntv.launcher

import android.content.Context
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.core.reminder.ReminderManager

/**
 * Kick-off reminders for the profile's teams: a match of one of them in today's guide gets the guide's
 * own programme reminder on the best channel, [leadMinutes] before (Settings → Sports → Match
 * reminders). Each match is reminded once, so a reminder the user dismissed does not come back.
 */
internal object FootballReminders {

    private fun doneKey(profileId: Long) = "football_reminded_$profileId"

    class Match(val channel: ChannelEntity, val programme: XtEpgEntry)

    private fun key(startMs: Long, title: String) = "$startMs|${FootballMatcher.fold(title)}"

    /** True for a reminder Flutlicht set for one of the profile's teams (the prompt then asks for the remote). */
    fun isTeamMatch(context: Context, profileId: Long, startMs: Long, title: String): Boolean =
        key(startMs, title) in FootballSetup.prefs(context).getStringSet(doneKey(profileId), emptySet()).orEmpty()

    suspend fun update(context: Context, profileId: Long, matches: List<Match>, leadMinutes: Int, now: Long) {
        if (!FootballSetup.remindersOn(context)) return
        val prefs = FootballSetup.prefs(context)
        // "start|title", kept for a day after kick-off.
        val done = prefs.getStringSet(doneKey(profileId), emptySet()).orEmpty()
            .filter { it.substringBefore('|').toLongOrNull()?.let { start -> start > now - DAY_MS } == true }
            .toMutableSet()
        val reminders = GlobalContext.get().get<ReminderManager>()
        for (m in matches) {
            val p = m.programme
            val id = key(p.startMs, p.title)
            if (id in done || p.startMs <= now) continue
            val added = reminders.add(
                profileId = profileId,
                channelId = m.channel.id,
                channelName = m.channel.name,
                epgChannelId = m.channel.epgChannelId,
                title = p.title,
                startMs = p.startMs,
                stopMs = p.stopMs,
                leadMinutes = leadMinutes,
            )
            if (added) done += id
        }
        prefs.edit().putStringSet(doneKey(profileId), done).apply()
    }

    private const val DAY_MS = 24 * 3_600_000L
}
