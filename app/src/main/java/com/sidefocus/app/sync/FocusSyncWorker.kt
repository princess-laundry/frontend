package com.sidefocus.app.sync

import android.content.Context
import androidx.work.*
import com.sidefocus.app.BuildConfig
import com.sidefocus.app.FocusApplication
import com.sidefocus.core.normalizeEndpoint
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
            val storedEndpoint = app.preferences.state.value.endpoint
            if (storedEndpoint.isBlank()) return@withLock Result.success()
            val endpoint = runCatching { normalizeEndpoint(storedEndpoint, allowUsbLoopback = BuildConfig.DEBUG) }
                .getOrElse { return@withLock Result.failure(workDataOf("error" to "서버 주소를 확인해 주세요")) }
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

fun scheduleSync(context: Context, replacePending: Boolean = false) {
    val app = context.applicationContext as FocusApplication
    val endpoint = runCatching { normalizeEndpoint(app.preferences.state.value.endpoint, allowUsbLoopback = BuildConfig.DEBUG) }
        .getOrElse { return }
    // ADB reverse can reach the PC even when Android has no active network.
    val requiredNetwork = if (endpoint.startsWith("http://")) NetworkType.NOT_REQUIRED else NetworkType.CONNECTED
    val request = OneTimeWorkRequestBuilder<FocusSyncWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(requiredNetwork).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
    // Reconnecting replaces work scheduled for the previous connection; a completed session queues another pass.
    val policy = if (replacePending) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
    WorkManager.getInstance(context).enqueueUniqueWork(SYNC_WORK, policy, request)
}
