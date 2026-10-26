package tv.own.owntv.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Flutlicht's night scenes, in the layered style of Jonas Laterne (super7): animated landscapes behind
 * the shell's pages, without characters.
 *
 * - [NightScene.NORDLICHT], [NightScene.MITTERNACHT] and the four worlds of NightWorlds.kt (Tiefsee, Mondwüste,
 *   Glimmwald, Hafenlichter) are themes the user picks under Glass & background; they show behind every page
 *   while no player covers the screen. The worlds also tint the panels to match ([StagePalette.forScene]).
 * - Flutlichtnacht, the night stadium, is not a theme of its own: it takes over Live TV and the TV Guide
 *   while the focused channel shows football (switchable), whatever theme is chosen.
 *
 * Built for the TCL G10 (see GradientTextures): every large gradient is rendered once into a small bitmap
 * (the still layers at half size, cones, curtains and glows as sprites), so a frame only stretches a few
 * dozen images and draws some dots. With Animations off a scene stands still.
 *
 * Each scene is designed on a 1280×720 board and scaled to cover the area like `xMidYMid slice`.
 */
enum class NightScene { NONE, NORDLICHT, MITTERNACHT, TIEFSEE, MONDWUESTE, GLIMMWALD, HAFENLICHTER }

/** The chosen scene and the football switch, kept in the app's own preferences (the core is not changed). */
object NightSceneSettings {
    private const val PREFS = "flutlicht_scenes"
    private const val KEY_SCENE = "scene"
    private const val KEY_STADIUM = "stadium"

    private var prefs: SharedPreferences? = null
    private val _scene = MutableStateFlow(NightScene.NONE)
    private val _stadium = MutableStateFlow(true)
    val scene: StateFlow<NightScene> = _scene.asStateFlow()
    val stadium: StateFlow<Boolean> = _stadium.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _scene.value = NightScene.entries.firstOrNull { it.name == p.getString(KEY_SCENE, null) } ?: NightScene.NONE
        _stadium.value = p.getBoolean(KEY_STADIUM, true)
    }

    fun setScene(scene: NightScene) {
        _scene.value = scene
        prefs?.edit()?.putString(KEY_SCENE, scene.name)?.apply()
    }

    fun setStadium(on: Boolean) {
        _stadium.value = on
        prefs?.edit()?.putBoolean(KEY_STADIUM, on)?.apply()
    }
}

/** Loads [NightSceneSettings] once; call it before reading the settings in a composable. */
@Composable
fun rememberNightSceneSettings(): NightSceneSettings {
    val context = LocalContext.current
    return remember { NightSceneSettings.apply { init(context) } }
}

/**
 * The page background for the shell: the stadium when [stadium] (football focused in Live TV or the
 * Guide), otherwise the chosen [scene]; nothing while [playerFree] is false. Scenes cross-fade.
 */
@Composable
fun Modifier.nightScenes(scene: NightScene, stadium: Boolean, playerFree: Boolean): Modifier =
    SCENE_ART.entries.fold(this.sceneLayer(playerFree && stadium, FlutlichtArt)) { m, (s, art) ->
        m.sceneLayer(playerFree && !stadium && scene == s, art)
    }

/** The art of every theme, in the order of [NightScene]; the stadium is not one of them. */
private val SCENE_ART: Map<NightScene, SceneArt> = linkedMapOf(
    NightScene.NORDLICHT to NordlichtArt,
    NightScene.MITTERNACHT to MitternachtArt,
    NightScene.TIEFSEE to TiefseeArt,
    NightScene.MONDWUESTE to MondwuesteArt,
    NightScene.GLIMMWALD to GlimmwaldArt,
    NightScene.HAFENLICHTER to HafenArt,
)

/** A small live picture of [scene] (the settings' help panel); the plain page colour for [NightScene.NONE]. */
@Composable
fun NightScenePreview(scene: NightScene, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(LocalStagePalette.current.page)
            .nightScenes(scene, stadium = false, playerFree = true),
    )
}

