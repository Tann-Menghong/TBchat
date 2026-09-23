package com.tannmenghong.tbchat.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val url = inputData.getString(URL_KEY) ?: error("Missing URL")
        val hash = inputData.getString(HASH_KEY) ?: error("Missing SHA-256")
        val modelId = inputData.getString(MODEL_KEY) ?: error("Missing model id")
        val path = inputData.getString(PATH_KEY) ?: error("Missing artifact path")
        val expectedBytes = inputData.getLong(BYTES_KEY, -1)
        val root = File(applicationContext.getExternalFilesDir(null), "models/$modelId").apply { mkdirs() }
        val target = File(root, path).apply { parentFile?.mkdirs() }
        val partial = File(target.path + ".part")
        val offset = partial.takeIf(File::exists)?.length() ?: 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        connection.connect()
        require(connection.responseCode in setOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) { "HTTP ${connection.responseCode}" }
        if (offset > 0 && connection.responseCode == HttpURLConnection.HTTP_OK) partial.delete()
        val startingAt = if (partial.exists()) partial.length() else 0L
        connection.inputStream.use { input -> FileOutputStream(partial, offset > 0 && partial.exists()).buffered(128 * 1024).use { output ->
            val buffer = ByteArray(128 * 1024); var total = startingAt; var read: Int
            while (input.read(buffer).also { read = it } >= 0) {
                output.write(buffer, 0, read); total += read
                if (expectedBytes > 0) setProgress(Data.Builder().putInt(PROGRESS_KEY, (total * 100 / expectedBytes).toInt()).build())
                if (isStopped) return Result.retry()
            }
        } }
        require(partial.sha256().equals(hash, ignoreCase = true)) { "Downloaded file checksum does not match." }
        require(partial.renameTo(target)) { "Could not install downloaded model." }
        Result.success()
    }.getOrElse { Result.failure(Data.Builder().putString(ERROR_KEY, it.message).build()) }

    private fun File.sha256(): String = inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(128 * 1024); var count: Int
        while (input.read(buffer).also { count = it } >= 0) digest.update(buffer, 0, count)
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val URL_KEY = "url"; const val HASH_KEY = "hash"; const val MODEL_KEY = "model"
        const val PATH_KEY = "path"; const val BYTES_KEY = "bytes"; const val PROGRESS_KEY = "progress"; const val ERROR_KEY = "error"
    }
}
