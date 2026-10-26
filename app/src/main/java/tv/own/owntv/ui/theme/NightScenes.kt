package tv.own.owntv.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.own.owntv.R

/**
 * Mayo TV's colour themes (Glass & background › Farbthema): Cyan Blau and six calm colours that tint page,
 * panels and greys. Nothing moves: the animated night scenes and the floodlit stadium are gone (Jonathan,
 * first test on the TV: "nur störend").
 */
enum class NightScene {
    NONE,
    GRAPHIT, SCHIEFER, KOENIGSBLAU, RASENGRUEN, BORDEAUX, ESPRESSO,
}

/** The name in the settings' stepper. */
val NightScene.labelRes: Int
    @StringRes get() = when (this) {
        NightScene.NONE -> R.string.tvplayer_scene_off
        NightScene.GRAPHIT -> R.string.tvplayer_color_graphit
        NightScene.SCHIEFER -> R.string.tvplayer_color_schiefer
        NightScene.KOENIGSBLAU -> R.string.tvplayer_color_koenigsblau
        NightScene.RASENGRUEN -> R.string.tvplayer_color_rasengruen
        NightScene.BORDEAUX -> R.string.tvplayer_color_bordeaux
        NightScene.ESPRESSO -> R.string.tvplayer_color_espresso
    }

/** What the help panel says about it. */
val NightScene.descRes: Int
    @StringRes get() = when (this) {
        NightScene.NONE -> R.string.tvplayer_scene_off_desc
        NightScene.GRAPHIT -> R.string.tvplayer_color_graphit_desc
        NightScene.SCHIEFER -> R.string.tvplayer_color_schiefer_desc
        NightScene.KOENIGSBLAU -> R.string.tvplayer_color_koenigsblau_desc
        NightScene.RASENGRUEN -> R.string.tvplayer_color_rasengruen_desc
        NightScene.BORDEAUX -> R.string.tvplayer_color_bordeaux_desc
        NightScene.ESPRESSO -> R.string.tvplayer_color_espresso_desc
    }

/** The chosen colour theme, kept in the app's own preferences (the core is not changed). A scene an older build stored falls back to Cyan Blau. */
object NightSceneSettings {
    private const val PREFS = "flutlicht_scenes"
    private const val KEY_SCENE = "scene"

    private var prefs: SharedPreferences? = null
    private val _scene = MutableStateFlow(NightScene.NONE)
    val scene: StateFlow<NightScene> = _scene.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _scene.value = NightScene.entries.firstOrNull { it.name == p.getString(KEY_SCENE, null) } ?: NightScene.NONE
    }

    fun setScene(scene: NightScene) {
        _scene.value = scene
        prefs?.edit()?.putString(KEY_SCENE, scene.name)?.apply()
    }
}

/** Loads [NightSceneSettings] once; call it before reading the settings in a composable. */
@Composable
fun rememberNightSceneSettings(): NightSceneSettings {
    val context = LocalContext.current
    return remember { NightSceneSettings.apply { init(context) } }
}

/** A small picture of [scene] (the settings' help panel): a sketch of the shell in its colours, the menu strip, a panel and the focus bar. */
@Composable
fun NightScenePreview(scene: NightScene, modifier: Modifier = Modifier) {
    val palette = LocalStagePalette.current
    Box(
        modifier
            .background(palette.page)
            .drawBehind {
                val u = size.height / 9f
                drawRect(palette.glassTint, Offset.Zero, Size(u * 1.1f, size.height))
                for (i in 0..3) drawCircle(palette.muted.copy(alpha = 0.7f), u * 0.18f, Offset(u * 0.55f, u * (1.1f + i * 0.9f)))
                drawRoundRect(palette.text, Offset(u * 1.8f, u * 0.8f), Size(u * 4f, u * 0.45f), CornerRadius(u * 0.1f))
                drawRoundRect(palette.muted, Offset(u * 1.8f, u * 1.5f), Size(u * 2.6f, u * 0.3f), CornerRadius(u * 0.1f))
                drawRoundRect(palette.glassTint, Offset(u * 1.8f, u * 2.4f), Size(size.width - u * 2.6f, u * 5.6f), CornerRadius(u * 0.4f))
                for (i in 0..3) {
                    val y = u * (2.85f + i * 1.25f)
                    if (i == 1) drawRoundRect(palette.text, Offset(u * 2.1f, y - u * 0.25f), Size(size.width - u * 3.2f, u * 1f), CornerRadius(u * 0.2f))
                    drawRoundRect(if (i == 1) palette.fxText else palette.body.copy(alpha = 0.8f), Offset(u * 2.5f, y + u * 0.1f), Size(u * (3.4f - (i % 2) * 0.8f), u * 0.3f), CornerRadius(u * 0.1f))
                    drawRoundRect(if (i == 1) palette.fxLine else palette.dim, Offset(size.width - u * 3.6f, y + u * 0.1f), Size(u * 2.2f, u * 0.3f), CornerRadius(u * 0.1f))
                }
            },
    )
}

