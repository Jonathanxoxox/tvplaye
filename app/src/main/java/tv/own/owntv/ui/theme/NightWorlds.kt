package tv.own.owntv.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/*
 * Four more worlds for the night scenes, in the same layered style and on the same 1280×720 board as
 * Nordlicht and Mitternacht (see NightScenes.kt): Tiefsee, Mondwüste, Glimmwald and Hafenlichter. Each hides a
 * little football in it, like the stadium far off in Nordlicht. Moving shapes reuse one Path, so a frame
 * allocates nothing.
 */

// ── helpers ──

private val scratch = Path()

/** The one reusable path: drawn at once, before the next shape is built. */
private inline fun path(block: Path.() -> Unit): Path = scratch.apply { reset(); block() }

private val roundStroke = HashMap<Float, Stroke>()

private fun stroke(width: Float): Stroke = roundStroke.getOrPut(width) { Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round) }

private fun frac(x: Float) = x - floor(x)

private fun DrawScope.box(color: Color, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) =
    drawRect(color, Offset(x, y), Size(w, h), alpha)

private fun DrawScope.oval(color: Color, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f, line: Float = 0f) =
    if (line > 0f) drawOval(color, Offset(x, y), Size(w, h), alpha, stroke(line)) else drawOval(color, Offset(x, y), Size(w, h), alpha)

private fun DrawScope.dot(color: Color, r: Float, x: Float, y: Float, alpha: Float = 1f, line: Float = 0f) =
    if (line > 0f) drawCircle(color, r, Offset(x, y), alpha, stroke(line)) else drawCircle(color, r, Offset(x, y), alpha)

private fun DrawScope.line(color: Color, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, alpha: Float = 1f, round: Boolean = false) =
    drawLine(color, Offset(x0, y0), Offset(x1, y1), w, if (round) StrokeCap.Round else StrokeCap.Butt, alpha = alpha)

private fun DrawScope.outline(p: Path, color: Color, w: Float, alpha: Float = 1f) = drawPath(p, color, alpha, stroke(w))

private fun vgrad(vararg stops: Pair<Float, Color>, y0: Float, y1: Float) = Brush.verticalGradient(*stops, startY = y0, endY = y1)

/** A tapered beam pointing down, 64×256, like the floodlight cones. */
private fun cone(make: (Int, Int, DrawScope.() -> Unit) -> ImageBitmap, color: Long, topA: Float, midA: Float): ImageBitmap =
    make(64, 256) {
        val p = Path().apply { moveTo(29f, 0f); lineTo(35f, 0f); lineTo(64f, 256f); lineTo(0f, 256f); close() }
        drawPath(p, vgrad(0f to tint(color, topA), 0.6f to tint(color, midA), 1f to tint(color, 0f), y0 = 0f, y1 = 256f))
    }

/** Fixed pseudo-random numbers, so a scene looks the same every time. */
private class Rng(seed: Long) {
    private var s = seed
    fun next(): Float {
        s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL
        return s / 4294967296f
    }
}

// ── Tiefsee: light from the surface, lantern jellyfish, bubbles, kelp, a sunken goal with an anglerfish keeper ──

internal object TiefseeArt : SceneArt() {
    override val hasFront = true

    /** x, y, radius, colour, bob cycles, phase, drift. */
    private val jellies = listOf(
        floatArrayOf(250f, 250f, 15f, 2f, 5f, 0.1f, 30f), floatArrayOf(560f, 150f, 22f, 1f, 4f, 0.4f, -40f),
        floatArrayOf(800f, 330f, 14f, 3f, 6f, 0.7f, 26f), floatArrayOf(1020f, 190f, 27f, 0f, 3f, 0.25f, 36f),
        floatArrayOf(1190f, 400f, 18f, 3f, 5f, 0.55f, -30f), floatArrayOf(430f, 470f, 11f, 2f, 7f, 0.85f, 20f),
    )
    private val jellyColors = longArrayOf(0xFF7CF7E6, 0xFFFF9AD5, 0xFFC2A8FF, 0xFFFFD27A)

    /** x, tilt, sways per loop. */
    private val rays = listOf(floatArrayOf(300f, -12f, 9f), floatArrayOf(520f, -8f, 7f), floatArrayOf(760f, -14f, 6f), floatArrayOf(980f, -4f, 5f), floatArrayOf(1170f, 8f, 7f))

    /** x, height, sways per loop, phase. */
    private val kelp = listOf(
        floatArrayOf(90f, 230f, 5f, 0f), floatArrayOf(130f, 170f, 4f, 0.3f), floatArrayOf(1120f, 260f, 4f, 0.6f), floatArrayOf(1160f, 190f, 5f, 0.15f),
        floatArrayOf(1236f, 240f, 3f, 0.45f), floatArrayOf(860f, 120f, 6f, 0.8f), floatArrayOf(700f, 90f, 5f, 0.2f),
    )
    private val vents = listOf(150f to 690f, 640f to 700f, 1080f to 690f)

    /** x, falls per loop, phase, radius: marine snow. */
    private val snow = Rng(7).let { r -> List(26) { floatArrayOf(r.next() * DW, 2f + floor(r.next() * 3f), r.next(), 0.8f + r.next() * 1.2f) } }

    override fun DrawScope.back() {
        drawRect(
            vgrad(0f to Color(0xFF0D5577), 0.16f to Color(0xFF0A3D60), 0.45f to Color(0xFF062444), 0.75f to Color(0xFF03132A), 1f to Color(0xFF020A18), y0 = 0f, y1 = DH),
            size = Size(DW, DH),
        )
        drawRect(vgrad(0f to tint(0xFF9FE8F0, 0.18f), 1f to tint(0xFF9FE8F0, 0f), y0 = 0f, y1 = 90f), size = Size(DW, 90f))
        val pts = floatArrayOf(80f, 520f, 170f, 548f, 260f, 506f, 380f, 552f, 470f, 528f, 600f, 560f, 720f, 534f, 840f, 556f, 960f, 516f, 1060f, 548f, 1160f, 522f, 1280f, 546f)
        val reef = path {
            moveTo(0f, DH); lineTo(0f, 540f)
            var px = 0f
            var py = 540f
            for (i in pts.indices step 2) {
                val x = pts[i]
                val y = pts[i + 1]
                quadraticTo(px + (x - px) / 2f, min(py, y) - 16f, x, y)
                px = x; py = y
            }
            lineTo(DW, DH); close()
        }
        drawPath(reef, vgrad(0f to Color(0xFF0B3050), 1f to Color(0xFF061A30), y0 = 500f, y1 = DH), alpha = 0.85f)
    }

