package tv.own.owntv.player

/** A channel watched lately, for the live card's row: its name and what is on it now ([caption]). */
class PanelTile(
    val key: Any,
    val label: String,
    val logoUrl: String? = null,
    val caption: String? = null,
    val onClick: () -> Unit,
)
