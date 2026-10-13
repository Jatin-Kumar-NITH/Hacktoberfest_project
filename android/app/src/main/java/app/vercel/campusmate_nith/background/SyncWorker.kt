package app.vercel.campusmate_nith.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.vercel.campusmate_nith.CampusMateApplication
import app.vercel.campusmate_nith.data.ApiException
import java.util.concurrent.TimeUnit

/** Checks for new notices, assignments and exams about once an hour, even when the app is closed. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CampusMateApplication
        val store = app.store
        if (store.campusCache == null) return Result.success()   // signed out
        return try {
            syncCampus(app)
            Push.sync(app)   // keeps push topics in step with your channels
            Result.success()
        } catch (e: ApiException) {
            if (e.status == 401 || e.status == 403) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.failure()   // unreadable data: try again on the next scheduled run
        }
    }

    companion object {
        /** Fetches the campus data and notifies about anything new. Also run when a push arrives. */
        suspend fun syncCampus(app: CampusMateApplication) {
            val store = app.store
            val r = app.api.campus()
            store.campusCache = r
            store.lastSync = System.currentTimeMillis()
            val news = Updates.takeNew(store, r.campus)
            if (store.notifyNew) Updates.notify(app, news)
            ClassReminders.schedule(app, r.campus)
            TodayWidget.refresh(app)
        }


        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("campus-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
