package com.tannmenghong.tbchat.download

import android.content.Context
import androidx.work.*
import com.tannmenghong.tbchat.domain.CatalogModel
import java.io.File
import java.util.concurrent.TimeUnit

data class DownloadStatus(val phase: Phase, val progress: Int = 0, val message: String = "") {
    enum class Phase { NOT_INSTALLED, QUEUED, DOWNLOADING, INSTALLED, FAILED }
}

class ModelDownloadManager(private val context: Context) {
    private fun workName(model: CatalogModel, path: String) = "${model.id}-$path"
    fun enqueue(model: CatalogModel) {
        model.artifacts.forEach { artifact ->
            val data = workDataOf(ModelDownloadWorker.URL_KEY to artifact.url, ModelDownloadWorker.HASH_KEY to artifact.sha256,
                ModelDownloadWorker.MODEL_KEY to model.id, ModelDownloadWorker.PATH_KEY to artifact.relativePath,
                ModelDownloadWorker.BYTES_KEY to artifact.bytes)
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>().setInputData(data)
                .setConstraints(Constraints(NetworkType.UNMETERED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).addTag("model-${model.id}").build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(model, artifact.relativePath), ExistingWorkPolicy.KEEP, request)
        }
    }
    fun isInstalled(model: CatalogModel): Boolean {
        val root = File(context.getExternalFilesDir(null), "models/${model.id}")
        return model.artifacts.all { File(root, it.relativePath).exists() }
    }
    fun cancel(model: CatalogModel) = WorkManager.getInstance(context).cancelAllWorkByTag("model-${model.id}")
    fun remove(model: CatalogModel): Boolean {
        cancel(model)
        return File(context.getExternalFilesDir(null), "models/${model.id}").deleteRecursively()
    }
    fun root(model: CatalogModel) = File(context.getExternalFilesDir(null), "models/${model.id}")
    fun status(model: CatalogModel): DownloadStatus {
        if (isInstalled(model)) return DownloadStatus(DownloadStatus.Phase.INSTALLED, 100)
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(workName(model, model.artifacts.first().relativePath)).get().firstOrNull()
            ?: return DownloadStatus(DownloadStatus.Phase.NOT_INSTALLED)
        return when (work.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadStatus(DownloadStatus.Phase.QUEUED, message = "Waiting for Wi-Fi")
            WorkInfo.State.RUNNING -> DownloadStatus(DownloadStatus.Phase.DOWNLOADING, work.progress.getInt(ModelDownloadWorker.PROGRESS_KEY, 0))
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> DownloadStatus(DownloadStatus.Phase.FAILED, message = work.outputData.getString(ModelDownloadWorker.ERROR_KEY) ?: "Download stopped")
            WorkInfo.State.SUCCEEDED -> DownloadStatus(DownloadStatus.Phase.NOT_INSTALLED)
        }
    }
}