    override fun DrawScope.front() {
        drawPath(
            path {
                moveTo(0f, DH); lineTo(0f, 640f); cubicTo(160f, 618f, 320f, 660f, 520f, 646f); cubicTo(720f, 632f, 860f, 612f, 1020f, 630f)
                cubicTo(1140f, 644f, 1220f, 626f, 1280f, 632f); lineTo(1280f, DH); close()
            },
            Color(0xFF05182C),
        )
        for (i in 0 until 6) {
            val y = 664f + i * 10f
            outline(path { moveTo(60f + i * 30f, y); cubicTo(300f, y - 8f, 500f, y + 8f, 760f - i * 20f, y) }, tint(0xFF2A6A88, 0.22f), 1.5f)
        }
        // the sunken goal, a little askew
        val ink = Color(0xFF0C3350)
        val net = tint(0xFF5FB8D0, 0.18f)
        rotate(-4f, Offset(960f, 640f)) {
            for (k in 0 until 7) line(net, 892f + k * 22f, 562f, 900f + k * 21f, 640f, 1.2f)
            for (k in 0 until 4) line(net, 892f, 562f + k * 20f, 1032f, 568f + k * 18f, 1.2f)
            line(ink, 886f, 556f, 1040f, 556f, 7f, round = true)
            line(ink, 888f, 556f, 888f, 646f, 7f, round = true)
            line(ink, 1038f, 556f, 1038f, 646f, 7f, round = true)
        }
        val rock = Color(0xFF031222)
        drawPath(path { moveTo(0f, DH); lineTo(0f, 600f); cubicTo(40f, 570f, 120f, 580f, 170f, 620f); cubicTo(210f, 650f, 240f, 690f, 250f, DH); close() }, rock)
        drawPath(path { moveTo(1280f, DH); lineTo(1280f, 590f); cubicTo(1230f, 570f, 1160f, 600f, 1130f, 640f); cubicTo(1110f, 670f, 1100f, 700f, 1096f, DH); close() }, rock)
        for (s in listOf(floatArrayOf(52f, 70f, 14f), floatArrayOf(72f, 50f, 12f), floatArrayOf(1218f, 64f, 13f), floatArrayOf(1196f, 44f, 11f))) {
            val (x, h, w) = Triple(s[0], s[1], s[2])
            drawPath(path { moveTo(x - w / 2f, 610f); lineTo(x - w / 2f, 610f - h); quadraticTo(x, 604f - h, x + w / 2f, 610f - h); lineTo(x + w / 2f, 610f); close() }, Color(0xFF041628))
            oval(tint(0xFF5CF0E0, 0.25f), x - w / 2f + 2f, 606f - h, w - 4f, 6f)
        }
        vignette(0xFF010610)
    }

    override fun sprites(make: (Int, Int, DrawScope.() -> Unit) -> ImageBitmap): List<ImageBitmap> = listOf(
        cone(make, 0xFF9FE8F0, 0.2f, 0.06f),
        glow(make, 0f to tint(0xFF9FE8F0, 0.16f), 1f to tint(0xFF9FE8F0, 0f)),
        glow(make, 0f to tint(0xFFFFE7A3, 1f), 0.3f to tint(0xFFFFC94D, 0.4f), 1f to tint(0xFFFFC94D, 0f)),
    ) + jellyColors.map { c -> glow(make, 0f to tint(c, 0.5f), 0.35f to tint(c, 0.16f), 1f to tint(c, 0f)) }

    override fun DrawScope.middle(t: Float, a: Float, s: List<ImageBitmap>) {
        val ray = s[0]
        val caustic = s[1]
        for (r in rays) {
            val x = r[0]
            rotate(r[1] + 3f * wave(t, r[2], x / 1000f), Offset(x, -40f)) { sprite(ray, x - 110f, -40f, 220f, 620f, a * pulse(t, r[2], x / 700f, 0.55f)) }
        }
        for (i in 0 until 6) {
            val x = 80f + i * 210f + 40f * wave(t, 3f + i % 3, i * 0.17f)
            sprite(caustic, x - 120f, -30f, 240f, 70f, a * pulse(t, 7f + i, i * 0.3f, 0.3f))
        }
        // a manta gliding through the distance, once per loop, wings beating slowly
        val mp = frac(t + 0.3f)
        val mx = -160f + 1600f * mp
        val my = 380f + 40f * wave(t, 2f, 0f)
        val flap = 0.55f + 0.45f * wave(t, 30f, 0f)
        val mf = min(1f, min(mp, 1f - mp) * 8f)
        val manta = Color(0xFF0A2C4C)
        scale(1f, flap, Offset(mx, my)) {
            drawPath(
                path {
                    moveTo(mx + 46f, my); quadraticTo(mx + 10f, my - 6f, mx - 4f, my - 44f); quadraticTo(mx - 16f, my - 10f, mx - 40f, my)
                    quadraticTo(mx - 16f, my + 10f, mx - 4f, my + 44f); quadraticTo(mx + 10f, my + 6f, mx + 46f, my); close()
                },
                manta, alpha = a * 0.8f * mf,
            )
        }
        line(manta, mx - 40f, my, mx - 86f, my + 4f, 2f, a * 0.8f * mf, round = true)
        val flake = Color(0xFFCFEFFF)
        for (f in snow) {
            val p = frac(t * f[1] + f[2])
            dot(flake, f[3], f[0] + 14f * wave(t, f[1] * 3f, f[2]), -10f + 640f * p, a * 0.35f * min(1f, (1f - p) * 4f))
        }
    }