/**
 * The panels of a world: page, glass and the cool greys tinted to the scene, so the rail and the sheets sit in
 * the picture instead of on it. Text, focus bar and switches stay as they are, and so does their contrast.
 * Nordlicht and Mitternacht keep Cyan Blau, as before.
 */
fun StagePalette.forScene(scene: NightScene): StagePalette {
    if (isLight) return this
    val t = when (scene) {
        NightScene.TIEFSEE -> SceneTint(0xFF0A2F4A, 0xFF114A6E, 0xFFA9CBE0, 0xFF7FA8C2, 0xFFC3DBEA, 0xFFD3E4EF)
        NightScene.MONDWUESTE -> SceneTint(0xFF2A1D45, 0xFF3C2A5E, 0xFFCDBFE0, 0xFFA595C2, 0xFFD6CBE8, 0xFFE3DAEF)
        NightScene.GLIMMWALD -> SceneTint(0xFF0D3A30, 0xFF145244, 0xFFB4D8C8, 0xFF88B8A4, 0xFFC6E2D4, 0xFFD5EAE0)
        NightScene.HAFENLICHTER -> SceneTint(0xFF14223A, 0xFF1F3354, 0xFFBCC8DC, 0xFF8E9DB8, 0xFFCBD5E6, 0xFFDCE3EE)
        else -> return this
    }
    val page = Color(t.page)
    return copy(
        page = page,
        glassTint = Color(t.glass),
        glassOff = Color(t.glass),
        muted = Color(t.muted),
        dim = Color(t.dim),
        tagText = Color(t.tag),
        body = Color(t.body),
        scrim = Color(page.red * 0.3f, page.green * 0.3f, page.blue * 0.3f, 0.55f),
        wash = page,
    )
}

private class SceneTint(val page: Long, val glass: Long, val muted: Long, val dim: Long, val tag: Long, val body: Long)

@Composable
private fun Modifier.sceneLayer(visible: Boolean, art: SceneArt): Modifier {
    val moving = animationsOn
    val on = visible && !LocalStagePalette.current.isLight
    val alpha = animateFloatAsState(if (on) 1f else 0f, if (moving) tween<Float>(900) else snap<Float>())
    if (!on && alpha.value <= 0f) return this
    val time: State<Float> = if (moving) {
        rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing)))
    } else {
        remember { mutableFloatStateOf(0.25f) }
    }
    val draw = remember(time, alpha, art) {
        Modifier.drawWithCache {
            val s = max(size.width / DW, size.height / DH)
            val ox = (size.width - DW * s) / 2f
            val oy = (size.height - DH * s) / 2f
            val bw = (size.width / 2f).roundToInt().coerceAtLeast(1)
            val bh = (size.height / 2f).roundToInt().coerceAtLeast(1)
            val density: Density = this
            val direction = layoutDirection
            val make: (Int, Int, DrawScope.() -> Unit) -> ImageBitmap = { w, h, block -> render(w, h, density, direction, block) }
            val back = make(bw, bh) { withTransform({ translate(ox / 2f, oy / 2f); scale(s / 2f, s / 2f, Offset.Zero) }) { with(art) { back() } } }
            val front = if (art.hasFront) {
                make(bw, bh) { withTransform({ translate(ox / 2f, oy / 2f); scale(s / 2f, s / 2f, Offset.Zero) }) { with(art) { front() } } }
            } else null
            val sprites = art.sprites(make)
            val full = IntSize(size.width.roundToInt(), size.height.roundToInt())
            onDrawBehind {
                val a = alpha.value
                val t = time.value
                drawImage(back, dstSize = full, alpha = a, filterQuality = FilterQuality.Low)
                withTransform({ translate(ox, oy); scale(s, s, Offset.Zero) }) { with(art) { middle(t, a, sprites) } }
                if (front != null) drawImage(front, dstSize = full, alpha = a, filterQuality = FilterQuality.Low)
                withTransform({ translate(ox, oy); scale(s, s, Offset.Zero) }) { with(art) { top(t, a, sprites) } }
            }
        }
    }
    return this.then(draw)
}

internal const val DW = 1280f
internal const val DH = 720f

/** One loop of every scene; each motion repeats a whole number of times in it, so nothing ever jumps. */
private const val LOOP_MS = 120_000

