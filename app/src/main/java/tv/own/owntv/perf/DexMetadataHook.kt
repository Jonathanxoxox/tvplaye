package tv.own.owntv.perf

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.update.UpdateManager

/**
 * Hands the pre-compiled start profile to Android while an update installs.
 *
 * Without it every update leaves the profile "waiting for compilation" until the nightly job runs, so the
 * first days after each update run slower. Core's updater (not changeable here) opens an install session
 * with a single entry "owntv-update"; Android compiles at install time when a dex-metadata entry of the
 * same name ("owntv-update.dm") sits next to it. CI publishes that .dm beside the APK; this class fetches
 * it as soon as an update is announced and adds it to the session the moment Core creates it.
 *
 * Everything is a best effort: no .dm, a header the device does not read, or a session already committed
 * simply leaves the install as it was. If an install that carried the .dm fails, the hook switches itself
 * off for good so it can never be the reason an update does not go through.
 */
object DexMetadataHook {

    enum class State { OFF, IDLE, FETCHING, READY, ADDED, SKIPPED, FAILED, DISABLED }

    private const val PREFS = "dexmeta"
    private const val KEY_DISABLED = "disabled"
    private const val KEY_LAST = "last"
    private const val SESSION_ENTRY = "owntv-update"
    private const val MAX_BYTES = 2L * 1024 * 1024

    private val mutableState = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = mutableState

    @Volatile private var prepared: File? = null
    @Volatile private var addedTo = -1

    /** Starts watching the updater; call once, after the start window. */
    fun start(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DISABLED, false)) {
            mutableState.value = State.DISABLED
            return
        }
        val installer = app.packageManager.packageInstaller
        installer.registerSessionCallback(object : PackageInstaller.SessionCallback() {
            override fun onCreated(sessionId: Int) {
                // Core's session never names the package (appPackageName stays null until the APK is read), so
                // ownership is "one of this app's own sessions".
                val mine = runCatching { installer.mySessions.any { it.sessionId == sessionId } }.getOrDefault(false)
                if (!mine) return
                addTo(app, installer, sessionId)
            }

            override fun onFinished(sessionId: Int, success: Boolean) {
                if (sessionId != addedTo) return
                if (!success) {
                    prefs.edit().putBoolean(KEY_DISABLED, true).apply()
                    record(app, State.FAILED, "Update mit Profil gescheitert")
                    StartLog.stamp("dexmeta-install-failed")
                }
            }

            override fun onBadgingChanged(sessionId: Int) = Unit
            override fun onActiveChanged(sessionId: Int, active: Boolean) = Unit
            override fun onProgressChanged(sessionId: Int, progress: Float) = Unit
        }, Handler(Looper.getMainLooper()))

        val manager = runCatching { GlobalContext.get().get<UpdateManager>() }.getOrNull() ?: return
        scope.launch {
            manager.state.collect { s ->
                if (s is UpdateManager.State.Available) prefetch(app, s.info.apkUrl)
            }
        }
    }

    private fun prefetch(app: Context, apkUrl: String) {
        if (!apkUrl.endsWith(".apk") || prepared != null) return
        mutableState.value = State.FETCHING
        val target = File(app.cacheDir, "update.dm")
        var why = ""
        val ok = runCatching {
            val request = Request.Builder().url(apkUrl.removeSuffix(".apk") + ".dm").header("User-Agent", "OwnTV").build()
            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) { why = "Download ${response.code}"; return@use false }
                val body = response.body ?: run { why = "leere Antwort"; return@use false }
                if (body.contentLength() !in 1..MAX_BYTES) { why = "Größe ${body.contentLength()}"; return@use false }
                target.outputStream().use { out -> body.byteStream().copyTo(out) }
                valid(target).also { if (!it) why = "Profilformat passt nicht (${header(target)})" }
            }
        }.getOrElse { why = it.javaClass.simpleName; false }
        if (ok) {
            prepared = target
            record(app, State.READY, "Datei geladen")
        } else {
            target.delete()
            record(app, State.SKIPPED, why)
        }
        StartLog.stamp(if (ok) "dexmeta-ready" else "dexmeta-skipped")
    }

    /** First 8 bytes of primary.prof inside the .dm, or null when it is no zip or has none. */
    private fun headerBytes(file: File): ByteArray? = runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("primary.prof") ?: return@use null
            val header = ByteArray(8)
            val read = zip.getInputStream(entry).use { input ->
                var n = 0
                while (n < 8) { val r = input.read(header, n, 8 - n); if (r < 0) break; n += r }
                n
            }
            if (read == 8) header else null
        }
    }.getOrNull()

    /** The header as text for the status line, e.g. "pro 015". */
    private fun header(file: File): String =
        headerBytes(file)?.let { String(it, Charsets.ISO_8859_1).replace('\u0000', ' ').trim() } ?: "kein Profil"

    /** A real zip with a profile whose header version is the one this Android reads. */
    private fun valid(file: File): Boolean {
        val h = headerBytes(file) ?: return false
        return String(h, 0, 3, Charsets.ISO_8859_1) == "pro" && String(h, 4, 3, Charsets.ISO_8859_1) == profileVersion()
    }

    /** The profile format by Android version (ART's ProfileCompilationInfo): 015 from Android 12, 010 on 9–11. */
    private fun profileVersion(): String = if (Build.VERSION.SDK_INT >= 31) "015" else if (Build.VERSION.SDK_INT >= 28) "010" else "009"

    private fun addTo(app: Context, installer: PackageInstaller, sessionId: Int) {
        val dm = prepared
        if (dm == null) {
            record(app, State.SKIPPED, "Update ohne Datei gestartet (${mutableState.value.name})")
            return
        }
        var why = ""
        val added = runCatching {
            installer.openSession(sessionId).use { session ->
                session.openWrite("$SESSION_ENTRY.dm", 0, dm.length()).use { out ->
                    dm.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
            }
        }.onFailure { why = it.javaClass.simpleName + ": " + (it.message ?: "").take(60) }.isSuccess
        if (added) addedTo = sessionId
        record(app, if (added) State.ADDED else State.SKIPPED, if (added) "in die Installation gelegt" else why)
        StartLog.stamp(if (added) "dexmeta-added" else "dexmeta-add-failed")
    }

    /** Sets the state and keeps the outcome across the restart the update causes. */
    private fun record(app: Context, state: State, why: String) {
        mutableState.value = state
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST, "${state.name} – $why").apply()
    }

    /** The outcome of the last update attempt, for the Start-Protokoll after the restart. */
    fun lastResult(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null)
}