    override fun DrawScope.top(t: Float, a: Float, s: List<ImageBitmap>) {
        val lure = s[2]
        val weed = Color(0xFF0A3A3E)
        val weedLight = tint(0xFF3FA890, 0.3f)
        for (k in kelp) {
            val x = k[0]
            val h = k[1]
            val sw = 26f * wave(t, k[2], k[3])
            val sw2 = 18f * wave(t, k[2], k[3] + 0.25f)
            val p = path { moveTo(x, 700f); cubicTo(x + sw2, 700f - h * 0.35f, x - sw2, 700f - h * 0.7f, x + sw, 700f - h) }
            outline(p, weed, 11f, a)
            outline(p, weedLight, 2.5f, a)
        }
        // the anglerfish keeper in front of the goal
        val fx = 930f + 30f * wave(t, 2f, 0f)
        val fy = 616f + 6f * wave(t, 5f, 0.2f)
        val body = Color(0xFF0E3452)
        drawPath(path { moveTo(fx + 30f, fy); lineTo(fx + 52f, fy - 14f); lineTo(fx + 52f, fy + 14f); close() }, body, alpha = a)
        oval(body, fx - 34f, fy - 16f, 68f, 34f, a)
        oval(tint(0xFF5FB8D0, 0.35f), fx - 34f, fy - 16f, 68f, 34f, a, line = 1.2f)
        outline(path { moveTo(fx - 32f, fy + 4f); quadraticTo(fx - 22f, fy + 10f, fx - 12f, fy + 4f) }, tint(0xFFBFF5E8, 0.5f), 1.2f, a)
        outline(path { moveTo(fx - 18f, fy - 14f); quadraticTo(fx - 36f, fy - 52f, fx - 54f, fy - 36f) }, Color(0xFF0A2A40), 2.5f, a)
        dot(tint(0xFFBFF5E8, 0.8f), 3f, fx - 20f, fy - 4f, a)
        val lr = 22f * pulse(t, 9f, 0f, 0.7f)
        sprite(lure, fx - 54f - lr, fy - 36f - lr, lr * 2f, lr * 2f, a * pulse(t, 9f, 0f, 0.6f))
        // bubbles from the vents
        val rim = Color(0xFFCFF6FF)
        vents.forEachIndexed { vi, (vx, vy) ->
            for (i in 0 until 4) {
                val ph = i * 0.27f + vi * 0.13f
                val p = frac(t * (5f + vi + i) + ph)
                val y = vy - 520f * p
                val x = vx + 8f * sin(2f * PI.toFloat() * (p * 3f + ph))
                val r = 2f + i % 3 + p * 2f
                val fade = min(1f, p * 6f) * (1f - p)
                dot(rim, r, x, y, a * 0.5f * fade, line = 1.2f)
                dot(Color.White, r * 0.3f, x - r * 0.35f, y - r * 0.35f, a * 0.6f * fade)
            }
        }
        for (j in jellies) {
            val r = j[2]
            val ci = j[3].toInt()
            val cyc = j[4]
            val ph = j[5]
            val c = jellyColors[ci]
            val x = j[0] + j[6] * wave(t, cyc / 2f, ph + 0.25f)
            val y = j[1] + 22f * wave(t, cyc, ph)
            val beat = wave(t, cyc * 4f, ph)
            val w = r * (1f + 0.08f * beat)
            val h = r * (0.9f - 0.06f * beat)
            sprite(s[3 + ci], x - r * 3f, y - r * 2.4f, r * 6f, r * 6f, a * pulse(t, cyc * 2f, ph, 0.6f))
            val arm = tint(c, 0.4f)
            for (k in 0 until 4) {
                val tx = x - w * 0.6f + k * w * 0.4f
                val len = r * (2.2f + 0.4f * (k % 2))
                val sw = r * 0.35f * wave(t, cyc * 2f, ph + k * 0.2f)
                outline(path { moveTo(tx, y); cubicTo(tx + sw, y + len * 0.35f, tx - sw, y + len * 0.7f, tx + sw * 0.6f, y + len) }, arm, 1.4f, a)
            }
            val bell = path {
                moveTo(x - w, y); cubicTo(x - w, y - h * 1.35f, x + w, y - h * 1.35f, x + w, y)
                quadraticTo(x + w * 0.75f, y + h * 0.22f, x + w * 0.5f, y); quadraticTo(x + w * 0.25f, y + h * 0.22f, x, y)
                quadraticTo(x - w * 0.25f, y + h * 0.22f, x - w * 0.5f, y); quadraticTo(x - w * 0.75f, y + h * 0.22f, x - w, y); close()
            }
            drawPath(bell, tint(c, 0.42f), alpha = a)
            oval(tint(0xFFFFFFFF, 0.35f), x - w * 0.45f, y - h * 0.85f, w * 0.6f, h * 0.35f, a)
        }
    }
}

// ── Mondwüste: a big moon over violet dunes, the milky way, shooting stars, a caravan, an oasis, a stadium far off ──

internal object MondwuesteArt : SceneArt() {
    override val hasFront = true

    private const val MOON_X = 1010f
    private const val MOON_Y = 170f

    private fun farY(x: Float) = 452f + 9f * sin(x / 150f + 0.5f) + 5f * sin(x / 47f + 1.3f)

    private val starList = SKY_STARS.take(6) + listOf(
        floatArrayOf(930f, 300f, 1.4f, 25f, 0.4f), floatArrayOf(1180f, 300f, 1f, 37f, 0.6f), floatArrayOf(1150f, 60f, 1.1f, 19f, 0.2f),
        floatArrayOf(1240f, 140f, 1.5f, 37f, 0.8f), floatArrayOf(760f, 260f, 1f, 25f, 0.35f), floatArrayOf(600f, 340f, 1.3f, 19f, 0.65f),
    ) + SKY_STARS.drop(12) + listOf(floatArrayOf(170f, 260f, 1.1f, 25f, 0.5f), floatArrayOf(420f, 300f, 1f, 19f, 0.1f), floatArrayOf(60f, 340f, 1.2f, 37f, 0.85f))

    /** phase, x, y, length, angle: a shooting star four times a loop each. */
    private val meteors = listOf(floatArrayOf(0.1f, 380f, 60f, 160f, 24f), floatArrayOf(0.55f, 760f, 40f, 130f, 30f), floatArrayOf(0.8f, 200f, 120f, 110f, 20f))

    /** y, crossings per loop, phase: sand blown along the dunes. */
    private val sand = Rng(5).let { r -> List(14) { floatArrayOf(520f + r.next() * 180f, 3f + floor(r.next() * 4f), r.next()) } }

    private val palms = listOf(floatArrayOf(930f, 96f, -14f), floatArrayOf(968f, 120f, 10f), floatArrayOf(1004f, 84f, 22f))
    private val fronds = floatArrayOf(-46f, 10f, -30f, 26f, 44f, 12f, 30f, 28f, -6f, -18f, 10f, 22f)

    override fun DrawScope.back() {
        drawRect(
            vgrad(
                0f to Color(0xFF07071E), 0.3f to Color(0xFF141238), 0.5f to Color(0xFF261D4E), 0.6f to Color(0xFF4A3466),
                0.66f to Color(0xFF7A4E70), 0.72f to Color(0xFF4A3466), 1f to Color(0xFF1A1230), y0 = 0f, y1 = DH,
            ),
            size = Size(DW, DH),
        )
        // the milky way: a soft band and a dust of small stars along it
        rotate(-18f, Offset(640f, 200f)) {
            scale(1f, 90f / 760f, Offset(640f, 200f)) {
                drawCircle(
                    Brush.radialGradient(0f to tint(0xFFC8B8FF, 0.17f), 0.6f to tint(0xFFB8A8E0, 0.05f), 1f to tint(0xFFC8B8FF, 0f), center = Offset(640f, 200f), radius = 760f),
                    760f, Offset(640f, 200f),
                )
            }
            val r = Rng(11)
            val dust = Color(0xFFF0E6FF)
            repeat(220) {
                val x = 640f + (r.next() * 2f - 1f) * 720f
                val y = 200f + (r.next() + r.next() + r.next() - 1.5f) * 70f
                dot(dust, 0.4f + r.next() * 0.8f, x, y, 0.2f + r.next() * 0.5f)
            }
        }
        drawPath(
            path { moveTo(0f, DH); var x = 0f; while (x <= DW) { lineTo(x, farY(x)); x += 20f }; lineTo(DW, DH); close() },
            vgrad(0f to Color(0xFF5A3A68), 1f to Color(0xFF3A2652), y0 = 440f, y1 = 520f),
        )
        // the stadium on the horizon
        val sx = 250f
        val sy = farY(sx)
        val ink = Color(0xFF2E1E46)
        drawPath(path { moveTo(sx - 52f, sy + 2f); lineTo(sx - 46f, sy - 12f); quadraticTo(sx, sy - 20f, sx + 46f, sy - 12f); lineTo(sx + 52f, sy + 2f); close() }, ink)
        for (mx in floatArrayOf(-40f, -16f, 16f, 40f)) {
            line(ink, sx + mx, sy - 10f, sx + mx * 1.1f, sy - 34f, 2f)
            dot(Color(0xFFFFF2D0), 2.2f, sx + mx * 1.1f, sy - 35f)
        }
    }

