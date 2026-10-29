package tv.own.owntv.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.stageAccent

/**
 * The film and episode player in the live player's clothes: the same band rising from the bottom edge
 * as the channel's info bar and OK panel, so a film, an episode and a channel read as one player. The
 * programme block becomes the title with its position, minutes left and badges; the seek line is the
 * live one, here focusable, and ◀▶ on it seek; under it the tiles and, on ▼, the settings.
 */
@Composable
internal fun VodControlPanel(
    player: PlaybackEngine,
    position: () -> Long,
    duration: Long,
    /** The episode that follows, for the line under the title; null for a film or the last episode. */
    upNext: String?,
    seekFocus: FocusRequester,
    tiles: List<PanelTile>,
    tileFocus: FocusRequester,
    settings: List<LiveOption>,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val u = rememberScreenUnits()
    Column(modifier.fillMaxWidth().background(PanelWash).padding(top = u.dp(60))) {
        VodInfoBlock(player, position, duration, upNext)
        Spacer(Modifier.height(u.dp(8)))
        // No length yet (a stream still opening), no line to seek on: the room stays, so the tiles do not jump.
        if (duration > 0L) VodSeekLine(player, position, duration, seekFocus, onActivity) else Spacer(Modifier.height(u.dp(16)))
        ControlPanelRows(tiles, tileFocus, settings, onActivity)
    }
}

/**
 * The programme block for a film or an episode, laid out as [ZapInfoBlock]: the title large, then where
 * the film stands, its progress and the minutes left with the series, season and episode (or the year)
 * and the stream's badges beside them, and when it ends and what follows under it. A logo only when the
 * stream brings one, as a channel does. Informational only; it never takes focus.
 */
@Composable
private fun VodInfoBlock(player: PlaybackEngine, position: () -> Long, duration: Long, upNext: String?) {
    val u = rememberScreenUnits()
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val chips by player.streamChips.collectAsStateWithLifecycle()
    val title = meta.title?.takeIf { it.isNotBlank() }
        ?: meta.episodeNumber?.let { stringResource(R.string.player_episode_number, it) }.orEmpty()
    // Series · Staffel 2 · Folge 5 for an episode, the year for a film.
    val details = buildList {
        meta.localizedSubtitle()?.takeIf { it.isNotBlank() }?.let(::add)
        meta.seasonNumber?.let { add(stringResource(R.string.player_season_number, it)) }
        // Without its own title the episode's number is the title already.
        if (!meta.title.isNullOrBlank()) meta.episodeNumber?.let { add(stringResource(R.string.player_episode_number, it)) }
        meta.year?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(stringResource(R.string.content_metadata_separator))
    val row = u.text(26, 500)
    val muted = Color.White.copy(alpha = 0.85f)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = u.dp(92)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(u.dp(74)),
    ) {
        meta.logoUrl?.takeIf { it.isNotBlank() }?.let { LogoPlate(it, title, u) }
        Column(Modifier.weight(1f)) {
            Text(title, style = u.text(40, 600), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(u.dp(12)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u.dp(22))) {
                VodTimes(position, duration, row, muted, u)
                if (details.isNotBlank()) {
                    Text(details, style = u.text(26, 600), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                panelChips(chips).forEach { StreamBadge(it, u) }
            }
            if (duration > 0L || upNext != null) {
                Spacer(Modifier.height(u.dp(6)))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u.dp(22))) {
                    if (duration > 0L) VodEndsAt(position, duration, row)
                    upNext?.let {
                        Text(stringResource(R.string.tvplayer_vod_up_next, it), style = row, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                }
            }
        }
    }
}

/** Position and length, the small progress bar and the minutes left: the line's only part that ticks. */
@Composable
private fun VodTimes(position: () -> Long, duration: Long, style: TextStyle, color: Color, u: ScreenUnits) {
    val pos = position().coerceAtLeast(0L)
    if (duration <= 0L) {
        Text(formatTime(pos), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    Text(stringResource(R.string.tvplayer_vod_position, formatTime(pos), formatTime(duration)), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    val progress = (pos.toFloat() / duration).coerceIn(0f, 1f)
    Box(Modifier.width(u.dp(78)).height(u.dp(5)).clip(RoundedCornerShape(u.dp(3))).background(Color.White.copy(alpha = 0.3f))) {
        Box(Modifier.fillMaxWidth(progress).height(u.dp(5)).background(Color.White))
    }
    val left = ((duration - pos).coerceAtLeast(0) + 59_999) / 60_000
    Text(stringResource(R.string.player_duration_minutes, left.toInt()), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** When the film ends on the clock, if it plays on from here. */
@Composable
private fun VodEndsAt(position: () -> Long, duration: Long, style: TextStyle) {
    val clock = rememberSystemTimeFormatter()
    val endMs = System.currentTimeMillis() + (duration - position()).coerceAtLeast(0)
    Text(stringResource(R.string.tvplayer_vod_ends_at, clock(endMs)), style = style, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * [ProgrammeSeekLine] for a film: the accent up to the playhead with its round thumb, what is buffered
 * a lighter grey ahead of it. Focused, the line thickens and the thumb grows a halo, and ◀▶ seek by the
 * step set in Settings (left is back in every locale). The position is read while drawing, so the
 * line redraws as the film plays without recomposing anything.
 */
@Composable
private fun VodSeekLine(
    player: PlaybackEngine,
    position: () -> Long,
    duration: Long,
    focusRequester: FocusRequester,
    onActivity: () -> Unit,
) {
    val u = rememberScreenUnits()
    val accent = stageAccent.accent
    val seekStep by player.seekStepMs.collectAsStateWithLifecycle()
    val buffered = player.bufferedMs.collectAsStateWithLifecycle()
    var focused by remember { mutableStateOf(false) }
    Canvas(
        Modifier.fillMaxWidth().padding(horizontal = u.dp(56)).height(u.dp(16))
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
    ) {
        val progress = (position().toFloat() / duration).coerceIn(0f, 1f)
        val ahead = (buffered.value.toFloat() / duration).coerceIn(progress, 1f)
        val line = u.toPx(if (focused) 7 else 5)
        val top = (size.height - line) / 2
        val radius = CornerRadius(line / 2)
        val thumb = Offset(size.width * progress, size.height / 2)
        drawRoundRect(Color.White.copy(alpha = if (focused) 0.5f else 0.4f), Offset(0f, top), Size(size.width, line), radius)
        drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(0f, top), Size(size.width * ahead, line), radius)
        drawRoundRect(accent, Offset(0f, top), Size(size.width * progress, line), radius)
        if (focused) drawCircle(accent.copy(alpha = 0.3f), radius = u.toPx(20), center = thumb)
        drawCircle(accent, radius = if (focused) u.toPx(12) else size.height / 2, center = thumb)
    }
}
