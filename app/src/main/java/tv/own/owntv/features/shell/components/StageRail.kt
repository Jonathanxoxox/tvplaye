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

/** The menu order: Home on top (Jonathan's start page), then Search and TV (which opens as the guide), More last. */
private val StageRailOrder = listOf(
    MainSection.HOME, MainSection.SEARCH, MainSection.LIVE_TV, MainSection.SPORTS,
    MainSection.MOVIES, MainSection.SERIES, MainSection.DOWNLOADS,
)

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
    // TV Player: the menu as the classic IPTV player draws it. At rest a narrow strip of icons down the
    // left edge; with focus it widens to icons and names, the app name on top and Settings at the bottom.
    // Focus is the near-white bar every list uses; the open section keeps a lighter bar.
    val expanded = state == RailState.OPEN || state == RailState.PINNED
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
    val items = StageRailOrder.filter { it == MainSection.SEARCH || it in visibleSections }

    // Opened over the page (Floating, or a widened Compact): the page slides aside ([rememberRailPush]),
    // bright as before, as in the classic IPTV player. Pages that add a column beside the menu read it.
    val openOverPage = state == RailState.OPEN
    androidx.compose.runtime.SideEffect { StageRailOpen.open = openOverPage }
    // Opening, the menu slides in from the left edge alongside the page it pushes, as in TiviMate.
    val openness = androidx.compose.animation.core.animateFloatAsState(if (expanded) 1f else 0f)
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(if (expanded) MenuWidth else StripWidth)
                .graphicsLayer {
                    if (expanded) translationX = -(1f - openness.value) * (MenuWidth - StripWidth).toPx() * if (rtl) -1f else 1f
                }
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
                .background(tv.own.owntv.ui.theme.inScene(if (expanded) MenuPanel else StripPanel))
                .padding(horizontal = if (expanded) 26.mpx else 14.mpx, vertical = 40.mpx),
            horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.mpx),
        ) {
            MenuLogo(expanded)
            Spacer(Modifier.weight(0.55f))
            if (nowPlaying != null) {
                RailItem(
                    icon = if (nowPlaying.audioMode) OwnTVIcon.EQ else OwnTVIcon.PLAY,
                    label = stringResource(R.string.shell_now_playing),
                    trailing = if (nowPlaying.audioMode && counts) stringResource(R.string.shell_now_playing_audio) else null,
                    expanded = expanded,
                    active = false,
                    onClick = onNowPlaying,
                )
            }
            items.forEach { section ->
                RailItem(
                    icon = section.stageIcon,
                    label = stringResource(section.labelRes),
                    trailing = if (counts) count(section)?.let { NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(it) } else null,
                    expanded = expanded,
                    active = section == selected,
                    onClick = { onSelect(section) },
                    modifier = if (section == focusSection) Modifier.focusRequester(selectedItemFocusRequester) else Modifier,
                )
            }
            Spacer(Modifier.weight(1f))
            // Settings at the bottom, like the classic player's gear (More holds Settings and the rest).
            RailItem(
                icon = OwnTVIcon.GEAR,
                label = stringResource(MainSection.MORE.labelRes),
                trailing = null,
                expanded = expanded,
                active = selected == MainSection.MORE || selected == MainSection.SETTINGS,
                onClick = { onSelect(MainSection.MORE) },
                modifier = if (focusSection == MainSection.MORE) Modifier.focusRequester(selectedItemFocusRequester) else Modifier,
            )
        }
    }
}

/** The open menu, a shade lighter than the page; the resting strip lighter still. */
private val MenuPanel = Color(0xFF14787C)
private val StripPanel = Color(0xFF1A878B)
private val MenuWidth = 440.mpx
private val StripWidth = 110.mpx

/** What every screen leaves free at its left edge for the resting icon strip. */
val StageRailStripWidth = StripWidth

/** Whether the menu is open over the page right now, for a page that shows its own column beside it. */
object StageRailOpen {
    var open by mutableStateOf(false)
    /** The guide's categories are out (focused or being entered): the status cluster steps aside for them too. */
    var guideGroups by mutableStateOf(false)
    /** The guide holds focus: the strip steps aside, as in TiviMate, and the guide takes its room. ◀ brings it back. */
    var guideFocused by mutableStateOf(false)
    /** A page that takes the whole width like the guide is shown (a match page). */
    var fullPage by mutableStateOf(false)
    /** That page holds focus: the strip steps aside as for the guide. ◀ brings it back. */
    var fullPageFocused by mutableStateOf(false)
}

/**
 * How far the page slides aside while the menu is open over it, as in the classic IPTV player: the page
 * moves over by the menu's extra width instead of lying cut off under it. A docked menu keeps its own
 * room and pushes nothing. Read it inside a graphics layer, so the slide redraws without recomposing.
 */
@Composable
fun rememberRailPush(state: RailState): androidx.compose.runtime.State<androidx.compose.ui.unit.Dp> =
    androidx.compose.animation.core.animateDpAsState(if (state == RailState.OPEN) MenuWidth - StripWidth else 0.mpx)

/** The app's name in the accent at the top of the open menu; its first letters on the strip. */
@Composable
private fun MenuLogo(expanded: Boolean) {
    // Mayo TV: the floodlight mark on the strip, with the name beside it when the menu is open.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = if (expanded) 10.mpx else 0.mpx),
    ) {
        // In the theme's muted ink, so the logo belongs to the menu instead of standing out of it.
        BrandMark(56.mpx, Modifier.padding(4.mpx), subtle = true)
        if (expanded) {
            Text(
                stringResource(R.string.tvplayer_app_name),
                style = stageText(40, 800),
                color = StageColors.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 18.mpx),
            )
        }
    }
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

/**
 * One menu row: on the strip only the icon (64 square), open the icon and the name. Focus is the
 * near-white bar with dark grey icon and text; the open section a lighter bar.
 */
@Composable
private fun RailItem(
    icon: OwnTVIcon,
    label: String,
    trailing: String?,
    expanded: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val a = stageAccent
    val r = 10.mpx
    StageSurface(
        onClick = onClick,
        radius = r,
        idle = if (active) Modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(r)) else Modifier,
        modifier = modifier.then(if (expanded) Modifier.fillMaxWidth().heightIn(min = 68.mpx) else Modifier.size(68.mpx)),
        contentAlignment = if (expanded) Alignment.CenterStart else Alignment.Center,
    ) { focused ->
        val tint = if (focused) a.onAccent else Color.White
        if (!expanded) {
            OwnTVIcon(icon, tint, Modifier.size(32.mpx))
            return@StageSurface
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.mpx, vertical = 8.mpx),
            horizontalArrangement = Arrangement.spacedBy(24.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OwnTVIcon(icon, tint, Modifier.size(32.mpx))
            Text(
                label,
                style = stageText(tv.own.owntv.ui.theme.StageListText, 600),
                color = tint,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(trailing, style = stageText(18, 500), color = if (focused) a.onAccent else StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
