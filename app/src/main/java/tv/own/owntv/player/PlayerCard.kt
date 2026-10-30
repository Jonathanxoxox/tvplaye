package tv.own.owntv.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.format.rememberBestDateFormatter
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.stageAccent

/**
 * The player's band, the same for a channel, a film and an episode (Version A, "Schlank", Jonathan,
 * 30 Oct 2026): no card, the words stand on a soft shade rising from the bottom of the picture. A small
 * line in capitals names the series or channel, the title, a quiet line of facts with the stream's badges,
 * and at the right what follows. Under it the thin progress line between its two times, and a row of round
 * glass buttons and pills, the chosen one white. Sized in frame pixels ([ScreenUnits]) so it takes the same
 * share of the picture on any box.
 */

/** Text and icons on the white focus. */
private val BandInk = Color(0xFF0B1418)

/** How a choice in the band's row is drawn. */
internal enum class CardItemStyle {
    /** A round glass button with its icon; its name shows under it while it is focused. */
    ROUND,
    /** A pill with an optional icon, the name and a fainter [CardItem.value] (the track in use). */
    PILL,
    /** A channel watched lately: its logo in a circle, the name, what is on it ([CardItem.value]). */
    CHANNEL,
}

/** One choice in the band's row. */
@Immutable
internal class CardItem(
    val key: Any,
    val label: String,
    val value: String? = null,
    val icon: OwnTVIcon? = null,
    val style: CardItemStyle = CardItemStyle.PILL,
    val logoUrl: String? = null,
    val onClick: () -> Unit,
)

/** The band's type: the main font at [px] frame pixels, without a shadow (the shade gives the contrast). */
@Composable
internal fun ScreenUnits.cardText(px: Number, weight: Int): TextStyle = text(px, weight).copy(shadow = null)

/** Numbers that tick keep their width, so a clock or a time does not jitter. */
@Composable
private fun ScreenUnits.cardNumbers(px: Number, weight: Int): TextStyle = cardText(px, weight).copy(fontFeatureSettings = "tnum")

/** The band itself: its content on a shade that rises from the bottom edge of the picture. */
@Composable
internal fun PlayerCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val u = rememberScreenUnits()
    Column(
        modifier.fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to Color(0xFF04080A).copy(alpha = 0.55f),
                    1f to Color(0xFF04080A).copy(alpha = 0.86f),
                ),
            )
            .padding(start = u.dp(96), end = u.dp(96), top = u.dp(150), bottom = u.dp(72)),
        content = content,
    )
}

/**
 * The band's head: [logo] (a channel's) at the left; [eyebrow] in small capitals, the [title] and a line of
 * [facts] with the stream's [badges]; at the right [nextLabel] in small capitals over [nextValue].
 */
