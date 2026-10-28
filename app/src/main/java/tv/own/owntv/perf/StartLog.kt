package tv.own.owntv.perf

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.profileinstaller.ProfileVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import tv.own.owntv.core.util.Perf

/**
 * The start-up timeline, readable on the TV itself (More > About > Start log). [stamp] is [Perf.stamp]
 * (tag OwnTVPerf, for the screenshot tour) plus an entry in a ring buffer the About page shows, so a
 * photo of the screen after a cold start says where the time went. Release builds keep it too.
 */
object StartLog {
    data class Entry(val label: String, val atMs: Long, val sinceLastMs: Long)

    /** What Android did with the baseline profile (see [readProfileStatus]). */
    enum class ProfileState { COMPILED, WAITING, NONE, ERROR }

    data class ProfileStatus(val state: ProfileState, val code: Int)

    /** Frames from the first composition to 10 s after the destination was ready. */
    data class Jank(val frames: Int, val janky: Int, val worstMs: Long)

    private const val MAX_ENTRIES = 64

    @Volatile private var t0 = SystemClock.elapsedRealtime()
    private val once = ConcurrentHashMap.newKeySet<String>()

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _profile = MutableStateFlow<ProfileStatus?>(null)
    val profile: StateFlow<ProfileStatus?> = _profile.asStateFlow()

    private val _jank = MutableStateFlow<Jank?>(null)
    val jank: StateFlow<Jank?> = _jank.asStateFlow()

    /** The zero point: the first line of [Application.onCreate]. */
    fun begin() {
        t0 = SystemClock.elapsedRealtime()
        Perf.begin()
    }

    fun stamp(label: String) {
        Perf.stamp(label)
        val at = SystemClock.elapsedRealtime() - t0
        _entries.update { old ->
            val since = old.lastOrNull()?.let { at - it.atMs } ?: at
            (old + Entry(label, at, since)).takeLast(MAX_ENTRIES)
        }
    }

    /** Only the first call per process records; the rest are free. For stamps on code that runs again and again. */
    fun stampOnce(label: String) {
        if (once.add(label)) stamp(label)
    }

    /** True for the first caller per [key] in this process. */
    fun firstTime(key: String): Boolean = once.add(key)

    /** Asks the platform whether the baseline profile is compiled in (off the main thread: it reads files). */
    suspend fun readProfileStatus() {
        val status = withContext(Dispatchers.IO) {
            runCatching {
                val s = ProfileVerifier.getCompilationStatusAsync().get()
                val state = when {
                    s.isCompiledWithProfile -> ProfileState.COMPILED
                    s.hasProfileEnqueuedForCompilation() -> ProfileState.WAITING
                    s.profileInstallResultCode >= 65536 -> ProfileState.ERROR
                    else -> ProfileState.NONE
                }
                ProfileStatus(state, s.profileInstallResultCode)
            }.getOrNull()
        } ?: ProfileStatus(ProfileState.ERROR, -1)
        _profile.value = status
        Perf.stamp("profile=${status.state.name.lowercase()}(${status.code})")
    }

    /**
     * Counts frames over [Window] until [finish]: the first composition up to 10 s after the destination was
     * ready. A frame is janky past 1.5 refresh intervals (the rule the tour's MayoJank report uses).
     */
    class JankWatch(private val window: Window) {
        private val frames = AtomicInteger()
        private val janky = AtomicInteger()
        private val worstNs = AtomicLong()
        private val thread = HandlerThread("start-jank").apply { start() }
        private val handler = Handler(thread.looper)
        private val listener: Window.OnFrameMetricsAvailableListener

        init {
            val refreshHz = window.decorView.display?.refreshRate?.takeIf { it > 1f } ?: 60f
            val jankNs = (1_000_000_000L / refreshHz * 1.5f).toLong()
            listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
                val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                frames.incrementAndGet()
                if (total > jankNs) janky.incrementAndGet()
                worstNs.accumulateAndGet(total) { a, b -> maxOf(a, b) }
            }
            window.addOnFrameMetricsAvailableListener(listener, handler)
        }

        fun finish() {
            runCatching { window.removeOnFrameMetricsAvailableListener(listener) }
            // Frames still queued on the thread land before this runs.
            handler.post {
                val result = Jank(frames.get(), janky.get(), worstNs.get() / 1_000_000)
                _jank.value = result
                Perf.stamp("jank frames=${result.frames} janky=${result.janky} worstMs=${result.worstMs}")
                thread.quitSafely()
            }
        }
    }
}