/**
 * The panels of a colour theme: page, glass and the cool greys tinted to it. Text, focus bar and switches stay as
 * they are, and so does their contrast.
 */
fun StagePalette.forScene(scene: NightScene): StagePalette {
    if (isLight) return this
    val t = tintOf(scene) ?: return this
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

/**
 * Page, glass, muted, dim, tag and body of each tinted theme, or null when it keeps Cyan Blau. The colour themes'
 * greys are Cyan Blau's moved into their hue at the same lightness, so text keeps at least 4.5 : 1 on every panel.
 */
private fun tintOf(scene: NightScene): SceneTint? = when (scene) {
    NightScene.GRAPHIT -> SceneTint(0xFF1C2024, 0xFF25292E, 0xFFC2C4C7, 0xFF9BA0A4, 0xFFD5D8DA, 0xFFDEE0E1)
    NightScene.SCHIEFER -> SceneTint(0xFF2A3540, 0xFF384552, 0xFFC0C4C9, 0xFF99A0A6, 0xFFD4D8DB, 0xFFDDE0E2)
    NightScene.KOENIGSBLAU -> SceneTint(0xFF1A2E6C, 0xFF263E88, 0xFFB7BDD2, 0xFF8B95B4, 0xFFCED2E1, 0xFFD8DCE7)
    NightScene.RASENGRUEN -> SceneTint(0xFF0F3C1E, 0xFF164C28, 0xFFB7D2C0, 0xFF8CB399, 0xFFCEE1D4, 0xFFD8E7DD)
    NightScene.BORDEAUX -> SceneTint(0xFF3A1622, 0xFF491E2D, 0xFFCFBAC1, 0xFFAE919B, 0xFFDFD0D5, 0xFFE5DADE)
    NightScene.ESPRESSO -> SceneTint(0xFF33241C, 0xFF412F25, 0xFFCBC3BE, 0xFFA99D96, 0xFFDCD6D3, 0xFFE3DEDC)
    NightScene.NONE -> null
}

/** The theme's page colour, or null when it keeps Cyan Blau. */
private fun scenePage(scene: NightScene): Color? = tintOf(scene)?.let { Color(it.page) }

/**
 * The dark surface tiers (rail, cards, outlines) moved into the theme's hue: each keeps its brightness and
 * saturation relative to the petrol page, so the steps between them stay as designed. Text and accent stay.
 */
fun OwnTVColors.forScene(scene: NightScene): OwnTVColors {
    if (!isDark) return this
    val page = scenePage(scene) ?: return this
    fun move(c: Color) = moveHue(c, page)
    return copy(
        background = move(background),
        surface = move(surface),
        surfaceContainerLowest = move(surfaceContainerLowest),
        surfaceContainerLow = move(surfaceContainerLow),
        surfaceContainer = move(surfaceContainer),
        surfaceContainerHigh = move(surfaceContainerHigh),
        surfaceContainerHighest = move(surfaceContainerHighest),
        outline = move(outline),
        outlineVariant = move(outlineVariant),
    )
}

/** A fixed dark petrol tone (the rail's panels) moved into the chosen theme's hue, like [OwnTVColors.forScene]. */
@Composable
fun inScene(petrol: Color): Color {
    val scene by rememberNightSceneSettings().scene.collectAsState()
    if (LocalStagePalette.current.isLight) return petrol
    val page = scenePage(scene) ?: return petrol
    return moveHue(petrol, page)
}

private fun moveHue(c: Color, page: Color): Color {
    val ref = hsl(DarkBackground)
    val to = hsl(page)
    val h = hsl(c)
    return fromHsl(to[0], (h[1] * to[1] / ref[1]).coerceIn(0f, 1f), (h[2] * to[2] / ref[2]).coerceIn(0f, 1f), c.alpha)
}

/** Hue (0..1), saturation, lightness. */
private fun hsl(c: Color): FloatArray {
    val mx = maxOf(c.red, c.green, c.blue)
    val mn = minOf(c.red, c.green, c.blue)
    val l = (mx + mn) / 2f
    val d = mx - mn
    if (d == 0f) return floatArrayOf(0f, 0f, l)
    val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
    val h = when (mx) {
        c.red -> ((c.green - c.blue) / d + if (c.green < c.blue) 6f else 0f)
        c.green -> (c.blue - c.red) / d + 2f
        else -> (c.red - c.green) / d + 4f
    } / 6f
    return floatArrayOf(h, s, l)
}

private fun fromHsl(h: Float, s: Float, l: Float, alpha: Float): Color {
    if (s == 0f) return Color(l, l, l, alpha)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    fun hue(t0: Float): Float {
        val t = if (t0 < 0f) t0 + 1f else if (t0 > 1f) t0 - 1f else t0
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 0.5f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    return Color(hue(h + 1f / 3f).coerceIn(0f, 1f), hue(h).coerceIn(0f, 1f), hue(h - 1f / 3f).coerceIn(0f, 1f), alpha)
}
