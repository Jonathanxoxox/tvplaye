package tv.own.owntv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.LocalStagePalette
import tv.own.owntv.ui.theme.StageFocusFill
import tv.own.owntv.ui.theme.StageOnFocus
import tv.own.owntv.ui.theme.stageAccent

/** One tile of the player's control panel: a tool (icon) or a recent channel (logo plus what is on). */
class PanelTile(
    val key: Any,
    val label: String,
    val icon: OwnTVIcon? = null,
    val logoUrl: String? = null,
    val caption: String? = null,
    val onClick: () -> Unit,
)


/**
 * The row of tiles under the info bar that OK brings up in the live player, as the classic IPTV player
 * draws it: Guide and History first, then the channels watched last. Tiles in a see-through accent,
 * the focused one stronger and a touch larger. Sized in frame pixels ([ScreenUnits]) so the row takes
 * the same share of the picture as in the classic player. [onActivity] keeps the panel up while the
 * user moves along it.
 */
@Composable
internal fun ControlPanelRow(
    tiles: List<PanelTile>,
    firstFocus: FocusRequester,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val u = rememberScreenUnits()
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = u.dp(54), end = u.dp(54), top = u.dp(22), bottom = u.dp(14)),
        horizontalArrangement = Arrangement.spacedBy(u.dp(16)),
    ) {
        items(tiles, key = { it.key }) { tile ->
            val first = tile === tiles.first()
            PanelTileView(tile, u, if (first) Modifier.focusRequester(firstFocus) else Modifier, onActivity)
        }
    }
}

@Composable
private fun PanelTileView(tile: PanelTile, u: ScreenUnits, modifier: Modifier, onActivity: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    // OK acts on its release, and only for a press that began on this tile: the press that brought the
    // film's panel up lands its release here, and must not pause the film it just showed.
    var pressed by remember { mutableStateOf(false) }
    val accent = stageAccent.accent
    Box(
        modifier
            .size(width = u.dp(236), height = u.dp(172))
            .scale(if (focused) 1.08f else 1f)
            .clip(RoundedCornerShape(u.dp(8)))
            .background(accent.copy(alpha = if (focused) 0.62f else 0.3f))
            .onFocusChanged { focused = it.isFocused; if (!it.isFocused) pressed = false; if (it.isFocused) onActivity() }
            .onKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (ok && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) pressed = true
                if (ok && e.type == KeyEventType.KeyUp) { if (pressed) tile.onClick(); pressed = false }
                ok
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = u.dp(12), vertical = u.dp(14)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (tile.icon != null) {
                OwnTVIcon(tile.icon, Color.White, Modifier.size(u.dp(70)))
                Spacer(Modifier.height(u.dp(12)))
                Text(tile.label, style = u.text(26, 600), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            } else {
                Box(
                    Modifier.size(width = u.dp(104), height = u.dp(98)).clip(RoundedCornerShape(u.dp(6))).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!tile.logoUrl.isNullOrBlank()) {
                        AsyncImage(model = tile.logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().padding(u.dp(8)))
                    } else {
                        Text(tile.label.take(3).uppercase(), style = u.text(24, 800).copy(shadow = null), color = Color(0xFF26363A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(u.dp(12)))
                // What is on, in a light accent like the classic player; the name if the guide has nothing.
                Text(
                    tile.caption?.takeIf { it.isNotBlank() } ?: tile.label,
                    style = u.text(22, 600), color = lerp(accent, Color.White, 0.55f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The lower half of the player's control panel, the same for a channel and a film: the tile row and,
 * on ▼, the settings under it; ▲ folds them away again. Until then the arrow says they are there.
 */
@Composable
internal fun ControlPanelRows(
    tiles: List<PanelTile>,
    focus: FocusRequester,
    settings: List<LiveOption>,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val u = rememberScreenUnits()
    var settingsOpen by remember { mutableStateOf(false) }
    val settingsFocus = remember { FocusRequester() }
    LaunchedEffect(settingsOpen) {
        if (settingsOpen) { withFrameNanos { }; runCatching { settingsFocus.requestFocus() } }
    }
    Column(modifier) {
        if (tiles.isNotEmpty()) {
            ControlPanelRow(
                tiles, focus, onActivity = onActivity,
                // ▼ from the tiles opens the settings under them.
                modifier = Modifier.onPreviewKeyEvent { e ->
                    if (e.key == Key.DirectionDown && settings.isNotEmpty()) {
                        if (e.type == KeyEventType.KeyDown) { settingsOpen = true; onActivity() }
                        true
                    } else false
                },
            )
        }
        when {
            settings.isEmpty() -> Spacer(Modifier.height(u.dp(60)))
            settingsOpen -> SettingsPanelRow(
                settings, settingsFocus, onActivity,
                // ▲ back to the tiles; the settings fold away again.
                modifier = Modifier.onPreviewKeyEvent { e ->
                    if (e.key == Key.DirectionUp && tiles.isNotEmpty()) {
                        if (e.type == KeyEventType.KeyDown) { settingsOpen = false; runCatching { focus.requestFocus() }; onActivity() }
                        true
                    } else false
                },
            )
            else -> MoreBelowHint()
        }
    }
}

/**
 * The player's settings under the tile row, as in TiviMate: ▼ from the tiles opens them (picture format,
 * tracks, subtitles and the rest), ▲ goes back. Until then a small arrow under the tiles says they are
 * there (Jonathan, 27 Oct 2026). The same entries as the options panel that holding OK opens.
 */
@Composable
internal fun SettingsPanelRow(
    options: List<LiveOption>,
    firstFocus: FocusRequester,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val u = rememberScreenUnits()
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = u.dp(54), end = u.dp(54), top = u.dp(8), bottom = u.dp(30)),
        horizontalArrangement = Arrangement.spacedBy(u.dp(14)),
    ) {
        items(options.size) { i ->
            SettingChip(options[i], u, if (i == 0) Modifier.focusRequester(firstFocus) else Modifier, onActivity)
        }
    }
}

@Composable
private fun SettingChip(option: LiveOption, u: ScreenUnits, modifier: Modifier, onActivity: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Row(
        modifier
            .height(u.dp(76))
            .clip(RoundedCornerShape(u.dp(8)))
            .background(if (focused) StageFocusFill else LocalStagePalette.current.glassTint)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onActivity() }
            .onKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (ok && e.type == KeyEventType.KeyUp) { option.onClick(); true } else ok
            }
            .focusable()
            .padding(horizontal = u.dp(24)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(u.dp(12)),
    ) {
        OwnTVIcon(option.icon, if (focused) StageOnFocus else Color.White, Modifier.size(u.dp(34)))
        Text(
            option.label,
            style = u.text(24, 600).copy(shadow = null),
            color = if (focused) StageOnFocus else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        option.value?.let {
            Text(
                it, style = u.text(24, 500).copy(shadow = null),
                color = (if (focused) StageOnFocus else Color.White).copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The arrow under the tile row: there is more below. */
@Composable
internal fun MoreBelowHint(modifier: Modifier = Modifier) {
    val u = rememberScreenUnits()
    Box(modifier.fillMaxWidth().padding(top = u.dp(10), bottom = u.dp(36)), contentAlignment = Alignment.Center) {
        OwnTVIcon(OwnTVIcon.CHEVRON_DOWN, Color.White.copy(alpha = 0.8f), Modifier.size(u.dp(28)))
    }
}
