package tv.own.owntv.ui

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.Window

/**
 * Janky frames per screen, for the screenshot tour's perf report (builds with -Pmayo.perfLog=true only).
 * Every frame's total duration comes from the platform's FrameMetrics (what JankStats reads on API 24+),
 * is counted against the screen the shell last named in [screen], and every 10 s one summary line per
 * screen goes to logcat: `adb logcat -s MayoJank`. A frame is janky past 1.5 refresh intervals.
 */
object FrameJankLog {
    private const val TAG = "MayoJank"
    private const val REPORT_EVERY_NS = 10_000_000_000L

    /** The screen frames are counted against, set by the shell (section and player mode). */
    @Volatile var screen: String = "start"

    private class Count {
        var frames = 0
        var janky = 0
        var worstNs = 0L
    }

    private val counts = LinkedHashMap<String, Count>()
    private var lastReportNs = 0L

    fun attach(window: Window) {
        val thread = HandlerThread("frame-jank").apply { start() }
        val refreshHz = window.decorView.display?.refreshRate?.takeIf { it > 1f } ?: 60f
        val jankNs = (1_000_000_000L / refreshHz * 1.5f).toLong()
        window.addOnFrameMetricsAvailableListener({ _, metrics, _ ->
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@addOnFrameMetricsAvailableListener
            val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            val count = counts.getOrPut(screen) { Count() }
            count.frames++
            if (total > jankNs) count.janky++
            if (total > count.worstNs) count.worstNs = total
            val now = System.nanoTime()
            if (now - lastReportNs >= REPORT_EVERY_NS) {
                lastReportNs = now
                for ((name, c) in counts) {
                    if (c.frames == 0) continue
                    Log.i(TAG, "screen=$name frames=${c.frames} jank=${c.janky} worstMs=${c.worstNs / 1_000_000}")
                    c.frames = 0; c.janky = 0; c.worstNs = 0L
                }
            }
        }, Handler(thread.looper))
    }
}
