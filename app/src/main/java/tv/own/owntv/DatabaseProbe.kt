package tv.own.owntv

import androidx.room.useWriterConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.koin.core.context.GlobalContext

/**
 * The startup database check (see MainActivity.probeDatabase), begun in OwnTVApp.onCreate right after
 * Koin, so opening the database and running its migrations overlaps the Activity's own start instead of
 * holding the main thread afterwards. Retry on the recovery screen asks again and gets a fresh check.
 */
internal object DatabaseProbe {
    private class Run {
        val done = CountDownLatch(1)

        @Volatile var error: String? = null
    }

    @Volatile private var early: Run? = null

    fun start() {
        early = launch()
    }

    /**
     * The failure message, or null when the database opened — or is still opening after [timeoutMs],
     * which counts as healthy so a slow box never sits on a blank window. The early run is used once.
     */
    fun await(timeoutMs: Long): String? {
        val run = early?.also { early = null } ?: launch()
        run.done.await(timeoutMs, TimeUnit.MILLISECONDS)
        return run.error
    }

    private fun launch(): Run {
        val run = Run()
        Thread({
            // Core configures a SQLiteDriver, and `openHelper` throws outright once one is set. Opening a
            // connection is what forces Room to run the migration chain now rather than in the first query.
            runCatching {
                kotlinx.coroutines.runBlocking {
                    GlobalContext.get().get<tv.own.owntv.core.database.OwnTVDatabase>().useWriterConnection { }
                }
            }.onFailure { run.error = it.message ?: it.javaClass.simpleName }
            run.done.countDown()
        }, "db-probe").start()
        return run
    }
}
