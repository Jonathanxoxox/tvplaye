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
            val key = "${p.startMs}|${FootballMatcher.fold(p.title)}"
            if (key in done || p.startMs <= now) continue
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
            if (added) done += key
        }
        prefs.edit().putStringSet(doneKey(profileId), done).apply()
    }

    private const val DAY_MS = 24 * 3_600_000L
}