    override fun DrawScope.front() {
        val crest: Path.() -> Unit = {
            moveTo(-20f, 560f); cubicTo(160f, 520f, 340f, 500f, 520f, 532f); cubicTo(700f, 566f, 860f, 500f, 1040f, 496f); cubicTo(1160f, 494f, 1240f, 512f, 1300f, 520f)
        }
        drawPath(path { moveTo(-20f, DH); lineTo(-20f, 560f); crest(this); lineTo(1300f, DH); close() }, vgrad(0f to Color(0xFF3C2754), 1f to Color(0xFF1E1434), y0 = 490f, y1 = DH))
        outline(path { crest(this) }, tint(0xFFF6C8E0, 0.28f), 2f)
        val palm = Color(0xFF150D28)
        for (pl in palms) {
            val x = pl[0]
            val tx = x + pl[2]
            val ty = 500f - pl[1]
            outline(path { moveTo(x, 500f); quadraticTo(x + pl[2] * 0.2f, 500f - pl[1] * 0.5f, tx, ty) }, palm, 5f)
            for (i in fronds.indices step 2) {
                val dx = fronds[i]
                val dy = fronds[i + 1]
                val cx = tx + dx * 0.5f
                val cy = ty + dy * 0.2f - 16f
                drawPath(path { moveTo(tx, ty - 3f); quadraticTo(cx, cy - 6f, tx + dx, ty + dy); quadraticTo(cx, cy + 6f, tx, ty + 3f); close() }, palm)
            }
        }
        val tent = Color(0xFF1A1030)
        drawPath(path { moveTo(1037f, 506f); lineTo(1060f, 470f); lineTo(1083f, 506f); close() }, tent)
        drawPath(path { moveTo(1093f, 506f); lineTo(1110f, 470f); lineTo(1127f, 506f); close() }, tent)
        drawPath(path { moveTo(1054f, 506f); lineTo(1060f, 486f); lineTo(1066f, 506f); close() }, tint(0xFFFFB45A, 0.8f))
        val near: Path.() -> Unit = { moveTo(-20f, 640f); cubicTo(200f, 610f, 420f, 660f, 640f, 650f); cubicTo(860f, 640f, 1000f, 600f, 1300f, 620f) }
        drawPath(path { moveTo(-20f, DH); lineTo(-20f, 640f); near(this); lineTo(1300f, DH); close() }, vgrad(0f to Color(0xFF22163A), 1f to Color(0xFF0E0920), y0 = 600f, y1 = DH))
        outline(path { near(this) }, tint(0xFFE89A8A, 0.22f), 1.5f)
        for (i in 0 until 5) {
            outline(path { moveTo(120f + i * 60f, 672f + i * 9f); quadraticTo(400f, 664f + i * 9f, 700f - i * 40f, 676f + i * 9f) }, tint(0xFFE89A8A, 0.1f), 1.2f)
        }
        vignette(0xFF05041A)
    }

    override fun sprites(make: (Int, Int, DrawScope.() -> Unit) -> ImageBitmap): List<ImageBitmap> = listOf(
        make(128, 128) {
            drawCircle(
                Brush.radialGradient(0f to Color(0xFFFBEFE6), 0.75f to Color(0xFFEED6CE), 1f to Color(0xFFD8B0B6), center = Offset(52f, 50f), radius = 66f),
                64f, Offset(64f, 64f),
            )
            val crater = tint(0xFFD8B4B8, 0.45f)
            for (c in listOf(floatArrayOf(48f, 44f, 12f), floatArrayOf(84f, 78f, 8f), floatArrayOf(74f, 34f, 6f), floatArrayOf(40f, 86f, 7f), floatArrayOf(90f, 52f, 4f))) {
                drawCircle(crater, c[2], Offset(c[0], c[1]))
            }
        },
        glow(make, 0f to tint(0xFFFFE8C8, 0.32f), 0.3f to tint(0xFFFFE8C8, 0.1f), 1f to tint(0xFFFFE8C8, 0f)),
        make(128, 8) { drawRect(Brush.horizontalGradient(0f to tint(0xFFFFFFFF, 0f), 1f to Color.White, startX = 0f, endX = 128f), Offset(0f, 3f), Size(128f, 2f)) },
        glow(make, 0f to tint(0xFFFFB45A, 0.85f), 0.25f to tint(0xFFFFB45A, 0.36f), 1f to tint(0xFFFFB45A, 0f)),
        glow(make, 0f to tint(0xFFFFC94D, 0.45f), 0.5f to tint(0xFFFFD68C, 0.1f), 1f to tint(0xFFFFD68C, 0f)),
        glow(make, 0f to tint(0xFFB8A8E0, 0.2f), 1f to tint(0xFFB8A8E0, 0f)),
        glow(make, 0f to tint(0xFFFFE2B8, 0.6f), 1f to tint(0xFFFFE2B8, 0f)),
    )

    override fun DrawScope.middle(t: Float, a: Float, s: List<ImageBitmap>) {
        val (moon, halo, trail, lamp, dome) = s
        stars(t, a, starList, Color(0xFFF0E6FF))
        val hr = 170f * (0.95f + 0.05f * wave(t, 12f, 0f))
        sprite(halo, MOON_X - hr, MOON_Y - hr, hr * 2f, hr * 2f, a)
        sprite(moon, MOON_X - 64f, MOON_Y - 64f, 128f, 128f, a)
        for (m in meteors) {
            val p = frac(t * 4f + m[0])
            if (p < 0.06f) {
                val k = p / 0.06f
                rotate(m[4], Offset(m[1], m[2])) { sprite(trail, m[1] + 260f * k - m[3], m[2] - 2f, m[3], 4f, a * sin(PI.toFloat() * k)) }
            }
        }
        sprite(dome, 250f - 110f, farY(250f) - 70f, 220f, 90f, a * pulse(t, 20f, 0f, 0.55f))
        // the caravan walking along the far crest, twice per loop
        val p = frac(t * 2f + 0.15f)
        val fade = a * min(1f, min(p, 1f - p) * 10f)
        val camel = Color(0xFF2C1C44)
        for (k in 0 until 4) {
            val x = 340f + 520f * p - k * 26f
            val y = farY(x) - 2f
            val step = wave(t, 160f, k * 0.25f)
            oval(camel, x - 9f, y - 16f + step, 18f, 9f, fade)
            dot(camel, 4.5f, x - 1f, y - 17f + step, fade)
            line(camel, x + 7f, y - 12f + step, x + 12f, y - 22f + step, 2.5f, fade, round = true)
            dot(camel, 2.6f, x + 13f, y - 23f + step, fade)
            line(camel, x - 6f, y - 8f, x - 6f - 2f * step, y, 1.6f, fade)
            line(camel, x + 5f, y - 8f, x + 5f + 2f * step, y, 1.6f, fade)
            if (k == 0) sprite(lamp, x + 4f, y - 32f, 22f, 22f, fade * pulse(t, 60f, 0f, 0.6f))
        }
    }

