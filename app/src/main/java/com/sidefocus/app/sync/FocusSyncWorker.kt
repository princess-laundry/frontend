package com.sidefocus.app.sync

import android.content.Context
import androidx.work.*
import com.sidefocus.app.BuildConfig
import com.sidefocus.app.FocusApplication
import com.sidefocus.core.upload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit

const val SYNC_WORK = "sidefocus-session-sync"

class FocusSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FocusApplication
        return app.syncMutex.withLock {
            val endpoint = app.preferences.state.value.endpoint
            if (endpoint.isBlank()) return@withLock Result.success()
            val client = SyncClient(endpoint, app.preferences.deviceId, TokenVault(applicationContext))
            try {
                app.preferences.saveThresholds(client.config())
                for (row in app.store.dao.pending()) {
                    if (isStopped) return@withLock Result.retry()
                    val session = row.session()
                    if (session.demo) { app.store.dao.markSync(row.id, "DEMO"); continue }
                    try {
                        client.upload(session.upload(BuildConfig.VERSION_NAME))
                        app.store.dao.markSync(row.id, "SYNCED")
                    } catch (exception: ApiFailure) {
                        if (exception.retryable) return@withLock Result.retry()
                        app.store.dao.markSync(row.id, "ERROR", "HTTP ${exception.status}")
                    }
                }
                Result.success()
            } catch (exception: CancellationException) { throw exception }
            catch (exception: ApiFailure) {
                if (exception.retryable) Result.retry() else Result.failure(workDataOf("error" to "HTTP ${exception.status}"))
            } catch (_: IOException) { Result.retry() }
            catch (_: Exception) { Result.failure(workDataOf("error" to "설정 또는 저장소를 확인해 주세요")) }
        }
    }
}

fun scheduleSync(context: Context) {
    val app = context.applicationContext as FocusApplication
    if (app.preferences.state.value.endpoint.isBlank()) return
    val request = OneTimeWorkRequestBuilder<FocusSyncWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
    // A finish event arriving during an upload gets its own subsequent pass.
    WorkManager.getInstance(context).enqueueUniqueWork(SYNC_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
}
