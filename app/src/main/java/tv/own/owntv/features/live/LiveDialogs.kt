package tv.own.owntv.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import tv.own.owntv.ui.theme.mpx
import androidx.tv.material3.Text
import tv.own.owntv.R
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner

/** Manual EPG matching: pick which guide channel this channel uses (search across all EPG feeds).
 *  Shared with the Guide screen (long-press a channel → Match EPG). */
@Composable
internal fun EpgMatchDialog(
    channelName: String,
    currentMatch: String?,
    loadChannels: suspend (String) -> List<tv.own.owntv.core.epg.GuideCandidate>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results by androidx.compose.runtime.produceState<List<tv.own.owntv.core.epg.GuideCandidate>?>(initialValue = null, query) {
        kotlinx.coroutines.delay(250)
        value = runCatching { loadChannels(query) }.getOrDefault(emptyList())
    }
    androidx.activity.compose.BackHandler { onDismiss() }

    // Pull focus into the dialog once the list first arrives (first result, else the search bar).
    // One-shot, so later search-driven reloads don't steal focus from the field while typing.
    val firstItemFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var didInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(results) {
        if (didInitialFocus || results == null) return@LaunchedEffect
        didInitialFocus = true
        kotlinx.coroutines.delay(60)
        if (results!!.isNotEmpty()) runCatching { firstItemFocus.requestFocus() }
        else runCatching { searchFocus.requestFocus() }
    }

    // The Stage picker: a menu panel over the scrim, focus trapped inside, the list capped so Close stays reachable.
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(tv.own.owntv.ui.theme.StageColors.Scrim),
            contentAlignment = Alignment.Center,
        ) {
            tv.own.owntv.ui.stage.StageMenu(Modifier.width(640.mpx).trapAllFocusExit().focusGroup()) {
                tv.own.owntv.ui.stage.StageMenuHeader(
                    title = stringResource(R.string.content_match_epg),
                    subtitle = if (currentMatch != null) {
                        stringResource(R.string.content_epg_match_prompt_current, channelName, currentMatch)
                    } else {
                        stringResource(R.string.content_epg_match_prompt, channelName)
                    },
                )
                tv.own.owntv.ui.stage.StageSearchField(
                    query = query, onQueryChange = { query = it },
                    placeholder = stringResource(R.string.content_search_guide_channels).trimEnd('…'),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.mpx).focusRequester(searchFocus),
                )
                Spacer(Modifier.height(12.mpx))
                val list = results
                when {
                    list == null -> androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(120.mpx), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 24) }
                    list.isEmpty() -> Text(
                        if (query.isBlank()) stringResource(R.string.content_no_epg_data) else stringResource(R.string.content_no_guide_channels, query),
                        style = tv.own.owntv.ui.theme.stageText(18, 500), color = tv.own.owntv.ui.theme.StageColors.Muted,
                        modifier = Modifier.padding(horizontal = 14.mpx, vertical = 12.mpx),
                    )
                    else -> LazyColumn(Modifier.fillMaxWidth().height(400.mpx)) {
                        items(list, key = { it.epgChannelId }) { epg ->
                            tv.own.owntv.ui.stage.StageMenuItem(
                                text = epg.displayName ?: epg.epgChannelId,
                                value = epg.epgChannelId.takeIf { epg.displayName != null },
                                checked = epg.epgChannelId == currentMatch,
                                onClick = { onPick(epg.epgChannelId) },
                                modifier = if (epg == list.first()) Modifier.focusRequester(firstItemFocus) else Modifier,
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.mpx),
                    horizontalArrangement = Arrangement.spacedBy(12.mpx, Alignment.End),
                ) {
                    if (currentMatch != null) tv.own.owntv.ui.stage.StageButton(stringResource(R.string.content_clear_match), onClick = onClear, height = 56.mpx, textSize = 19)
                    tv.own.owntv.ui.stage.StageButton(stringResource(R.string.content_close), onClick = onDismiss, height = 56.mpx, textSize = 19, tinted = true)
                }
            }
        }
    }
}