    override fun DrawScope.top(t: Float, a: Float, s: List<ImageBitmap>) {
        val lamp = s[3]
        val veil = s[5]
        val mote = s[6]
        sprite(lamp, 1028f, 470f, 64f, 64f, a * pulse(t, 40f, 0.2f, 0.65f))
        sprite(veil, -200f + 120f * wave(t, 2f, 0f), 470f, 1000f, 80f, a)
        sprite(veil, 500f + 100f * wave(t, 3f, 0.5f), 560f, 900f, 70f, a)
        for (m in sand) {
            val p = frac(t * m[1] + m[2])
            sprite(mote, -20f + 1320f * p, m[0] + 10f * wave(t, m[1] * 4f, m[2]), 8f, 8f, a * 0.8f * sin(PI.toFloat() * p))
        }
    }
}

// ── Glimmwald: glowing mushrooms, moonlight through the trunks, spores, fireflies and a fairy ring for a centre circle ──

internal object GlimmwaldArt : SceneArt() {
    override val hasFront = true

    private const val RING_X = 860f
    private const val RING_Y = 640f
    private const val RING_RX = 170f
    private const val RING_RY = 26f

    private val farTrunks = floatArrayOf(150f, 16f, 240f, 22f, 330f, 14f, 430f, 26f, 520f, 18f, 610f, 14f, 700f, 24f, 790f, 16f, 1000f, 20f, 1090f, 14f, 1180f, 24f, 1250f, 16f)
    private val midTrunks = floatArrayOf(200f, 34f, 470f, 40f, 660f, 30f, 1040f, 44f, 1220f, 36f)

    /** x, ground y, size, colour, phase. */
    private val caps = listOf(
        floatArrayOf(108f, 650f, 26f, 0f, 0f), floatArrayOf(140f, 662f, 16f, 0f, 0.3f), floatArrayOf(84f, 666f, 12f, 2f, 0.6f),
        floatArrayOf(1130f, 640f, 30f, 1f, 0.2f), floatArrayOf(1168f, 656f, 18f, 2f, 0.5f), floatArrayOf(1100f, 660f, 14f, 0f, 0.8f),
        floatArrayOf(1196f, 668f, 11f, 1f, 0.1f), floatArrayOf(600f, 676f, 14f, 2f, 0.4f), floatArrayOf(622f, 684f, 9f, 0f, 0.7f),
    )
    private val capColors = longArrayOf(0xFF5CF0D0, 0xFFFF7AD9, 0xFFB497FF)

    /** x, tilt, phase. */
    private val rays = listOf(floatArrayOf(880f, 20f, 0f), floatArrayOf(960f, 18f, 0.3f), floatArrayOf(1040f, 22f, 0.6f), floatArrayOf(1110f, 16f, 0.15f))
    private val flies = listOf(
        floatArrayOf(300f, 520f, 13f, 0f), floatArrayOf(520f, 450f, 11f, 0.2f), floatArrayOf(760f, 540f, 9f, 0.45f), floatArrayOf(980f, 470f, 13f, 0.7f),
        floatArrayOf(1180f, 520f, 10f, 0.1f), floatArrayOf(420f, 600f, 11f, 0.55f), floatArrayOf(1040f, 590f, 12f, 0.85f),
    )

    /** x, rises per loop, phase, size. */
    private val spores = Rng(3).let { r -> List(18) { floatArrayOf(80f + r.next() * 1120f, 3f + floor(r.next() * 4f), r.next(), 4f + r.next() * 5f) } }

    override fun DrawScope.back() {
        drawRect(vgrad(0f to Color(0xFF030B12), 0.35f to Color(0xFF06171C), 0.62f to Color(0xFF0B2626), 1f to Color(0xFF04100F), y0 = 0f, y1 = DH), size = Size(DW, DH))
        drawRect(Brush.radialGradient(0f to tint(0xFFCFE8FF, 0.16f), 1f to tint(0xFFCFE8FF, 0f), center = Offset(960f, 40f), radius = 360f), Offset(600f, 0f), Size(680f, 400f))
        dot(Color(0xFFEAF4FF), 26f, 980f, 70f, 0.9f)
        dot(tint(0xFFCFE8FF, 0.25f), 34f, 980f, 70f)
        val far = Color(0xFF0C2A2C)
        for (i in farTrunks.indices step 2) {
            val x = farTrunks[i]
            val w = farTrunks[i + 1]
            drawPath(path { moveTo(x - w / 2f, DH); lineTo(x - w * 0.4f, 0f); lineTo(x + w * 0.4f, 0f); lineTo(x + w / 2f, DH); close() }, far, alpha = 0.75f)
        }
        drawRect(vgrad(0f to tint(0xFF3A7A70, 0f), 0.5f to tint(0xFF3A7A70, 0.14f), 1f to tint(0xFF3A7A70, 0f), y0 = 440f, y1 = 640f), Offset(0f, 440f), Size(DW, 200f))
        val mid = Color(0xFF071A1C)
        for (i in midTrunks.indices step 2) {
            val x = midTrunks[i]
            val w = midTrunks[i + 1]
            drawPath(
                path {
                    moveTo(x - w / 2f - 10f, DH); quadraticTo(x - w / 2f, 600f, x - w * 0.42f, 0f); lineTo(x + w * 0.42f, 0f)
                    quadraticTo(x + w / 2f, 600f, x + w / 2f + 10f, DH); close()
                },
                mid,
            )
        }
    }

