package tv.own.owntv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Keeps the launcher rows current: every 15 minutes (the shortest period Android allows), after a
 * reboot, when the launcher asks for the app's rows, and whenever the app starts or a favourite changes.
 * Reads only the local database and guide, so it costs no playlist download.
 */
class LauncherRowsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        LauncherRows(applicationContext).refresh()
        Result.success()
    } catch (e: Exception) {
        Log.w(TAG, "launcher rows not refreshed", e)
        if (runAttemptCount < 2) Result.retry() else Result.failure()
    }

    companion object {
        private const val TAG = "LauncherRows"
        private const val PERIODIC = "tvplayer-launcher-rows"
        private const val NOW = "tvplayer-launcher-rows-now"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LauncherRowsWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** A refresh soon; calls in quick succession collapse into the last one. */
        fun refreshSoon(context: Context, delaySeconds: Long = 5) {
            val request = OneTimeWorkRequestBuilder<LauncherRowsWorker>().setInitialDelay(delaySeconds, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** Boot and the launcher's INITIALIZE_PROGRAMS: put the rows up without the app having been opened. */
class LauncherRowsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LauncherRowsWorker.schedule(context)
        LauncherRowsWorker.refreshSoon(context, delaySeconds = 0)
    }
}
