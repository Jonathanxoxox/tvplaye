package tv.own.owntv.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Flutlichtnacht: a night stadium behind Live TV and the TV Guide while a football programme is focused,
 * in the layered style of Jonas Laterne (super7): floodlight cones that sway, light that breathes, mist
 * that drifts and dust rising in the light. No characters.
 *
 * Built for the TCL G10 (see GradientTextures): every large gradient is rendered once into a small
 * bitmap — the still scene at half size, the cone and the glows as sprites — so a frame only stretches
 * about twenty images and draws a few dots. With Animations off the scene stands still.
 *
 * The scene is designed on a 1280×720 board and scaled to cover the area like `xMidYMid slice`.
 */
@Composable
fun Modifier.flutlichtNight(visible: Boolean): Modifier {
    val moving = animationsOn
    val on = visible && !LocalStagePalette.current.isLight
    val alpha = animateFloatAsState(if (on) 1f else 0f, if (moving) tween<Float>(900) else snap<Float>())
    if (!on && alpha.value <= 0f) return this
    val time: State<Float> = if (moving) {
        rememberInfiniteTransition()
            .animateFloat(0f, 1f, infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing)))
    } else {
        remember { mutableFloatStateOf(0.25f) }
    }
    val draw = remember(time, alpha) {
        Modifier.drawWithCache {
            val s = max(size.width / DW, size.height / DH)
            val ox = (size.width - DW * s) / 2f
            val oy = (size.height - DH * s) / 2f
            val bw = (size.width / 2f).roundToInt().coerceAtLeast(1)
            val bh = (size.height / 2f).roundToInt().coerceAtLeast(1)
            val still = render(bw, bh, this, layoutDirection) {
                withTransform({ translate(ox / 2f, oy / 2f); scale(s / 2f, s / 2f, Offset.Zero) }) { stillScene() }
            }
            val cone = render(64, 256, this, layoutDirection) { coneSprite() }
            val head = render(64, 64, this, layoutDirection) { glowSprite(listOf(0f to Color(0xFFFFF6DC).copy(alpha = 0.85f), 0.3f to Color(0xFFFFD68C).copy(alpha = 0.35f), 1f to Color(0x00FFD68C))) }
            val mote = render(32, 32, this, layoutDirection) { glowSprite(listOf(0f to Color(0xFFFFF0C8).copy(alpha = 0.9f), 0.3f to Color(0xFFFFD68C).copy(alpha = 0.35f), 1f to Color(0x00FFD68C))) }
            val fog = render(64, 64, this, layoutDirection) { glowSprite(listOf(0f to Color(0xFF9FE8F0).copy(alpha = 0.14f), 1f to Color(0x009FE8F0))) }
            val bowl = render(64, 64, this, layoutDirection) { glowSprite(listOf(0f to Color(0xFFFFC94D).copy(alpha = 0.38f), 0.5f to Color(0xFF5CF0D0).copy(alpha = 0.08f), 1f to Color(0x005CF0D0))) }
            onDrawBehind {
                val a = alpha.value
                val t = time.value
                drawImage(still, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), alpha = a, filterQuality = FilterQuality.Low)
                withTransform({ translate(ox, oy); scale(s, s, Offset.Zero) }) {
                    movingScene(t, a, cone, head, mote, fog, bowl)
                }
            }
        }
    }
    return this.then(draw)
}

private const val DW = 1280f
private const val DH = 720f

/** One loop of the whole scene; every motion repeats a whole number of times in it, so it never jumps. */
private const val LOOP_MS = 120_000

private class Mast(val x: Float, val y: Float, val tilt: Float, val reach: Float, val cycles: Int, val phase: Float)

private val MASTS = listOf(
    Mast(600f, 92f, -14f, 330f, 10, 0f),
    Mast(760f, 70f, -6f, 340f, 8, 0.3f),
    Mast(1060f, 70f, 7f, 340f, 8, 0.55f),
    Mast(1220f, 92f, 15f, 330f, 10, 0.8f),
)

