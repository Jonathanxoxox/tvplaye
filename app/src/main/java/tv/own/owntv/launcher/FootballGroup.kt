package tv.own.owntv.launcher

import android.content.Context
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext
import tv.own.owntv.R
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.entity.CustomCategoryMemberEntity
import tv.own.owntv.core.model.MediaType

/**
 * The channel group "Fußball jetzt & heute": the channels showing football for the profile's teams and
 * competitions now or later today, running matches first. It is one of the profile's own Live TV
 * groups (a custom category), so the channel list, the guide and the group sheet show it like any
 * other, at the top. Rebuilt with the launcher rows; switched off in Settings → Sports.
 */
internal object FootballGroup {

    private fun idKey(profileId: Long) = "football_group_id_$profileId"
    private fun membersKey(profileId: Long) = "football_group_members_$profileId"

    /** [channelIds] in the order the group shows them; an empty list leaves the group empty. */
    suspend fun update(context: Context, profileId: Long, channelIds: List<Long>) {
        val koin = GlobalContext.get()
        val store = koin.get<CustomizationStore>()
        val dao = koin.get<CustomCategoryDao>()
        val prefs = FootballSetup.prefs(context)
        val existing = prefs.getString(idKey(profileId), null)
            ?.takeIf { id -> store.observe(profileId, MediaType.LIVE).first().customCategories.any { it.id == id } }

        if (!FootballSetup.groupOn(context)) {
            if (existing != null) {
                dao.clearContext(profileId, MediaType.LIVE, existing)
                store.deleteCustomCategory(profileId, MediaType.LIVE, existing)
            }
            prefs.edit().remove(idKey(profileId)).remove(membersKey(profileId)).apply()
            return
        }

        val id = existing ?: store.createCustomCategory(profileId, MediaType.LIVE, context.getString(R.string.tvplayer_football_group)).id
            .also { prefs.edit().putString(idKey(profileId), it).remove(membersKey(profileId)).apply() }
        // Unchanged members are not written again: a rewrite reloads the list under the user's focus.
        val signature = channelIds.joinToString(",")
        if (existing != null && prefs.getString(membersKey(profileId), null) == signature) return
        dao.replaceContext(
            profileId, MediaType.LIVE, id,
            channelIds.mapIndexed { i, channelId ->
                CustomCategoryMemberEntity(profileId = profileId, mediaType = MediaType.LIVE, contextKey = id, itemId = channelId, position = i)
            },
        )
        prefs.edit().putString(membersKey(profileId), signature).apply()
    }
}
