package tv.own.owntv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.hudInk
import tv.own.owntv.ui.theme.hudShade
import tv.own.owntv.ui.theme.stageAccent
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 28.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(hudShade(0.82f))
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier.size(width = 110.dp, height = 70.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!logoUrl.isNullOrBlank()) {
                AsyncImage(model = logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().padding(6.dp))
            } else {
                Text(title.orEmpty().take(3).uppercase(), style = stageText(24, 800), color = hudInk.copy(alpha = 0.7f))
            }
        }
        Column(Modifier.width(300.dp)) {
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = stageText(20, 600), color = hudInk.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(title.orEmpty(), style = stageText(28, 700), color = hudInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.weight(1f)) {
            val now = guide?.now
            if (now != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        stringResource(R.string.content_live_time_range, formatTime(now.startMs), formatTime(now.stopMs)),
                        style = stageText(20, 600), color = hudInk.copy(alpha = 0.6f), maxLines = 1,
                    )
                    Text(now.title, style = stageText(25, 600), color = hudInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val span = (now.stopMs - now.startMs).toFloat()
                if (span > 0f) {
                    val progress = ((nowMs - now.startMs) / span).coerceIn(0f, 1f)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(hudInk.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                    }
                }
            } else {
                Text(stringResource(R.string.tvplayer_no_guide), style = stageText(22, 500), color = hudInk.copy(alpha = 0.5f), maxLines = 1)
            }
            guide?.next?.let { next ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.tvplayer_next_at, formatTime(next.startMs)), style = stageText(20, 600), color = hudInk.copy(alpha = 0.45f), maxLines = 1)
                    Text(next.title, style = stageText(22, 500), color = hudInk.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val clockTime = remember(nowMs) { formatTime(nowMs) }
        Text(clockTime, style = stageText(30, 700), color = hudInk.copy(alpha = 0.85f), maxLines = 1)
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
