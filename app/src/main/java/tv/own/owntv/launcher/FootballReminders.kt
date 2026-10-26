package tv.own.owntv.launcher

import android.content.Context
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.reminder.ReminderManager

/**
 * Mayo TV no longer sets kick-off reminders by itself (Jonathan: no pop-ups). Older builds did, keyed
 * "start|title" in the app's preferences; this takes the ones still waiting back out, once.
 */
internal object FootballReminders {

    private fun doneKey(profileId: Long) = "football_reminded_$profileId"

    suspend fun clear(context: Context, profileId: Long) {
        val prefs = FootballSetup.prefs(context)
        val done = prefs.getStringSet(doneKey(profileId), null) ?: return
        val reminders = GlobalContext.get().get<ReminderManager>()
        reminders.observe(profileId).first()
            .filter { r -> "${r.startMs}|${FootballMatcher.fold(r.title)}" in done }
            .forEach { reminders.dismiss(it.id) }
        prefs.edit().remove(doneKey(profileId)).apply()
    }
}
