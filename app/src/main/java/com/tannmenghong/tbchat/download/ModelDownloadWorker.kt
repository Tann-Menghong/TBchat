package com.tannmenghong.tbchat.download

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.pm.ServiceInfo

class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) { try {
        val url = inputData.getString(URL_KEY) ?: error("Missing URL")
        val hash = inputData.getString(HASH_KEY) ?: error("Missing SHA-256")
        val modelId = inputData.getString(MODEL_KEY) ?: error("Missing model id")
        val path = inputData.getString(PATH_KEY) ?: error("Missing artifact path")
        val expectedBytes = inputData.getLong(BYTES_KEY, -1)
        require(modelId.matches(Regex("[a-zA-Z0-9_-]+")) && !path.contains("..") && !File(path).isAbsolute)
        require(url.startsWith("https://") && expectedBytes > 0 && hash.matches(Regex("[a-fA-F0-9]{64}")))
        setForeground(createForegroundInfo("Preparing model download"))
        val root = File(applicationContext.getExternalFilesDir(null), "models/$modelId").apply { mkdirs() }
        val target = File(root, path).apply { parentFile?.mkdirs() }
        val partial = File(target.path + ".part")
        val offset = partial.takeIf(File::exists)?.length() ?: 0L
        var lastProgress = -1
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        connection.connect()
        require(connection.responseCode in setOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) { "HTTP ${connection.responseCode}" }
        if (connection.responseCode == HttpURLConnection.HTTP_PARTIAL) require(connection.getHeaderField("Content-Range")?.startsWith("bytes $offset-") == true) { "Invalid resume response; retry download." }
        if (offset > 0 && connection.responseCode == HttpURLConnection.HTTP_OK) partial.delete()
        val startingAt = if (partial.exists()) partial.length() else 0L
        connection.inputStream.use { input -> FileOutputStream(partial, offset > 0 && partial.exists()).buffered(128 * 1024).use { output ->
            val buffer = ByteArray(128 * 1024); var total = startingAt; var read: Int
            while (input.read(buffer).also { read = it } >= 0) {
                output.write(buffer, 0, read); total += read
                require(total <= expectedBytes) { "Downloaded file exceeds expected size." }
                if (expectedBytes > 0) {
                    val percent = (total * 100 / expectedBytes).toInt()
                    if (percent != lastProgress) {
                        setProgress(Data.Builder().putInt(PROGRESS_KEY, percent).build())
                        setForeground(createForegroundInfo("Downloading model: $percent%"))
                        lastProgress = percent
                    }
                }
                if (isStopped) throw CancellationException()
            }
        } }
        connection.disconnect()
        require(partial.length() == expectedBytes) { "Incomplete download. Tap retry to resume." }
        if (!partial.sha256().equals(hash, ignoreCase = true)) { partial.delete(); error("Checksum mismatch. Please retry the download.") }
        require(partial.renameTo(target)) { "Could not install downloaded model." }
        Result.success()
    } catch (e: CancellationException) { throw e }
      catch (e: Exception) { Result.failure(Data.Builder().putString(ERROR_KEY, e.message).build()) } }

    private fun File.sha256(): String = inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(128 * 1024); var count: Int
        while (input.read(buffer).also { count = it } >= 0) digest.update(buffer, 0, count)
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun createForegroundInfo(status: String): ForegroundInfo {
        val channelId = "model_downloads"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(channelId, "Model downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("TBchat")
            .setContentText(status)
            .setOngoing(true)
            .build()
        return ForegroundInfo(4_208, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val URL_KEY = "url"; const val HASH_KEY = "hash"; const val MODEL_KEY = "model"
        const val PATH_KEY = "path"; const val BYTES_KEY = "bytes"; const val PROGRESS_KEY = "progress"; const val ERROR_KEY = "error"
    }
}
