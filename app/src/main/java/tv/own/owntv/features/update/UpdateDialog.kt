package tv.own.owntv.features.update

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.compose.koinInject
import tv.own.owntv.R
import androidx.compose.ui.unit.em
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.core.update.UpdateManager
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.LandmarkLoader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * The in-app update dialog (used both from Settings → Check for updates and the automatic prompt).
 * Binds to [UpdateManager]'s state machine: checking → up-to-date / available → downloading.
 * [checkOnOpen] makes opening the dialog trigger a fresh check (the Settings path).
 */
@Composable
fun UpdateDialog(onDismiss: () -> Unit, checkOnOpen: Boolean = false) {
    val manager: UpdateManager = koinInject()
    val state by manager.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val context = LocalContext.current
    // Mayo TV kommt per Sideload auf die Box und darf anfangs keine Apps installieren. Statt erst
    // 50 MB zu laden und dann an der Sperre zu scheitern, öffnet „Jetzt aktualisieren“ zuerst die
    // Freigabe; kommt man mit erteilter Erlaubnis zurück, startet der Download von selbst.
    var waitingForInstallPermission by remember { mutableStateOf(false) }
    val startUpdate: () -> Unit = {
        if (canInstallPackages(context)) manager.downloadAndInstall()
        else if (openInstallPermissionSettings(context)) waitingForInstallPermission = true
        else manager.downloadAndInstall() // keine Einstellungsseite: der Installer fragt dann selbst
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (waitingForInstallPermission && canInstallPackages(context)) {
            waitingForInstallPermission = false
            manager.downloadAndInstall()
        }
    }

    LaunchedEffect(Unit) {
        if (checkOnOpen) manager.check()
    }
    LaunchedEffect(state) {
        if (state is UpdateManager.State.Available || state is UpdateManager.State.UpToDate || state is UpdateManager.State.Failed) {
            runCatching { focus.requestFocus() }
        }
    }
    val busy = state is UpdateManager.State.Idle || state is UpdateManager.State.Checking || state is UpdateManager.State.Downloading
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.update_title),
        width = 900.mpx,
        scroll = false,
        buttons = if (busy) null else ({
            when (val s = state) {
                UpdateManager.State.UpToDate ->
                    StageButton(stringResource(R.string.settings_close), onClick = onDismiss, height = 56.mpx, textSize = 19, tinted = true, modifier = Modifier.focusRequester(focus))
                is UpdateManager.State.Available -> {
                    StageButton(stringResource(R.string.update_later), onClick = onDismiss, height = 56.mpx, textSize = 19)
                    StageButton(stringResource(R.string.update_now), onClick = startUpdate, icon = OwnTVIcon.DOWNLOADS, height = 56.mpx, textSize = 19, tinted = true, modifier = Modifier.focusRequester(focus))
                }
                is UpdateManager.State.Failed -> {
                    StageButton(stringResource(R.string.settings_close), onClick = onDismiss, height = 56.mpx, textSize = 19)
                    StageButton(stringResource(R.string.update_try_again), onClick = { manager.retry() }, height = 56.mpx, textSize = 19, tinted = true, modifier = Modifier.focusRequester(focus))
                }
                else -> Unit
            }
        }),
    ) {
        val body = stageText(18, 400).copy(lineHeight = (18 * 1.45f).mpxSp)
        when (val s = state) {
            UpdateManager.State.Idle, UpdateManager.State.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                LandmarkLoader(sizeDp = 28)
                Spacer(Modifier.width(16.mpx))
                Text(stringResource(R.string.update_checking), style = body, color = StageColors.Muted)
            }
            UpdateManager.State.UpToDate -> Text(stringResource(R.string.update_latest, manager.currentVersion), style = body, color = StageColors.Muted)
            is UpdateManager.State.Available -> {
                Text(stringResource(R.string.update_available_version, s.info.version, manager.currentVersion), style = stageText(19, 600), color = StageColors.Text)
                if (waitingForInstallPermission) {
                    Text(stringResource(R.string.tvplayer_update_allow_install), style = body, color = StageColors.Muted, modifier = Modifier.padding(top = 10.mpx))
                }
                if (s.info.notes.isNotBlank()) {
                    Text(
                        stringResource(R.string.update_whats_new).uppercase(),
                        style = stageText(14, 800, 0.12.em), color = StageColors.Dim,
                        modifier = Modifier.padding(top = 22.mpx, bottom = 10.mpx),
                    )
                    Text(
                        renderReleaseNotes(s.info.notes, headingColor = StageColors.Text),
                        style = stageText(17, 400).copy(lineHeight = (17 * 1.5f).mpxSp),
                        color = StageColors.Muted,
                        // The notes scroll; the buttons under them stay on screen.
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    )
                }
            }
            is UpdateManager.State.Downloading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LandmarkLoader(sizeDp = 28)
                    Spacer(Modifier.width(16.mpx))
                    Text(stringResource(R.string.update_downloading, s.percent), style = body, color = StageColors.Text)
                }
                Text(stringResource(R.string.update_installer), style = body, color = StageColors.Muted, modifier = Modifier.padding(top = 10.mpx))
            }
            is UpdateManager.State.Failed -> Text(updateFailureText(s.failure), style = body, color = StageColors.Muted)
        }
    }
}

/** Ob Mayo TV Pakete installieren darf (ab Android 8 eine Freigabe pro App). */
private fun canInstallPackages(context: android.content.Context): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

/** Öffnet „Unbekannte Apps installieren“ für Mayo TV; false, wenn die Box diese Seite nicht hat. */
private fun openInstallPermissionSettings(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return false
    val intent = android.content.Intent(
        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        android.net.Uri.fromParts("package", context.packageName, null),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}

/**
 * Renders the minimal release notes (from CHANGELOG_APP.md, via the GitHub release body) for the update
 * dialog. Lightweight Markdown only — enough for our bullet-only format: `### ` section headers become
 * bold heading lines, `- ` bullets become "• ", and inline `**bold**` spans render bold. Everything else
 * is shown as-is. Not a full Markdown parser.
 */
private fun renderReleaseNotes(notes: String, headingColor: Color): AnnotatedString = buildAnnotatedString {
    val lines = notes.replace("\r\n", "\n").trim().split("\n")
    lines.forEachIndexed { index, raw ->
        if (index > 0) append("\n")
        val line = raw.trimEnd()
        when {
            line.startsWith("### ") ->
                withStyle(SpanStyle(color = headingColor, fontWeight = FontWeight.Bold)) {
                    appendInline(line.removePrefix("### ").trim())
                }
            line.startsWith("## ") ->
                withStyle(SpanStyle(color = headingColor, fontWeight = FontWeight.Bold)) {
                    appendInline(line.removePrefix("## ").trim())
                }
            line.startsWith("- ") -> { append("•  "); appendInline(line.removePrefix("- ")) }
            line.startsWith("* ") -> { append("•  "); appendInline(line.removePrefix("* ")) }
            else -> appendInline(line)
        }
    }
}

/** Appends [text], turning `**bold**` spans into actual bold runs (leaves other characters untouched). */
private fun AnnotatedString.Builder.appendInline(text: String) {
    var i = 0
    while (i < text.length) {
        val start = text.indexOf("**", i)
        if (start < 0) { append(text.substring(i)); break }
        val end = text.indexOf("**", start + 2)
        if (end < 0) { append(text.substring(i)); break }
        append(text.substring(i, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(start + 2, end)) }
        i = end + 2
    }
}
