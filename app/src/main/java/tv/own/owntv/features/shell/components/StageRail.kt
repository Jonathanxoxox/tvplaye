package tv.own.owntv.features.shell.components

import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.settings.SettingsRepository.NavLength
import tv.own.owntv.core.settings.SettingsRepository.NavSize
import tv.own.owntv.ui.components.BrandMark
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.Wordmark
import tv.own.owntv.ui.components.rememberAppliedIcon
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.drawBoxShadow
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.ownTvTween
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import java.text.NumberFormat

/** What the Stage rail shows right now: the Navigation setting plus where focus is (see OwnTVShell). */
enum class RailState {
    /** A 7 px accent glow at the screen edge; the items stay focusable so ◀ can still reach them. */
    HIDDEN,
    /** The 84 px icon capsule: Floating at rest, or Size = Compact. */
    CAPSULE,
    /** The chosen Size over a scrim: the floating rail while it holds focus. */
    OPEN,
    /** The chosen Size without the scrim: Navigation = Docked. */
    PINNED,
}

/**
 * What the rail's "Now Playing" item shows: the docked channel's logo, or the equalizer while Audio
 * Mode is running (there is no picture to stand for then).
 */
data class NowPlayingRail(val logoUrl: String?, val audioMode: Boolean, val playing: Boolean)

/** The menu order: Search, then TV and the Guide first as in a classic IPTV player, More last. */
private val StageRailOrder = listOf(
    MainSection.SEARCH, MainSection.LIVE_TV, MainSection.EPG, MainSection.SPORTS,
    MainSection.MOVIES, MainSection.SERIES, MainSection.DOWNLOADS, MainSection.HOME,
)

/** The rail's width with names: Normal 280 · Wide 340 · Extra wide 400; Compact is the 84 capsule. */
private val NavSize.openWidth get() = when (this) {
    NavSize.COMPACT -> 84
    NavSize.NORMAL -> 280
    NavSize.WIDE -> 340
    NavSize.EXTRA_WIDE -> 400
}

/** The space a docked rail takes from the content, from the screen edge: 28 + 84 capsule, 24 + width with names. */
fun railDockedReserve(size: NavSize) =
    if (size == NavSize.COMPACT) (28 + 84 + 16).mpx else (24 + size.openWidth + 16).mpx

/**
 * Where content starts below the top-right cluster (34 + its 68 height + a gap) on screens that have not
 * been redrawn for Stage yet. Each screen's own phase takes this away as it gets the full canvas.
 */
val StageContentTop = 110.mpx

/**
 * The Stage navigation rail: a floating glass capsule, the rail at its
 * [size] over a scrim or docked, or only the accent glow at the edge. [count] is the number beside an
 * item (Wide and up) and [detail] the line under its name (Extra wide), null for none. [length] Full
 * runs the rail the height of the screen. Entering the rail from the content always lands on the
 * selected section, and ▶ out of it returns to exactly where the content was ([contentFocusRequester]).
 */
