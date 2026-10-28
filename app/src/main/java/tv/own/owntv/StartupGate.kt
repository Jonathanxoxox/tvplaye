package tv.own.owntv

import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.own.owntv.perf.StartLog

/**
 * When the start is "through". Whatever the viewer cannot see in the first seconds (launcher rows, the
 * auto-refresh check, the update check, the sports board) waits on [awaitSettled] instead of competing with
 * the decoder and the first frames for the one database connection and the eMMC. Nothing is dropped, only
 * moved later. Both gates are process-wide and fire once: a second shell composition (a language switch)
 * never waits again.
 *
 * [firstFrame]: the player showed its first picture; or the shell stood for a second without starting a
 * player (start page); or, at the latest, 4 s after the destination was ready.
 * [settled]: 2 s after [firstFrame], at the latest 8 s after the process started (so a background
 * process that is never shown still gets to its work).
 */
object StartupGate {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val firstFrame = CompletableDeferred<Unit>()
    private val settled = CompletableDeferred<Unit>()

    @Volatile private var destinationTimerStarted = false

    init {
        scope.launch {
            val deadline = Process.getStartElapsedRealtime() + SETTLED_DEADLINE_MS
            delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            settle()
        }
    }

    suspend fun awaitFirstFrame() = firstFrame.await()

    suspend fun awaitSettled() = settled.await()

    val isSettled: Boolean get() = settled.isCompleted

    /** The splash hand-off: from here the first frame may take 4 s at most. */
    fun onDestinationReady() {
        if (destinationTimerStarted) return
        destinationTimerStarted = true
        scope.launch {
            delay(FIRST_FRAME_FALLBACK_MS)
            markFirstFrame()
        }
    }

    fun markFirstFrame() {
        if (!firstFrame.complete(Unit)) return
        StartLog.stamp("startup-first-frame")
        scope.launch {
            delay(SETTLE_AFTER_FIRST_FRAME_MS)
            settle()
        }
    }

    private fun settle() {
        if (settled.complete(Unit)) StartLog.stamp("startup-settled")
    }

    private const val FIRST_FRAME_FALLBACK_MS = 4_000L
    private const val SETTLE_AFTER_FIRST_FRAME_MS = 2_000L
    private const val SETTLED_DEADLINE_MS = 8_000L
}
