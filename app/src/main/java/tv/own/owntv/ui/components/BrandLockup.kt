package tv.own.owntv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.own.owntv.R
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.brand.AppIconSwitcher
import tv.own.owntv.ui.theme.StageColors

/**
 * The icon colour the launcher shows right now. The in-app logo follows it, not the saved choice, so
 * logo and home-screen icon change together after the restart.
 */
@Composable
fun rememberAppliedIcon(): AppIcon {
    val context = LocalContext.current
    return remember(context) { AppIconSwitcher.applied(context) }
}

/**
 * The app's mark at [size]: the floodlight. Mayo TV has one mark, whatever icon colour core keeps on record.
 * [subtle] draws it in the theme's muted ink instead of its own colours, so it sits in the page rather than on it.
 */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier, subtle: Boolean = false) {
    if (subtle) {
        Image(
            painterResource(R.drawable.flutlicht_mark_mono), contentDescription = null, modifier = modifier.size(size),
            alpha = 0.9f, colorFilter = ColorFilter.tint(StageColors.Muted, BlendMode.SrcIn),
        )
    } else {
        Image(painterResource(R.drawable.flutlicht_mark), contentDescription = null, modifier = modifier.size(size))
    }
}

/**
 * How far in the letters of the wordmark start: the picture keeps room for the glow on both sides (335 of
 * 1806 px). Next to other things, shift it left by this × its width so "M" lines up with what is beside or below it.
 */
const val WordmarkInset = 335f / 1806f

/** The wordmark at [width]: the cream "Mayo TV" with a warm glow, one picture of 903 × 180. [subtle] as for [BrandMark]. */
@Composable
fun Wordmark(width: Dp, modifier: Modifier = Modifier, subtle: Boolean = false) {
    val size = modifier.width(width).height(width * 180f / 903f)
    if (subtle) {
        Image(
            painterResource(R.drawable.flutlicht_wordmark_mono), contentDescription = null, modifier = size,
            alpha = 0.6f, colorFilter = ColorFilter.tint(StageColors.Muted, BlendMode.SrcIn),
        )
    } else {
        Image(painterResource(R.drawable.flutlicht_wordmark), contentDescription = null, modifier = size)
    }
}

/**
 * The lockup (Brand tab, C column): the mark, then the wordmark at 2.75 × the mark's width (62 →
 * 170 in the design). Side by side the gap is 0.23 × the mark (14 / 62); [stacked] (the preview pane)
 * puts the mark above with a 0.2 × gap.
 */
@Composable
fun BrandLockup(
    modifier: Modifier = Modifier,
    markSize: Int = 36,
    stacked: Boolean = false,
    subtle: Boolean = true,
) {
    val wordWidth = (markSize * 2.75f).dp
    val parts = @Composable {
        BrandMark(markSize.dp, subtle = subtle)
        // Side by side the glow margin would double the gap; stacked, the letters are centred in the picture anyway.
        Wordmark(wordWidth, if (stacked) Modifier else Modifier.offset(x = -wordWidth * WordmarkInset), subtle = subtle)
    }
    if (stacked) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((markSize * 0.2f).dp),
        ) { parts() }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((markSize * 0.23f).dp),
        ) { parts() }
    }
}