@Composable
fun StageRail(
    state: RailState,
    size: NavSize,
    length: NavLength,
    selected: MainSection,
    visibleSections: Set<MainSection>,
    onSelect: (MainSection) -> Unit,
    count: (MainSection) -> Int?,
    detail: @Composable (MainSection) -> String?,
    profileName: String,
    profileLine: String,
    onSwitchProfile: () -> Unit,
    onPickAvatar: () -> Unit,
    /** The profile's drawn avatar (-1 = none) and its own picture (blank = none). */
    avatarId: Int = -1,
    avatarPath: String = "",
    selectedItemFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    /** A screen's own way in, when it has one (Home returns to the exact control left from). */
    enterContent: (() -> Boolean)? = null,
    onFocused: () -> Unit,
    nowPlaying: NowPlayingRail?,
    onNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TV Player: the menu as a classic IPTV player draws it. Out of sight until ◀ calls it in, then a
    // flat panel down the full left edge with an icon and a name per row; no capsule, no edge glow.
    // The rows stay focusable while hidden, so ◀ from the content still reaches them.
    val open = state == RailState.OPEN || state == RailState.PINNED
    val counts = size >= NavSize.WIDE
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    var hasFocus by remember { mutableStateOf(false) }
    // Search, Settings and More all land on a rail item: Search has its own, Settings lives behind More.
    val focusSection = when {
        selected == MainSection.SETTINGS || selected == MainSection.MORE -> MainSection.MORE
        selected == MainSection.SEARCH || selected in visibleSections -> selected
        else -> StageRailOrder.firstOrNull { it in visibleSections } ?: MainSection.MORE
    }
    val items = StageRailOrder.filter { it == MainSection.SEARCH || it in visibleSections } + MainSection.MORE
    val menuAccent = MenuHighlight

    val slide = remember { Animatable(-1f) }
    val slideSpec: androidx.compose.animation.core.AnimationSpec<Float> = ownTvTween(220)
    LaunchedEffect(open) { slide.animateTo(if (open) 0f else -1f, slideSpec) }

    Box(modifier.fillMaxSize()) {
        if (state == RailState.OPEN) {
            Box(
                Modifier.fillMaxSize()
                    .alpha((1f + slide.value).coerceIn(0f, 1f))
                    .background(Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.75f),
                        0.45f to Color.Black.copy(alpha = 0.45f),
                        1f to Color.Black.copy(alpha = 0.15f),
                    )),
            )
        }
        val width = MenuWidth
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(width)
                .graphicsLayer {
                    translationX = slide.value * width.toPx() * (if (rtl) -1f else 1f)
                }
                .alpha((1f + slide.value).coerceIn(0f, 1f))
                .onFocusChanged {
                    // Every entry from the content lands on the selected section, not on whatever item
                    // happens to line up; moves inside the rail do not re-trigger this.
                    val entered = it.hasFocus && !hasFocus
                    hasFocus = it.hasFocus
                    if (it.hasFocus) onFocused()
                    if (entered) scope.launch { runCatching { selectedItemFocusRequester.requestFocus() } }
                }
                .focusProperties {
                    onExit = {
                        val out = if (rtl) FocusDirection.Left else FocusDirection.Right
                        if (requestedFocusDirection == out && enterContent != null) {
                            // Focus cannot be moved from inside a focus change: stop this one, and let
                            // the screen place focus itself on the next frame.
                            cancelFocusChange()
                            scope.launch {
                                withFrameNanos { }
                                if (!enterContent()) runCatching { contentFocusRequester.requestFocus() }
                            }
                        } else if (requestedFocusDirection == out && runCatching { contentFocusRequester.requestFocus() }.getOrDefault(false)) {
                            cancelFocusChange()
                        }
                    }
                }
                // ▶ always leaves for the content (the open menu covers the content's left column).
                .onKeyEvent { e ->
                    val out = if (rtl) Key.DirectionLeft else Key.DirectionRight
                    if (e.key != out) return@onKeyEvent false
                    if (e.type == KeyEventType.KeyDown) scope.launch {
                        if (enterContent?.invoke() != true) runCatching { contentFocusRequester.requestFocus() }
                    }
                    true
                }
                .focusGroup()
                .background(MenuPanel)
                .padding(horizontal = 18.mpx, vertical = 36.mpx),
            verticalArrangement = Arrangement.spacedBy(4.mpx),
        ) {
            MenuClock()
            Spacer(Modifier.height(22.mpx))
            if (nowPlaying != null) {
                RailItem(
                    icon = if (nowPlaying.audioMode) OwnTVIcon.EQ else OwnTVIcon.PLAY,
                    label = stringResource(R.string.shell_now_playing),
                    trailing = if (nowPlaying.audioMode && counts) stringResource(R.string.shell_now_playing_audio) else null,
                    active = false,
                    accent = menuAccent,
                    onClick = onNowPlaying,
                )
                RailSeparator()
            }
            items.forEach { section ->
                RailItem(
                    icon = section.stageIcon,
                    label = stringResource(section.labelRes),
                    trailing = if (counts) count(section)?.let { NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(it) } else null,
                    active = section == selected ||
                        (section == MainSection.MORE && selected == MainSection.SETTINGS),
                    accent = menuAccent,
                    onClick = { onSelect(section) },
                    modifier = if (section == focusSection) Modifier.focusRequester(selectedItemFocusRequester) else Modifier,
                )
            }
            Spacer(Modifier.weight(1f))
            RailSeparator()
            RailProfile(profileName, profileLine.takeIf { counts }, avatarId, avatarPath, menuAccent, onSwitchProfile, onPickAvatar)
        }
    }
}

/** The menu panel: near-black grey, a little see-through so the picture behind it still shows. */
private val MenuPanel = Color(0xF214161A)
private val MenuWidth = 420.mpx

/** Focus in the menu is a soft white bar with white text, not the accent fill used elsewhere. */
private val MenuHighlight = tv.own.owntv.ui.theme.StageAccent(
    accent = Color.White.copy(alpha = 0.16f),
    onAccent = Color.White,
    focus = Color.Transparent,
)