    override fun DrawScope.front() {
        drawPath(
            path { moveTo(0f, DH); lineTo(0f, 640f); cubicTo(200f, 620f, 420f, 646f, 640f, 630f); cubicTo(820f, 616f, 1000f, 618f, 1280f, 634f); lineTo(1280f, DH); close() },
            vgrad(0f to Color(0xFF08201E), 1f to Color(0xFF020909), y0 = 616f, y1 = DH),
        )
        val dark = Color(0xFF020909)
        drawPath(path { moveTo(0f, DH); lineTo(0f, 0f); lineTo(58f, 0f); quadraticTo(52f, 420f, 70f, 620f); quadraticTo(90f, 680f, 130f, DH); close() }, dark)
        drawPath(path { moveTo(1280f, DH); lineTo(1280f, 0f); lineTo(1224f, 0f); quadraticTo(1232f, 400f, 1214f, 610f); quadraticTo(1196f, 680f, 1150f, DH); close() }, dark)
        val fern = Color(0xFF051514)
        for (f in listOf(floatArrayOf(260f, 700f, 1f, 1f), floatArrayOf(330f, 712f, 0.8f, -1f), floatArrayOf(980f, 706f, 0.9f, 1f), floatArrayOf(1040f, 716f, 0.7f, -1f), floatArrayOf(740f, 716f, 0.6f, 1f))) {
            val (x, y) = f[0] to f[1]
            val sc = f[2]
            val dir = f[3]
            for (k in 0 until 6) {
                val ang = ((-150f + k * 22f) * PI / 180f).toFloat()
                val len = (70f - abs(k - 2.5f) * 8f) * sc
                val ex = x + cos(ang) * len * dir
                val ey = y + sin(ang) * len
                outline(path { moveTo(x, y); quadraticTo((x + ex) / 2f + 6f * dir, (y + ey) / 2f - 14f, ex, ey) }, fern, 6f * sc)
            }
        }
        // canopy: clumps of leaves, open around the moon
        val r = Rng(9)
        var x = -40f
        while (x <= DW + 40f) {
            val rad = 46f + r.next() * 40f
            val y = 10f + r.next() * 50f + if (x > 760f && x < 1200f) -20f else 0f
            if (x <= 850f || x >= 1110f) dot(dark, rad, x + 10f, y)
            x += 46f
        }
        val leaf = Color(0xFF031012)
        for (b in listOf(floatArrayOf(700f, 30f, 880f, 110f), floatArrayOf(1260f, 40f, 1120f, 120f), floatArrayOf(300f, 60f, 420f, 160f))) {
            outline(path { moveTo(b[0], b[1]); quadraticTo((b[0] + b[2]) / 2f, b[1] + 10f, b[2], b[3]) }, dark, 6f)
            for (k in 1..4) {
                val u = k / 5f
                oval(leaf, b[0] + (b[2] - b[0]) * u - 12f, b[1] + (b[3] - b[1]) * u * u + 10f * u - 4f, 24f, 12f)
            }
        }
        vignette(0xFF010607)
    }

    override fun sprites(make: (Int, Int, DrawScope.() -> Unit) -> ImageBitmap): List<ImageBitmap> = listOf(
        cone(make, 0xFFCFE8FF, 0.12f, 0.04f),
        glow(make, 0f to tint(0xFFC8FFE8, 0.8f), 1f to tint(0xFFC8FFE8, 0f)),
        glow(make, 0f to tint(0xFFFFE7A3, 1f), 0.3f to tint(0xFFFFC94D, 0.4f), 1f to tint(0xFFFFC94D, 0f)),
        glow(make, 0f to tint(0xFF7FD6C0, 0.12f), 1f to tint(0xFF7FD6C0, 0f)),
        glow(make, 0f to tint(0xFF9FFFD0, 0.28f), 1f to tint(0xFF9FFFD0, 0f)),
    ) + capColors.map { c -> glow(make, 0f to tint(c, 0.6f), 0.35f to tint(c, 0.2f), 1f to tint(c, 0f)) }

    override fun DrawScope.middle(t: Float, a: Float, s: List<ImageBitmap>) {
        val ray = s[0]
        val fog = s[3]
        for (r in rays) {
            rotate(r[1] + 1.5f * wave(t, 4f, r[2]), Offset(r[0], 40f)) { sprite(ray, r[0] - 90f, 40f, 180f, 660f, a * pulse(t, 6f, r[2], 0.45f)) }
        }
        sprite(fog, -100f + 80f * wave(t, 2f, 0f), 500f, 900f, 120f, a)
        sprite(fog, 500f + 80f * wave(t, 3f, 0.4f), 540f, 900f, 110f, a)
    }

    override fun DrawScope.top(t: Float, a: Float, s: List<ImageBitmap>) {
        val spore = s[1]
        val fly = s[2]
        val clearing = s[4]
        sprite(clearing, RING_X - 260f, RING_Y - 90f, 520f, 170f, a * pulse(t, 10f, 0f, 0.6f))
        // the fairy ring: tiny caps light up one after another, round and round
        val stem = tint(0xFFDDEFE8, 0.5f)
        for (i in 0 until 26) {
            val u = 2f * PI.toFloat() * i / 26f
            val x = RING_X + RING_RX * cos(u)
            val y = RING_Y + RING_RY * sin(u)
            val lit = pulse(t, 8f, -i / 26f, 0.25f)
            sprite(s[5 + i % 3], x - 14f, y - 16f, 28f, 28f, a * lit)
            line(stem, x, y, x, y - 6f, 2f, a)
            drawPath(path { moveTo(x - 5f, y - 5f); quadraticTo(x, y - 13f, x + 5f, y - 5f); close() }, tint(capColors[i % 3], 0.5f + 0.5f * lit), alpha = a)
        }
        // the puffball on the centre spot
        val by = RING_Y - 8f + 2f * wave(t, 10f, 0f)
        sprite(spore, RING_X - 22f, by - 22f, 44f, 44f, a * pulse(t, 10f, 0f, 0.5f))
        dot(Color(0xFFEFFFF6), 8f, RING_X, by, a)
        dot(tint(0xFF8FD8C0, 0.6f), 2.4f, RING_X - 2f, by - 1f, a)
        val stemFill = tint(0xFFD8F2EA, 0.55f)
        val spot = tint(0xFFFFFFFF, 0.6f)
        for (m in caps) {
            val x = m[0]
            val y = m[1]
            val r = m[2]
            val ci = m[3].toInt()
            val lit = pulse(t, 12f, m[4], 0.55f)
            sprite(s[5 + ci], x - r * 2.6f, y - r * 2.4f, r * 5.2f, r * 4.4f, a * lit)
            drawPath(
                path {
                    moveTo(x - r * 0.18f, y); quadraticTo(x - r * 0.28f, y - r * 0.5f, x - r * 0.14f, y - r * 0.9f); lineTo(x + r * 0.14f, y - r * 0.9f)
                    quadraticTo(x + r * 0.28f, y - r * 0.5f, x + r * 0.18f, y); close()
                },
                stemFill, alpha = a,
            )
            drawPath(
                path { moveTo(x - r, y - r * 0.85f); cubicTo(x - r * 0.9f, y - r * 1.7f, x + r * 0.9f, y - r * 1.7f, x + r, y - r * 0.85f); quadraticTo(x, y - r, x - r, y - r * 0.85f); close() },
                tint(capColors[ci], 0.55f + 0.35f * lit), alpha = a,
            )
            dot(spot, r * 0.12f, x - r * 0.45f, y - r * 1.2f, a)
            dot(spot, r * 0.09f, x + r * 0.2f, y - r * 1.38f, a)
            dot(spot, r * 0.08f, x + r * 0.55f, y - r * 1.08f, a)
        }
        for (sp in spores) {
            val p = frac(t * sp[1] + sp[2])
            val sz = sp[3]
            sprite(spore, sp[0] + 20f * wave(t, sp[1] * 2f, sp[2]) - sz / 2f, 690f - 420f * p - sz / 2f, sz, sz, a * 0.8f * sin(PI.toFloat() * p))
        }
        for (f in flies) {
            val u = 2f * PI.toFloat() * (t * f[2] + f[3])
            val x = f[0] + 36f * sin(u)
            val y = f[1] - 24f * sin(2f * u) + 10f * cos(u)
            sprite(fly, x - 7f, y - 7f, 14f, 14f, a * pulse(t, f[2] * 2f, f[3], 0.15f))
        }
    }
}

