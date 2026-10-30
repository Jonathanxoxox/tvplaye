package tv.own.owntv.ui.theme

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.own.owntv.R

/**
 * Manrope, the player card's typeface, as a choice for the whole interface (Jonathan, 30 Oct 2026).
 *
 * The list of fonts the settings offer comes from core ([tv.own.owntv.core.theme.AppFontFamily]), which
 * this app does not change; Manrope is Mayo TV's own addition to it. While it is chosen it stands in for
 * the core family the settings keep, and choosing any other font in the list sets it aside again.
 */
object MayoFont {
    /** The key the settings' font list uses for Manrope, next to the core families' names. */
    const val KEY = "MAYO_MANROPE"

    private const val PREFS = "tvplayer"
    private const val CHOSEN = "font_manrope"
    private val state = MutableStateFlow(false)
    @Volatile private var loaded = false

    /** Whether Manrope is the interface's font; read from the app's preferences on first use. */
    fun flow(context: Context): StateFlow<Boolean> {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    state.value = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(CHOSEN, false)
                    loaded = true
                }
            }
        }
        return state.asStateFlow()
    }

    fun set(context: Context, chosen: Boolean) {
        flow(context)
        state.value = chosen
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(CHOSEN, chosen).apply()
    }
}

// Every weight the interface uses (400–800) from one variable file, as Plus Jakarta Sans.
internal val ManropeFamily = FontFamily(
    variableFont(R.font.manrope_variable, FontWeight.Normal),
    variableFont(R.font.manrope_variable, FontWeight.Medium),
    variableFont(R.font.manrope_variable, FontWeight.SemiBold),
    variableFont(R.font.manrope_variable, FontWeight.Bold),
    variableFont(R.font.manrope_variable, FontWeight.ExtraBold),
)
