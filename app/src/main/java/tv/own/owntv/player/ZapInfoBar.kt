package tv.own.owntv.player

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.ui.format.combinedDateTimeSkeleton
import tv.own.owntv.ui.format.rememberBestDateFormatter
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.LocalMainFontFamily
import tv.own.owntv.ui.theme.stageAccent

/**
 * Pixels of a 1920×1080 frame on this screen. The player's OK panel is measured off the classic IPTV
 * player's at full HD (Jonathan, 27 Oct 2026), and it must take the same share of the picture on any
 * box, whatever its density or the app's zoom, so it is sized in these rather than in design pixels.
 */
@Immutable
internal class ScreenUnits(private val unit: Dp, private val density: Density) {
    fun dp(px: Number): Dp = unit * px.toFloat()
    fun sp(px: Number): TextUnit = with(density) { dp(px).toSp() }
    fun toPx(px: Number): Float = with(density) { dp(px).toPx() }
}

@Composable
internal fun rememberScreenUnits(): ScreenUnits {
    val width = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current
    return remember(width, density) { ScreenUnits((width / 1920f).dp, density) }
}

/** Type on the picture: the main font at [px] frame pixels, with a tight shadow to lift it off bright video. */
@Composable
internal fun ScreenUnits.text(px: Number, weight: Int): TextStyle = TextStyle(
    fontFamily = LocalMainFontFamily.current,
    fontSize = sp(px),
    fontWeight = FontWeight(weight),
    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, toPx(2)), toPx(4)),
)

/** The dark wash the panel and the zap bar sit on, rising from the bottom edge. */
internal val PanelWash = Brush.verticalGradient(
    0f to Color.Transparent,
    0.22f to Color.Black.copy(alpha = 0.6f),
    1f to Color.Black.copy(alpha = 0.88f),
)

/**
 * The programme block of the info bar: the logo on a white tile at the left, then the programme
 * large, its times, progress and minutes left with the channel and the stream's badges beside them,
 * and the next programme under it. Informational only; it never takes focus.
 */
