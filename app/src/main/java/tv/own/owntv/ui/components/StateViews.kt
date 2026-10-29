package tv.own.owntv.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.ui.theme.ownTvTween

/** A picture for [LandmarkLoader]. One with [ownColours] is shown as it is instead of in the accent: a crest is its colours. */
private class Landmark(@DrawableRes val res: Int, val ownColours: Boolean = false)

/** The pictures [LandmarkLoader] goes through, tall and wide by turns. All are 120 × 80; the buildings stand on one ground line. */
private val Landmarks = arrayOf(
    Landmark(R.drawable.landmark_elbphilharmonie),
    Landmark(R.drawable.landmark_stadtmusikanten),
    Landmark(R.drawable.landmark_volksparkstadion),
    Landmark(R.drawable.landmark_hsv_raute, ownColours = true),
    Landmark(R.drawable.landmark_michel),
    Landmark(R.drawable.landmark_koehlbrandbruecke),
    Landmark(R.drawable.landmark_roter_sand),
    Landmark(R.drawable.landmark_holstentor),
    Landmark(R.drawable.landmark_rickmer_rickmers),
)

/** How long one landmark stands before the next fades in, and how long that fade takes. */
private const val LandmarkHoldMs = 1600L
private const val LandmarkFadeMs = 450

/**
 * The next landmark any loader shows. Shared, so the tour goes on from one wait to the next: a channel that
 * loads in a second shows a new picture each time instead of the Elbphilharmonie every time.
 */
private var nextLandmark = 0

private fun takeLandmark(): Int = nextLandmark.also { nextLandmark = (it + 1) % Landmarks.size }

/**
 * What the app shows while it waits: the Elbphilharmonie, the Bremen Town Musicians, the Volksparkstadion,
 * the HSV crest and more landmarks of the north, one after the other, in the accent (the crest in its own
 * colours). [sizeDp] is its height; it is half as wide again, as the pictures are.
 *
 * It took the place of a spinning ring (29 Oct 2026, Jonathan), which stuttered whenever the box was busy —
 * and loading is exactly when it is. A picture that stands still and fades to the next every 1.6 s has
 * nothing to stutter between fades. With Animations off the pictures change without the fade.
 */
@Composable
fun LandmarkLoader(
    modifier: Modifier = Modifier,
    sizeDp: Int = 44,
    color: Color = tv.own.owntv.ui.theme.stageAccent.accent,
) {
    var shown by remember { mutableIntStateOf(takeLandmark()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(LandmarkHoldMs)
            shown = takeLandmark()
        }
    }
    Crossfade(
        targetState = shown,
        modifier = modifier.size(width = (sizeDp * 1.5f).dp, height = sizeDp.dp),
        animationSpec = ownTvTween(LandmarkFadeMs),
        label = "landmark",
    ) { index ->
        val landmark = Landmarks[index]
        Image(
            painterResource(landmark.res),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            colorFilter = if (landmark.ownColours) null else ColorFilter.tint(color),
        )
    }
}
