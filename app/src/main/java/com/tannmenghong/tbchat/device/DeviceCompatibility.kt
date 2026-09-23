package com.tannmenghong.tbchat.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import com.tannmenghong.tbchat.domain.CatalogModel

data class Compatibility(val eligible: Boolean, val reasons: List<String>)

object DeviceCompatibility {
    fun check(context: Context, model: CatalogModel): Compatibility {
        val reasons = mutableListOf<String>()
        if (Build.VERSION.SDK_INT < 29) reasons += "Android 10 or later is required."
        if (!Build.SUPPORTED_ABIS.contains("arm64-v8a")) reasons += "This build supports 64-bit Arm phones only."
        val memory = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }
        val memoryGb = (memory.totalMem / (1024L * 1024L * 1024L)).toInt()
        if (memoryGb < model.requiredRamGb) reasons += "Model needs at least ${model.requiredRamGb} GB RAM."
        val free = StatFs(context.getExternalFilesDir(null)?.path ?: context.filesDir.path).availableBytes
        if (free < model.requiredStorageBytes) reasons += "Not enough free storage for this model."
        return Compatibility(reasons.isEmpty(), reasons)
    }
}
