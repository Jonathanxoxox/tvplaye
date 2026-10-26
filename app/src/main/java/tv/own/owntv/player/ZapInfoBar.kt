package tv.own.owntv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * The info bar along the bottom of the picture after a channel change: logo, number and name on the
 * left, then the programme on air with its times and progress, and what comes after it. The clock
 * sits at the bar's end. Informational only; it never takes focus.
 */
@Composable
internal fun ZapInfoBar(
    title: String?,
    subtitle: String?,
    logoUrl: String?,
    guide: EpgNowNext?,
    modifier: Modifier = Modifier,
) {
    val formatTime = rememberSystemTimeFormatter()
    // The bar is up for a few seconds, so the moment it opened is precise enough.
    val nowMs = remember(guide) { System.currentTimeMillis() }
    val accent = stageAccent.accent
    // As the classic IPTV player draws it: no panel, a dark wash rising from the bottom edge, the logo
    // on a white tile at the left, then the programme large, its times, progress and minutes left with
    // the channel beside them, and the next programme under it.
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.8f))))
            .padding(start = 86.mpx, end = 86.mpx, top = 108.mpx, bottom = 61.mpx),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(47.mpx)) {
            Box(
                Modifier.size(width = 216.mpx, height = 216.mpx).clip(RoundedCornerShape(11.mpx)).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (!logoUrl.isNullOrBlank()) {
                    AsyncImage(model = logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().padding(18.mpx))
                } else {
                    Text(title.orEmpty().take(3).uppercase(), style = stageText(30, 800), color = Color(0xFF26363A))
                }
            }
            Column(Modifier.weight(1f)) {
                val now = guide?.now
                Text(
                    now?.title ?: title.orEmpty(),
                    style = stageText(40, 600), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(11.mpx))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(29.mpx)) {
                    if (now != null) {
                        Text(
                            stringResource(R.string.content_live_time_range, formatTime(now.startMs), formatTime(now.stopMs)),
                            style = stageText(24, 500), color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        val span = (now.stopMs - now.startMs).toFloat()
                        if (span > 0f) {
                            val progress = ((nowMs - now.startMs) / span).coerceIn(0f, 1f)
                            Box(Modifier.width(126.mpx).height(7.mpx).clip(RoundedCornerShape(4.mpx)).background(Color.White.copy(alpha = 0.3f))) {
                                Box(Modifier.fillMaxWidth(progress).height(7.mpx).background(Color.White))
                            }
                            val left = ((now.stopMs - nowMs).coerceAtLeast(0) + 59_999) / 60_000
                            Text(stringResource(R.string.player_duration_minutes, left.toInt()), style = stageText(24, 500), color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        Text(stringResource(R.string.tvplayer_no_guide), style = stageText(24, 500), color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (now != null) {
                        subtitle?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = stageText(24, 600), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        }
                        // The channel's name takes what the times leave and ends in … rather than past the edge.
                        Text(title.orEmpty(), style = stageText(24, 600), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                }
                guide?.next?.let { next ->
                    Spacer(Modifier.height(11.mpx))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(29.mpx)) {
                        Text(
                            stringResource(R.string.content_live_time_range, formatTime(next.startMs), formatTime(next.stopMs)),
                            style = stageText(24, 500), color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(next.title, style = stageText(24, 500), color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                }
            }
        }
        // The seek line along the bar's foot, in the accent up to now.
        guide?.now?.let { now ->
            val span = (now.stopMs - now.startMs).toFloat()
            if (span > 0f) {
                val progress = ((nowMs - now.startMs) / span).coerceIn(0f, 1f)
                Box(Modifier.align(Alignment.BottomStart).offset(y = 36.mpx).fillMaxWidth().height(5.mpx).background(Color.White.copy(alpha = 0.35f))) {
                    Box(Modifier.fillMaxWidth(progress).height(5.mpx).background(accent))
                }
            }
        }
    }
}

/** [ZapInfoBar] for whatever the engine is playing, read from its metadata like [ChannelCard]. */
@Composable
internal fun ZapInfoBar(player: PlaybackEngine, guide: EpgNowNext?, modifier: Modifier = Modifier) {
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    ZapInfoBar(
        title = meta.title?.takeIf { it.isNotBlank() },
        subtitle = meta.localizedSubtitle(),
        logoUrl = meta.logoUrl,
        guide = guide,
        modifier = modifier,
    )
}
