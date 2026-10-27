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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.LocalStagePalette
import tv.own.owntv.ui.theme.StageFocusFill
import tv.own.owntv.ui.theme.StageOnFocus
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

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
 * draws it: Guide and History first, then the channels watched last. Teal tiles, the focused one lighter
 * and a touch larger. [onActivity] keeps the panel up while the user moves along it.
 */
@Composable
internal fun ControlPanelRow(
    tiles: List<PanelTile>,
    firstFocus: FocusRequester,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 86.mpx, vertical = 18.mpx),
        horizontalArrangement = Arrangement.spacedBy(22.mpx),
    ) {
        items(tiles, key = { it.key }) { tile ->
            val first = tile === tiles.first()
            PanelTileView(tile, if (first) Modifier.focusRequester(firstFocus) else Modifier, onActivity)
        }
    }
}

@Composable
private fun PanelTileView(tile: PanelTile, modifier: Modifier, onActivity: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(width = 317.mpx, height = 202.mpx)
            .scale(if (focused) 1.04f else 1f)
            .clip(RoundedCornerShape(11.mpx))
            // The app's focus language: the near-white bar with dark ink, idle tiles in the theme's glass.
            .background(if (focused) StageFocusFill else LocalStagePalette.current.glassTint)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onActivity() }
            .onKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (ok && e.type == KeyEventType.KeyUp) { tile.onClick(); true } else ok
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.mpx, vertical = 18.mpx),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                tile.icon != null -> OwnTVIcon(tile.icon, if (focused) StageOnFocus else Color.White, Modifier.size(72.mpx))
                else -> Box(
                    Modifier.size(width = 137.mpx, height = 86.mpx).clip(RoundedCornerShape(7.mpx)).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!tile.logoUrl.isNullOrBlank()) {
                        AsyncImage(model = tile.logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().padding(9.mpx))
                    } else {
                        Text(tile.label.take(3).uppercase(), style = stageText(20, 800), color = Color(0xFF26363A))
                    }
                }
            }
            Spacer(Modifier.height(14.mpx))
            Text(
                tile.caption?.takeIf { it.isNotBlank() } ?: tile.label,
                style = stageText(tv.own.owntv.ui.theme.StageListText, 600), color = if (focused) StageOnFocus else Color.White, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
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
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 86.mpx, vertical = 14.mpx),
        horizontalArrangement = Arrangement.spacedBy(14.mpx),
    ) {
        items(options.size) { i ->
            SettingChip(options[i], if (i == 0) Modifier.focusRequester(firstFocus) else Modifier, onActivity)
        }
    }
}

@Composable
private fun SettingChip(option: LiveOption, modifier: Modifier, onActivity: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Row(
        modifier
            .height(72.mpx)
            .clip(RoundedCornerShape(11.mpx))
            .background(if (focused) StageFocusFill else LocalStagePalette.current.glassTint)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onActivity() }
            .onKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (ok && e.type == KeyEventType.KeyUp) { option.onClick(); true } else ok
            }
            .focusable()
            .padding(horizontal = 22.mpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.mpx),
    ) {
        OwnTVIcon(option.icon, if (focused) StageOnFocus else Color.White, Modifier.size(32.mpx))
        Text(
            option.label,
            style = stageText(tv.own.owntv.ui.theme.StageListText, 600),
            color = if (focused) StageOnFocus else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        option.value?.let {
            Text(
                it, style = stageText(tv.own.owntv.ui.theme.StageListText, 500),
                color = (if (focused) StageOnFocus else Color.White).copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The arrow under the tile row: there is more below. */
@Composable
internal fun MoreBelowHint(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(bottom = 6.mpx), contentAlignment = Alignment.Center) {
        OwnTVIcon(OwnTVIcon.CHEVRON_DOWN, Color.White.copy(alpha = 0.7f), Modifier.size(28.mpx))
    }
}
