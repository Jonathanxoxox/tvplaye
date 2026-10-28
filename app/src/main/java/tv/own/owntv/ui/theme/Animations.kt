package tv.own.owntv.ui.theme

import androidx.annotation.StringRes
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import tv.own.owntv.R
import tv.own.owntv.core.theme.AnimationLevel

/** The user-facing label for each animation level. */
val AnimationLevel.labelRes: Int
    @StringRes get() = when (this) {
        AnimationLevel.FULL -> R.string.common_on
        AnimationLevel.OFF -> R.string.common_off
    }

/** Current animation level, provided at the theme root from the user's setting. */
val LocalAnimationLevel = staticCompositionLocalOf { AnimationLevel.FULL }

/** True unless the user has turned animations fully Off — for spots that gate a transition entirely. */
val animationsOn: Boolean
    @Composable @ReadOnlyComposable get() = LocalAnimationLevel.current != AnimationLevel.OFF

/**
 * A tween whose duration follows the user's Animations setting (Off → an instant 0 ms snap).
 *
 * **Never pass this to `infiniteRepeatable`.** Compose divides the play time by the iteration
 * duration to work out which repeat it is in, so a 0 ms iteration is a divide-by-zero on the main
 * thread one frame after the animation starts. Gate the whole transition on [animationsOn] instead
 * and hand `infiniteRepeatable` a plain fixed-duration `tween`.
 */
@Composable
@ReadOnlyComposable
fun <T> ownTvTween(durationMs: Int = 200, easing: Easing = FastOutSlowInEasing): TweenSpec<T> =
    tween(LocalAnimationLevel.current.scale(durationMs), easing = easing)

/**
 * Soft start, soft landing: the curve the guide's panels and the picture's hand-over slide on. Not the long
 * decelerating tail it had first: its last third crept on by a pixel or two a frame, which read as the
 * picture dragging behind on Jonathan's box (27 Oct 2026).
 */
val StageEase: Easing = androidx.compose.animation.core.CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/**
 * Offscreen while [moving]: a slide then moves a picture of the content drawn once, instead of drawing every
 * row, text and shadow again each frame — which the Nokia 8010's small GPU does not manage at 60 fps. Only
 * while moving, since a video surface inside an offscreen layer would lose its picture.
 */
fun slideCompositing(moving: Boolean): androidx.compose.ui.graphics.CompositingStrategy =
    if (moving) androidx.compose.ui.graphics.CompositingStrategy.Offscreen else androidx.compose.ui.graphics.CompositingStrategy.Auto

/**
 * The guide's slides (menu, groups, picture to and from the corner), [durationMs] long on the television.
 *
 * Compose shortens every animation by the box's animator scale, and many Android TV boxes ship at 0.5× (or
 * are set there to feel faster): 320 ms became 160 ms on Jonathan's, too short to read as motion (27 Oct
 * 2026). The app's own Animations switch decides instead; a scale of 0 (motion off) is still respected.
 */
@Composable
fun <T> stageSlide(durationMs: Int = 280): TweenSpec<T> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scale = androidx.compose.runtime.remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
    }
    val ms = if (scale <= 0f) 0 else LocalAnimationLevel.current.scale((durationMs / scale).toInt())
    return tween(ms, easing = StageEase)
}

/**
 * Comes in the way TiviMate's sheets and menus do, the first time it is drawn: from [x] and [y] (fractions of
 * its own width and height, so -1 is one full width to the left), from [scale], and from nothing when [fade].
 * Only the layer moves, on [stageSlide], so nothing is measured or laid out again while it plays.
 *
 * A [fade] draws each shape with the alpha instead of into a buffer: a buffer is clipped to the layer, which
 * cut the glass's shadow off until the fade was over, and is the Nokia 8010's expensive case. Without a
 * fade the slide is [slideCompositing], unless [offscreen] is false — a sheet whose shadow falls outside it.
 */
@Composable
fun Modifier.stageEnter(
    x: Float = 0f,
    y: Float = 0f,
    scale: Float = 1f,
    fade: Boolean = false,
    offscreen: Boolean = true,
    durationMs: Int = 240,
): Modifier {
    val spec = stageSlide<Float>(durationMs)
    // How much of the way is still to go: 1 at the start, 0 when in place.
    val left = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (spec.durationMillis > 0) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { left.animateTo(0f, spec) }
    return graphicsLayer {
        val l = left.value
        translationX = size.width * x * l
        translationY = size.height * y * l
        if (scale != 1f) { scaleX = 1f - (1f - scale) * l; scaleY = scaleX }
        if (fade) {
            alpha = 1f - l
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
        } else if (offscreen) {
            compositingStrategy = slideCompositing(l > 0f)
        }
    }
}

/**
 * [content] slides in from [x] / [y] (fractions of its own size) while [visible], and back out the same way
 * when it turns false, as TiviMate's info bar rises from the bottom and sinks away again. For parts that do
 * not hold focus; a sheet that does comes in with [stageEnter] and leaves at once, so focus never sits on
 * something on its way out.
 */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun StageAppear(
    visible: Boolean,
    modifier: Modifier = Modifier,
    x: Float = 0f,
    y: Float = 0f,
    durationMs: Int = 240,
    content: @Composable () -> Unit,
) {
    val spec = stageSlide<Float>(durationMs)
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = androidx.compose.animation.EnterTransition.None,
        exit = androidx.compose.animation.ExitTransition.None,
    ) {
        val shown by transition.animateFloat({ spec }) {
            if (it == androidx.compose.animation.EnterExitState.Visible) 1f else 0f
        }
        androidx.compose.foundation.layout.Box(
            Modifier.graphicsLayer {
                val l = 1f - shown
                translationX = size.width * x * l
                translationY = size.height * y * l
                compositingStrategy = slideCompositing(l > 0f)
            },
        ) { content() }
    }
}
