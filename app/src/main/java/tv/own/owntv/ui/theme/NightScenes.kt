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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.own.owntv.R

/**
 * Mayo TV's colour themes (Glass & background › Farbthema): Cyan Blau, plain greys, coloured pages and modern
 * themes with an accent of their own. They tint page, panels and greys. Nothing moves: the animated night scenes and the floodlit stadium are gone (Jonathan,
 * first test on the TV: "nur störend").
 */
enum class NightScene {
    NONE,
    // Plain: calm, nearly grey, the accent stays the one chosen in Settings.
    GRAPHIT, BASALT, SCHIEFER, TAUPE, SALBEI, PFLAUME,
    // Coloured pages.
    KOENIGSBLAU, RASENGRUEN, BORDEAUX, ESPRESSO,
    // Modern: a dark page with an accent of its own (see [SceneTint.accent]).
    NEON, ABENDROT, FLUTLICHT, GLETSCHER,
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
        NightScene.BASALT -> R.string.tvplayer_color_basalt
        NightScene.TAUPE -> R.string.tvplayer_color_taupe
        NightScene.SALBEI -> R.string.tvplayer_color_salbei
        NightScene.PFLAUME -> R.string.tvplayer_color_pflaume
        NightScene.NEON -> R.string.tvplayer_color_neon
        NightScene.ABENDROT -> R.string.tvplayer_color_abendrot
        NightScene.FLUTLICHT -> R.string.tvplayer_color_flutlicht
        NightScene.GLETSCHER -> R.string.tvplayer_color_gletscher
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
        NightScene.BASALT -> R.string.tvplayer_color_basalt_desc
        NightScene.TAUPE -> R.string.tvplayer_color_taupe_desc
        NightScene.SALBEI -> R.string.tvplayer_color_salbei_desc
        NightScene.PFLAUME -> R.string.tvplayer_color_pflaume_desc
        NightScene.NEON -> R.string.tvplayer_color_neon_desc
        NightScene.ABENDROT -> R.string.tvplayer_color_abendrot_desc
        NightScene.FLUTLICHT -> R.string.tvplayer_color_flutlicht_desc
        NightScene.GLETSCHER -> R.string.tvplayer_color_gletscher_desc
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

/** [accent] (0 = none) replaces the accent chosen in Settings while the theme is on: buttons, focus ring, progress. */
private class SceneTint(val page: Long, val glass: Long, val muted: Long, val dim: Long, val tag: Long, val body: Long, val accent: Long = 0)

/**
 * Page, glass, muted, dim, tag and body of each tinted theme, or null when it keeps Cyan Blau. The greys are Cyan
 * Blau's moved into the theme's hue at the same lightness, so text keeps at least 4.5 : 1 on every panel. Glass
 * (the categories column, the menus) is at least 1.3 : 1 against the page, like Cyan Blau's own step, so the
 * categories never run into the guide.
 */
private fun tintOf(scene: NightScene): SceneTint? = when (scene) {
    NightScene.GRAPHIT -> SceneTint(0xFF1C2024, 0xFF2E3338, 0xFFC2C4C7, 0xFF9BA0A4, 0xFFD5D8DA, 0xFFDEE0E1)
    NightScene.BASALT -> SceneTint(0xFF141517, 0xFF2B2C2F, 0xFFC3C4C6, 0xFF9D9FA2, 0xFFD6D7D9, 0xFFDFDFE0)
    NightScene.SCHIEFER -> SceneTint(0xFF2A3540, 0xFF3A4855, 0xFFC0C4C9, 0xFF99A0A6, 0xFFD4D8DB, 0xFFDDE0E2)
    NightScene.TAUPE -> SceneTint(0xFF2A2724, 0xFF3F3C35, 0xFFC6C4C3, 0xFFA2A09D, 0xFFD9D8D6, 0xFFE0E0DF)
    NightScene.SALBEI -> SceneTint(0xFF25302A, 0xFF34443C, 0xFFC2C7C4, 0xFF9BA49F, 0xFFD5DAD7, 0xFFDEE1DF)
    NightScene.PFLAUME -> SceneTint(0xFF2A1F2D, 0xFF423146, 0xFFC7C0C9, 0xFFA399A6, 0xFFD9D5DA, 0xFFE1DDE2)
    NightScene.KOENIGSBLAU -> SceneTint(0xFF1A2E6C, 0xFF27408C, 0xFFB7BDD2, 0xFF8B95B4, 0xFFCED2E1, 0xFFD8DCE7)
    NightScene.RASENGRUEN -> SceneTint(0xFF0F3C1E, 0xFF17502A, 0xFFB7D2C0, 0xFF8CB399, 0xFFCEE1D4, 0xFFD8E7DD)
    NightScene.BORDEAUX -> SceneTint(0xFF3A1622, 0xFF5D2337, 0xFFCFBAC1, 0xFFAE919B, 0xFFDFD0D5, 0xFFE5DADE)
    NightScene.ESPRESSO -> SceneTint(0xFF33241C, 0xFF4A352B, 0xFFCBC3BE, 0xFFA99D96, 0xFFDCD6D3, 0xFFE3DEDC)
    NightScene.NEON -> SceneTint(0xFF120D22, 0xFF2A244F, 0xFFBFBACF, 0xFF9891AE, 0xFFD4D0DF, 0xFFDDDAE5, accent = 0xFFFF6EB4)
    NightScene.ABENDROT -> SceneTint(0xFF25131B, 0xFF442831, 0xFFCCBDC4, 0xFFAA959E, 0xFFDDD2D7, 0xFFE3DCDF, accent = 0xFFFF8A5B)
    NightScene.FLUTLICHT -> SceneTint(0xFF0B1912, 0xFF1B3223, 0xFFBCCDC4, 0xFF93ACA0, 0xFFD1DED8, 0xFFDBE4E0, accent = 0xFFF2D04B)
    NightScene.GLETSCHER -> SceneTint(0xFF0F1B28, 0xFF1C314F, 0xFFBAC4CF, 0xFF919FAE, 0xFFD0D7DF, 0xFFDADFE5, accent = 0xFF7FD3FF)
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
    val t = tintOf(scene) ?: return this
    val page = Color(t.page)
    fun move(c: Color) = moveHue(c, page)
    val tinted = copy(
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
    if (t.accent == 0L) return tinted
    // A modern theme's own accent. Text on it is near-black: every accent here is light enough for 7 : 1.
    val accent = Color(t.accent)
    val onAccent = Color(0xFF101418)
    val focus = if (focusBorder == primary) accent else focusBorder
    return tinted.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = lerp(Color(t.glass), accent, 0.35f),
        onPrimaryContainer = lerp(accent, Color.White, 0.6f),
        focusBorder = focus,
        focusGlow = focus.copy(alpha = 0.40f),
        accentOnVideo = accent,
        onAccentOnVideo = onAccent,
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