/** x, y, radius, twinkles per loop, phase. */
private val STARS = listOf(
    floatArrayOf(512f, 36f, 1.3f, 37f, 0f), floatArrayOf(590f, 88f, 1f, 25f, 0.3f), floatArrayOf(664f, 24f, 1.5f, 19f, 0.5f),
    floatArrayOf(710f, 130f, 1f, 37f, 0.7f), floatArrayOf(802f, 52f, 1.2f, 25f, 0.1f), floatArrayOf(874f, 18f, 1f, 19f, 0.9f),
    floatArrayOf(930f, 104f, 1.4f, 25f, 0.4f), floatArrayOf(988f, 40f, 1f, 37f, 0.6f), floatArrayOf(1044f, 140f, 1.1f, 19f, 0.2f),
    floatArrayOf(1112f, 28f, 1.5f, 37f, 0.8f), floatArrayOf(1170f, 96f, 1f, 25f, 0.35f), floatArrayOf(1236f, 56f, 1.3f, 19f, 0.65f),
    floatArrayOf(476f, 160f, 1f, 25f, 0.15f), floatArrayOf(840f, 170f, 1f, 19f, 0.45f), floatArrayOf(380f, 70f, 1.2f, 37f, 0.55f),
    floatArrayOf(240f, 30f, 1f, 25f, 0.75f), floatArrayOf(120f, 110f, 1.2f, 19f, 0.95f),
)

/** x, y, radius, rises per loop, phase: dust drifting up through the floodlight. */
private val MOTES = listOf(
    floatArrayOf(640f, 250f, 7f, 13f, 0f), floatArrayOf(700f, 210f, 5f, 10f, 0.33f), floatArrayOf(780f, 240f, 8f, 12f, 0.7f),
    floatArrayOf(850f, 196f, 5f, 13f, 0.5f), floatArrayOf(1000f, 230f, 7f, 10f, 0.15f), floatArrayOf(1090f, 204f, 5f, 12f, 0.6f),
    floatArrayOf(1180f, 246f, 6f, 13f, 0.85f), floatArrayOf(1240f, 190f, 4f, 10f, 0.4f),
)

private fun wave(t: Float, cycles: Float, phase: Float): Float = sin((2.0 * PI * (t * cycles + phase)).toFloat())

private fun render(w: Int, h: Int, density: Density, direction: LayoutDirection, block: DrawScope.() -> Unit): ImageBitmap {
    val bitmap = ImageBitmap(w, h)
    CanvasDrawScope().draw(density, direction, androidx.compose.ui.graphics.Canvas(bitmap), Size(w.toFloat(), h.toFloat()), block)
    return bitmap
}

private fun DrawScope.coneSprite() {
    val w = size.width
    val h = size.height
    val path = Path().apply { moveTo(w / 2f - 3f, 0f); lineTo(w / 2f + 3f, 0f); lineTo(w, h); lineTo(0f, h); close() }
    val c = Color(0xFFFFE7A3)
    drawPath(path, Brush.verticalGradient(0f to c.copy(alpha = 0.42f), 0.6f to c.copy(alpha = 0.1f), 1f to c.copy(alpha = 0f), startY = 0f, endY = h))
}

private fun DrawScope.glowSprite(stops: List<Pair<Float, Color>>) {
    drawRect(Brush.radialGradient(*stops.toTypedArray(), center = center, radius = size.minDimension / 2f))
}