/**
 * One scene: [back] and [front] stand still and are rendered once; [middle] moves between them (behind
 * mountains and hills), [top] moves in front. All four draw on the 1280×720 board.
 */
internal abstract class SceneArt {
    open val hasFront: Boolean = false
    abstract fun DrawScope.back()
    open fun DrawScope.front() {}
    abstract fun sprites(make: (Int, Int, DrawScope.() -> Unit) -> ImageBitmap): List<ImageBitmap>
    open fun DrawScope.middle(t: Float, a: Float, s: List<ImageBitmap>) {}
    abstract fun DrawScope.top(t: Float, a: Float, s: List<ImageBitmap>)
}

// ── shared helpers ──

internal fun wave(t: Float, cycles: Float, phase: Float): Float = sin((2.0 * PI * (t * cycles + phase)).toFloat())

internal fun pulse(t: Float, cycles: Float, phase: Float, low: Float): Float = low + (1f - low) * (0.5f + 0.5f * wave(t, cycles, phase))

private fun render(w: Int, h: Int, density: Density, direction: LayoutDirection, block: DrawScope.() -> Unit): ImageBitmap {
    val bitmap = ImageBitmap(w, h)
    CanvasDrawScope().draw(density, direction, androidx.compose.ui.graphics.Canvas(bitmap), Size(w.toFloat(), h.toFloat()), block)
    return bitmap
}

internal fun DrawScope.sprite(image: ImageBitmap, x: Float, y: Float, w: Float, h: Float, alpha: Float) = drawImage(
    image,
    dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
    dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
    alpha = alpha.coerceIn(0f, 1f),
    filterQuality = FilterQuality.Low,
)

internal fun DrawScope.glowSprite(vararg stops: Pair<Float, Color>) {
    drawRect(Brush.radialGradient(*stops, center = center, radius = size.minDimension / 2f))
}

internal fun glow(make: (Int, Int, DrawScope.() -> Unit) -> ImageBitmap, vararg stops: Pair<Float, Color>): ImageBitmap =
    make(64, 64) { glowSprite(*stops) }

internal fun tint(color: Long, alpha: Float) = Color(color).copy(alpha = alpha)

internal fun DrawScope.stars(t: Float, a: Float, list: List<FloatArray>, color: Color) {
    for (st in list) drawCircle(color, st[2], Offset(st[0], st[1]), alpha = a * pulse(t, st[3], st[4], 0.25f))
}

internal fun DrawScope.vignette(color: Long) {
    drawRect(
        Brush.radialGradient(0.55f to tint(color, 0f), 1f to tint(color, 0.75f), center = Offset(DW / 2f, DH * 0.45f), radius = 900f),
        size = Size(DW, DH),
    )
}

/** x, y, radius, twinkles per loop, phase — spread over the upper half of the board. */
internal val SKY_STARS = listOf(
    floatArrayOf(512f, 36f, 1.3f, 37f, 0f), floatArrayOf(590f, 88f, 1f, 25f, 0.3f), floatArrayOf(664f, 24f, 1.5f, 19f, 0.5f),
    floatArrayOf(710f, 130f, 1f, 37f, 0.7f), floatArrayOf(802f, 52f, 1.2f, 25f, 0.1f), floatArrayOf(874f, 18f, 1f, 19f, 0.9f),
    floatArrayOf(930f, 104f, 1.4f, 25f, 0.4f), floatArrayOf(988f, 40f, 1f, 37f, 0.6f), floatArrayOf(1044f, 140f, 1.1f, 19f, 0.2f),
    floatArrayOf(1112f, 28f, 1.5f, 37f, 0.8f), floatArrayOf(1170f, 96f, 1f, 25f, 0.35f), floatArrayOf(1236f, 56f, 1.3f, 19f, 0.65f),
    floatArrayOf(476f, 160f, 1f, 25f, 0.15f), floatArrayOf(840f, 170f, 1f, 19f, 0.45f), floatArrayOf(380f, 70f, 1.2f, 37f, 0.55f),
    floatArrayOf(240f, 30f, 1f, 25f, 0.75f), floatArrayOf(120f, 110f, 1.2f, 19f, 0.95f), floatArrayOf(300f, 150f, 1f, 37f, 0.25f),
)

