package tv.own.owntv.player

import tv.own.owntv.ui.theme.hudShade
import tv.own.owntv.ui.theme.hudInk
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.own.owntv.R
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.core.settings.RemoteShortcutAction
import tv.own.owntv.core.settings.RemoteShortcutPress
import tv.own.owntv.ui.components.LocalRemoteShortcuts
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.displayText // PlayerFailureReason.displayText, for the error overlay
import tv.own.owntv.ui.theme.gradientWash

/**
 * The full-screen player HUD. This file owns the HUD's STATE — visibility, direct tune, the dialog the
 * user has open, the zap/engine flashes — and composes the pieces that draw it. Those pieces live in
 * sibling files: [PlayerHudChrome] (top bar, OSD cards, transport, bottom bar), [PlayerHudControls]
 * (buttons, seek bars, shared formatters) and [PlayerHudDialogs] (every dialog the HUD opens).
 */

@Composable
internal fun MediaSpec.displayText(): String {
    val decoderText = decoder?.let {
        when (it) {
            is DecoderSpec.Hardware -> buildList {
                add(stringResource(R.string.player_decoder_hardware))
                if (it.direct) add(stringResource(R.string.player_decoder_direct))
            }.joinToString(stringResource(R.string.player_metadata_separator))
            is DecoderSpec.Software -> buildList {
                add(stringResource(R.string.player_decoder_software))
                if (it.gpu) add(stringResource(R.string.player_decoder_gpu))
            }.joinToString(stringResource(R.string.player_metadata_separator))
            is DecoderSpec.Named -> buildList {
                add(
                    when (it.value.lowercase()) {
                        "exoplayer" -> stringResource(R.string.settings_player_exoplayer)
                        "mpv" -> stringResource(R.string.settings_player_mpv)
                        else -> it.value
                    },
                )
                if (it.hardware) add(stringResource(R.string.player_decoder_hardware))
                if (it.direct) add(stringResource(R.string.player_decoder_direct))
            }.joinToString(stringResource(R.string.player_metadata_separator))
        }
    }
    return listOfNotNull(codec, resolution, decoderText)
        .joinToString(stringResource(R.string.player_metadata_separator))
}

@Composable
private fun PlaybackFailure.displayText(): String {
    // Resolved through the same [describe] mapping the toast renderer uses. LocalConfiguration is read
    // so a language change still recomposes this, exactly as stringResource would.
    androidx.compose.ui.platform.LocalConfiguration.current
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    return describe { id, args -> resources.getString(id, *args.toTypedArray()) }
}

internal const val DIRECT_TUNE_TIMEOUT_MS = 2_000L
private const val DIRECT_TUNE_FEEDBACK_MS = 1_500L
private const val DIRECT_TUNE_PLAYBACK_WAIT_MS = 8_000L
private const val MAX_DIRECT_TUNE_DIGITS = 5
private const val PLAYER_SHORTCUT_LONG_PRESS_MS = 600L

/** Re-poll for late-arriving audio/subtitle tracks while the track menu is open and still empty.
 *  20 × 300 ms = 6 s, comfortably past the slowest observed HDR/DTS track report, then it stops. */
private const val TRACK_POLL_MS = 300L
private const val TRACK_POLL_TRIES = 20

/** A stall shorter than this is an ordinary re-buffer and gets the plain spinner. Past it, a line under the
 *  spinner says the stream is reconnecting by itself, so a long outage doesn't look like a hung app.
 *  Counted from the stall, which itself only starts once the buffer (~10s) has run dry. */
private const val RECONNECT_NOTICE_AFTER_MS = 10_000L

internal enum class HudDialog { NONE, AUDIO, SUBS, SPEED, ZOOM, QUALITY, VOLUME, JUMP_BACK, SLEEP_TIMER }

/** What the top-left channel OSD shows for direct tune: the digits being typed, the channel a number
 *  resolved to, or a failure message. All three render as the same card as the channel OSD. */
private sealed interface TuneOsd {
    data class Entry(val digits: String) : TuneOsd
    data class Tuned(val info: DirectTuneChannelInfo) : TuneOsd
    data class Message(val digits: String, val text: String) : TuneOsd
}