@Composable
internal fun CardHeader(
    title: String,
    facts: List<String>,
    modifier: Modifier = Modifier,
    badges: List<String> = emptyList(),
    eyebrow: String? = null,
    logo: CardLogo? = null,
    nextLabel: String? = null,
    nextValue: String? = null,
) {
    val u = rememberScreenUnits()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(u.dp(48))) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(u.dp(28))) {
            logo?.let { LogoTile(it) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(u.dp(14))) {
                eyebrow?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it.uppercase(), style = u.cardText(17, 600).copy(letterSpacing = u.sp(2.4)),
                        color = Color.White.copy(alpha = 0.62f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(title, style = u.cardText(48, 500).copy(letterSpacing = u.sp(-0.7)), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (facts.isNotEmpty() || badges.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u.dp(14))) {
                        facts.forEachIndexed { i, fact ->
                            if (i > 0) Box(Modifier.size(u.dp(4)).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)))
                            Text(fact, style = u.cardNumbers(20, 400), color = Color.White.copy(alpha = 0.74f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        }
                        badges.forEach { Badge(it) }
                    }
                }
            }
        }
        if (nextValue != null) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(u.dp(6)), modifier = Modifier.widthIn(max = u.dp(520)).padding(bottom = u.dp(4))) {
                nextLabel?.let {
                    Text(it.uppercase(), style = u.cardText(15, 600).copy(letterSpacing = u.sp(2.1)), color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(nextValue, style = u.cardText(20, 400), color = Color.White.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A channel's logo for the band's head; the first letters of [title] stand in without one. */
@Immutable
internal data class CardLogo(val url: String?, val title: String?)

@Composable
private fun LogoTile(logo: CardLogo) {
    val u = rememberScreenUnits()
    Box(
        Modifier.size(u.dp(96)).clip(RoundedCornerShape(u.dp(18))).background(Color.White.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!logo.url.isNullOrBlank()) {
            AsyncImage(model = logo.url, contentDescription = null, modifier = Modifier.fillMaxSize().padding(u.dp(12)))
        } else {
            Text(logo.title.orEmpty().take(3).uppercase(), style = u.cardText(22, 600), color = Color(0xFF1B2A44), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** FHD, HDR, 5.1: the stream's facts as small outlined badges after the line of facts. */
@Composable
private fun Badge(label: String) {
    val u = rememberScreenUnits()
    Box(
        Modifier.border(u.dp(1), Color.White.copy(alpha = 0.34f), RoundedCornerShape(u.dp(5))).padding(horizontal = u.dp(7), vertical = u.dp(3)),
    ) {
        Text(label, style = u.cardText(13, 600).copy(letterSpacing = u.sp(1)), color = Color.White.copy(alpha = 0.86f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The progress line between its two times: [start] at the left, [end] at the right, the accent up to the
 * playhead with a round white thumb on it, what is buffered a lighter grey ahead. [focused] (a film's line,
 * where ◀▶ seek) draws it thicker with a larger thumb. The line is read while drawing, so it follows the film
 * without recomposing anything; [lineModifier] goes on the line itself (its focus and keys).
 */
@Composable
internal fun CardLine(
    start: String,
    end: String,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    lineModifier: Modifier = Modifier,
    ahead: () -> Float = { 0f },
    focused: Boolean = false,
) {
    val u = rememberScreenUnits()
    val accent = stageAccent.accent
    Row(modifier.fillMaxWidth().padding(top = u.dp(30)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u.dp(22))) {
        Text(start, style = u.cardNumbers(18, 400), color = Color.White.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(u.dp(92)))
        Canvas(lineModifier.weight(1f).height(u.dp(22))) {
            val p = progress().coerceIn(0f, 1f)
            val line = u.toPx(if (focused) 6 else 4)
            val top = (size.height - line) / 2
            val radius = CornerRadius(line / 2)
            drawRoundRect(Color.White.copy(alpha = if (focused) 0.24f else 0.18f), Offset(0f, top), Size(size.width, line), radius)
            val a = ahead().coerceIn(p, 1f)
            if (a > p) drawRoundRect(Color.White.copy(alpha = 0.26f), Offset(0f, top), Size(size.width * a, line), radius)
            drawRoundRect(accent, Offset(0f, top), Size(size.width * p, line), radius)
            val thumb = Offset(size.width * p, size.height / 2)
            val r = u.toPx(if (focused) 11 else 7)
            drawCircle(Color.Black.copy(alpha = 0.25f), radius = r + u.toPx(4), center = thumb)
            drawCircle(Color.White, radius = r, center = thumb)
        }
        Text(end, style = u.cardNumbers(18, 400), color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(u.dp(92)), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/**
 * The band's row of choices: [start] from the left (they scroll if there are more than fit, as the recent
 * channels may), [end] at the right. A [divider] after that many of [start] separates the controls from the
 * tracks or the tools from the channels. [firstFocus] lands on the first of [start].
 */
@Composable
internal fun CardMenu(
    start: List<CardItem>,
    end: List<CardItem>,
    firstFocus: FocusRequester,
    onActivity: () -> Unit,
    divider: Int = -1,
) {
    val u = rememberScreenUnits()
    Row(Modifier.fillMaxWidth().padding(top = u.dp(34)), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(u.dp(24))) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(u.dp(16)),
        ) {
            start.forEachIndexed { i, item ->
                CardButton(item, if (i == 0) Modifier.focusRequester(firstFocus) else Modifier, onActivity)
                if (i + 1 == divider && i + 1 < start.size) {
                    Box(Modifier.padding(start = u.dp(8), end = u.dp(8), top = u.dp(14)).width(u.dp(1)).height(u.dp(32)).background(Color.White.copy(alpha = 0.18f)))
                }
            }
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(u.dp(16))) {
            end.forEach { CardButton(it, Modifier, onActivity) }
        }
    }
}

/**
 * One choice, in its [CardItemStyle]: glass while idle, white with near-black ink while focused. OK acts on
 * its release, and only for a press that began here: the press that brought the band up lands its release
 * on the first choice, and must not pause the film it just showed.
 */
@Composable
private fun CardButton(item: CardItem, modifier: Modifier, onActivity: () -> Unit) {
    val u = rememberScreenUnits()
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val round = item.style == CardItemStyle.ROUND
    val shape = RoundedCornerShape(u.dp(30))
    val ink = if (focused) BandInk else Color.White.copy(alpha = 0.88f)
    val faint = if (focused) BandInk.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.52f)
    val face = modifier
        .height(u.dp(60))
        .then(if (round) Modifier.width(u.dp(60)) else Modifier)
        .clip(shape)
        .background(if (focused) Color.White else Color.White.copy(alpha = 0.08f))
        .then(if (focused) Modifier else Modifier.border(u.dp(1), Color.White.copy(alpha = 0.14f), shape))
        .onFocusChanged { focused = it.isFocused; if (!it.isFocused) pressed = false; if (it.isFocused) onActivity() }
        .onKeyEvent { e ->
            val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
            if (ok && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) pressed = true
            if (ok && e.type == KeyEventType.KeyUp) { if (pressed) item.onClick(); pressed = false }
            ok
        }
        .focusable()
    if (round) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(u.dp(10))) {
            Box(face.semantics { contentDescription = item.label }, contentAlignment = Alignment.Center) {
                item.icon?.let { OwnTVIcon(it, ink, Modifier.size(u.dp(22)), filled = focused) }
            }
            // Room kept for the name, so the row does not jump when focus moves along it.
            Text(
                item.label, style = u.cardText(15, 500), color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(if (focused) 1f else 0f).widthIn(max = u.dp(160)),
            )
        }
        return
    }
    val channel = item.style == CardItemStyle.CHANNEL
    Row(
        face.padding(start = u.dp(if (channel) 8 else 24), end = u.dp(if (channel) 22 else 24)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(u.dp(12)),
    ) {
        if (channel) {
            Box(Modifier.size(u.dp(44)).clip(CircleShape).background(Color.White.copy(alpha = 0.94f)), contentAlignment = Alignment.Center) {
                if (!item.logoUrl.isNullOrBlank()) {
                    AsyncImage(model = item.logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().padding(u.dp(6)))
                } else {
                    Text(item.label.take(4).uppercase(), style = u.cardText(12, 600), color = Color(0xFF1B2A44), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(u.dp(2))) {
                Text(item.label, style = u.cardText(17, if (focused) 600 else 500), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = u.dp(240)))
                item.value?.let { Text(it, style = u.cardText(14, 400), color = faint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = u.dp(240))) }
            }
        } else {
            item.icon?.let { OwnTVIcon(it, ink, Modifier.size(u.dp(20))) }
            Text(item.label, style = u.cardText(18, if (focused) 600 else 400), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.value?.let { Text(it, style = u.cardText(18, 400), color = faint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = u.dp(260))) }
        }
    }
}

/** Day, date and time at the top right while the band is up, on one quiet line. */
@Composable
internal fun PanelClock(modifier: Modifier = Modifier) {
    val u = rememberScreenUnits()
    val date = rememberBestDateFormatter("EEEdMMM")
    val time = rememberSystemTimeFormatter()
    val separator = stringResource(R.string.content_metadata_separator)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(60_000 - System.currentTimeMillis() % 60_000); now = System.currentTimeMillis() }
    }
    Text(
        date(now) + separator + time(now),
        style = u.text(26, 400).copy(fontFeatureSettings = "tnum"), color = Color.White.copy(alpha = 0.82f),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(top = u.dp(48), end = u.dp(72)),
    )
}
