package tv.own.owntv.features.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.features.epg.GuidePreviewSpot
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.player.MpvVideoSurface
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.ui.theme.mpx

/**
 * The picture layer of the shell: full screen, the guide's corner, or the move between them. One box,
 * one surface call site, so the stream plays straight on across the hand-over. [content] is whatever draws
 * over the picture (the player's controls); it is told whether the picture is full screen.
 */
@Composable
internal fun ShellVideoLayer(
    playerMode: PlayerMode,
    inCorner: Boolean,
    guideHandover: Boolean,
    handoverGrow: Boolean,
    handoverShrink: Animatable<Float, AnimationVector1D>,
    exoShown: Boolean,
    liveVm: LiveViewModel,
    player: OwnTVPlayer,
    autoFrameRate: Boolean,
    afrMatchResolution: Boolean,
    afrPauseSecs: Int,
    content: @Composable BoxScope.(isFull: Boolean) -> Unit,
) {
    val isFull = playerMode == PlayerMode.FULLSCREEN
    // 0 = the whole screen, 1 = the guide's corner. Read in layout and draw only, so only this box moves.
    val cornerness = {
        when {
            playerMode == PlayerMode.NONE -> 1f
            handoverGrow -> 1f - handoverShrink.value
            else -> handoverShrink.value
        }
    }
    val cornerRadius = 20.mpx
    Box(
        modifier = if (guideHandover || inCorner) {
            // Laid out full screen and only transformed while drawing: the picture moves and shrinks as a
            // picture, with no layout pass and no new surface size per frame (the video plane is scaled by
            // the display hardware, as a SurfaceView under a scaled layer is).
            Modifier.fillMaxSize().graphicsLayer {
                val f = cornerness()
                val to = GuidePreviewSpot.rect
                transformOrigin = TransformOrigin(0f, 0f)
                if (to != null && size.width > 0f && size.height > 0f) {
                    scaleX = 1f + (to.width / size.width - 1f) * f
                    scaleY = 1f + (to.height / size.height - 1f) * f
                    translationX = (to.left + GuidePreviewSpot.shift()) * f
                    translationY = to.top * f
                }
                // The corner's rounding grows in as the picture lands (drawn before the scale shrinks it).
                shape = RoundedCornerShape(cornerRadius.toPx() * f / scaleX.coerceAtLeast(0.05f))
                clip = true
            }.background(Color.Black)
        } else {
            Modifier.fillMaxSize().background(Color.Black)
        },
    ) {
        // "Promote Preview": a Live channel playing on ExoPlayer renders the ExoPlayer surface, in full screen
        // and in the guide's corner (same call site = the surface persists, so playback never blips).
        // Everything else (mpv) renders mpv's surface.
        if (exoShown) {
            tv.own.owntv.player.ExoPreviewSurface(
                engine = liveVm.previewEngine, modifier = Modifier.fillMaxSize(),
                keepAwake = playerMode != PlayerMode.NONE,
                // Kept through the corner too: a display mode switched back and forth on every Back
                // is a black screen each way.
                autoFrameRate = (isFull || inCorner || guideHandover) && autoFrameRate,
            )
            if (inCorner) {
                val cornerState by liveVm.previewEngine.state.collectAsStateWithLifecycle()
                if (cornerState == tv.own.owntv.player.LivePreviewEngine.State.LOADING) {
                    tv.own.owntv.ui.components.OwnTVSpinner(sizeDp = 24, modifier = Modifier.align(Alignment.Center))
                }
            }
        } else {
            MpvVideoSurface(
                player = player, modifier = Modifier.fillMaxSize(), autoFrameRate = isFull && autoFrameRate,
                afrMatchResolution = afrMatchResolution, afrHoldSecs = afrPauseSecs,
            )
        }
        // The item has no video track of its own (a radio channel, a music-only "movie"). Playing it is
        // correct — but a black screen with sound reads as a broken player, so name what is happening.
        // Read from whichever engine is on screen; only ever composed when there is no video to lose.
        val audioOnlyMedia by if (exoShown) {
            liveVm.previewEngine.audioOnlyMedia.collectAsStateWithLifecycle()
        } else {
            player.audioOnlyMedia.collectAsStateWithLifecycle()
        }
        if (audioOnlyMedia) {
            tv.own.owntv.player.AudioOnlyBadge(modifier = Modifier.fillMaxSize(), compact = !isFull)
        }
        // Direct render mode: mpv can't draw subtitles on the decoder-owned surface — the app does.
        if (!exoShown) {
            tv.own.owntv.player.SubtitleOverlay(player = player, modifier = Modifier.fillMaxSize(), sizeScale = 1f)
        }
        content(isFull)
    }
}