// ── Flutlichtnacht: the night stadium ──

private object FlutlichtArt : SceneArt() {
    private class Mast(val x: Float, val y: Float, val tilt: Float, val reach: Float, val cycles: Int, val phase: Float)

    private val masts = listOf(
        Mast(600f, 92f, -14f, 330f, 10, 0f),
        Mast(760f, 70f, -6f, 340f, 8, 0.3f),
        Mast(1060f, 70f, 7f, 340f, 8, 0.55f),
        Mast(1220f, 92f, 15f, 330f, 10, 0.8f),
    )

    /** x, y, radius, rises per loop, phase: dust drifting up through the floodlight. */
    private val motes = listOf(
        floatArrayOf(640f, 250f, 7f, 13f, 0f), floatArrayOf(700f, 210f, 5f, 10f, 0.33f), floatArrayOf(780f, 240f, 8f, 12f, 0.7f),
        floatArrayOf(850f, 196f, 5f, 13f, 0.5f), floatArrayOf(1000f, 230f, 7f, 10f, 0.15f), floatArrayOf(1090f, 204f, 5f, 12f, 0.6f),
        floatArrayOf(1180f, 246f, 6f, 13f, 0.85f), floatArrayOf(1240f, 190f, 4f, 10f, 0.4f),
    )

    override fun DrawScope.back() {
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
        for (m in masts) {
            val pole = Path().apply { moveTo(m.x - 3f, m.y + 8f); lineTo(m.x + 3f, m.y + 8f); lineTo(m.x + 7f, 300f); lineTo(m.x - 7f, 300f); close() }
            drawPath(pole, ink)
            drawRoundRect(ink, Offset(m.x - 18f, m.y - 8f), Size(36f, 18f), CornerRadius(3f))
            for (row in 0..1) for (col in 0..3) drawCircle(Color(0xFFFFF6DC), 2f, Offset(m.x - 12f + col * 8f, m.y - 2f + row * 6f))
        }

        val bowl = Path().apply { moveTo(540f, 330f); lineTo(566f, 262f); quadraticTo(910f, 214f, 1254f, 262f); lineTo(1280f, 330f); close() }
        drawPath(bowl, Color(0xFF051019))
        val rim = Path().apply { moveTo(566f, 262f); quadraticTo(910f, 214f, 1254f, 262f) }
        drawPath(rim, Color(0xFF7FE6E0), alpha = 0.35f, style = Stroke(1.5f))
        for (x in listOf(640f, 700f, 790f, 880f, 980f, 1080f, 1160f)) {
            val k = (x - 910f) / 344f
            drawRect(Color(0xFFFFD68C), Offset(x, 262f - 48f * (1f - k * k) + 4f), Size(3f, 2f), alpha = 0.55f)
        }

        drawRect(
            Brush.verticalGradient(0f to Color(0xFF0B2A30), 1f to Color(0xFF020910), startY = 330f, endY = DH),
            topLeft = Offset(0f, 330f), size = Size(DW, DH - 330f),
        )
        val line = tint(0xFF7FE6E0, 0.07f)
        drawOval(line, Offset(740f, 466f), Size(340f, 108f), style = Stroke(2f))
        drawLine(line, Offset(910f, 340f), Offset(910f, DH), 2f)
        val pitch = Path().apply { moveTo(560f, 360f); lineTo(1260f, 360f); lineTo(1380f, DH); lineTo(440f, DH); close() }
        drawPath(pitch, line, style = Stroke(2f))
        vignette(0xFF01060C)
    }