/** The time at the top of the menu, large and plain. */
@Composable
private fun MenuClock() {
    val formatTime = tv.own.owntv.ui.format.rememberSystemTimeFormatter()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L - now % 60_000L)
            now = System.currentTimeMillis()
        }
    }
    Text(
        formatTime(now), style = stageText(40, 700), color = StageColors.Text, maxLines = 1,
        modifier = Modifier.padding(start = 18.mpx),
    )
}

private val MainSection.stageIcon: OwnTVIcon
    get() = when (this) {
        MainSection.SEARCH -> OwnTVIcon.SEARCH
        MainSection.HOME -> OwnTVIcon.HOME
        MainSection.LIVE_TV -> OwnTVIcon.LIVE_TV
        MainSection.EPG -> OwnTVIcon.EPG
        MainSection.SPORTS -> OwnTVIcon.SPORTS
        MainSection.MOVIES -> OwnTVIcon.MOVIES
        MainSection.SERIES -> OwnTVIcon.SERIES
        MainSection.DOWNLOADS -> OwnTVIcon.DOWNLOADS
        MainSection.MORE, MainSection.SETTINGS -> OwnTVIcon.TILES
    }

@Composable
private fun RailSeparator() {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 18.mpx, vertical = 10.mpx)
            .height(1.mpx)
            .background(StageColors.Ink.copy(alpha = 0.10f)),
    )
}

/**
 * One menu row, 64 high: icon and name, the count at the end from Size Wide up. The open section is
 * marked by a short accent bar at the row's start and its name in bold; focus is the soft white bar.
 */
@Composable
private fun RailItem(
    icon: OwnTVIcon,
    label: String,
    trailing: String?,
    active: Boolean,
    accent: tv.own.owntv.ui.theme.StageAccent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bar = stageAccent.accent
    val r = 10.mpx
    StageSurface(
        onClick = onClick,
        radius = r,
        accent = accent,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.mpx),
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        if (active) {
            Box(
                Modifier.align(Alignment.CenterStart).size(4.mpx, 30.mpx)
                    .background(bar, RoundedCornerShape(2.mpx)),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.mpx, vertical = 8.mpx),
            horizontalArrangement = Arrangement.spacedBy(22.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OwnTVIcon(icon, if (focused || active) Color.White else StageColors.NavText, Modifier.size(30.mpx))
            Text(
                label,
                style = stageText(24, if (active) 700 else 500),
                color = if (focused || active) Color.White else StageColors.ItemText,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(trailing, style = stageText(18, 500), color = StageColors.Dim, maxLines = 1)
            }
        }
    }
}

/** The profile at the bottom of the menu: avatar and name. OK switches profile; a long press changes the avatar. */
@Composable
private fun RailProfile(name: String, line: String?, avatarId: Int, avatarPath: String, accent: tv.own.owntv.ui.theme.StageAccent, onSwitchProfile: () -> Unit, onPickAvatar: () -> Unit) {
    val initial = name.trim().take(1).uppercase().ifEmpty { "?" }
    StageSurface(
        onClick = onSwitchProfile,
        onLongClick = onPickAvatar,
        radius = 10.mpx,
        accent = accent,
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Row(
            Modifier.padding(horizontal = 16.mpx, vertical = 10.mpx),
            horizontalArrangement = Arrangement.spacedBy(16.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RailAvatar(initial, size = 40, textSize = 18, avatarId = avatarId, imagePath = avatarPath)
            Column {
                Text(name, style = stageText(20, 600), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (line != null) {
                    Text(line, style = stageText(15, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * `.frail .av`: the profile's avatar or own picture in a circle; with neither, its initial on the orange
 * gradient, 20/800 in #2A0D12. More's profile row draws it 58 / 24.
 */
@Composable
internal fun RailAvatar(initial: String, size: Int = 46, textSize: Int = 20, avatarId: Int = -1, imagePath: String = "") {
    if (avatarId != -1 || imagePath.isNotBlank()) {
        tv.own.owntv.ui.components.OwnTVAvatar(avatarId = avatarId, imagePath = imagePath, modifier = Modifier.size(size.mpx).clip(CircleShape))
        return
    }
    Box(
        Modifier
            .size(size.mpx)
            .background(Brush.linearGradient(listOf(Color(0xFFFFB35C), Color(0xFFFF5F7A))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = stageText(textSize, 800), color = Color(0xFF2A0D12), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
