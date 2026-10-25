package tv.own.owntv.player

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.stage.StagePopupIcon
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.gradientWash
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/** One row of the options panel: [value] is drawn at the row's end (the engine in use, say). */
internal data class LiveOption(
    val label: String,
    val icon: OwnTVIcon,
    val value: String? = null,
    val onClick: () -> Unit,
)

/**
 * The live player's options as a panel down the right edge, over the running picture: favourite,
 * tracks, picture format, recording and the rest, one row each. Long OK or the Menu key opens it.
 *
 * A real popup window like the HUD's dialogs, so it owns D-pad focus and nothing behind it can take
 * it. Back closes it; a row's action decides itself whether the panel stays open.
 */
@Composable
internal fun LiveOptionsPanel(
    title: String?,
    options: List<LiveOption>,
    /** Opened by holding OK: swallow OK until that press is released, or its repeats pick a row. */
    ignoreOkUntilReleased: Boolean,
    onDismiss: () -> Unit,
) {
    var okArmed by remember { mutableStateOf(!ignoreOkUntilReleased) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { first.requestFocus() } }
    OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        Box(
            Modifier.fillMaxSize()
                .gradientWash(
                    vertical = false,
                    0.45f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.55f),
                )
                .onPreviewKeyEvent { e ->
                    val isOk = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    if (!okArmed && isOk) {
                        if (e.type == KeyEventType.KeyUp) okArmed = true
                        true
                    } else false
                }
                .trapAllFocusExit().focusGroup(),
        ) {
            Column(
                Modifier.align(Alignment.CenterEnd)
                    .padding(horizontal = 20.mpx, vertical = 22.mpx)
                    .fillMaxHeight()
                    .width(560.mpx)
                    .stageGlass(30.mpx, overContent = true)
                    .padding(24.mpx),
                verticalArrangement = Arrangement.spacedBy(14.mpx),
            ) {
                if (title != null) {
                    Text(
                        title, style = stageText(30, 800), color = StageColors.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.mpx, bottom = 4.mpx),
                    )
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.mpx)) {
                    itemsIndexed(options) { i, option ->
                        StagePopupOption(
                            title = option.label,
                            onClick = option.onClick,
                            value = option.value,
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            leading = { StagePopupIcon(option.icon) },
                        )
                    }
                }
            }
        }
    }
}