    override fun sprites(make: (Int, Int, DrawScope.() -> Unit) -> ImageBitmap): List<ImageBitmap> = listOf(
        make(64, 256) {
            val path = Path().apply { moveTo(29f, 0f); lineTo(35f, 0f); lineTo(64f, 256f); lineTo(0f, 256f); close() }
            val c = Color(0xFFFFE7A3)
            drawPath(path, Brush.verticalGradient(0f to c.copy(alpha = 0.42f), 0.6f to c.copy(alpha = 0.1f), 1f to c.copy(alpha = 0f), startY = 0f, endY = 256f))
        },
        glow(make, 0f to tint(0xFFFFF6DC, 0.85f), 0.3f to tint(0xFFFFD68C, 0.35f), 1f to tint(0xFFFFD68C, 0f)),
        glow(make, 0f to tint(0xFFFFF0C8, 0.9f), 0.3f to tint(0xFFFFD68C, 0.35f), 1f to tint(0xFFFFD68C, 0f)),
        glow(make, 0f to tint(0xFF9FE8F0, 0.14f), 1f to tint(0xFF9FE8F0, 0f)),
        glow(make, 0f to tint(0xFFFFC94D, 0.38f), 0.5f to tint(0xFF5CF0D0, 0.08f), 1f to tint(0xFF5CF0D0, 0f)),
    )

    override fun DrawScope.top(t: Float, a: Float, s: List<ImageBitmap>) {
        val (cone, head, mote, fog, bowl) = s
        sprite(bowl, 530f, 142f, 760f, 240f, a * pulse(t, 24f, 0f, 0.55f))
        for (m in masts) {
            rotate(m.tilt + 2.5f * wave(t, m.cycles.toFloat(), m.phase), Offset(m.x, m.y)) {
                sprite(cone, m.x - 120f, m.y, 240f, m.reach, a)
            }
        }
        for (m in masts) {
            val r = if (m.y < 80f) 52f else 46f
            sprite(head, m.x - r, m.y + 1f - r, r * 2f, r * 2f, a * pulse(t, 24f, m.phase, 0.55f))
        }
        sprite(fog, 440f + 70f * wave(t, 3f, 0f), 260f, 840f, 80f, a)
        sprite(fog, 20f + 70f * wave(t, 2f, 0.4f), 216f, 760f, 68f, a)
        stars(t, a, SKY_STARS, Color(0xFFE8F6FF))
        for (m in motes) {
            val p = (t * m[3] + m[4]).let { it - floor(it) }
            val fade = if (p < 0.15f) p / 0.15f else (1f - p) / 0.85f
            sprite(mote, m[0] - m[2], m[1] - 150f * p - m[2], m[2] * 2f, m[2] * 2f, a * fade)
        }
    }
}

// ── Nordlicht: aurora over mountains and a lake, a stadium glowing far off ──

private object NordlichtArt : SceneArt() {
    override val hasFront = true

    /** The three curtains on the board: path, bounds (x, y, w, h), sprite size, gradient. */
    private class Curtain(val path: Path, val x: Float, val y: Float, val w: Float, val h: Float, val stops: Array<Pair<Float, Color>>, val cycles: Float, val phase: Float, val drift: Float)

    private val curtains = listOf(
        Curtain(
            Path().apply {
                moveTo(80f, 40f); cubicTo(260f, 10f, 380f, 120f, 560f, 70f); cubicTo(740f, 20f, 880f, 0f, 1060f, 60f)
                cubicTo(1240f, 120f, 1260f, 90f, 1340f, 40f); lineTo(1340f, 230f); cubicTo(1180f, 260f, 1020f, 200f, 860f, 240f)
                cubicTo(700f, 280f, 540f, 270f, 380f, 230f); cubicTo(220f, 190f, 160f, 250f, 80f, 220f); close()
            },
            80f, 0f, 1260f, 280f, arrayOf(0f to tint(0xFF5CF0D0, 0f), 0.6f to tint(0xFF5CF0D0, 0.18f), 1f to tint(0xFF5CF0D0, 0f)), 4f, 0.2f, 60f,
        ),
        Curtain(
            Path().apply {
                moveTo(200f, 20f); cubicTo(340f, 60f, 460f, 0f, 600f, 40f); cubicTo(740f, 80f, 860f, 110f, 1000f, 50f)
                cubicTo(1140f, -10f, 1200f, 10f, 1300f, 50f); lineTo(1300f, 200f); cubicTo(1160f, 230f, 1020f, 170f, 880f, 210f)
                cubicTo(740f, 250f, 600f, 240f, 460f, 200f); cubicTo(320f, 160f, 280f, 220f, 200f, 190f); close()
            },
            200f, 0f, 1100f, 250f, arrayOf(0f to tint(0xFF7CFFB2, 0f), 0.55f to tint(0xFF7CFFB2, 0.32f), 1f to tint(0xFF5CF0D0, 0f)), 6f, 0f, -70f,
        ),
        Curtain(
            Path().apply {
                moveTo(420f, 60f); cubicTo(540f, 30f, 660f, 100f, 780f, 60f); cubicTo(900f, 20f, 1000f, 20f, 1120f, 70f)
                lineTo(1120f, 180f); cubicTo(1000f, 200f, 880f, 160f, 760f, 190f); cubicTo(640f, 220f, 540f, 200f, 420f, 170f); close()
            },
            420f, 20f, 700f, 200f, arrayOf(0f to tint(0xFFB497FF, 0f), 0.5f to tint(0xFF9FFFD0, 0.22f), 1f to tint(0xFF5CF0D0, 0f)), 5f, 0.5f, 80f,
        ),
    )

