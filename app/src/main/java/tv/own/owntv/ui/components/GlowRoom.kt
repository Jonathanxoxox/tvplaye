package tv.own.owntv.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/**
 * A list clips whatever is drawn outside it, which cuts the focused row's or poster's glow. This lays
 * the list out [room] wider at each side; the list pads its content back by the same [room], so the
 * items sit exactly where they would without it and the glow has room to show.
 */
fun Modifier.glowRoom(room: Dp): Modifier = layout { measurable, constraints ->
    val extra = room.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra * 2, maxWidth = constraints.maxWidth + extra * 2))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-extra, 0) }
}