@Composable
internal fun ZapInfoBlock(
    title: String?,
    subtitle: String?,
    logoUrl: String?,
    guide: EpgNowNext?,
    chips: List<String>,
    nowMs: Long,
    modifier: Modifier = Modifier,
) {
    val u = rememberScreenUnits()
    val formatTime = rememberSystemTimeFormatter()
    val row = u.text(26, 500)
    val muted = Color.White.copy(alpha = 0.85f)
    Row(
        modifier.fillMaxWidth().padding(horizontal = u.dp(92)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(u.dp(74)),
    ) {
        Box(
            Modifier.size(width = u.dp(164), height = u.dp(152)).clip(RoundedCornerShape(u.dp(10))).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (!logoUrl.isNullOrBlank()) {
                AsyncImage(model = logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().padding(u.dp(14)))
            } else {
                Text(title.orEmpty().take(3).uppercase(), style = u.text(34, 800).copy(shadow = null), color = Color(0xFF26363A), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.weight(1f)) {
            val now = guide?.now
            Text(now?.title ?: title.orEmpty(), style = u.text(40, 600), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(u.dp(12)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u.dp(22))) {
                if (now != null) {
                    Text(stringResource(R.string.content_live_time_range, formatTime(now.startMs), formatTime(now.stopMs)), style = row, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val span = (now.stopMs - now.startMs).toFloat()
                    if (span > 0f) {
                        val progress = ((nowMs - now.startMs) / span).coerceIn(0f, 1f)
                        Box(Modifier.width(u.dp(78)).height(u.dp(5)).clip(RoundedCornerShape(u.dp(3))).background(Color.White.copy(alpha = 0.3f))) {
                            Box(Modifier.fillMaxWidth(progress).height(u.dp(5)).background(Color.White))
                        }
                        val left = ((now.stopMs - nowMs).coerceAtLeast(0) + 59_999) / 60_000
                        Text(stringResource(R.string.player_duration_minutes, left.toInt()), style = row, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(stringResource(R.string.tvplayer_no_guide), style = row, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = u.text(26, 600), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // The channel's name takes what the times leave and ends in … rather than past the edge.
                if (now != null) {
                    Text(title.orEmpty(), style = u.text(26, 600), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                chips.forEach { StreamBadge(it, u) }
            }
            guide?.next?.let { next ->
                Spacer(Modifier.height(u.dp(6)))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u.dp(22))) {
                    Text(stringResource(R.string.content_live_time_range, formatTime(next.startMs), formatTime(next.stopMs)), style = row, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(next.title, style = row, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
            }
        }
    }
}

/** FHD, 25 FPS, STEREO: the stream's facts as small outlined badges after the channel. */
@Composable
private fun StreamBadge(label: String, u: ScreenUnits) {
    Box(
        Modifier
            .border(u.dp(1.5f), Color.White.copy(alpha = 0.6f), RoundedCornerShape(u.dp(6)))
            .padding(horizontal = u.dp(8), vertical = u.dp(2)),
    ) {
        Text(label, style = u.text(16, 700), color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The seek line across the picture under the programme block: the accent up to now with a round
 * thumb on it, the rest light grey. Without a programme the room stays, so the tiles do not jump.
 */
@Composable
internal fun ProgrammeSeekLine(guide: EpgNowNext?, nowMs: Long, modifier: Modifier = Modifier) {
    val u = rememberScreenUnits()
    val accent = stageAccent.accent
    val now = guide?.now
    val span = now?.let { (it.stopMs - it.startMs).toFloat() } ?: 0f
    Canvas(modifier.fillMaxWidth().padding(horizontal = u.dp(56)).height(u.dp(16))) {
        if (now == null || span <= 0f) return@Canvas
        val progress = ((nowMs - now.startMs) / span).coerceIn(0f, 1f)
        val line = u.toPx(5)
        val top = (size.height - line) / 2
        val radius = CornerRadius(line / 2)
        drawRoundRect(Color.White.copy(alpha = 0.4f), Offset(0f, top), Size(size.width, line), radius)
        drawRoundRect(accent, Offset(0f, top), Size(size.width * progress, line), radius)
        drawCircle(accent, radius = size.height / 2, center = Offset(size.width * progress, size.height / 2))
    }
}

/** The info bar along the bottom of the picture after a channel change: the programme block and its seek line. */
@Composable
internal fun ZapInfoBar(player: PlaybackEngine, guide: EpgNowNext?, modifier: Modifier = Modifier) {
    val u = rememberScreenUnits()
    // The bar is up for a few seconds, so the moment it opened is precise enough.
    val nowMs = remember(guide) { System.currentTimeMillis() }
    Column(modifier.fillMaxWidth().background(PanelWash).padding(top = u.dp(60), bottom = u.dp(56))) {
        PlayerZapInfoBlock(player, guide, nowMs)
        Spacer(Modifier.height(u.dp(8)))
        ProgrammeSeekLine(guide, nowMs)
    }
}

/** [ZapInfoBlock] for whatever the engine is playing, read from its metadata like [ChannelCard]. */
@Composable
internal fun PlayerZapInfoBlock(player: PlaybackEngine, guide: EpgNowNext?, nowMs: Long, modifier: Modifier = Modifier) {
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val chips by player.streamChips.collectAsStateWithLifecycle()
    ZapInfoBlock(
        title = meta.title?.takeIf { it.isNotBlank() },
        subtitle = meta.localizedSubtitle(),
        logoUrl = meta.logoUrl,
        guide = guide,
        // The aspect and the bitrate are the player's own business; the panel shows FHD, FPS and sound.
        chips = chips.filterNot { it.contains(':') || it.endsWith("Mbps") },
        nowMs = nowMs,
        modifier = modifier,
    )
}

/** Day, date and time at the top right while the info bar is up, as the classic player shows them. */
@Composable
internal fun PanelClock(modifier: Modifier = Modifier) {
    val u = rememberScreenUnits()
    val context = LocalContext.current
    val format = rememberBestDateFormatter(combinedDateTimeSkeleton("EEEdMMM", DateFormat.is24HourFormat(context)))
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(60_000 - System.currentTimeMillis() % 60_000); now = System.currentTimeMillis() }
    }
    Text(
        format(now), style = u.text(36, 500), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(top = u.dp(20), end = u.dp(44)),
    )
}
