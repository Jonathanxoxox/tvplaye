package tv.own.owntv.player

import android.animation.ValueAnimator
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.View
import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.ui.theme.animationsOn

/**
 * The player's wait while a channel or a film loads: a dot in the accent that breathes, with a thin ring
 * running out from it. Calmer than the landmarks, which stay in the menus; over the picture they drew
 * the eye at every channel change (Jonathan, 30 Oct 2026).
 *
 * The same wait stands in every picture, full screen and the previews in Live TV and the guide alike.
 * It shows only once the wait passes [appearAfterMs]: most channel changes are quicker, and then
 * nothing shows but the new picture. Until then its room stays empty, so nothing around it moves.
 *
 * An AnimatedVectorDrawable in a View, like the landmarks, so its motion runs on the render thread and a
 * box busy opening the stream cannot make it stutter. With Animations off the dot simply stands.
 */
@Composable
fun PlayerLoader(
    modifier: Modifier = Modifier,
    sizeDp: Int = 72,
    color: Color = tv.own.owntv.ui.theme.stageAccent.accent,
    appearAfterMs: Long = LOADER_DELAY_MS,
) {
    val due by produceState(appearAfterMs <= 0L) { delay(appearAfterMs); value = true }
    Box(modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        if (due) LoaderDot(sizeDp, color)
    }
}

/** A wait shorter than this shows no loader at all, so a quick channel change is just the new picture. */
private const val LOADER_DELAY_MS = 600L

@Composable
private fun LoaderDot(sizeDp: Int, color: Color) {
    val resources = LocalResources.current
    val moving = animationsOn && ValueAnimator.areAnimatorsEnabled()
    // A copy of its own: two loaders must not share one animation.
    val picture = remember(resources) {
        ResourcesCompat.getDrawable(resources, R.drawable.player_loader, null)!!.mutate() as AnimatedVectorDrawable
    }
    DisposableEffect(picture, moving) {
        if (moving) picture.start()
        onDispose { picture.stop() }
    }
    val tint = color.toArgb()
    AndroidView(
        factory = { ImageView(it).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO } },
        modifier = Modifier.size(sizeDp.dp),
        update = { view ->
            picture.setTint(tint)
            if (view.drawable !== picture) view.setImageDrawable(picture)
        },
    )
}