    private val firs = listOf(floatArrayOf(18f, 240f, 18f), floatArrayOf(46f, 214f, 22f), floatArrayOf(1234f, 222f, 24f), floatArrayOf(1262f, 250f, 22f))

    override fun DrawScope.back() {
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF020713), 0.35f to Color(0xFF06142C), 0.55f to Color(0xFF0C2440), 0.7f to Color(0xFF12334E), 1f to Color(0xFF030A14),
                startY = 0f, endY = DH,
            ),
            size = Size(DW, DH),
        )
    }

    override fun DrawScope.front() {
        drawRect(
            Brush.verticalGradient(0f to Color(0xFF12334E), 1f to Color(0xFF030A14), startY = 300f, endY = DH),
            topLeft = Offset(0f, 300f), size = Size(DW, DH - 300f),
        )
        val far = floatArrayOf(0f, 270f, 60f, 236f, 130f, 256f, 210f, 196f, 290f, 248f, 380f, 214f, 470f, 252f, 560f, 206f, 650f, 244f, 740f, 186f, 830f, 236f, 920f, 214f, 1010f, 250f, 1100f, 200f, 1190f, 240f, 1280f, 220f)
        drawPath(ridgePath(far, 300f), Color(0xFF0C2236))
        val near = floatArrayOf(0f, 290f, 90f, 262f, 190f, 280f, 300f, 250f, 420f, 278f, 540f, 256f, 660f, 282f, 780f, 254f, 900f, 276f, 1020f, 258f, 1140f, 280f, 1280f, 262f)
        drawPath(ridgePath(near, 312f), Color(0xFF081828))
        drawPath(Path().apply { moveTo(1020f, 300f); lineTo(1028f, 286f); quadraticTo(1060f, 280f, 1092f, 286f); lineTo(1100f, 300f); close() }, Color(0xFF051019))
        for (f in firs) drawPath(Path().apply { moveTo(f[0] - f[2], 312f); lineTo(f[0], f[1]); lineTo(f[0] + f[2], 312f); close() }, Color(0xFF040E18))
        vignette(0xFF01060C)
    }

    private fun ridgePath(points: FloatArray, bottom: Float) = Path().apply {
        moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
        lineTo(DW, bottom); lineTo(0f, bottom); close()
    }

    override fun sprites(make: (Int, Int, DrawScope.() -> Unit) -> ImageBitmap): List<ImageBitmap> =
        curtains.map { c ->
            make((c.w / 4f).roundToInt(), (c.h / 2f).roundToInt()) {
                withTransform({ scale(size.width / c.w, size.height / c.h, Offset.Zero); translate(-c.x, -c.y) }) {
                    drawPath(c.path, Brush.verticalGradient(*c.stops, startY = c.y, endY = c.y + c.h))
                }
            }
        } + listOf(
            glow(make, 0f to tint(0xFFFFC94D, 0.5f), 1f to tint(0xFFFFC94D, 0f)),
            glow(make, 0f to tint(0xFFBFF5E8, 0.13f), 1f to tint(0xFFBFF5E8, 0f)),
            glow(make, 0f to tint(0xFF7CFFB2, 0.16f), 1f to tint(0xFF7CFFB2, 0f)),
        )

    override fun DrawScope.middle(t: Float, a: Float, s: List<ImageBitmap>) {
        stars(t, a, SKY_STARS, Color(0xFFE8F6FF))
        curtains.forEachIndexed { i, c ->
            val sy = 1f + 0.12f * wave(t, c.cycles, c.phase)
            val dx = c.drift * wave(t, c.cycles, c.phase + 0.25f)
            withTransform({ translate(dx, 0f); scale(1f, sy, Offset(c.x + c.w / 2f, c.y + c.h)) }) {
                sprite(s[i], c.x, c.y, c.w, c.h, a)
            }
        }
    }

    override fun DrawScope.top(t: Float, a: Float, s: List<ImageBitmap>) {
        val dome = s[3]
        val fog = s[4]
        val shimmer = s[5]
        sprite(dome, 950f, 256f, 220f, 68f, a * pulse(t, 20f, 0f, 0.5f))
        sprite(shimmer, 200f, 316f, 1100f, 70f, a * pulse(t, 17f, 0.3f, 0.35f))
        sprite(fog, 20f + 40f * wave(t, 3f, 0f), 282f, 1240f, 44f, a)
    }
}

