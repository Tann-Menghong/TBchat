package com.tannmenghong.tbchat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tannmenghong.tbchat.catalog.StarterCatalog
import com.tannmenghong.tbchat.data.*
import com.tannmenghong.tbchat.device.DeviceCompatibility
import com.tannmenghong.tbchat.domain.CatalogModel
import com.tannmenghong.tbchat.download.*
import com.tannmenghong.tbchat.inference.LocalChat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModel(private val context: Context) : ViewModel() {
    private val database = LocalDatabase.open(context)
    private val dao = database.dao()
    private val downloads = ModelDownloadManager(context)
    private val nativeDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var engine: LocalChat? = null
    private var loaded: String? = null
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val temperature = MutableStateFlow(preferences.getFloat("temperature", 0.7f))
    val systemPrompt = MutableStateFlow(preferences.getString("system", "You are a helpful assistant. Be clear and accurate.")!!)
    val models = StarterCatalog.catalog.models
    val modelStatus = MutableStateFlow(models.associate { it.id to DownloadStatus(DownloadStatus.Phase.NOT_INSTALLED) })
    val selectedConversation = MutableStateFlow<String?>(null)
    val conversations = dao.conversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val messages = selectedConversation.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.messages(id) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val busy = MutableStateFlow(false)
    val response = MutableStateFlow("")
    val status = MutableStateFlow("Private. Local. Yours.")
    @Volatile private var stopRequested = false

    init { viewModelScope.launch { while (isActive) { refreshModelStatus(); delay(1500) } } }
    suspend fun refreshModelStatus() = withContext(Dispatchers.IO) { modelStatus.value = models.associate { it.id to downloads.status(it) } }
    fun download(model: CatalogModel) { downloads.enqueue(model) }
    fun cancel(model: CatalogModel) { downloads.cancel(model) }
    fun remove(model: CatalogModel) {
        if (busy.value) return
        viewModelScope.launch(nativeDispatcher) {
            if (loaded == model.id) { engine?.close(); loaded = null }
            downloads.remove(model)
        }
    }
    fun compatibility(model: CatalogModel) = DeviceCompatibility.check(context, model)
    fun stop() { stopRequested = true }
    fun newChat() { if (!busy.value) { selectedConversation.value = null; response.value = "" } }
    fun select(id: String) { if (!busy.value) { selectedConversation.value = id; response.value = "" } }
    fun delete(id: String) = viewModelScope.launch { if (!busy.value) { dao.deleteConversation(id); if (selectedConversation.value == id) newChat() } }
    fun rename(id: String, title: String) = viewModelScope.launch { if (title.isNotBlank()) dao.rename(id, title.trim()) }
    fun settings(prompt: String, value: Float) { systemPrompt.value = prompt; temperature.value = value; preferences.edit().putString("system", prompt).putFloat("temperature", value).apply() }
    fun clearHistory() = viewModelScope.launch { if (!busy.value) { dao.clearConversations(); dao.clearImages(); newChat() } }
    fun send(text: String) {
        if (text.isBlank() || busy.value) return
        val model = models.firstOrNull { modelStatus.value[it.id]?.phase == DownloadStatus.Phase.INSTALLED }
        if (model == null) { status.value = "Download a model in the library first."; return }
        busy.value = true; stopRequested = false; response.value = ""
        viewModelScope.launch {
            val id = selectedConversation.value ?: UUID.randomUUID().toString().also { selectedConversation.value = it }
            try {
                val history = dao.history(id)
                val now = System.currentTimeMillis()
                if (history.isEmpty()) dao.putConversation(ConversationEntity(id, text.take(48), now, now))
                dao.putMessage(MessageEntity(UUID.randomUUID().toString(), id, "user", text, now))
                withContext(nativeDispatcher) {
                    status.value = "Loading model…"
                    val local = engine ?: LocalChat().also { engine = it }
                    if (loaded != model.id) { local.open(downloads.root(model).resolve(model.artifacts.first().relativePath).path); loaded = model.id }
                    val prompt = buildString {
                        append("<|im_start|>system\n${systemPrompt.value}<|im_end|>\n")
                        history.takeLast(12).forEach { append("<|im_start|>${it.role}\n${it.body}<|im_end|>\n") }
                        append("<|im_start|>user\n$text /no_think<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n")
                    }
                    status.value = "Reading conversation…"
                    local.begin(prompt.toByteArray(Charsets.UTF_8), temperature.value)
                    status.value = "Generating on your phone…"
                    val bytes = ByteArrayOutputStream()
                    for (i in 0 until 768) {
                        if (stopRequested) break
                        val piece = local.next() ?: break
                        bytes.write(piece)
                        response.value = bytes.toString("UTF-8")
                    }
                }
                if (response.value.isNotBlank()) dao.putMessage(MessageEntity(UUID.randomUUID().toString(), id, "assistant", response.value, System.currentTimeMillis()))
                status.value = if (stopRequested) "Response stopped." else "Generated offline on your phone."
            } catch (e: Exception) { status.value = e.message ?: "Generation failed." }
              catch (e: UnsatisfiedLinkError) { status.value = "Native engine could not load on this device." }
            finally { response.value = ""; busy.value = false }
        }
    }
    override fun onCleared() {
        stopRequested = true
        CoroutineScope(nativeDispatcher).launch { engine?.close(); nativeDispatcher.close(); database.close() }
    }
}