// ── Hafenlichter: a harbour town at night, its lights mirrored in the water, a ship passing, a stadium behind ──

internal object HafenArt : SceneArt() {
    override val hasFront = true

    /** The waterline. */
    private const val H = 430f

    /** Lights the water mirrors: x, width, cool (1) or warm (0), phase. */
    private val mirrored = listOf(
        floatArrayOf(118f, 6f, 0f, 0f), floatArrayOf(205f, 4f, 1f, 0.3f), floatArrayOf(262f, 8f, 0f, 0.6f), floatArrayOf(330f, 5f, 0f, 0.1f),
        floatArrayOf(380f, 4f, 1f, 0.8f), floatArrayOf(450f, 7f, 0f, 0.45f), floatArrayOf(585f, 10f, 1f, 0.2f), floatArrayOf(640f, 12f, 0f, 0.7f),
        floatArrayOf(700f, 10f, 1f, 0.5f), floatArrayOf(760f, 12f, 0f, 0.9f), floatArrayOf(860f, 5f, 0f, 0.35f), floatArrayOf(940f, 4f, 1f, 0.65f),
        floatArrayOf(1010f, 6f, 0f, 0.15f), floatArrayOf(1090f, 5f, 1f, 0.55f), floatArrayOf(1180f, 6f, 0f, 0.25f), floatArrayOf(1250f, 4f, 0f, 0.75f),
    )

    /** x, top, boom direction. */
    private val cranes = listOf(floatArrayOf(930f, 250f, 1f), floatArrayOf(1050f, 230f, -1f), floatArrayOf(1170f, 262f, 1f))
    private val boxes = longArrayOf(0xFF5A2E32, 0xFF2E4A5E, 0xFF6A5232, 0xFF3A3A5A)
    private val skyStars = SKY_STARS.filter { it[1] < 200f }
    private val lamps = floatArrayOf(110f, 380f)

    private val roof: Path.() -> Unit = {
        moveTo(556f, 300f); quadraticTo(580f, 270f, 600f, 296f); quadraticTo(630f, 252f, 662f, 290f); quadraticTo(700f, 236f, 736f, 284f)
        quadraticTo(762f, 262f, 784f, 292f); quadraticTo(800f, 280f, 814f, 300f)
    }

    override fun DrawScope.back() {
        drawRect(
            vgrad(0f to Color(0xFF04081A), 0.36f to Color(0xFF0A1530), 0.52f to Color(0xFF1A2446), 0.6f to Color(0xFF3A3256), 0.6f to Color(0xFF0E1A34), 1f to Color(0xFF040814), y0 = 0f, y1 = DH),
            size = Size(DW, DH),
        )
        // the town's glow on the low clouds
        scale(1f, 120f / 820f, Offset(640f, 420f)) {
            drawCircle(Brush.radialGradient(0f to tint(0xFFFF9E6B, 0.16f), 1f to tint(0xFFFF9E6B, 0f), center = Offset(640f, 420f), radius = 820f), 820f, Offset(640f, 420f))
        }
        val ink = Color(0xFF0A1226)
        val ink2 = Color(0xFF121C38)
        // far houses, a few windows lit
        val r = Rng(21)
        var x = 0f
        while (x < DW) {
            val h = 14f + r.next() * 34f
            val w = 16f + r.next() * 22f
            box(ink2, x, H - h, w, h)
            if (r.next() < 0.7f) box(tint(0xFFFFC98A, 0.5f + r.next() * 0.4f), x + 4f + r.next() * (w - 8f), H - h + 5f + r.next() * (h - 10f), 2.5f, 3f)
            x += 18f + floor(r.next() * 16f)
        }
        // the TV tower
        drawPath(path { moveTo(196f, H); lineTo(200f, 150f); lineTo(210f, 150f); lineTo(214f, H); close() }, ink)
        oval(ink, 186f, 196f, 38f, 22f)
        box(ink, 192f, 214f, 26f, 8f)
        line(ink, 205f, 150f, 205f, 112f, 2f)
        for (k in 0 until 4) box(tint(0xFFBFD8FF, 0.6f), 192f + k * 8f, 206f, 4f, 2f)
        // the church tower with its copper spire and a lit clock
        box(ink, 344f, 270f, 34f, 160f)
        box(ink, 340f, 262f, 42f, 12f)
        drawPath(path { moveTo(346f, 262f); lineTo(352f, 226f); lineTo(370f, 226f); lineTo(376f, 262f); close() }, ink)
        drawPath(path { moveTo(352f, 226f); lineTo(361f, 150f); lineTo(370f, 226f); close() }, Color(0xFF16324A))
        dot(tint(0xFFFFD68C, 0.85f), 6f, 361f, 290f)
        // the concert hall: brick base, glass top under a wave roof
        box(Color(0xFF1C1626), 560f, 352f, 250f, 78f)
        val brick = tint(0xFF3A2A30, 0.55f)
        for (row in 0 until 4) for (c in 0 until 22) box(brick, 566f + c * 11f, 360f + row * 17f, 6f, 8f)
        drawPath(path { moveTo(556f, 352f); lineTo(556f, 300f); roof(this); lineTo(814f, 352f); close() }, vgrad(0f to Color(0xFF2A3E62), 1f to Color(0xFF17243E), y0 = 240f, y1 = 352f))
        val r2 = Rng(4)
        val cool = tint(0xFFBFD8FF, 0.55f)
        val warm = tint(0xFFFFD9A0, 0.6f)
        for (row in 0 until 5) for (c in 0 until 24) {
            if (r2.next() < 0.45f) box(if (r2.next() < 0.5f) cool else warm, 562f + c * 10.4f, 306f + row * 9f, 6f, 4f)
        }
        outline(path { roof(this) }, tint(0xFFCFE4FF, 0.55f), 2f)
        // container cranes and the boxes on the quay
        for (cr in cranes) {
            val cx = cr[0]
            val top = cr[1]
            val dir = cr[2]
            line(ink, cx - 22f, H, cx - 14f, top + 40f, 4f)
            line(ink, cx + 22f, H, cx + 14f, top + 40f, 4f)
            line(ink, cx - 18f, top + 110f, cx + 18f, top + 110f, 3f)
            line(ink, cx - 60f * dir, top + 40f, cx + 110f * dir, top + 40f, 5f)
            line(ink, cx, top, cx - 60f * dir, top + 40f, 2f)
            line(ink, cx, top, cx + 110f * dir, top + 40f, 2f)
            line(ink, cx, top, cx, top + 40f, 3f)
            box(ink, cx - 10f, top + 42f, 20f, 14f)
            box(tint(0xFFFFD68C, 0.7f), cx - 6f, top + 46f, 5f, 4f)
        }
        val r3 = Rng(8)
        for (i in 0 until 18) box(tint(boxes[i % 4], 0.85f), 880f + (i % 9) * 40f + r3.next() * 4f, H - 12f - (i / 9) * 12f, 36f, 11f)
        box(ink, 0f, H - 2f, DW, 4f)
        drawRect(vgrad(0f to Color(0xFF101C38), 0.4f to Color(0xFF0A1228), 1f to Color(0xFF040814), y0 = H, y1 = DH), Offset(0f, H + 2f), Size(DW, DH - H))
        // the stadium behind the town, on the left
        drawPath(path { moveTo(30f, H - 26f); lineTo(40f, H - 44f); quadraticTo(90f, H - 54f, 140f, H - 44f); lineTo(150f, H - 26f); close() }, ink2)
        for (mx in floatArrayOf(44f, 76f, 106f, 138f)) {
            line(ink2, mx, H - 44f, mx, H - 74f, 2f)
            dot(Color(0xFFFFF2D0), 2.2f, mx, H - 75f)
        }
    }

