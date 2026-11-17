package com.metrolist.music.playback

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.content.getSystemService
import com.metrolist.music.App
import com.metrolist.music.utils.DownloadNetworkPolicy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider

internal object OfflineDataSyncScheduler {
    const val JOB_ID = 138230

    fun configure(context: Context, enabled: Boolean) {
        val scheduler = context.getSystemService<JobScheduler>() ?: return
        if (!enabled) scheduler.cancel(JOB_ID)
        else if (scheduler.getPendingJob(JOB_ID) == null) {
            if (scheduler.schedule(request(context)) != JobScheduler.RESULT_SUCCESS) {
                Timber.w("Could not schedule offline data completion")
            }
        }
    }

    internal fun request(context: Context): JobInfo {
        val builder = JobInfo.Builder(JOB_ID, ComponentName(context, OfflineDataSyncService::class.java))
            .setPersisted(true)
            .setPeriodic(TimeUnit.MINUTES.toMillis(15))
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .setBackoffCriteria(TimeUnit.MINUTES.toMillis(30), JobInfo.BACKOFF_POLICY_EXPONENTIAL)
        if (Build.VERSION.SDK_INT >= 28) {
            builder.setRequiredNetwork(NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED).build())
        } else builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
        return builder.build()
    }
}

@AndroidEntryPoint
class OfflineDataSyncService : JobService() {
    @Inject lateinit var downloads: Provider<DownloadUtil>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null
    private var activeParameters: JobParameters? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work?.cancel()
        activeParameters = params
        work = scope.launch {
            val completed = try {
                withTimeoutOrNull(TimeUnit.MINUTES.toMillis(8)) {
                    (application as App).settingsReady.await()
                    withContext(Dispatchers.IO) {
                        if (!DownloadNetworkPolicy.get(applicationContext).maySyncOfflineData()) return@withContext false
                        downloads.get().syncOfflineDataOnWifi()
                    }
                } ?: false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Timber.w(error, "Offline data completion interrupted"); false }
            if (activeParameters === params) {
                activeParameters = null
                work = null
                jobFinished(params, !completed)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        if (activeParameters === params) {
            activeParameters = null
            work?.cancel()
            work = null
        }
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
