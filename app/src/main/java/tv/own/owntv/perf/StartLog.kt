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

    /** Frames per area ([area]: section and player) since the start, so a photo after scrolling says where it stutters. */
    data class AreaJank(val area: String, val frames: Int, val janky: Int, val worstMs: Long)

    private const val MAX_ENTRIES = 64

    @Volatile private var t0 = SystemClock.elapsedRealtime()
    private val once = ConcurrentHashMap.newKeySet<String>()

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _profile = MutableStateFlow<ProfileStatus?>(null)
    val profile: StateFlow<ProfileStatus?> = _profile.asStateFlow()

    private val _jank = MutableStateFlow<Jank?>(null)
    val jank: StateFlow<Jank?> = _jank.asStateFlow()

    private val _areas = MutableStateFlow<List<AreaJank>>(emptyList())
    val areas: StateFlow<List<AreaJank>> = _areas.asStateFlow()

    /** Where the frames are counted now, set by the shell (section and player mode). */
    @Volatile var area: String = "start"

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
     * Counts frames over [Window]: the first composition up to 10 s after the destination was ready for the
     * start line ([finish]), and per [area] for as long as the Activity lives. A frame is janky past 1.5 refresh
     * intervals (the rule the tour's MayoJank report uses). The first few very slow frames are stamped into the
     * timeline as they happen, so the photo shows what they sat next to.
     */
    class JankWatch(private val window: Window) {
        private val frames = AtomicInteger()
        private val janky = AtomicInteger()
        private val worstNs = AtomicLong()
        @Volatile private var startWindow = true
        private val thread = HandlerThread("start-jank").apply { start() }
        private val handler = Handler(thread.looper)
        private val listener: Window.OnFrameMetricsAvailableListener

        // Touched on [thread] only.
        private class Count(var frames: Int = 0, var janky: Int = 0, var worstNs: Long = 0L)
        private val perArea = LinkedHashMap<String, Count>()
        private var slowStamped = 0
        private var publishedAtNs = 0L

        init {
            val refreshHz = window.decorView.display?.refreshRate?.takeIf { it > 1f } ?: 60f
            val jankNs = (1_000_000_000L / refreshHz * 1.5f).toLong()
            listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
                val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                val isJanky = total > jankNs
                if (startWindow) {
                    frames.incrementAndGet()
                    if (isJanky) janky.incrementAndGet()
                    worstNs.accumulateAndGet(total) { a, b -> maxOf(a, b) }
                }
                val name = area
                val count = perArea.getOrPut(name) { Count() }
                count.frames++
                if (isJanky) count.janky++
                if (total > count.worstNs) count.worstNs = total
                if (total >= SLOW_FRAME_NS && slowStamped < MAX_SLOW_STAMPS) {
                    slowStamped++
                    stamp("slow-frame ${total / 1_000_000} ms $name")
                }
                val now = System.nanoTime()
                if (now - publishedAtNs >= PUBLISH_EVERY_NS) {
                    publishedAtNs = now
                    _areas.value = perArea.map { (a, c) -> AreaJank(a, c.frames, c.janky, c.worstNs / 1_000_000) }
                }
            }
            window.addOnFrameMetricsAvailableListener(listener, handler)
        }

        /** The start window is over: publish its line. Counting per area goes on. */
        fun finish() {
            startWindow = false
            // Frames still queued on the thread land before this runs.
            handler.post {
                val result = Jank(frames.get(), janky.get(), worstNs.get() / 1_000_000)
                _jank.value = result
                Perf.stamp("jank frames=${result.frames} janky=${result.janky} worstMs=${result.worstMs}")
            }
        }

        /** The Activity is going: stop listening to its window. */
        fun release() {
            runCatching { window.removeOnFrameMetricsAvailableListener(listener) }
            handler.post { thread.quitSafely() }
        }
    }

    /** At least this long, a frame is stamped into the timeline (a fifth of a second: plainly visible). */
    private const val SLOW_FRAME_NS = 200_000_000L
    private const val MAX_SLOW_STAMPS = 8
    private const val PUBLISH_EVERY_NS = 1_000_000_000L
}
