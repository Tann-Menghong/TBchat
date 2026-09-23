package com.tannmenghong.tbchat.inference

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import java.io.File

sealed interface EngineState { data object Ready : EngineState; data class Error(val message: String) : EngineState }
data class ImageRequest(val prompt: String, val negativePrompt: String, val seed: Long, val steps: Int, val guidance: Float, val width: Int = 512, val height: Int = 512)

interface ChatEngine {
    suspend fun load(modelDirectory: File): EngineState
    fun generate(systemPrompt: String, userPrompt: String): Flow<String>
    fun cancel()
    fun unload()
}

interface DiffusionEngine {
    suspend fun load(modelDirectory: File): EngineState
    suspend fun generate(request: ImageRequest, onProgress: (Int) -> Unit): Bitmap
    fun cancel()
    fun unload()
}

/** JNI contract implemented when llama.cpp is vendored under app/src/main/cpp. */
object LlamaNativeBridge {
    external fun load(modelPath: String): Boolean
    external fun cancel()
    external fun unload()
}

/** ONNX pipeline implementation point: tokenizer, text encoder, UNet loop, scheduler, and VAE decoder. */
class OnnxDiffusionEngine : DiffusionEngine {
    override suspend fun load(modelDirectory: File) = EngineState.Error("Install the ONNX model package and native pipeline before use.")
    override suspend fun generate(request: ImageRequest, onProgress: (Int) -> Unit): Bitmap = error("Image model is not loaded.")
    override fun cancel() = Unit
    override fun unload() = Unit
}