// ── Mitternacht: moon, purple hills, fireflies, a little pitch lit in the valley ──

private object MitternachtArt : SceneArt() {
    override val hasFront = true

    /** x, y, wanders per loop, phase. */
    private val fireflies = listOf(
        floatArrayOf(140f, 560f, 13f, 0f), floatArrayOf(330f, 520f, 11f, 0.2f), floatArrayOf(520f, 600f, 9f, 0.45f),
        floatArrayOf(700f, 540f, 13f, 0.7f), floatArrayOf(930f, 590f, 10f, 0.1f), floatArrayOf(1090f, 530f, 12f, 0.55f),
        floatArrayOf(1230f, 610f, 9f, 0.85f), floatArrayOf(420f, 640f, 11f, 0.35f),
    )

    private const val MOON_X = 1080f
    private const val MOON_Y = 150f

    override fun DrawScope.back() {
        drawRect(
            Brush.verticalGradient(0f to Color(0xFF0B1026), 0.45f to Color(0xFF1B1440), 0.75f to Color(0xFF2A1650), 1f to Color(0xFF1B1440), startY = 0f, endY = DH),
            size = Size(DW, DH),
        )
    }

    override fun DrawScope.front() {
        val mid = Path().apply {
            moveTo(-60f, 720f); lineTo(-60f, 500f); cubicTo(120f, 476f, 260f, 510f, 420f, 490f)
            cubicTo(580f, 470f, 700f, 476f, 880f, 502f); cubicTo(1060f, 528f, 1160f, 480f, 1360f, 494f); lineTo(1360f, 720f); close()
        }
        drawPath(mid, Color(0xFF1B1440))
        drawPath(Path().apply { moveTo(820f, 500f); lineTo(828f, 488f); quadraticTo(880f, 480f, 932f, 488f); lineTo(940f, 500f); close() }, Color(0xFF0F0B2A))
        for (p in listOf(826f to 476f, 846f to 470f, 914f to 470f, 934f to 476f)) drawCircle(Color(0xFFFFE7A3), 1.6f, Offset(p.first, p.second))
        val near = Path().apply {
            moveTo(0f, 720f); lineTo(0f, 600f); cubicTo(160f, 588f, 320f, 610f, 480f, 596f)
            cubicTo(640f, 582f, 800f, 584f, 960f, 602f); cubicTo(1120f, 620f, 1180f, 592f, 1280f, 598f); lineTo(1280f, 720f); close()
        }
        drawPath(near, Color(0xFF0F0B2A))
        vignette(0xFF070A1C)
    }