/**
 * Per-channel EPG time offset. Providers often hang both the East and the West stream of a network
 * off ONE guide, so one of them runs hours out; this moves that channel's guide only. Shared with the
 * Guide screen (long-press a channel → EPG offset). The change is written on "Done", so stepping
 * through a few hours is a single edit.
 */
@Composable
internal fun EpgOffsetDialog(
    channelName: String,
    currentMinutes: Int?,
    globalMinutes: Int,
    onSet: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableStateOf(currentMinutes ?: globalMinutes) }
    val valueFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { valueFocus.requestFocus() } }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.content_epg_time_offset),
        body = stringResource(R.string.content_epg_offset_channel_description, channelName),
        eyebrow = null,
        width = 760.mpx,
        buttons = {
            if (currentMinutes != null) tv.own.owntv.ui.stage.StageButton(stringResource(R.string.content_epg_offset_use_global), onClick = { onSet(null); onDismiss() }, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_done), onClick = { onSet(minutes); onDismiss() }, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        // ◀ ▶ shift by half an hour, -12 h … +14 h; Done keeps it.
        tv.own.owntv.ui.stage.StageSurface(
            onClick = { onSet(minutes); onDismiss() },
            radius = 18.mpx,
            focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
            modifier = Modifier.fillMaxWidth().height(84.mpx).focusRequester(valueFocus).onPreviewKeyEvent { e ->
                val d = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
                if (d != 0 && e.type == KeyEventType.KeyDown) minutes = (minutes + d * 30).coerceIn(-12 * 60, 14 * 60)
                d != 0
            },
            contentAlignment = Alignment.Center,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.mpx), verticalAlignment = Alignment.CenterVertically) {
                tv.own.owntv.ui.components.OwnTVIcon(tv.own.owntv.ui.components.OwnTVIcon.CHEVRON, tv.own.owntv.ui.theme.StageColors.Muted, Modifier.size(26.mpx).graphicsLayer { rotationZ = 180f })
                Text(liveEpgShiftLabel(minutes), style = tv.own.owntv.ui.theme.stageText(36, 800), color = tv.own.owntv.ui.theme.stageAccent.accent, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                tv.own.owntv.ui.components.OwnTVIcon(tv.own.owntv.ui.components.OwnTVIcon.CHEVRON, tv.own.owntv.ui.theme.StageColors.Muted, Modifier.size(26.mpx))
            }
        }
        Text(
            stringResource(if (currentMinutes == null) R.string.content_epg_offset_following_global else R.string.content_epg_offset_channel_only, liveEpgShiftLabel(globalMinutes)),
            style = tv.own.owntv.ui.theme.stageText(15, 500), color = tv.own.owntv.ui.theme.StageColors.Muted, modifier = Modifier.padding(top = 12.mpx),
        )
    }
}

/** Remind me's value: "5 min before", or "At the start" when the reminder comes at the start. */
@Composable
internal fun reminderLeadText(minutes: Int): String =
    if (minutes == 0) stringResource(R.string.settings_reminder_at_start)
    else stringResource(R.string.content_remind_before, stringResource(R.string.player_duration_minutes, minutes))

@Composable
internal fun liveEpgShiftLabel(minutes: Int): String {
    if (minutes == 0) return stringResource(R.string.common_off)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: java.util.Locale.US
    val number = java.text.NumberFormat.getIntegerInstance(locale)
    val sign = if (minutes < 0) "−" else "+"
    val absolute = kotlin.math.abs(minutes)
    val hours = absolute / 60
    val remainder = absolute % 60
    return when {
        hours == 0 -> stringResource(R.string.content_epg_shift_minutes, sign, number.format(remainder))
        remainder == 0 -> stringResource(R.string.content_epg_shift_hours, sign, number.format(hours))
        else -> stringResource(
            R.string.content_epg_shift_hours_minutes,
            sign,
            number.format(hours),
            number.format(remainder),
        )
    }
}