@Composable
fun PlayerHud(
    player: PlaybackEngine,
    onBack: () -> Unit,
    // Live only, and only when Multiview is switched on in Settings: turn this channel into tile 1 of
    // the grid. Null hides the button entirely — the feature is opt-in, like the record button.
    onMultiview: (() -> Unit)? = null,
    // Live only, and only once "Record what I'm watching" is switched on in Settings: record the
    // channel already playing, over the connection already open. Null hides the button entirely —
    // the feature does not exist until the user has accepted the one-connection trade-off.
    onRecordThis: (() -> Unit)? = null,
    /** True while that recording is running, so the button can say Stop instead of Record. */
    recordingThis: Boolean = false,
    // True while the shell draws an overlay ABOVE the HUD (e.g. the channel-list overlay). The HUD goes
    // inert: its auto-hide timer pauses and — crucially — it makes no focus requests, so it can't yank
    // D-pad focus off the overlay. The existing dialog guard below covers only the HUD's OWN dialogs;
    // shell-level overlays need this flag. Default false = no behavior change for other callers.
    inert: Boolean = false,
    onChannelUp: (() -> Unit)? = null,
    onChannelDown: (() -> Unit)? = null,
    // Live: open the channel-list overlay (Left while the controls are hidden). Null = not a live channel.
    onOpenChannelList: (() -> Unit)? = null,
    // Live: open the watch-history list (Right while the controls are hidden) — jump straight back to a
    // recent channel without leaving full-screen. Null = not a live channel.
    onOpenHistoryList: (() -> Unit)? = null,
    // Live rewind / timeshift (catch-up channels). onRewindLive non-null = this live channel can rewind;
    // timeshiftOffsetSec non-null = currently watching that many seconds behind the live edge.
    onRewindLive: (() -> Unit)? = null,
    onForwardLive: (() -> Unit)? = null,
    onGoToLive: (() -> Unit)? = null,
    onScrubLive: ((Int) -> Unit)? = null, // timeline scrub: +sec = back, −sec = toward live
    // The playing channel's guide, for the timeline's programme ticks and the scrub bubble's name.
    liveProgrammes: List<LiveProgramme> = emptyList(),
    /** The saved copy's holes, for the rewind bar. */
    liveGaps: () -> List<LongRange> = { emptyList() },
    // "Go back to…": aim at a point in the archive instead of nudging toward it with rewind. Null =
    // not a catch-up channel. [jumpBackOptions] is read when the list opens so its clock times are
    // computed against the moment the user asked, not the moment the HUD was composed.
    jumpBackOptions: (() -> List<Int>)? = null,
    onJumpBack: ((Int) -> Unit)? = null,
    // Back to the channel watched before this one. Null hides the button (no previous channel yet).
    onPreviousChannel: (() -> Unit)? = null,
    // Archive depth of the current channel, for the exact-time picker's day/HH:MM bounds.
    jumpBackWindowSec: (() -> Int)? = null,
    // Read as a lambda, not a value: the offset ticks once a second, and taking it as a plain Int?
    // made the shell (the caller) recompose on every tick just to hand it over. Invoked below, so the
    // per-second read lands in the HUD's own scope, which already recomposes with the position clock.
    timeshiftOffsetSec: (() -> Int?)? = null,
    // Direct tune: enter a provider channel number to switch channels. Null = disabled (not live / no channel).
    onTuneToNumber: (suspend (Int) -> DirectTuneResult)? = null,
    // Channel identity key for direct tune: changing this cancels any in-flight submission.
    directTuneContextKey: Long = 0L,
    // Live "compatibility mode": pin this channel to the mpv engine (fixes UHD artifacts / undecodable
    // streams ExoPlayer can't handle). null = not a live channel; true = currently pinned to mpv.
    compatMode: Boolean? = null,
    onToggleCompatMode: (() -> Unit)? = null,
    // VOD engine toggle: switch THIS movie/episode between mpv and ExoPlayer (e.g. to reach tracks only
    // one engine exposes, or to try the other engine on a problem file). null = not a VOD;
    // true = currently playing on ExoPlayer.
    vodOnExo: Boolean? = null,
    onToggleVodEngine: (() -> Unit)? = null,
    // Movie/episode only: open the OpenSubtitles search from the Subtitles dialog.
    // Null for Live TV and when there's no current-item context, which hides the ADD SUBTITLES row.
    onSearchSubtitles: (() -> Unit)? = null,
    // Movie/episode only: pick a local subtitle file — no account needed, same gating.
    onSelectLocalSubtitle: (() -> Unit)? = null,
    // Favorite toggle for the CURRENT item (live channel / movie / series). Null hides the button
    // (no item context). [favorite] = current state — fills the star teal when true.
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    // Live guide card (Before / Now playing / Next for the playing channel) — supplied by the shell
    // (the EPG data lives in LiveViewModel, not the player). Rendered on the right edge whenever the
    // controls are visible, like the top-bar channel card; informational only, never focusable.
    liveEpgCard: (@Composable () -> Unit)? = null,
    // Now/next lines for the channel-change banner (the playing channel's guide). Null = name only.
    zapGuide: (@Composable () -> Unit)? = null,
    // The playing channel's guide for the info bar along the bottom after a channel change. Non-null
    // switches the live player to the classic IPTV remote: OK shows the info bar with the control panel,
    // Left the channel list, Right the channel before, Up/Down change channel (held: history / search),
    // long OK or Menu the options panel, long Menu the guide.
    zapInfo: (@Composable () -> ZapInfo?)? = null,
    // The classic remote's extra targets (guide, search, the control panel's channel tiles). Grouped in
    // one value: one more parameter each pushed this function past what the release build verifies.
    remote: LiveRemoteActions? = null,
    // The archive's own wall-clock instant while catch-up/rewind is playing; null means the picture is
    // the present, and only the real clock shows. Drives the second, framed clock at top centre.
    // Lambda for the same reason as [timeshiftOffsetSec]: this instant advances every second too.
    watchingWallMs: (() -> Long?)? = null,
    // When the programme on air ends, read as the sleep timer opens. Null = no "End of programme".
    sleepProgrammeEndMs: (() -> Long?)? = null,
    // Settings → "Left and right rewind live TV": with the controls hidden, Left/Right on a
    // catch-up channel scrub instead of opening the lists. Films always seek.
    liveLeftRightRewinds: Boolean = false,
    // Sports Hub — only for a channel started from a match; all null means the plain Live player.
    // The match's score line under the channel name, drawn only while the controls are up.
    matchLine: (@Composable () -> Unit)? = null,
    // The other channels showing the match, and their count for the tool's label.
    onOtherChannels: (() -> Unit)? = null,
    otherChannelsCount: Int = 0,
    // The match page.
    // A goal toast for another followed match is up: OK switches to it instead of showing the controls.
    onToastOk: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Nothing that ticks is read in this root scope: with the controls hidden it used to recompose
    // the whole HUD once a second for a position nobody could see. The offset, the archive clock and the
    // position are handed down as lambdas and read by the piece that draws them.
    val timeshiftOffset: () -> Int? = { timeshiftOffsetSec?.invoke() }
    val onOpenGuide = remote?.onOpenGuide
    val onOpenSearch = remote?.onOpenSearch
    val layoutDirection = LocalLayoutDirection.current
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val position = player.position.collectAsStateWithLifecycle()
    val duration by player.duration.collectAsStateWithLifecycle()
    val buffering by player.buffering.collectAsStateWithLifecycle()
    val error by player.error.collectAsStateWithLifecycle()
    val errorInfo by player.errorInfo.collectAsStateWithLifecycle()
    val providerBackOff by player.providerBackOff.collectAsStateWithLifecycle()
    val stalledSinceMs by player.stalledSinceMs.collectAsStateWithLifecycle()
    val nav by player.nav.collectAsStateWithLifecycle()
    val volume by player.volume.collectAsStateWithLifecycle()
    val videoRes by player.videoRes.collectAsStateWithLifecycle()
    val streamChips by player.streamChips.collectAsStateWithLifecycle()
    val engineChip by player.engineChip.collectAsStateWithLifecycle()
    val audioCount by player.audioCount.collectAsStateWithLifecycle()
    val audioDelayMs by player.audioDelayMs.collectAsStateWithLifecycle()
    val audioDelayRemembered by player.audioDelayRemembered.collectAsStateWithLifecycle()
    val subCount by player.subCount.collectAsStateWithLifecycle()
    val zoomMode by player.zoomMode.collectAsStateWithLifecycle()
    val speed by player.speed.collectAsStateWithLifecycle()
    val isLive = player.isLiveContent
    val switchedToExo = stringResource(R.string.player_switch_exo)
    val switchedToMpv = stringResource(R.string.player_switch_mpv)
    val tuneNotFound = stringResource(R.string.player_channel_not_found)
    val multipleChannels = stringResource(R.string.player_multiple_channels)
    val tuneFailed = stringResource(R.string.player_tune_failed)

    val nextUpTitle by player.nextUpTitle.collectAsStateWithLifecycle()
    // The sleep timer's "End of episode" stops there, so there is no next to count down to.
    val stopsAtItemEnd by player.stopsAtItemEnd.collectAsStateWithLifecycle()

    var dialog by remember { mutableStateOf(HudDialog.NONE) }
    val playFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val catchFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    // The timeline, and whether the next reveal of the controls lands on it rather than Play.
    val seekFocus = remember { FocusRequester() }
    var focusSeekOnShow by remember { mutableStateOf(false) }

    // Next-episode countdown card (VOD queues only): appears in the last ~30s before the automatic
    // advance (which fires at duration − 8s), counts down to it, and offers Play now / Cancel.
    var autoNextDismissed by remember { mutableStateOf(false) }
    // Re-arm when the queued next episode changes (i.e. after an advance to a new item).
    LaunchedEffect(nextUpTitle, nav.hasNext) { autoNextDismissed = false }
    // Derived, so the root recomposes when the card appears or goes, not on every position tick.
    val msToAdvance: () -> Long = { if (!isLive && duration > 0L) (duration - 8_000L) - position.value else Long.MAX_VALUE }
    val nextCardDue by remember(isLive, duration) { derivedStateOf { msToAdvance() in 0L..30_000L } }
    val showNextCard = !isLive && error == null && nav.hasNext && nextUpTitle != null && !stopsAtItemEnd &&
        nextCardDue && !autoNextDismissed

    // List-first live player (see [zapInfo]): the picture alone on entry, with just the info bar.
    // Not tied to the channel list: at app start on the last channel that list is still loading, and
    // waiting for it showed the classic controls for a moment first (Jonathan, 27 Oct 2026). ◀ simply
    // does nothing until the list is there.
    val listFirst = zapInfo != null
    var controlsVisible by remember { mutableStateOf(!listFirst) }
    LaunchedEffect(listFirst) { if (listFirst) controlsVisible = false }
    var showOptions by remember { mutableStateOf(false) }
    // The options panel ignores OK until the press that opened it has been released, or the
    // auto-repeat of a held OK would choose its first row.
    var optionsOpenedByOk by remember { mutableStateOf(false) }
    // The info bar with the tile row under it (OK in the live player), and what keeps it up.
    var showPanel by remember { mutableStateOf(false) }
    var panelTick by remember { mutableIntStateOf(0) }
    val panelFocus = remember { FocusRequester() }
    // Which mapped key is held, to tell a short press from a long one (see PlayerKeyPress).
    val keyPress = remember { PlayerKeyPress() }
    var showInfo by remember { mutableStateOf(false) } // stream technical-info overlay
    // Used only by "Report this stream", which writes the current readout into the playback log.
    val reportContext = androidx.compose.ui.platform.LocalContext.current
    val keyMap by remember { PlayerKeyMap.flow(reportContext) }.collectAsStateWithLifecycle()
    var wakeTick by remember { mutableIntStateOf(0) }
    // A stream error brought the classic controls up over the list-first live player: a provider hiccup
    // looked like the old player appearing out of nowhere (Jonathan, 27 Oct 2026). There the error screen
    // with its Retry stands alone, and ▲▼ still zap away from a dead channel.
    // Same for the dialogs (audio, subtitles…): opened from the list-first panel they draw on their own, and the
    // classic bar appearing behind them looked like the old player coming back (Jonathan, 28 Oct 2026).
    val forceShow = !listFirst && (error != null || dialog != HudDialog.NONE)
    // First Back hides the controls (instead of leaving the channel); with the controls already hidden
    // this handler is disabled, so Back falls through to the shell, which exits the player. Also disabled
    // while an error/dialog is up (a dialog handles its own Back; an error should exit).
    BackHandler(enabled = controlsVisible && !forceShow) { controlsVisible = false }
    // Channel zap (live only): a brief "now watching" card on up/down without revealing the full HUD.
    val canZap = onChannelUp != null && onChannelDown != null
    var channelFlash by remember { mutableIntStateOf(0) }
    var showFlash by remember { mutableStateOf(false) }
    LaunchedEffect(channelFlash) { if (channelFlash > 0) { showFlash = true; delay(if (listFirst) 4000 else 3000); showFlash = false } }
    // List-first: every channel change shows the info bar, from the list, a number or a zap alike.
    // The channel the player opened on is the entry, not a change: no info bar for it, only for a change made inside.
    var entryKey by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(directTuneContextKey) {
        if (!listFirst || directTuneContextKey == 0L) return@LaunchedEffect
        if (entryKey == null) { entryKey = directTuneContextKey; return@LaunchedEffect }
        if (directTuneContextKey != entryKey) { entryKey = directTuneContextKey; channelFlash++ }
    }

    // Engine-switch confirmation toast: a brief "Switched to MPV/ExoPlayer" at the bottom-center when the
    // user flips the engine via the HUD toggle. Mirrors the channel-flash pattern above.
    var engineMsg by remember { mutableStateOf<String?>(null) }
    var engineFlash by remember { mutableIntStateOf(0) }
    LaunchedEffect(engineFlash) { if (engineFlash > 0) { delay(1800); engineMsg = null } }
    // A channel juddering on ExoPlayer: say once that the other engine may play it smoothly. Longer than
    // the switch confirmation — this one has to be read.
    val smoothnessHint by player.smoothnessHint.collectAsStateWithLifecycle()
    val smoothnessHintText = stringResource(R.string.player_smoothness_hint)
    LaunchedEffect(smoothnessHint) {
        if (smoothnessHint) {
            engineMsg = smoothnessHintText
            delay(6_000)
            if (engineMsg == smoothnessHintText) engineMsg = null
        }
    }
    // Wrap the engine toggles so a click also surfaces the toast naming the engine we're switching TO.
    val toggleCompat: (() -> Unit)? = onToggleCompatMode?.let { cb -> {
        engineMsg = if (compatMode == true) switchedToExo else switchedToMpv; engineFlash++; cb()
    } }
    val toggleVod: (() -> Unit)? = onToggleVodEngine?.let { cb -> {
        engineMsg = if (vodOnExo == true) switchedToMpv else switchedToExo; engineFlash++; cb()
    } }

    // ---- Direct tune (channel-number entry) ----
    var digitBuffer by remember { mutableStateOf("") }
    var submissionRequest by remember { mutableStateOf<Int?>(null) }
    var submissionTick by remember { mutableIntStateOf(0) }

    var lookupInFlight by remember { mutableStateOf(false) }

    var tuneOsd by remember { mutableStateOf<TuneOsd?>(null) }
    var tuneOsdTick by remember { mutableIntStateOf(0) }

    val digitsActive = digitBuffer.isNotEmpty()
    val heldDigitKeys = remember { mutableSetOf<Key>() }

    val cancelDirectTune: () -> Unit = {
        digitBuffer = ""
        submissionRequest = null
        tuneOsd = null
        heldDigitKeys.clear()
        submissionTick++
        tuneOsdTick++
    }

    val zap: (Int) -> Unit = { d ->
        cancelDirectTune()
        (if (d < 0) onChannelUp else onChannelDown)?.invoke(); channelFlash++
    }

    // What a mapped key does; false when this player has no such target, so the key falls through.
    val runKeyAction: (PlayerKeyAction, PlayerKey, Boolean) -> Boolean = { action, key, long ->
        when (action) {
            PlayerKeyAction.NONE -> true
            PlayerKeyAction.INFO_PANEL -> { showPanel = true; true }
            PlayerKeyAction.OPTIONS -> { optionsOpenedByOk = key == PlayerKey.OK && long; showOptions = true; true }
            PlayerKeyAction.GUIDE -> onOpenGuide?.let { it(); true } ?: false
            PlayerKeyAction.CHANNEL_LIST -> onOpenChannelList?.let { it(); true } ?: false
            PlayerKeyAction.LAST_CHANNEL -> onPreviousChannel?.let { cancelDirectTune(); it(); true } ?: false
            PlayerKeyAction.CHANNEL_UP -> if (canZap) { zap(1); true } else false
            PlayerKeyAction.CHANNEL_DOWN -> if (canZap) { zap(-1); true } else false
            PlayerKeyAction.HISTORY -> onOpenHistoryList?.let { it(); true } ?: false
            PlayerKeyAction.SEARCH -> onOpenSearch?.let { it(); true } ?: false
        }
    }

    // Restartable timeout: each new digit restarts the ~2 s window. On expiry, submit.
    LaunchedEffect(digitBuffer) {
        if (digitBuffer.isEmpty()) return@LaunchedEffect
        delay(DIRECT_TUNE_TIMEOUT_MS)
        val num = digitBuffer.toIntOrNull()
        digitBuffer = ""
        if (num != null) { submissionRequest = num; submissionTick++ }
        else tuneOsd = null
    }
    // Submission: keyed on the immutable tick so setting submissionRequest=null doesn't cancel us.
    // lookupInFlight covers only the suspend callback, not the result-display period.
    LaunchedEffect(submissionTick) {
        val num = submissionRequest ?: return@LaunchedEffect
        submissionRequest = null
        lookupInFlight = true
        val result = try {
            onTuneToNumber?.invoke(num)
        } finally {
            lookupInFlight = false
            // A KeyUp can be lost when focus or the window changes mid-entry (dialog, PiP, app switch),
            // which would strand that digit in the held set and make the key dead until the next KeyUp.
            // A completed submission ends the entry, so no held state can legitimately survive it.
            heldDigitKeys.clear()
        }
        tuneOsd = when (result) {
            is DirectTuneResult.Found -> TuneOsd.Tuned(result.channel)
            is DirectTuneResult.NotFound -> TuneOsd.Message(num.toString(), tuneNotFound)
            is DirectTuneResult.Ambiguous -> TuneOsd.Message(num.toString(), multipleChannels)
            is DirectTuneResult.Failed -> TuneOsd.Message(num.toString(), tuneFailed)
            is DirectTuneResult.Cancelled -> null
            null -> null
        }
        if (tuneOsd != null) tuneOsdTick++
    }
    // Result-feedback expiry, keyed on tuneOsdTick so a new entry invalidates the old timer. A tuned
    // channel holds the OSD until the new stream is actually on screen (the lookup returns the moment
    // playback is KICKED OFF, not when it starts) and then DIRECT_TUNE_FEEDBACK_MS longer.
    LaunchedEffect(tuneOsdTick) {
        when (val osd = tuneOsd) {
            is TuneOsd.Tuned -> {
                if (osd.info.restarted) {
                    withTimeoutOrNull(DIRECT_TUNE_PLAYBACK_WAIT_MS) {
                        // Two phases: the outgoing stream can still report playing for a beat (Stalker/mpv
                        // resolve their URL asynchronously), so wait for the teardown before the start.
                        snapshotFlow { isPlaying && !buffering && error == null }.first { !it }
                        snapshotFlow { (isPlaying && !buffering) || error != null }.first { it }
                    }
                }
                delay(DIRECT_TUNE_FEEDBACK_MS)
                tuneOsd = null
            }
            is TuneOsd.Message -> { delay(DIRECT_TUNE_FEEDBACK_MS); tuneOsd = null }
            is TuneOsd.Entry, null -> Unit
        }
    }
    // Cancellation triggers (CH+/-, D-pad, overlay open, HUD dialog open).
    LaunchedEffect(inert) { if (inert) cancelDirectTune() }
    LaunchedEffect(dialog) { if (dialog != HudDialog.NONE) cancelDirectTune() }
    // Channel-key cleanup: narrow to pending entry state only. Do not clear timed result feedback
    // from a successful tune that changed the playing channel.
    LaunchedEffect(directTuneContextKey) {
        if (digitBuffer.isNotEmpty() || submissionRequest != null) {
            digitBuffer = ""
            submissionRequest = null
            heldDigitKeys.clear()
            submissionTick++
            // Abandoned digits have no timer of their own — drop the card with the entry it belonged to.
            if (tuneOsd is TuneOsd.Entry) tuneOsd = null
        }
    }
    // Back cancels digit entry before it hides/exits controls.
    BackHandler(enabled = digitsActive) { digitBuffer = ""; tuneOsd = null }
    BackHandler(enabled = showPanel && !digitsActive) { showPanel = false }
    LaunchedEffect(showPanel, panelTick, inert, dialog, showOptions) {
        if (showPanel && !inert && dialog == HudDialog.NONE && !showOptions) { delay(8000); showPanel = false }
    }
    LaunchedEffect(showPanel) {
        if (showPanel) { withFrameNanos { }; runCatching { panelFocus.requestFocus() } }
    }

    // Only for the "report this stream" button, whose readout is now gathered off the main thread.
    val hudScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(forceShow) { if (forceShow) controlsVisible = true }
    LaunchedEffect(controlsVisible, player) { if (controlsVisible) player.refreshStreamChips() }
    DisposableEffect(showInfo, player) {
        if (showInfo) player.refreshStreamChips()
        player.setBitrateTrackingEnabled(showInfo)
        onDispose { player.setBitrateTrackingEnabled(false) }
    }
    LaunchedEffect(controlsVisible, wakeTick, forceShow, inert) {
        // Don't auto-hide under an overlay — hiding is what triggers the catch-all focus grab below.
        if (controlsVisible && !forceShow && !inert) { delay(4500); controlsVisible = false }
    }
    LaunchedEffect(controlsVisible, error, dialog, inert, showNextCard, showOptions, showPanel) {
        // Never steal focus while a dialog is open (its rows own it) or while a shell overlay is up
        // (inert — the overlay owns the D-pad); when either closes this re-runs and hands focus back.
        if (dialog != HudDialog.NONE || inert || showOptions || showPanel) return@LaunchedEffect
        // The next-episode countdown card owns focus while it's up so Play now / Cancel are reachable.
        if (showNextCard) { runCatching { nextFocus.requestFocus() }; return@LaunchedEffect }
        if (controlsVisible) {
            val toSeek = focusSeekOnShow
            focusSeekOnShow = false
            when {
                error != null -> runCatching { retryFocus.requestFocus() }
                // Falls back to Play when no timeline is drawn after all (no duration yet, say).
                toSeek -> runCatching { seekFocus.requestFocus() }.onFailure { runCatching { playFocus.requestFocus() } }
                else -> runCatching { playFocus.requestFocus() }
            }
        } else if (error != null && listFirst) {
            runCatching { retryFocus.requestFocus() }
        } else runCatching { catchFocus.requestFocus() }
    }

    // The player sits over opaque video (never a glass surface — see Glass.kt), so its HUD buttons
    // stay flat regardless of glass mode: opt out of the DIALOGS default explicitly.
    val remoteShortcuts = LocalRemoteShortcuts.current
    var shortcutKeyCode by remember { mutableIntStateOf(android.view.KeyEvent.KEYCODE_UNKNOWN) }
    var shortcutPressedAt by remember { mutableStateOf(0L) }
    val dispatchPlayerShortcut: (RemoteShortcutAction) -> Unit = { action ->
        when (action) {
            RemoteShortcutAction.OPEN_SUBTITLE_CONTROLS -> { if (!listFirst) controlsVisible = true; dialog = HudDialog.SUBS }
            RemoteShortcutAction.OPEN_AUDIO_CONTROLS -> { if (!listFirst) controlsVisible = true; dialog = HudDialog.AUDIO }
            RemoteShortcutAction.OPEN_ASPECT_CONTROLS -> { if (!listFirst) controlsVisible = true; dialog = HudDialog.ZOOM }
            RemoteShortcutAction.TOGGLE_PLAYBACK_INFO -> showInfo = !showInfo
            else -> remoteShortcuts.dispatch(action)
        }
    }
    // The live player's settings: down the right edge on long OK, and under the tile row on ▼ (see
    // HudControlPanel). Built only while one of the two is up.
    val liveOptions: List<LiveOption> = if (showOptions || (showPanel && listFirst)) {
        val close = { showOptions = false; showPanel = false }
        val open: (HudDialog) -> Unit = { d -> showOptions = false; showPanel = false; dialog = d }
        val qualities by player.videoQualities.collectAsStateWithLifecycle()
        buildList {
            onToggleFavorite?.let { toggle ->
                add(LiveOption(stringResource(if (favorite) R.string.tvplayer_option_unfavorite else R.string.tvplayer_option_favorite), OwnTVIcon.FAVORITE) { close(); toggle() })
            }
            onPreviousChannel?.let { prev -> add(LiveOption(stringResource(R.string.player_previous_channel), OwnTVIcon.SWAP) { close(); prev() }) }
            onOpenHistoryList?.let { history -> add(LiveOption(stringResource(R.string.content_category_recently_watched), OwnTVIcon.HISTORY) { close(); history() }) }
            if (onJumpBack != null) add(LiveOption(stringResource(R.string.player_tool_catchup), OwnTVIcon.CATCHUP) { open(HudDialog.JUMP_BACK) })
            add(LiveOption(stringResource(R.string.player_tool_audio), OwnTVIcon.AUDIO, audioCount.takeIf { it > 1 }?.toString()) { open(HudDialog.AUDIO) })
            add(LiveOption(stringResource(R.string.player_tool_subtitles), OwnTVIcon.SUBTITLE, subCount.takeIf { it > 0 }?.toString()) { open(HudDialog.SUBS) })
            add(LiveOption(stringResource(R.string.player_tool_aspect), OwnTVIcon.ASPECT) { open(HudDialog.ZOOM) })
            if (qualities.isNotEmpty()) add(LiveOption(stringResource(R.string.player_tool_quality), OwnTVIcon.VIDEO) { open(HudDialog.QUALITY) })
            onRecordThis?.let { rec ->
                add(LiveOption(stringResource(if (recordingThis) R.string.recording_stop else R.string.recording_record), OwnTVIcon.LIVE_TV) { close(); rec() })
            }
            onMultiview?.let { mv -> add(LiveOption(stringResource(R.string.multiview_button), OwnTVIcon.LIST_GRID) { close(); mv() }) }
            toggleCompat?.let { toggle ->
                add(LiveOption(
                    stringResource(R.string.player_tool_engine), OwnTVIcon.VIDEO,
                    stringResource(if (compatMode == true) R.string.player_engine_mpv else R.string.player_engine_exo),
                ) { close(); toggle() })
            }
            add(LiveOption(stringResource(R.string.player_sleep_timer), OwnTVIcon.BEDTIME) { open(HudDialog.SLEEP_TIMER) })
            add(LiveOption(stringResource(R.string.player_tool_info), OwnTVIcon.INFO) { close(); showInfo = !showInfo })
            add(LiveOption(stringResource(R.string.tvplayer_option_controls), OwnTVIcon.LIST) { close(); controlsVisible = true })
        }
    } else emptyList()
    Box(
        modifier = modifier.fillMaxSize()
            .onPreviewKeyEvent { e ->
            // ---- Direct-tune digit capture (before the existing KeyDown guard) ----
            // Number keys are consumed globally here, HUD visible or not: on a TV remote a digit press
            // during live playback can only mean "tune to this channel", and swallowing both KeyDown and
            // KeyUp keeps a half-typed number from leaking into whatever else is focused underneath.
            // onTuneToNumber is null outside fullscreen live (see OwnTVShell), so nothing else is affected.
            if (onTuneToNumber != null && !inert && dialog == HudDialog.NONE) {
                val digit = keyToDigit(e.key)
                if (digit != null) {
                    if (e.type == KeyEventType.KeyUp) {
                        heldDigitKeys.remove(e.key)
                        return@onPreviewKeyEvent true
                    }
                    if (e.type == KeyEventType.KeyDown) {
                        if (lookupInFlight || !heldDigitKeys.add(e.key)) {
                            return@onPreviewKeyEvent true
                        }
                        val enteredDigits = digitBuffer + digit
                        tuneOsd = TuneOsd.Entry(enteredDigits)
                        tuneOsdTick++
                        if (enteredDigits.length == MAX_DIRECT_TUNE_DIGITS) {
                            digitBuffer = ""
                            submissionRequest = enteredDigits.toIntOrNull()
                            submissionTick++
                        } else {
                            digitBuffer = enteredDigits
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                // Enter/Center/NumpadEnter: submit immediately while digits are pending.
                if (e.type == KeyEventType.KeyDown && !lookupInFlight && digitsActive &&
                    (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)
                ) {
                    val num = digitBuffer.toIntOrNull()
                    digitBuffer = ""
                    if (num != null) { submissionRequest = num; submissionTick++ }
                    return@onPreviewKeyEvent true
                }
            }
            // A dedicated TV channel key belongs to the channel player for the whole session. Handle it
            // before configurable browse shortcuts so an OSD button, an engine handoff, or a paging binding
            // cannot temporarily claim it. KeyUp is swallowed too, keeping the complete press inside the HUD.
            if (canZap && (e.key == Key.ChannelUp || e.key == Key.ChannelDown)) {
                if (e.type == KeyEventType.KeyDown) {
                    zap(if (e.key == Key.ChannelUp) 1 else -1)
                }
                return@onPreviewKeyEvent true
            }
            // The control panel's tiles own the D-pad while it is up; every press keeps it up.
            if (showPanel) {
                // Menu closes the tile bar and goes on to what the Menu key does (the options), as it does without the bar.
                if (e.key != Key.Menu) {
                    if (e.type == KeyEventType.KeyDown) panelTick++
                    return@onPreviewKeyEvent false
                }
                if (e.type == KeyEventType.KeyDown) showPanel = false
            }
            // Configured player shortcuts run inside this original, stable preview handler. Direct
            // tune above has first priority, so number keys keep changing live channels fullscreen.
            if (remoteShortcuts.enabled && !inert && dialog == HudDialog.NONE && !digitsActive) {
                val keyCode = e.nativeKeyEvent.keyCode
                val keyBindings = remoteShortcuts.bindings.filter {
                    it.keyCode == keyCode &&
                        it.action != RemoteShortcutAction.PAGE_TOWARD_FIRST &&
                        it.action != RemoteShortcutAction.PAGE_TOWARD_LAST &&
                        it.action != RemoteShortcutAction.JUMP_TO_FIRST &&
                        it.action != RemoteShortcutAction.JUMP_TO_LAST
                }
                if (keyBindings.isNotEmpty()) {
                    when (e.type) {
                        KeyEventType.KeyDown -> {
                            if (shortcutKeyCode != keyCode) {
                                shortcutKeyCode = keyCode
                                shortcutPressedAt = e.nativeKeyEvent.eventTime
                            }
                            return@onPreviewKeyEvent true
                        }
                        KeyEventType.KeyUp -> if (shortcutKeyCode == keyCode) {
                            val heldLong = e.nativeKeyEvent.eventTime - shortcutPressedAt >= PLAYER_SHORTCUT_LONG_PRESS_MS
                            val binding = if (heldLong) {
                                keyBindings.firstOrNull { it.press == RemoteShortcutPress.LONG }
                                    ?: keyBindings.firstOrNull { it.press == RemoteShortcutPress.SHORT }
                            } else {
                                keyBindings.firstOrNull { it.press == RemoteShortcutPress.SHORT }
                            }
                            shortcutKeyCode = android.view.KeyEvent.KEYCODE_UNKNOWN
                            binding?.let { dispatchPlayerShortcut(it.action) }
                            return@onPreviewKeyEvent true
                        }
                    }
                }
            }
            // ---- List-first keys: OK, Menu, the arrows, as set in Settings › Remote control ----
            val scrubsLeftRight = error == null && (if (onScrubLive != null) liveLeftRightRewinds else !isLive && duration > 0L)
            val mapped = if (listFirst && !inert && dialog == HudDialog.NONE && !showOptions) playerKeyOf(e) else null
            if (mapped != null && (mapped == PlayerKey.MENU || (!controlsVisible && !showNextCard && onToastOk == null && !digitsActive)) &&
                (mapped != PlayerKey.UP && mapped != PlayerKey.DOWN || (canZap && isLive)) &&
                (mapped != PlayerKey.LEFT && mapped != PlayerKey.RIGHT || !scrubsLeftRight) &&
                (mapped != PlayerKey.OK || error == null)
            ) {
                if (keyPress.handle(e, mapped, keyMap.getValue(mapped), runKeyAction)) return@onPreviewKeyEvent true
            }
            // The classic remote's other player keys (Info, Guide and the colours are mapped above):
            // Backspace the guide, Record the recording, Stop leaves the picture.
            if (listFirst && !controlsVisible && !inert && dialog == HudDialog.NONE && !showOptions && !digitsActive) {
                val action: (() -> Unit)? = when (e.key) {
                    Key.Backspace -> onOpenGuide
                    Key.MediaRecord -> onRecordThis
                    Key.MediaStop -> onBack
                    else -> null
                }
                if (action != null) {
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) action()
                    return@onPreviewKeyEvent true
                }
            }
            // ---- Existing key handling (unchanged, but skip for digit KeyUp already consumed above) ----
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                // Channel surfing: media prev/next always zap alongside the dedicated CH keys handled
                // above. D-pad Up/Down zap ONLY on live streams while the HUD is hidden (when it's visible,
                // Up/Down navigate the controls) —
                // this is the only way to change channels on remotes without CH keys (e.g. Fire TV).
                //
                // Direction is channel-number order, not list-position order: "up" (CH+, D-pad Up) is
                // always the NEXT channel — further down an ascending list, delta +1 — matching the
                // de facto TV convention (Live Channels, YouTube TV, Pluto TV). All of these keys move
                // the same way; there is deliberately no split between CH+ and D-pad Up. Wrapping is
                // intended: CH-/Down from the first channel lands on the last, and vice versa.
                canZap && e.key == Key.MediaNext -> { zap(1); true }
                canZap && e.key == Key.MediaPrevious -> { zap(-1); true }
                canZap && isLive && !controlsVisible && e.key == Key.DirectionUp -> { zap(1); true }
                canZap && isLive && !controlsVisible && e.key == Key.DirectionDown -> { zap(-1); true }
                // Left/Right with the controls hidden: reveal them on the timeline and take the first
                // step, so the jump is seen and holding the key goes on scrubbing the bar itself. Physical
                // keys, like the bars: left is back in time in every locale. A film always does this; a
                // live channel only with its archive and the setting on — otherwise the lists below.
                // Decided in the bottom bar's own order: a channel with an archive draws the live timeline
                // even while rewound (when the engine is playing a file and calls it not live), so that
                // is what the keys scrub; only without one does a film's seek bar apply.
                !controlsVisible && error == null && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight) &&
                    (if (onScrubLive != null) liveLeftRightRewinds else !isLive && duration > 0L) -> {
                    val back = e.key == Key.DirectionLeft
                    if (onScrubLive != null) {
                        onScrubLive(if (back) LIVE_SCRUB_STEP_SEC else -LIVE_SCRUB_STEP_SEC)
                    } else {
                        val step = player.seekStepMs.value
                        player.seekBy(if (back) -step else step)
                    }
                    focusSeekOnShow = true
                    controlsVisible = true
                    true
                }
                // The category list lives at logical Start; history lives at logical End.
                onOpenChannelList != null && !controlsVisible &&
                    e.key.horizontalDirection(layoutDirection) == HorizontalDirection.START -> { onOpenChannelList(); true }
                // List-first: Right jumps back to the channel watched before, as classic IPTV players do;
                // the history list moves into the options panel.
                listFirst && onPreviousChannel != null && !controlsVisible &&
                    e.key.horizontalDirection(layoutDirection) == HorizontalDirection.END -> { cancelDirectTune(); onPreviousChannel(); true }
                onOpenHistoryList != null && !controlsVisible &&
                    e.key.horizontalDirection(layoutDirection) == HorizontalDirection.END -> { onOpenHistoryList(); true }
                onToastOk != null && !controlsVisible &&
                    (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter) -> { onToastOk(); true }
                controlsVisible -> { wakeTick++; false }
                else -> false
            }
        },
    ) {
        if (!controlsVisible && !showNextCard && !showPanel) {
            Box(
                Modifier.fillMaxSize().focusRequester(catchFocus).focusable()
                    // Any other key brings the controls up, except in the list-first live player: there a key
                    // with nothing to do (▲▼ before the channel list is in, say) does nothing, rather than
                    // show the classic controls. They stay one row away in the options panel.
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key != Key.Back && !listFirst) { controlsVisible = true; true } else false
                    },
            )
        }

        // Stream technical info — drawn over everything (and kept up even when the controls auto-hide), so
        // you can read live bitrate/buffer while watching. Toggled from the bottom bar's info button.
        if (showInfo) {
            // Sits clear of the taller unified top strip (logo + guide) rather than under a title row.
            StreamInfoOverlay(player, modifier = Modifier.align(Alignment.TopEnd).padding(top = 112.dp, end = 20.dp))
        }

        // Top-left OSD stack: the channel card (briefly on a zap, or the freshly tuned channel) plus the
        // direct-tune card, which pushes down under it. Drawn outside the controls-visible block so both
        // zapping and digit entry stay visible with the HUD hidden. With the controls up the unified top
        // strip already names the channel, so only a direct tune — whose card names the channel the stream
        // is still switching to — draws here.
        val tuned = (tuneOsd as? TuneOsd.Tuned)?.info
        Column(
            modifier = Modifier.align(Alignment.TopStart)
                .padding(start = 28.dp, top = if (controlsVisible) 92.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isLive && (tuned != null || (showFlash && !controlsVisible && !listFirst))) {
                // A fresh tune drives the card from the lookup result, not player metadata: the Stalker and
                // mpv paths publish their metadata after an async resolve, which would show the old channel.
                if (tuned != null) {
                    ChannelOsdCard(title = tuned.name, subtitle = tuned.number?.let { stringResource(R.string.player_channel_number, it) }, logoUrl = tuned.logoUrl, guide = zapGuide)
                } else {
                    ChannelCard(player, guide = zapGuide)
                }
            }
            when (val osd = tuneOsd) {
                is TuneOsd.Entry -> ChannelNumberCard(osd.digits)
                is TuneOsd.Message -> ChannelNumberCard(osd.digits, error = osd.text)
                is TuneOsd.Tuned, null -> Unit
            }
        }

        // List-first: the channel change shows the info bar along the bottom instead of the corner card.
        if (listFirst && isLive && showPanel && zapInfo != null) {
            HudControlPanel(
                player, zapInfo(), remote, onOpenHistoryList, panelFocus, liveOptions,
                onClose = { showPanel = false }, onActivity = { panelTick++ },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else if (listFirst && isLive && showFlash && !controlsVisible && tuned == null && zapInfo != null) {
            ZapInfoBar(player, zapInfo(), modifier = Modifier.align(Alignment.BottomCenter))
        }
        val clockUp = listFirst && isLive && zapInfo != null && (showPanel || (showFlash && !controlsVisible && tuned == null))
        if (clockUp) PanelClock(Modifier.align(Alignment.TopEnd))

        // The REC badge, and it is deliberately OUTSIDE `if (controlsVisible)`.
        //
        // The shell hides the status pill during fullscreen playback, so without this a recording
        // running behind the picture would be completely invisible — which is the one thing that
        // must not happen. It is drawn whenever anything is recording, HUD up or down, and it is not
        // focusable and not interactive: stopping a recording is the Recordings screen's job, and a
        // stop button one stray D-pad press from the middle of a programme is not a kindness.
        RecordingBadge(modifier = Modifier.align(Alignment.TopEnd).padding(top = if (clockUp) 40.dp else 0.dp))

        if (controlsVisible) {
            // Scrims: a FLAT semi-transparent panel behind the controls, feathered to transparent only at
            // the inner edge. A pure gradient faded out exactly where the chips and the Now/Next text sit,
            // so those washed out on bright scenes; a hard-edged band would instead draw a visible seam
            // across the picture. The colour stops give the panel first, then the feather.
            Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(210.dp)
                .gradientWash(
                    vertical = true,
                    0.0f to hudShade(0.72f),
                    0.5f to hudShade(0.68f),
                    1.0f to Color.Transparent,
                ))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(260.dp)
                .gradientWash(
                    vertical = true,
                    0.0f to Color.Transparent,
                    0.45f to hudShade(0.68f),
                    1.0f to hudShade(0.78f),
                ))

            // The active engine (MPV/EXO) leads the mini chips so users can always tell which player is on.
            // One unified strip: back · logo · chips-over-channel-name · Now/Next guide. The channel name
            // used to be drawn twice (here and in a floating card below), with the guide stranded on the
            // right edge — that space belongs to the history list now.
            TopBar(
                player, isLive, listOfNotNull(engineChip) + streamChips.ifEmpty { listOfNotNull(videoRes) }, duration, onBack,
                modifier = Modifier.align(Alignment.TopStart),
                below = matchLine,
                trailing = if (error == null) liveEpgCard else null,
                // Hidden behind an error overlay along with the rest of the chrome: a clock ticking
                // over a failure message just draws the eye to the wrong thing.
                centre = if (error == null) {
                    { PlayerClock(watchingMs = watchingWallMs?.invoke()) }
                } else null,
            )

            // Hide the transport (play/seek/prev/next) and bottom bar while an error is up — the error
            // overlay owns the screen with its own Retry, so the play/rewind/forward must not show behind it.
            if (error == null) {
                CenterControls(player, nav, isPlaying, isLive, onRewindLive, onForwardLive, timeshiftOffset, playFocus, modifier = Modifier.align(Alignment.Center))

                val reportDuration = duration.takeIf { it > 0 }?.let { formatTime(it) }
                val reportSavedMessage = stringResource(R.string.player_report_saved)

                BottomBar(
                    seekFocus = seekFocus,
                    player = player, isLive = isLive, position = { position.value }, duration = duration,
                    volume = volume, audioCount = audioCount, subCount = subCount, zoomMode = zoomMode,
                    speedLabel = formatSpeed(speed),
                    onScrubLive = onScrubLive, timeshiftOffset = timeshiftOffset, onGoToLive = onGoToLive,
                    liveProgrammes = liveProgrammes,
                    liveGaps = liveGaps,
                    onOpenJumpBack = if (onJumpBack != null) { { dialog = HudDialog.JUMP_BACK } } else null,
                    onPreviousChannel = onPreviousChannel,
                    onOtherChannels = onOtherChannels, otherChannelsCount = otherChannelsCount,
                    compatMode = compatMode, onToggleCompatMode = toggleCompat,
                    vodOnExo = vodOnExo, onToggleVodEngine = toggleVod,
                    onInfo = { showInfo = !showInfo }, infoOn = showInfo,
                    onReport = {
                        val meta = player.currentMeta.value
                        // The readout is gathered on the player's own thread now, so the report is
                        // written from a coroutine. The confirmation still flashes immediately — the user
                        // pressed a button and must see it acknowledged.
                        // Read at the press, not per tick.
                        val reportPosition = tv.own.owntv.ui.components.formatTimestamp(reportContext.resources, position.value)
                        hudScope.launch {
                            val snapshot = buildString {
                                appendLine(player.streamInfo().joinToString("\n") { (k, v) -> "  $k: $v" })
                                appendLine("  position: $reportPosition${reportDuration?.let { " / $it" }.orEmpty()}")
                            }
                            PlaybackErrorLog.report(
                                context = reportContext,
                                engine = engineChip ?: "?",
                                live = isLive,
                                title = meta.title,
                                snapshot = snapshot,
                            )
                        }
                        engineMsg = reportSavedMessage
                        engineFlash++
                    },
                    favorite = favorite, onToggleFavorite = onToggleFavorite,
                    onOpenDialog = { dialog = it },
                    onMultiview = onMultiview, onRecordThis = onRecordThis, recordingThis = recordingThis, onBack = onBack,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }

        // Next-episode countdown card (VOD queue only) — surfaces the automatic advance with Play now /
        // Cancel. Shown independently of the main controls so it appears even after they auto-hide.
        if (showNextCard) {
            NextEpisodeCard(
                seconds = { ((msToAdvance() + 999L) / 1000L).toInt().coerceIn(0, 30) },
                title = nextUpTitle ?: "",
                playFocus = nextFocus,
                onPlayNow = { autoNextDismissed = true; player.next() },
                onCancel = { autoNextDismissed = true; player.cancelAutoNext() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 120.dp),
            )
        }

        // Engine-switch confirmation toast (bottom-center, semi-transparent) — shown briefly after the
        // user flips the engine via the HUD's MPV/EXO toggle.
        engineMsg?.let { msg ->
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    msg,
                    style = tv.own.owntv.ui.theme.stageText(25, 600),
                    color = hudInk,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(hudShade(0.55f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Status overlay (always shown).
        when {
            error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.player_playback_error), style = tv.own.owntv.ui.theme.stageText(40, 600), color = Color.White)
                Spacer(Modifier.height(8.dp))
                error?.let {
                    Text(it.displayText(), style = tv.own.owntv.ui.theme.stageText(25, 400), color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                }
                // Structured technical detail so a user can report the real cause without adb/logcat:
                // plain reason → media spec (codec • resolution • decoder) → raw engine/codec line.
                errorInfo?.let { info ->
                    info.reason?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it.displayText(), style = tv.own.owntv.ui.theme.stageText(25, 400), color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.8f))
                    }
                    info.spec?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it.displayText(), style = tv.own.owntv.ui.theme.stageText(22, 700), color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
                    }
                    info.raw?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.player_raw_error, it), style = tv.own.owntv.ui.theme.stageText(22, 400), color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.8f))
                    }
                }
                // While a recording is what took the picture away, this screen has TWO buttons
                // instead of a lone Retry, because Retry alone cannot work: the provider's only
                // stream is busy and trying again just fails again.
                //
                // The default protects the recording — a live programme is gone forever, a rewatch
                // is not — and the user can overrule it in one press. The parenthetical on the first
                // button is the whole point: nobody should stop a recording without being told.
                val recordingConflict = rememberRecordingConflict(error)
                Spacer(Modifier.height(18.dp))
                if (recordingConflict != null) {
                    Text(
                        stringResource(R.string.player_recording_conflict),
                        style = tv.own.owntv.ui.theme.stageText(25, 400),
                        color = Color.White.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        tv.own.owntv.ui.stage.StageButton(
                            stringResource(R.string.player_keep_watching_stop_recording),
                            onClick = { recordingConflict.stopAndRetry() },
                            modifier = Modifier.focusRequester(retryFocus),
                            icon = OwnTVIcon.PLAY,
                            tinted = true,
                        )
                        tv.own.owntv.ui.stage.StageButton(
                            stringResource(R.string.settings_close),
                            onClick = onBack,
                        )
                    }
                } else {
                    tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_retry), onClick = { player.retry() }, modifier = Modifier.focusRequester(retryFocus), icon = OwnTVIcon.PLAY, tinted = true)
                }
            }
            // A provider wait looks like loading, because that is what it is: the channel is queued behind
            // the panel's own countdown and the engine re-asks by itself. The line under the spinner says
            // why nothing is happening yet, so nobody reaches for Retry (or thinks the channel is dead).
            buffering -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                OwnTVSpinner(sizeDp = 56)
                val stalledSince = stalledSinceMs
                if (providerBackOff == null && stalledSince != null) {
                    val showNotice by produceState(false, stalledSince) {
                        delay(RECONNECT_NOTICE_AFTER_MS - (android.os.SystemClock.elapsedRealtime() - stalledSince))
                        value = true
                    }
                    if (showNotice) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.player_reconnecting),
                            style = tv.own.owntv.ui.theme.stageText(25, 400),
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.7f),
                        )
                    }
                }
                providerBackOff?.let { wait ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(
                            R.string.player_provider_retry_after,
                            wait.httpCode,
                            wait.message ?: stringResource(R.string.player_provider_busy),
                            wait.secondsLeft,
                        ),
                        style = tv.own.owntv.ui.theme.stageText(25, 400),
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                }
            }
        }
    }

    if (showOptions) {
        val meta by player.currentMeta.collectAsStateWithLifecycle()
        LiveOptionsPanel(
            title = meta.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.tvplayer_options_title),
            options = liveOptions,
            ignoreOkUntilReleased = optionsOpenedByOk,
            onDismiss = { showOptions = false },
        )
    }

    when (dialog) {
        // Track lists are SNAPSHOT once when the dialog opens (re-polled only while still empty —
        // heavy HDR/DTS streams report their tracks late). Reading player.xxxTracks() directly in
        // composition handed the dialog a fresh list on every HUD recomposition, endlessly rebuilding
        // the rows and losing/yanking D-pad focus.
        //
        // The re-poll is BOUNDED: a stream that genuinely has no such track (most radio channels have
        // no subtitles) would otherwise wake the CPU every 300 ms for as long as the menu stays open.
        // Tracks that arrive later than TRACK_POLL_TRIES × 300 ms have never been observed.
        HudDialog.AUDIO -> {
            var audioTracks by remember { mutableStateOf(player.audioTracks()) }
            LaunchedEffect(Unit) {
                repeat(TRACK_POLL_TRIES) {
                    if (audioTracks.isNotEmpty()) return@LaunchedEffect
                    delay(TRACK_POLL_MS)
                    audioTracks = player.audioTracks()
                }
            }
            TrackDialog(
                stringResource(R.string.player_audio_track), audioTracks,
                onSelect = { player.selectAudio(it.mpvId); dialog = HudDialog.NONE }, onOff = null,
                onDismiss = { dialog = HudDialog.NONE },
                // A/V-sync nudge wherever the engine can actually shift audio: mpv, VOD *and* live (a live
                // stream can arrive with the provider's own drift baked in), on mpv and ExoPlayer alike.
                audioDelayMs = if (player.audioDelayAvailable()) audioDelayMs else null,
                onAdjustAudioDelay = if (player.audioDelayAvailable()) ({ d -> player.adjustAudioDelay(d) }) else null,
                audioDelayRemembered = audioDelayRemembered,
                onToggleRememberAudioDelay = if (player.audioDelayAvailable()) ({ player.toggleRememberAudioDelay() }) else null,
            )
        }
        HudDialog.SUBS -> {
            var subTracks by remember { mutableStateOf(player.textTracks()) }
            LaunchedEffect(Unit) {
                repeat(TRACK_POLL_TRIES) {
                    if (subTracks.isNotEmpty()) return@LaunchedEffect
                    delay(TRACK_POLL_MS)
                    subTracks = player.textTracks()
                }
            }
            val subDelay by player.subDelayMs.collectAsStateWithLifecycle()
            TrackDialog(
                stringResource(R.string.player_subtitles), subTracks,
                onSelect = { player.selectSubtitle(it.mpvId); dialog = HudDialog.NONE },
                onOff = { player.disableSubtitles(); dialog = HudDialog.NONE },
                onDismiss = { dialog = HudDialog.NONE },
                onSearchSubtitles = onSearchSubtitles?.let { open -> { dialog = HudDialog.NONE; open() } },
                onSelectLocalSubtitle = onSelectLocalSubtitle?.let { open -> { dialog = HudDialog.NONE; open() } },
                // Subtitle timing: only when adjustment applies to the ACTIVE subtitle on the
                // current engine (any mpv text sub; external side-loads on ExoPlayer).
                subDelayMs = subDelay,
                onAdjustSubDelay = if (player.subtitleTimingAvailable()) player::adjustSubtitleDelay else null,
                onResetSubDelay = player::resetSubtitleDelay,
            )
        }
        HudDialog.SPEED -> SpeedDialog(current = speed, onSelect = { player.setSpeed(it); dialog = HudDialog.NONE }, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.ZOOM -> ZoomDialog(current = zoomMode, onSelect = { player.setZoomModeByUser(it); dialog = HudDialog.NONE }, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.QUALITY -> {
            // Snapshot as it opens, like the track lists above: a live list would rebuild the rows.
            val heights = remember { player.videoQualities.value }
            val pick by player.videoQualityPick.collectAsStateWithLifecycle()
            QualityDialog(heights, pick, onSelect = { player.selectVideoQuality(it); dialog = HudDialog.NONE }, onDismiss = { dialog = HudDialog.NONE })
        }
        HudDialog.VOLUME -> VolumeDialog(player, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.SLEEP_TIMER -> SleepTimerDialog(
            timer = org.koin.compose.koinInject(),
            programmeEndMs = remember { sleepProgrammeEndMs?.invoke() },
            onDismiss = { dialog = HudDialog.NONE },
        )
        // "Go back to…". The options are read here, as the list opens, so the clock times shown are
        // relative to the moment the user asked rather than to when the HUD was first composed.
        HudDialog.JUMP_BACK -> {
            val options = remember { jumpBackOptions?.invoke().orEmpty() }
            val windowSec = remember { jumpBackWindowSec?.invoke() ?: 0 }
            if (options.isEmpty()) {
                dialog = HudDialog.NONE
            } else {
                tv.own.owntv.features.live.CatchupJumpDialog(
                    title = stringResource(R.string.content_catchup_jump),
                    offsetsSec = options,
                    windowSec = windowSec,
                    onPick = { dialog = HudDialog.NONE; onJumpBack?.invoke(it) },
                    onDismiss = { dialog = HudDialog.NONE },
                )
            }
        }
        HudDialog.NONE -> Unit
    }
}

/** What the live player's classic remote reaches beyond the HUD itself; see [PlayerHud]'s `remote`. */
@androidx.compose.runtime.Immutable
class LiveRemoteActions(
    /** The guide (long Menu, Guide/Red/Green, the panel's first tile). */
    val onOpenGuide: (() -> Unit)?,
    /** Search (long Down). */
    val onOpenSearch: (() -> Unit)?,
    /** The recently watched channels for the control panel's tiles, read only while the panel is up. */
    val panelChannels: (@Composable () -> List<PanelTile>)?,
)

/** The info bar with the tile row under it: Guide and History first, then the channels watched last. */
@Composable
private fun HudControlPanel(
    player: PlaybackEngine,
    info: ZapInfo?,
    remote: LiveRemoteActions?,
    onOpenHistoryList: (() -> Unit)?,
    focus: FocusRequester,
    settings: List<LiveOption>,
    onClose: () -> Unit,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var settingsOpen by remember { mutableStateOf(false) }
    val settingsFocus = remember { FocusRequester() }
    LaunchedEffect(settingsOpen) {
        if (settingsOpen) { withFrameNanos { }; runCatching { settingsFocus.requestFocus() } }
    }
    val recent = remote?.panelChannels?.invoke().orEmpty()
    val guideLabel = stringResource(R.string.tvplayer_panel_guide)
    val historyLabel = stringResource(R.string.tvplayer_panel_history)
    val tiles = buildList {
        remote?.onOpenGuide?.let { add(PanelTile("guide", guideLabel, OwnTVIcon.EPG) { onClose(); it() }) }
        onOpenHistoryList?.let { add(PanelTile("history", historyLabel, OwnTVIcon.HISTORY) { onClose(); it() }) }
        recent.forEach { ch -> add(PanelTile(ch.key, ch.label, logoUrl = ch.logoUrl, caption = ch.caption) { onClose(); ch.onClick() }) }
    }
    val u = rememberScreenUnits()
    val guide = info?.guide
    // The panel opens on a moment; the seek line and minutes left are read from it, as in the zap bar.
    val nowMs = remember(guide) { System.currentTimeMillis() }
    // Proportions of the classic player at 1920×1080: the programme block from y 622, the seek line
    // at 790, tiles 236×172 from 820, the arrow at 1030 (Jonathan's photo, 27 Oct 2026).
    Column(modifier.fillMaxWidth().background(PanelWash).padding(top = u.dp(60))) {
        PlayerZapInfoBlock(player, info, nowMs)
        Spacer(Modifier.height(u.dp(8)))
        ProgrammeSeekLine(guide, nowMs)
        Column {
            if (tiles.isNotEmpty()) {
                ControlPanelRow(
                    tiles, focus, onActivity = onActivity,
                    // ▼ from the tiles opens the settings under them.
                    modifier = Modifier.onPreviewKeyEvent { e ->
                        if (e.key == Key.DirectionDown && settings.isNotEmpty()) {
                            if (e.type == KeyEventType.KeyDown) { settingsOpen = true; onActivity() }
                            true
                        } else false
                    },
                )
            }
            when {
                settings.isEmpty() -> Spacer(Modifier.height(u.dp(60)))
                settingsOpen -> SettingsPanelRow(
                    settings, settingsFocus, onActivity,
                    // ▲ back to the tiles; the settings fold away again.
                    modifier = Modifier.onPreviewKeyEvent { e ->
                        if (e.key == Key.DirectionUp && tiles.isNotEmpty()) {
                            if (e.type == KeyEventType.KeyDown) { settingsOpen = false; runCatching { focus.requestFocus() }; onActivity() }
                            true
                        } else false
                    },
                )
                else -> MoreBelowHint()
            }
        }
    }
}
