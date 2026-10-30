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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.format.rememberBestDateFormatter
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.stageAccent

/**
 * The player's card, the same for a channel, a film and an episode: one glass card floating over the
 * bottom of the picture, with the artwork, a small line naming the series or channel in the accent, the
 * title, its details, and on the right what matters next (when the film ends, how long the programme
 * runs). Under it a thin progress line with its times, and a row of words to choose from, the chosen one
 * a white pill. Sized in frame pixels ([ScreenUnits]) so it takes the same share of the picture on any box.
 */

/** The card's fill: dark enough to read on, as the box cannot blur the video behind it. */
private val CardFill = Color(0xFF0E1418).copy(alpha = 0.84f)

/** Text on the white pill. */
private val CardInk = Color(0xFF0B1418)

/** One choice in the card's row. [value] follows the label in a fainter tone (the audio track in use). */
@Immutable
internal class CardItem(
    val key: Any,
    val label: String,
    val value: String? = null,
    val icon: OwnTVIcon? = null,
    /** A round button with only its icon; [label] is then what a screen reader says. */
    val iconOnly: Boolean = false,
    val onClick: () -> Unit,
)

/** What stands at the left of the card's head: a film's poster, or a channel's logo on its white tile. */
@Immutable
internal sealed interface CardArtwork {
    data class Poster(val url: String) : CardArtwork
    data class Logo(val url: String?, val title: String?) : CardArtwork
}

/** The card's type: the main font at [px] frame pixels, without the shadow text on the bare picture needs. */
@Composable
internal fun ScreenUnits.cardText(px: Number, weight: Int): TextStyle = text(px, weight).copy(shadow = null)

/** Numbers that tick keep their width, so a clock or a time does not jitter. */
@Composable
private fun ScreenUnits.cardNumbers(px: Number, weight: Int): TextStyle = cardText(px, weight).copy(fontFeatureSettings = "tnum")

/** The card itself, over a faint shade that keeps the picture's bottom edge from showing through bright. */
@Composable
internal fun PlayerCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val u = rememberScreenUnits()
    val shape = RoundedCornerShape(u.dp(28))
    Box(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f)))
            .padding(start = u.dp(64), end = u.dp(64), top = u.dp(80), bottom = u.dp(56)),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(CardFill)
                .border(u.dp(1), Color.White.copy(alpha = 0.09f), shape)
                .padding(start = u.dp(44), end = u.dp(44), top = u.dp(36), bottom = u.dp(30)),
            verticalArrangement = Arrangement.spacedBy(u.dp(28)),
            content = content,
        )
    }
}

/**
 * The card's head: [artwork] at the left, then [lead] in the accent and [context] after a slash (the
 * series and its season and episode, the channel's number and name), the title, its [details] in a
 * quiet line, and at the right [asideValue] large under its small [asideLabel].
 */