    override fun DrawScope.front() {
        // the pier in front, with bollards and two lamps
        drawPath(path { moveTo(0f, DH); lineTo(0f, 650f); lineTo(430f, 676f); lineTo(480f, DH); close() }, Color(0xFF08101E))
        line(Color(0xFF1A2844), 0f, 650f, 430f, 676f, 2f)
        for (x in floatArrayOf(60f, 180f, 300f)) {
            box(Color(0xFF0A1222), x - 8f, 650f + x * 0.06f - 14f, 16f, 16f)
            oval(Color(0xFF0E1830), x - 10f, 650f + x * 0.06f - 18f, 20f, 7f)
        }
        val pole = Color(0xFF18243E)
        for (x in lamps) {
            val y = 650f + x * 0.06f
            line(pole, x, y, x, y - 110f, 3f)
            box(pole, x - 7f, y - 122f, 14f, 14f)
        }
        vignette(0xFF02040C)
    }

    override fun sprites(make: (Int, Int, DrawScope.() -> Unit) -> ImageBitmap): List<ImageBitmap> = listOf(
        glow(make, 0f to tint(0xFFFFC98A, 0.8f), 0.5f to tint(0xFFFFC98A, 0.35f), 1f to tint(0xFFFFC98A, 0f)),
        glow(make, 0f to tint(0xFFBFD8FF, 0.7f), 0.5f to tint(0xFFBFD8FF, 0.3f), 1f to tint(0xFFBFD8FF, 0f)),
        glow(make, 0f to tint(0xFFFF4A4A, 0.9f), 0.3f to tint(0xFFFF4A4A, 0.3f), 1f to tint(0xFFFF4A4A, 0f)),
        glow(make, 0f to tint(0xFFFFE2B0, 0.85f), 0.3f to tint(0xFFFFC98A, 0.3f), 1f to tint(0xFFFFC98A, 0f)),
        glow(make, 0f to tint(0xFFAFC8E8, 0.1f), 1f to tint(0xFFAFC8E8, 0f)),
        glow(make, 0f to tint(0xFFFFE7A3, 0.45f), 0.5f to tint(0xFFFFD68C, 0.1f), 1f to tint(0xFFFFD68C, 0f)),
    )

    override fun DrawScope.middle(t: Float, a: Float, s: List<ImageBitmap>) {
        val (warm, cool, red, _, mist) = s
        val dome = s[5]
        stars(t, a, skyStars, Color(0xFFE8F0FF))
        sprite(dome, 90f - 120f, H - 130f, 240f, 110f, a * pulse(t, 20f, 0f, 0.55f))
        // reflections: every light a broken column of ripples that shivers on the water
        for (m in mirrored) {
            val x = m[0]
            val ph = m[3]
            val img = if (m[2] > 0f) cool else warm
            for (k in 0 until 8) {
                val y = H + 6f + k * 17f
                val dx = 5f * wave(t, 23f + k * 6f, ph + k * 0.3f)
                val ww = m[1] * (1.2f + k * 0.12f) * (0.75f + 0.25f * wave(t, 29f + k * 4f, ph + k * 0.17f))
                sprite(img, x - ww + dx, y, ww * 2f, 6f, a * pulse(t, 17f + k * 4f, ph + k * 0.21f, 0.4f) * (1f - k * 0.1f))
            }
        }
        // the ship, once per loop, with its reflection
        val p = frac(t + 0.6f)
        val fade = a * min(1f, min(p, 1f - p) * 8f)
        val sx = -260f + 1800f * p
        val sy = 470f
        val hull = Color(0xFF070D1C)
        drawPath(path { moveTo(sx, sy); lineTo(sx + 240f, sy); lineTo(sx + 226f, sy + 18f); lineTo(sx + 12f, sy + 18f); close() }, hull, alpha = fade)
        box(hull, sx + 182f, sy - 34f, 34f, 34f, fade)
        for (i in 0 until 6) box(tint(boxes[i % 3], 0.9f), sx + 20f + i * 26f, sy - 16f, 24f, 15f, fade)
        for (i in 0 until 4) box(tint(0xFFFFE2B0, 0.9f), sx + 186f + i * 7f, sy - 28f, 4f, 3f, fade)
        dot(Color(0xFFFF5A5A), 2.2f, sx + 4f, sy - 2f, fade)
        dot(Color.White, 2.2f, sx + 200f, sy - 40f, fade)
        for (k in 0 until 4) sprite(warm, sx + 190f - k * 2f + 3f * wave(t, 31f, k * 0.2f), sy + 22f + k * 12f, 30f + k * 4f, 6f, fade * (0.9f - k * 0.2f))
        // aviation lights on the crane tops and the TV tower
        cranes.forEachIndexed { i, cr -> sprite(red, cr[0] - 10f, cr[1] - 10f, 20f, 20f, a * if (wave(t, 50f, i * 0.33f) > 0.3f) 1f else 0.15f) }
        sprite(red, 195f, 102f, 20f, 20f, a * if (wave(t, 40f, 0.5f) > 0.2f) 1f else 0.15f)
        sprite(mist, -100f + 120f * wave(t, 2f, 0f), 430f, 900f, 60f, a)
        sprite(mist, 500f + 120f * wave(t, 3f, 0.3f), 450f, 900f, 60f, a)
    }

    override fun DrawScope.top(t: Float, a: Float, s: List<ImageBitmap>) {
        val lamp = s[3]
        lamps.forEachIndexed { i, x ->
            val y = 650f + x * 0.06f - 115f
            sprite(lamp, x - 40f, y - 40f, 80f, 80f, a * pulse(t, 30f, i * 0.4f, 0.8f))
        }
    }
}