    override fun sprites(make: (Int, Int, DrawScope.() -> Unit) -> ImageBitmap): List<ImageBitmap> = listOf(
        make(128, 32) {
            val path = Path().apply { moveTo(0f, 14f); lineTo(128f, 0f); lineTo(128f, 32f); lineTo(0f, 18f); close() }
            drawPath(path, Brush.horizontalGradient(0f to tint(0xFFFFF6DC, 0.22f), 1f to tint(0xFFFFF6DC, 0f), startX = 0f, endX = 128f))
        },
        glow(make, 0f to tint(0xFFFFF6DC, 0.55f), 0.4f to tint(0xFFB497FF, 0.18f), 1f to tint(0xFFB497FF, 0f)),
        make(64, 64) {
            drawCircle(
                Brush.radialGradient(0f to Color.White, 0.7f to Color(0xFFF1E9FF), 1f to Color(0xFFCBBEF0), center = Offset(26f, 26f), radius = 32f),
                radius = 32f,
            )
            drawCircle(tint(0xFFCBBEF0, 0.6f), 5f, Offset(26f, 22f))
            drawCircle(tint(0xFFCBBEF0, 0.6f), 3.5f, Offset(40f, 40f))
            drawCircle(tint(0xFFCBBEF0, 0.6f), 2.5f, Offset(36f, 18f))
        },
        glow(make, 0f to tint(0xFFFFE7A3, 1f), 0.3f to tint(0xFFFFC94D, 0.4f), 1f to tint(0xFFFFC94D, 0f)),
        glow(make, 0f to tint(0xFFC9B8FF, 0.12f), 1f to tint(0xFFC9B8FF, 0f)),
        glow(make, 0f to tint(0xFFFFC94D, 0.45f), 1f to tint(0xFFFFC94D, 0f)),
    )

    override fun DrawScope.middle(t: Float, a: Float, s: List<ImageBitmap>) {
        val (ray, halo, moon, _, cloud) = s
        stars(t, a, SKY_STARS, Color(0xFFE8E0FF))
        rotate(360f * t, Offset(MOON_X, MOON_Y)) {
            for (k in 0 until 5) rotate(k * 72f, Offset(MOON_X, MOON_Y)) { sprite(ray, MOON_X, MOON_Y - 40f, 280f, 80f, a) }
        }
        val hr = 120f * (0.96f + 0.08f * (0.5f + 0.5f * wave(t, 17f, 0f)))
        sprite(halo, MOON_X - hr, MOON_Y - hr, hr * 2f, hr * 2f, a * pulse(t, 17f, 0f, 0.6f))
        sprite(moon, MOON_X - 40f, MOON_Y - 40f, 80f, 80f, a)
        sprite(cloud, -100f + 60f * wave(t, 2f, 0f), 200f, 900f, 90f, a)
        sprite(cloud, 500f + 60f * wave(t, 3f, 0.5f), 300f, 800f, 80f, a)
        val far = Path().apply {
            val dx = 30f * wave(t, 4f, 0f)
            moveTo(-60f + dx, 720f); lineTo(-60f + dx, 430f); cubicTo(80f + dx, 400f, 200f + dx, 440f, 340f + dx, 416f)
            cubicTo(480f + dx, 392f, 600f + dx, 390f, 760f + dx, 426f); cubicTo(920f + dx, 462f, 1060f + dx, 410f, 1200f + dx, 400f)
            cubicTo(1340f + dx, 390f, 1320f + dx, 420f, 1360f + dx, 416f); lineTo(1360f + dx, 720f); close()
        }
        drawPath(far, Color(0xFF2A1650), alpha = 0.7f * a)
    }

    override fun DrawScope.top(t: Float, a: Float, s: List<ImageBitmap>) {
        val fly = s[3]
        val pitch = s[5]
        sprite(pitch, 790f, 452f, 180f, 60f, a * pulse(t, 20f, 0f, 0.5f))
        for (f in fireflies) {
            val u = 2f * PI.toFloat() * (t * f[2] + f[3])
            val x = f[0] + 30f * sin(u)
            val y = f[1] - 26f * sin(2f * u) + 10f * cos(u)
            sprite(fly, x - 7f, y - 7f, 14f, 14f, a * pulse(t, f[2] * 2f, f[3], 0.15f))
        }
    }
}