/** Everything that stands still, drawn once on the 1280×720 board. */
private fun DrawScope.stillScene() {
    drawRect(
        Brush.verticalGradient(0f to Color(0xFF030A14), 0.45f to Color(0xFF082033), 0.75f to Color(0xFF0E3446), 1f to Color(0xFF05121C), startY = 0f, endY = DH),
        size = Size(DW, DH),
    )
    val ridge = Path().apply {
        moveTo(0f, 300f); lineTo(0f, 262f)
        quadraticTo(120f, 236f, 240f, 254f); quadraticTo(360f, 272f, 480f, 246f)
        quadraticTo(600f, 220f, 720f, 238f); quadraticTo(840f, 256f, 960f, 250f)
        quadraticTo(1080f, 244f, 1200f, 236f); lineTo(1280f, 244f); lineTo(1280f, 300f); close()
    }
    drawPath(ridge, Color(0xFF0A2534), alpha = 0.8f)

    val ink = Color(0xFF06131D)
    for (m in MASTS) {
        val pole = Path().apply { moveTo(m.x - 3f, m.y + 8f); lineTo(m.x + 3f, m.y + 8f); lineTo(m.x + 7f, 300f); lineTo(m.x - 7f, 300f); close() }
        drawPath(pole, ink)
        drawRoundRect(ink, Offset(m.x - 18f, m.y - 8f), Size(36f, 18f), androidx.compose.ui.geometry.CornerRadius(3f))
        for (row in 0..1) for (col in 0..3) {
            drawCircle(Color(0xFFFFF6DC), 2f, Offset(m.x - 12f + col * 8f, m.y - 2f + row * 6f))
        }
    }

    val bowl = Path().apply { moveTo(540f, 330f); lineTo(566f, 262f); quadraticTo(910f, 214f, 1254f, 262f); lineTo(1280f, 330f); close() }
    drawPath(bowl, Color(0xFF051019))
    val rim = Path().apply { moveTo(566f, 262f); quadraticTo(910f, 214f, 1254f, 262f) }
    drawPath(rim, Color(0xFF7FE6E0), alpha = 0.35f, style = Stroke(1.5f))
    for (x in listOf(640f, 700f, 790f, 880f, 980f, 1080f, 1160f)) {
        val y = 262f - 48f * (1f - ((x - 910f) / 344f).let { it * it })
        drawRect(Color(0xFFFFD68C), Offset(x, y + 4f), Size(3f, 2f), alpha = 0.55f)
    }

    drawRect(
        Brush.verticalGradient(0f to Color(0xFF0B2A30), 1f to Color(0xFF020910), startY = 330f, endY = DH),
        topLeft = Offset(0f, 330f), size = Size(DW, DH - 330f),
    )
    val line = Color(0xFF7FE6E0).copy(alpha = 0.07f)
    drawOval(line, Offset(740f, 466f), Size(340f, 108f), style = Stroke(2f))
    drawLine(line, Offset(910f, 340f), Offset(910f, DH), 2f)
    val pitch = Path().apply { moveTo(560f, 360f); lineTo(1260f, 360f); lineTo(1380f, DH); lineTo(440f, DH); close() }
    drawPath(pitch, line, style = Stroke(2f))

    drawRect(
        Brush.radialGradient(0.55f to Color(0x0001060C), 1f to Color(0xBF01060C), center = Offset(DW / 2f, DH * 0.45f), radius = 900f),
        size = Size(DW, DH),
    )
}

/** What moves, on the same board: glow over the bowl, swaying cones, breathing lamps, mist, stars, dust. */
private fun DrawScope.movingScene(t: Float, a: Float, cone: ImageBitmap, head: ImageBitmap, mote: ImageBitmap, fog: ImageBitmap, bowl: ImageBitmap) {
    fun sprite(image: ImageBitmap, x: Float, y: Float, w: Float, h: Float, alpha: Float) = drawImage(
        image,
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
        alpha = (alpha * a).coerceIn(0f, 1f),
        filterQuality = FilterQuality.Low,
    )

    val breathe = 0.55f + 0.45f * (0.5f + 0.5f * wave(t, 24f, 0f))
    sprite(bowl, 910f - 380f, 262f - 120f, 760f, 240f, breathe)

    for (m in MASTS) {
        rotate(m.tilt + 2.5f * wave(t, m.cycles.toFloat(), m.phase), Offset(m.x, m.y)) {
            sprite(cone, m.x - 120f, m.y, 240f, m.reach, 1f)
        }
    }
    for (m in MASTS) {
        val r = if (m.y < 80f) 52f else 46f
        sprite(head, m.x - r, m.y + 1f - r, r * 2f, r * 2f, 0.55f + 0.45f * (0.5f + 0.5f * wave(t, 24f, m.phase)))
    }

    sprite(fog, 860f - 420f + 70f * wave(t, 3f, 0f), 300f - 40f, 840f, 80f, 1f)
    sprite(fog, 400f - 380f + 70f * wave(t, 2f, 0.4f), 250f - 34f, 760f, 68f, 1f)

    val star = Color(0xFFE8F6FF)
    for (st in STARS) {
        drawCircle(star, st[2], Offset(st[0], st[1]), alpha = a * (0.25f + 0.75f * (0.5f + 0.5f * wave(t, st[3], st[4]))))
    }

    for (m in MOTES) {
        val p = (t * m[3] + m[4]).let { it - floor(it) }
        val fade = if (p < 0.15f) p / 0.15f else (1f - p) / 0.85f
        val r = m[2]
        sprite(mote, m[0] - r, m[1] - 150f * p - r, r * 2f, r * 2f, fade)
    }
}
