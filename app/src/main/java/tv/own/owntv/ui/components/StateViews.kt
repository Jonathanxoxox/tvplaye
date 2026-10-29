package tv.own.owntv.ui.components

import android.animation.ValueAnimator
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import tv.own.owntv.R
import tv.own.owntv.ui.theme.animationsOn

/** A picture for [LandmarkLoader]. One with [ownColours] is shown as it is instead of in the accent: a crest is its colours. */
private class Landmark(@DrawableRes val res: Int, val ownColours: Boolean = false)

/** The pictures [LandmarkLoader] shows. All are 120 × 80; the buildings stand on one ground line. */
private val Landmarks = listOf(
    Landmark(R.drawable.landmark_elbphilharmonie),
    Landmark(R.drawable.landmark_stadtmusikanten),
    Landmark(R.drawable.landmark_volksparkstadion),
    Landmark(R.drawable.landmark_hsv_raute, ownColours = true),
    Landmark(R.drawable.landmark_michel),
    Landmark(R.drawable.landmark_koehlbrandbruecke),
    Landmark(R.drawable.landmark_roter_sand),
    Landmark(R.drawable.landmark_rickmer_rickmers),
)

/**
 * One picture's turn at an animator scale of 1, as the vectors play it on the render thread: fade in, stand,
 * fade out, and from 0.94 of the turn to its end nothing at all (extras/brand/source/landmarks.py, ROOT_FADE).
 * The next picture takes over in that moment of nothing; [SwapAt] aims for the middle of it.
 */
private const val TurnMs = 5000L
private const val SwapFrom = 0.945f
private const val SwapAt = 0.97f
private const val SwapUntil = 0.995f

/** With motion off, how long each picture stands. */
private const val StillMs = 2500L

/** How long a picture may take to begin on the render thread before the loader stops waiting and stands still. */
private const val StartGraceMs = 3000L

/**
 * The pictures still to come, shuffled: each once before any comes again, and never the same one twice in a row
 * across a reshuffle. Shared, so the tour goes on from one wait to the next.
 */
private val bag = ArrayDeque<Landmark>()
private var lastLandmark: Landmark? = null

private fun takeLandmark(): Landmark {
    if (bag.isEmpty()) {
        bag.addAll(Landmarks.shuffled())
        if (bag.first() === lastLandmark) bag.addLast(bag.removeFirst())
    }
    return bag.removeFirst().also { lastLandmark = it }
}

/**
 * What the app shows while it waits: landmarks of the north and the HSV crest in a random order, each moving a
 * little — the lighthouse's lamp turns, the Rickmer Rickmers rocks on the swell, La Ola runs round the
 * Volksparkstadion, the rooster crows. In the accent, the crest in its own colours. [sizeDp] is its height; it
 * is half as wide again, as the pictures are.
 *
 * It took the place of a spinning ring (29 Oct 2026, Jonathan), which stuttered whenever the box was busy, and
 * loading is exactly when it is. The pictures are AnimatedVectorDrawables in a View, so their motion and their
 * fades run on the render thread, where a busy main thread cannot hold them up. Only the change to the next
 * picture is made here, and only in the moment between two pictures when neither shows: a change that comes too
 * late finds the same picture fading in again, and waits for the end of that turn.
 *
 * With Animations off, or the box's animator scale at 0, the pictures stand still and simply change. So they do
 * if a picture never begins on the render thread: it starts unseen, and still pictures beat none.
 */
@Composable
fun LandmarkLoader(
    modifier: Modifier = Modifier,
    sizeDp: Int = 44,
    color: Color = tv.own.owntv.ui.theme.stageAccent.accent,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    // The box's animator scale, which the render thread plays every turn at (ValueAnimator only tells from API 33).
    val animatorScale = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
    }
    var stillOnly by remember { mutableStateOf(false) }
    val moving = !stillOnly && animationsOn && animatorScale > 0f && ValueAnimator.areAnimatorsEnabled()
    var landmark by remember { mutableStateOf(takeLandmark()) }
    // A copy of its own: two loaders showing the same landmark must not share one animation.
    val picture = remember(resources, landmark, moving) {
        ResourcesCompat.getDrawable(resources, landmark.res, null)!!.mutate() as AnimatedVectorDrawable
    }
    // When the render thread begins the turn, which is when the picture is first drawn, not when it is asked to.
    val started = remember(picture) { CompletableDeferred<Long>() }
    DisposableEffect(picture) {
        if (moving) {
            picture.alpha = 0 // nothing until the fade in begins
            picture.registerAnimationCallback(object : Animatable2.AnimationCallback() {
                override fun onAnimationStart(drawable: Drawable?) {
                    started.complete(SystemClock.uptimeMillis())
                }
            })
            picture.start()
        }
        onDispose {
            picture.clearAnimationCallbacks()
            picture.stop()
        }
    }
    LaunchedEffect(picture) {
        if (moving) {
            // The moment of nothing comes at the box's animator scale too.
            val turn = (TurnMs * animatorScale).toLong().coerceAtLeast(1L)
            val from = withTimeoutOrNull(StartGraceMs) { started.await() }
            if (from == null) {
                stillOnly = true
                return@LaunchedEffect
            }
            while (true) {
                val at = (SystemClock.uptimeMillis() - from) % turn
                if (at >= turn * SwapFrom && at <= turn * SwapUntil) break
                val wait = (turn * SwapAt).toLong() - at
                delay(if (wait > 0) wait else wait + turn)
            }
        } else {
            delay(StillMs)
        }
        landmark = takeLandmark()
    }
    val tint = color.toArgb()
    AndroidView(
        factory = { ImageView(it).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO } },
        modifier = modifier.size(width = (sizeDp * 1.5f).dp, height = sizeDp.dp),
        update = { view ->
            if (landmark.ownColours) picture.setTintList(null) else picture.setTint(tint)
            if (view.drawable !== picture) view.setImageDrawable(picture)
        },
    )
}
