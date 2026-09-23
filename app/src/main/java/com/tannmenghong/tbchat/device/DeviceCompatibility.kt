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
        // Android reserves part of advertised RAM for hardware; allow that reservation.
        if (memory.totalMem < model.requiredRamGb * 1_000_000_000L * 0.85) reasons += "Model needs approximately ${model.requiredRamGb} GB RAM."
        val free = StatFs(context.getExternalFilesDir(null)?.path ?: context.filesDir.path).availableBytes
        if (free < model.requiredStorageBytes) reasons += "Not enough free storage for this model."
        return Compatibility(reasons.isEmpty(), reasons)
    }
}
