package tv.own.owntv.player

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.R
import tv.own.owntv.ui.format.rememberSystemTimeFormatter

/**
 * The film and episode player in the same band as the channel's ([PlayerCard]): the series with its season
 * and episode in small capitals, the title, the year, when it ends and the minutes left with the stream's
 * badges, and what follows at the right. Under it the progress line, focusable here, where ◀▶ seek; then
 * the choices: Pause and the episode's neighbours as round buttons, the tracks as pills, Settings at the right.
 */
@Composable
internal fun VodControlPanel(
    player: PlaybackEngine,
    position: () -> Long,
    duration: Long,
    /** The episode that follows, for the right of the head; null for a film or the last episode. */
    upNext: String?,
    seekFocus: FocusRequester,
    start: List<CardItem>,
    end: List<CardItem>,
    menuFocus: FocusRequester,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
    /** How many of [start] are the round controls, before the divider and the track pills. */
    controls: Int = -1,
) {
    PlayerCard(modifier) {
        VodCardHeader(player, position, duration, upNext)
        // No length yet (a stream still opening), no line to seek on: the room stays, so the row does not jump.
        if (duration > 0L) {
            VodCardLine(player, position, duration, seekFocus, onActivity)
        } else {
            CardLine(start = formatTime(0L), end = formatTime(0L), progress = { 0f }, modifier = Modifier.alpha(0f))
        }
        CardMenu(start, end, menuFocus, onActivity, divider = controls)
    }
}

@Composable
private fun VodCardHeader(player: PlaybackEngine, position: () -> Long, duration: Long, upNext: String?) {
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val chips by player.streamChips.collectAsStateWithLifecycle()
    val separator = stringResource(R.string.content_metadata_separator)
    val episodeTitle = meta.title?.takeIf { it.isNotBlank() }
    val title = episodeTitle
        ?: meta.episodeNumber?.let { stringResource(R.string.player_episode_number, it) }.orEmpty()
    // An episode: the series, its season and episode. A film has none of them.
    val eyebrow = buildList {
        meta.localizedSubtitle()?.takeIf { it.isNotBlank() }?.let(::add)
        meta.seasonNumber?.let { add(stringResource(R.string.player_season_number, it)) }
        // Without its own title the episode's number is the title already.
        if (episodeTitle != null) meta.episodeNumber?.let { add(stringResource(R.string.player_episode_number, it)) }
    }.joinToString(separator).ifBlank { null }
    val clock = rememberSystemTimeFormatter()
    val facts = buildList {
        meta.year?.takeIf { it.isNotBlank() }?.let(::add)
        if (duration > 0L) {
            // Read each second, as the position ticks: when it ends if it plays on from here, and what is left.
            val left = (duration - position()).coerceAtLeast(0)
            add(stringResource(R.string.tvplayer_card_ends_at, clock(System.currentTimeMillis() + left)))
            val minutes = ((left + 59_999) / 60_000).toInt()
            add(pluralStringResource(R.plurals.tvplayer_card_left, minutes, minutes))
        }
    }
    CardHeader(
        title = title,
        facts = facts,
        badges = panelChips(chips),
        eyebrow = eyebrow,
        nextLabel = upNext?.let { stringResource(R.string.tvplayer_card_next) },
        nextValue = upNext,
    )
}

/**
 * The film's line between where it stands and its length. Focused it thickens with a larger thumb, and ◀▶
 * seek by the step set in Settings (left is back in every locale). Its times tick here, not in the band.
 */
@Composable
private fun VodCardLine(
    player: PlaybackEngine,
    position: () -> Long,
    duration: Long,
    focusRequester: FocusRequester,
    onActivity: () -> Unit,
) {
    val seekStep by player.seekStepMs.collectAsStateWithLifecycle()
    val buffered = player.bufferedMs.collectAsStateWithLifecycle()
    var focused by remember { mutableStateOf(false) }
    CardLine(
        start = formatTime(position().coerceIn(0L, duration)),
        end = formatTime(duration),
        progress = { position().toFloat() / duration },
        ahead = { buffered.value.toFloat() / duration },
        focused = focused,
        lineModifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onActivity() }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) when (e.key) {
                    Key.DirectionLeft -> { player.seekBy(-seekStep); true }
                    Key.DirectionRight -> { player.seekBy(seekStep); true }
                    else -> false
                } else false
            }
            .focusable(),
    )
}