@Composable
internal fun CardHeader(
    title: String,
    details: List<String>,
    modifier: Modifier = Modifier,
    artwork: CardArtwork? = null,
    lead: String? = null,
    context: String? = null,
    asideLabel: String? = null,
    asideValue: String? = null,
) {
    val u = rememberScreenUnits()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u.dp(36))) {
        when (artwork) {
            is CardArtwork.Poster -> CardPoster(artwork.url)
            is CardArtwork.Logo -> CardLogo(artwork.url, artwork.title)
            null -> Unit
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(u.dp(10))) {
            if (lead != null || context != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u.dp(12))) {
                    lead?.let { Text(it, style = u.cardText(18, 600), color = stageAccent.accent, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (lead != null && context != null) Text("/", style = u.cardText(18, 600), color = Color.White.copy(alpha = 0.3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    context?.let { Text(it, style = u.cardText(18, 500), color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
                }
            }
            Text(title, style = u.cardText(44, 600).copy(letterSpacing = u.sp(-0.9)), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (details.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u.dp(18))) {
                    details.forEach {
                        Text(it, style = u.cardText(18, 500), color = Color.White.copy(alpha = 0.62f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                }
            }
        }
        if (asideValue != null) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(u.dp(4))) {
                asideLabel?.let { Text(it, style = u.cardText(16, 500), color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(asideValue, style = u.cardNumbers(32, 500), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A channel's logo on its white tile; the first letters of [title] without one. */
@Composable
private fun CardLogo(logoUrl: String?, title: String?) {
    val u = rememberScreenUnits()
    Box(
        Modifier.size(u.dp(112)).clip(RoundedCornerShape(u.dp(22))).background(Color.White.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(model = logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().padding(u.dp(14)))
        } else {
            Text(title.orEmpty().take(3).uppercase(), style = u.cardText(26, 700), color = Color(0xFF1B2A44), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A film's or series' poster, upright, with a hairline so a dark poster keeps its edge. */
@Composable
private fun CardPoster(url: String) {
    val u = rememberScreenUnits()
    val shape = RoundedCornerShape(u.dp(12))
    AsyncImage(
        model = url, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.size(width = u.dp(112), height = u.dp(168)).clip(shape)
            .background(Color.White.copy(alpha = 0.06f)).border(u.dp(1), Color.White.copy(alpha = 0.08f), shape),
    )
}

/**
 * The thin progress line: white up to the playhead, a short upright tick on it, what is buffered a
 * lighter grey ahead. [focused] (a film's line, where ◀▶ seek) draws it thicker and in the accent.
 * Read while drawing, so the line follows the film without recomposing anything.
 */
@Composable
internal fun CardLine(progress: () -> Float, modifier: Modifier = Modifier, ahead: () -> Float = { 0f }, focused: Boolean = false) {
    val u = rememberScreenUnits()
    val accent = stageAccent.accent
    Canvas(modifier.fillMaxWidth().height(u.dp(18))) {
        val p = progress().coerceIn(0f, 1f)
        val line = u.toPx(if (focused) 5 else 3)
        val top = (size.height - line) / 2
        val radius = CornerRadius(line / 2)
        val fill = if (focused) accent else Color.White
        drawRoundRect(Color.White.copy(alpha = 0.16f), Offset(0f, top), Size(size.width, line), radius)
        val a = ahead().coerceIn(p, 1f)
        if (a > p) drawRoundRect(Color.White.copy(alpha = 0.24f), Offset(0f, top), Size(size.width * a, line), radius)
        drawRoundRect(fill, Offset(0f, top), Size(size.width * p, line), radius)
        val tickW = u.toPx(if (focused) 4 else 3)
        val tickH = u.toPx(if (focused) 26 else 18)
        drawRoundRect(
            fill, Offset(size.width * p - tickW / 2, (size.height - tickH) / 2), Size(tickW, tickH), CornerRadius(tickW / 2),
        )
    }
}

/** The two times under the line: [start] brighter when it is where the picture stands. */
@Composable
internal fun CardTimes(start: String, end: String, startIsNow: Boolean) {
    val u = rememberScreenUnits()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(start, style = u.cardNumbers(16, 500), color = Color.White.copy(alpha = if (startIsNow) 0.88f else 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(end, style = u.cardNumbers(16, 500), color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The card's row of choices under a hairline: [start] from the left (they scroll if there are more than
 * fit, the recent channels say), [end] at the right. A [divider] after that many of [start] separates the
 * tools from the channels. [firstFocus] lands on the first of [start].
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
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(u.dp(1)).background(Color.White.copy(alpha = 0.08f)))
        Spacer(Modifier.height(u.dp(22)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u.dp(24))) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(u.dp(6)),
            ) {
                start.forEachIndexed { i, item ->
                    CardButton(item, if (i == 0) Modifier.focusRequester(firstFocus) else Modifier, onActivity)
                    if (i + 1 == divider && i + 1 < start.size) {
                        Box(Modifier.padding(horizontal = u.dp(10)).width(u.dp(1)).height(u.dp(26)).background(Color.White.copy(alpha = 0.14f)))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u.dp(6))) {
                end.forEach { CardButton(it, Modifier, onActivity) }
            }
        }
    }
}

/**
 * One choice: words on the card, the focused one a white pill with dark words. OK acts on its release,
 * and only for a press that began here: the press that brought the card up lands its release on the
 * first choice, and must not pause the film it just showed.
 */
@Composable
private fun CardButton(item: CardItem, modifier: Modifier, onActivity: () -> Unit) {
    val u = rememberScreenUnits()
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(u.dp(26))
    val ink = if (focused) CardInk else Color.White.copy(alpha = 0.72f)
    Row(
        modifier
            .height(u.dp(52))
            .then(if (item.iconOnly) Modifier.width(u.dp(52)) else Modifier)
            .clip(shape)
            .background(if (focused) Color.White else Color.Transparent)
            .then(if (item.iconOnly && !focused) Modifier.border(u.dp(1), Color.White.copy(alpha = 0.14f), shape) else Modifier)
            .onFocusChanged { focused = it.isFocused; if (!it.isFocused) pressed = false; if (it.isFocused) onActivity() }
            .onKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (ok && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) pressed = true
                if (ok && e.type == KeyEventType.KeyUp) { if (pressed) item.onClick(); pressed = false }
                ok
            }
            .focusable()
            .then(if (item.iconOnly) Modifier.semantics { contentDescription = item.label } else Modifier)
            .padding(horizontal = if (item.iconOnly) u.dp(0) else u.dp(22)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(u.dp(10), Alignment.CenterHorizontally),
    ) {
        item.icon?.let { OwnTVIcon(it, ink, Modifier.size(u.dp(if (item.iconOnly) 22 else 18)), filled = !item.iconOnly) }
        if (!item.iconOnly) {
            Text(item.label, style = u.cardText(19, if (focused) 600 else 500), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.value?.let {
                Text(
                    it, style = u.cardText(19, 500), color = if (focused) CardInk.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.42f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = u.dp(280)),
                )
            }
        }
    }
}

/** Day and date small, the time larger, at the top right while the card is up. */
@Composable
internal fun PanelClock(modifier: Modifier = Modifier) {
    val u = rememberScreenUnits()
    val date = rememberBestDateFormatter("EEEdMMM")
    val time = rememberSystemTimeFormatter()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(60_000 - System.currentTimeMillis() % 60_000); now = System.currentTimeMillis() }
    }
    Row(
        modifier.padding(top = u.dp(44), end = u.dp(64)),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(u.dp(14)),
    ) {
        Text(date(now), style = u.text(18, 500), color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = u.dp(4)))
        Text(time(now), style = u.text(30, 500).copy(fontFeatureSettings = "tnum"), color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
