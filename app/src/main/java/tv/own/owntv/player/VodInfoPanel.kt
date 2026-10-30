package tv.own.owntv.player

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.R
import tv.own.owntv.ui.format.rememberSystemTimeFormatter

/**
 * The film and episode player in the same card as the channel's ([PlayerCard]): the poster, the series
 * in the accent with its season and episode, the title, the year, the stream's facts and what follows,
 * and when the film ends at the right. Under it the progress line, focusable here, where ◀▶ seek; then
 * the choices, Pause and the episode's neighbours on the left, the tracks and More on the right.
 */
@Composable
internal fun VodControlPanel(
    player: PlaybackEngine,
    position: () -> Long,
    duration: Long,
    /** The film's or series' poster; null draws the card without one. */
    artworkUrl: String?,
    /** The episode that follows, for the details; null for a film or the last episode. */
    upNext: String?,
    seekFocus: FocusRequester,
    start: List<CardItem>,
    end: List<CardItem>,
    menuFocus: FocusRequester,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerCard(modifier) {
        VodCardHeader(player, position, duration, artworkUrl, upNext)
        // No length yet (a stream still opening), no line to seek on: the room stays, so the row does not jump.
        if (duration > 0L) {
            VodCardLine(player, position, duration, seekFocus, onActivity)
        } else {
            VodCardLineSpace()
        }
        CardMenu(start, end, menuFocus, onActivity)
    }
}

@Composable
private fun VodCardHeader(player: PlaybackEngine, position: () -> Long, duration: Long, artworkUrl: String?, upNext: String?) {
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val chips by player.streamChips.collectAsStateWithLifecycle()
    val separator = stringResource(R.string.content_metadata_separator)
    val episodeTitle = meta.title?.takeIf { it.isNotBlank() }
    val title = episodeTitle
        ?: meta.episodeNumber?.let { stringResource(R.string.player_episode_number, it) }.orEmpty()
    // An episode: the series in the accent, then its season and episode. A film has neither.
    val series = meta.localizedSubtitle()?.takeIf { it.isNotBlank() }
    val place = buildList {
        meta.seasonNumber?.let { add(stringResource(R.string.player_season_number, it)) }
        // Without its own title the episode's number is the title already.
        if (episodeTitle != null) meta.episodeNumber?.let { add(stringResource(R.string.player_episode_number, it)) }
    }.joinToString(separator).ifBlank { null }
    val details = buildList {
        meta.year?.takeIf { it.isNotBlank() }?.let(::add)
        panelChips(chips).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(separator)) }
        upNext?.let { add(stringResource(R.string.tvplayer_vod_up_next, it)) }
    }
    CardHeader(
        title = title,
        details = details,
        artwork = artworkUrl?.takeIf { it.isNotBlank() }?.let { CardArtwork.Poster(it) }
            ?: meta.logoUrl?.takeIf { it.isNotBlank() }?.let { CardArtwork.Logo(it, title) },
        lead = series,
        context = place,
        asideLabel = if (duration > 0L) stringResource(R.string.tvplayer_card_ends_at) else null,
        asideValue = if (duration > 0L) vodEndsAt(position, duration) else null,
    )
}

/** When the film ends on the clock, if it plays on from here. Read each second, as the position ticks. */
@Composable
private fun vodEndsAt(position: () -> Long, duration: Long): String {
    val clock = rememberSystemTimeFormatter()
    return clock(System.currentTimeMillis() + (duration - position()).coerceAtLeast(0))
}

/**
 * The film's line and its times: elapsed at the left, what is left at the right. Focused it thickens and
 * takes the accent, and ◀▶ seek by the step set in Settings (left is back in every locale).
 */
@Composable
private fun VodCardLine(
    player: PlaybackEngine,
    position: () -> Long,
    duration: Long,
    focusRequester: FocusRequester,
    onActivity: () -> Unit,
) {
    val u = rememberScreenUnits()
    val seekStep by player.seekStepMs.collectAsStateWithLifecycle()
    val buffered = player.bufferedMs.collectAsStateWithLifecycle()
    var focused by remember { mutableStateOf(false) }
    Column {
        CardLine(
            progress = { position().toFloat() / duration },
            ahead = { buffered.value.toFloat() / duration },
            focused = focused,
            modifier = Modifier
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
        Spacer(Modifier.height(u.dp(12)))
        VodTimes(position, duration)
    }
}

/** The times under the line: the only part of the card besides the end time that ticks. */
@Composable
private fun VodTimes(position: () -> Long, duration: Long) {
    val pos = position().coerceIn(0L, duration)
    CardTimes(
        start = formatTime(pos),
        end = stringResource(R.string.player_time_remaining, formatTime(duration - pos)),
        startIsNow = true,
    )
}

/** The line's room while the film has no length yet. */
@Composable
private fun VodCardLineSpace() {
    val u = rememberScreenUnits()
    Column(Modifier.alpha(0f)) {
        CardLine(progress = { 0f })
        Spacer(Modifier.height(u.dp(12)))
        CardTimes(start = formatTime(0L), end = formatTime(0L), startIsNow = false)
    }
}
