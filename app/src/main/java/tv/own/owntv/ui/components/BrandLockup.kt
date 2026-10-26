package tv.own.owntv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.own.owntv.R
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.brand.AppIconSwitcher

/**
 * The icon colour the launcher shows right now. The in-app logo follows it, not the saved choice, so
 * logo and home-screen icon change together after the restart.
 */
@Composable
fun rememberAppliedIcon(): AppIcon {
    val context = LocalContext.current
    return remember(context) { AppIconSwitcher.applied(context) }
}

/** The app's mark at [size]: the floodlight. Flutlicht has one mark, whatever icon colour core keeps on record. */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.flutlicht_mark), contentDescription = null, modifier = modifier.size(size))
}

/** The wordmark at [width]: the cream "Flutlicht" whose i-dot is a glowing floodlight lamp, one picture of 903 × 180. */
@Composable
fun Wordmark(width: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.flutlicht_wordmark), contentDescription = null, modifier = modifier.width(width).height(width * 180f / 903f))
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
) {
    val parts = @Composable {
        BrandMark(markSize.dp)
        Wordmark((markSize * 2.75f).dp)
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
