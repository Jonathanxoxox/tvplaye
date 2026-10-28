package tv.own.owntv.ui.components

import tv.own.owntv.core.ui.stage.StageGlyph

/**
 * The Stage line icons, as SVG path data on the same 24×24 grid [OwnTVIcon] draws on. The
 * paths live in core ([StageGlyph], shared with the phone); its names are the [OwnTVIcon] entries. The
 * phone-only marks (PLUS, MOON) have no entry here, so the TV keeps drawing ADD and BEDTIME its own way.
 */
internal val StageGlyphPaths: Map<OwnTVIcon, String> = StageGlyph.entries.mapNotNull { glyph ->
    OwnTVIcon.entries.firstOrNull { it.name == glyph.name }?.let { it to (MayoGlyphPaths[it] ?: glyph.path) }
}.toMap()

/**
 * Mayo TV's own drawing of a core glyph, used in its place. Sport is football here, so a ball
 * replaces the trophy; Serien is a picture with a second episode behind it, so it no longer reads
 * as a stack of lids next to Live-TV's set.
 */
private val MayoGlyphPaths: Map<OwnTVIcon, String> = mapOf(
    OwnTVIcon.SPORTS to "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0Z" +
        "M12 8l3.8 2.76-1.45 4.48h-4.7l-1.45-4.48Z" +
        "M12 8V3M15.8 10.76l4.76-1.54M14.35 15.24l2.94 4.04M9.65 15.24l-2.94 4.04M8.2 10.76l-4.76-1.54",
    OwnTVIcon.SERIES to "M4 8h11a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2Z" +
        "M6.5 4.5H19a2 2 0 0 1 2 2V16" +
        "M8 12v5l4.2-2.5Z",
)
