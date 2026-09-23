package com.tannmenghong.tbchat.download

import android.content.Context
import androidx.work.*
import com.tannmenghong.tbchat.domain.CatalogModel
import java.io.File
import java.util.concurrent.TimeUnit

class ModelDownloadManager(private val context: Context) {
    fun enqueue(model: CatalogModel) {
        model.artifacts.forEach { artifact ->
            val data = workDataOf(ModelDownloadWorker.URL_KEY to artifact.url, ModelDownloadWorker.HASH_KEY to artifact.sha256,
                ModelDownloadWorker.MODEL_KEY to model.id, ModelDownloadWorker.PATH_KEY to artifact.relativePath,
                ModelDownloadWorker.BYTES_KEY to artifact.bytes)
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>().setInputData(data)
                .setConstraints(Constraints(NetworkType.UNMETERED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).addTag("model-${model.id}").build()
            WorkManager.getInstance(context).enqueueUniqueWork("${model.id}-${artifact.relativePath}", ExistingWorkPolicy.KEEP, request)
        }
    }
    fun remove(model: CatalogModel): Boolean = File(context.getExternalFilesDir(null), "models/${model.id}").deleteRecursively()
}
