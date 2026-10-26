package tv.own.owntv.player

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The remote's keys the fullscreen live player gives a meaning of its own (Settings › Remote control). */
enum class PlayerKey { OK, MENU, LEFT, RIGHT, UP, DOWN, INFO, GUIDE, RED, GREEN, YELLOW, BLUE }

/** What a key does in the fullscreen live player, short or held. */
enum class PlayerKeyAction {
    NONE, INFO_PANEL, OPTIONS, GUIDE, CHANNEL_LIST, LAST_CHANNEL, CHANNEL_UP, CHANNEL_DOWN, HISTORY, SEARCH,
}

/** One key's short and long meaning. */
data class PlayerKeyBinding(val short: PlayerKeyAction, val long: PlayerKeyAction)

/**
 * The fullscreen live player's key map. The defaults are the classic IPTV player's: OK the info bar
 * (held: options), Menu the options (held: the guide), Left the channel list, Right the channel
 * before (held: the info bar), Up/Down change channel (held: history / search), Info and Yellow
 * the info bar, Guide, Red and Green the guide, Blue the options.
 */
object PlayerKeyMap {
    val defaults: Map<PlayerKey, PlayerKeyBinding> = mapOf(
        PlayerKey.OK to PlayerKeyBinding(PlayerKeyAction.INFO_PANEL, PlayerKeyAction.OPTIONS),
        PlayerKey.MENU to PlayerKeyBinding(PlayerKeyAction.OPTIONS, PlayerKeyAction.GUIDE),
        PlayerKey.LEFT to PlayerKeyBinding(PlayerKeyAction.CHANNEL_LIST, PlayerKeyAction.NONE),
        PlayerKey.RIGHT to PlayerKeyBinding(PlayerKeyAction.LAST_CHANNEL, PlayerKeyAction.INFO_PANEL),
        PlayerKey.UP to PlayerKeyBinding(PlayerKeyAction.CHANNEL_UP, PlayerKeyAction.HISTORY),
        PlayerKey.DOWN to PlayerKeyBinding(PlayerKeyAction.CHANNEL_DOWN, PlayerKeyAction.SEARCH),
        PlayerKey.INFO to PlayerKeyBinding(PlayerKeyAction.INFO_PANEL, PlayerKeyAction.NONE),
        PlayerKey.GUIDE to PlayerKeyBinding(PlayerKeyAction.GUIDE, PlayerKeyAction.NONE),
        PlayerKey.RED to PlayerKeyBinding(PlayerKeyAction.GUIDE, PlayerKeyAction.NONE),
        PlayerKey.GREEN to PlayerKeyBinding(PlayerKeyAction.GUIDE, PlayerKeyAction.NONE),
        PlayerKey.YELLOW to PlayerKeyBinding(PlayerKeyAction.INFO_PANEL, PlayerKeyAction.NONE),
        PlayerKey.BLUE to PlayerKeyBinding(PlayerKeyAction.OPTIONS, PlayerKeyAction.NONE),
    )

    private const val PREFS = "tvplayer"
    private const val PREFIX = "player_key_"
    private val state = MutableStateFlow(defaults)
    @Volatile private var loaded = false

    /** The current map; read from the app's preferences on first use. */
    fun flow(context: Context): StateFlow<Map<PlayerKey, PlayerKeyBinding>> {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    state.value = PlayerKey.entries.associateWith { k ->
                        val d = defaults.getValue(k)
                        PlayerKeyBinding(
                            short = prefs.getString(PREFIX + k.name + "_short", null)?.let(::actionOf) ?: d.short,
                            long = prefs.getString(PREFIX + k.name + "_long", null)?.let(::actionOf) ?: d.long,
                        )
                    }
                    loaded = true
                }
            }
        }
        return state.asStateFlow()
    }

    fun set(context: Context, key: PlayerKey, long: Boolean, action: PlayerKeyAction) {
        val current = flow(context).value.getValue(key)
        state.value = state.value + (key to if (long) current.copy(long = action) else current.copy(short = action))
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREFIX + key.name + if (long) "_long" else "_short", action.name).apply()
    }

    fun reset(context: Context) {
        flow(context)
        state.value = defaults
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply { prefs.all.keys.filter { it.startsWith(PREFIX) }.forEach(::remove) }.apply()
    }

    private fun actionOf(name: String) = runCatching { PlayerKeyAction.valueOf(name) }.getOrNull()
}

/**
 * Short against held for the player's mapped keys: a key with nothing on its long press acts at once
 * on the press; otherwise the release is the short press and the first repeat the long one.
 * [run] performs an action and says whether it could (a target the player lacks falls through).
 */
class PlayerKeyPress {
    private var pending: PlayerKey? = null
    private var swallowUp: PlayerKey? = null

    fun handle(
        e: KeyEvent,
        key: PlayerKey,
        binding: PlayerKeyBinding,
        run: (PlayerKeyAction, PlayerKey, Boolean) -> Boolean,
    ): Boolean {
        when (e.type) {
            KeyEventType.KeyDown -> {
                if (e.nativeKeyEvent.repeatCount == 0) {
                    if (binding.long == PlayerKeyAction.NONE) {
                        pending = null
                        if (!run(binding.short, key, false)) return false
                        swallowUp = key
                    } else {
                        pending = key
                    }
                } else if (pending == key) {
                    pending = null
                    swallowUp = key
                    if (!run(binding.long, key, true)) run(binding.short, key, false)
                }
                return pending == key || swallowUp == key
            }
            KeyEventType.KeyUp -> {
                if (pending == key) {
                    pending = null
                    run(binding.short, key, false)
                    return true
                }
                if (swallowUp == key) { swallowUp = null; return true }
                return false
            }
            else -> return false
        }
    }

}

/** The mapped key a remote key event stands for; Left/Right by the screen, not the reading direction. */
fun playerKeyOf(e: KeyEvent): PlayerKey? = when (e.key) {
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> PlayerKey.OK
    Key.Menu -> PlayerKey.MENU
    Key.DirectionLeft -> PlayerKey.LEFT
    Key.DirectionRight -> PlayerKey.RIGHT
    Key.DirectionUp -> PlayerKey.UP
    Key.DirectionDown -> PlayerKey.DOWN
    Key.Info -> PlayerKey.INFO
    Key.Guide -> PlayerKey.GUIDE
    Key.ProgramRed -> PlayerKey.RED
    Key.ProgramGreen -> PlayerKey.GREEN
    Key.ProgramYellow -> PlayerKey.YELLOW
    Key.ProgramBlue -> PlayerKey.BLUE
    else -> null
}
