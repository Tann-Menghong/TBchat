package com.tannmenghong.tbchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tannmenghong.tbchat.catalog.CatalogRepository
import com.tannmenghong.tbchat.catalog.CatalogVerifier
import com.tannmenghong.tbchat.data.*
import com.tannmenghong.tbchat.device.DeviceCompatibility
import com.tannmenghong.tbchat.domain.CatalogModel
import com.tannmenghong.tbchat.domain.ModelCatalog
import com.tannmenghong.tbchat.download.ModelDownloadManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

private const val CATALOG_PUBLIC_KEY = "MCowBQYDK2VwAyEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        val vm = ViewModelProvider(this, Factory(app))[StudioViewModel::class.java]
        setContent { MaterialTheme { StudioApp(vm) } }
    }
}

private class Factory(private val context: android.content.Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = StudioViewModel(context) as T
}

class StudioViewModel(private val context: android.content.Context) : ViewModel() {
    private val db = LocalDatabase.open(context); private val dao = db.dao()
    val conversations = dao.conversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val selectedConversation = MutableStateFlow<String?>(null)
    val messages = selectedConversation.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.messages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val catalog = MutableStateFlow<ModelCatalog?>(null)
    val catalogMessage = MutableStateFlow("Refresh the signed catalog before downloading models.")
    private val catalogRepo = CatalogRepository(context, CatalogVerifier(CATALOG_PUBLIC_KEY))
    private val downloader = ModelDownloadManager(context)

    fun refreshCatalog() = viewModelScope.launch {
        catalogMessage.value = "Checking signed catalog…"
        catalogRepo.refresh().onSuccess { catalog.value = it; catalogMessage.value = "Catalog verified." }
            .onFailure { catalogRepo.cached().onSuccess { cached -> catalog.value = cached; catalogMessage.value = "Using verified cached catalog." }
                .onFailure { catalogMessage.value = it.message ?: "Catalog unavailable." } }
    }
    fun send(text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        val id = selectedConversation.value ?: UUID.randomUUID().toString().also { selectedConversation.value = it }
        val now = System.currentTimeMillis()
        dao.putConversation(ConversationEntity(id, text.take(40), now, now))
        dao.putMessage(MessageEntity(UUID.randomUUID().toString(), id, "user", text, now))
        dao.putMessage(MessageEntity(UUID.randomUUID().toString(), id, "assistant", "Download and load a chat model to generate an offline response.", now + 1))
    }
    fun install(model: CatalogModel) { downloader.enqueue(model) }
    fun compatible(model: CatalogModel) = DeviceCompatibility.check(context, model)
    fun clearHistory() = viewModelScope.launch { dao.clearConversations(); dao.clearImages() }
}

@Composable private fun StudioApp(vm: StudioViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val labels = listOf("Chat", "Create Image", "Models", "Settings")
    Scaffold(bottomBar = { NavigationBar { labels.forEachIndexed { index, label -> NavigationBarItem(index == tab, { tab = index }, { Text(label) }) } } }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) { when (tab) {
            0 -> ChatScreen(vm); 1 -> ImageScreen(); 2 -> ModelsScreen(vm); else -> SettingsScreen(vm)
        } }
    }
}

@Composable private fun ChatScreen(vm: StudioViewModel) {
    val messages by vm.messages.collectAsStateWithLifecycle(); var input by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(16.dp).fillMaxSize()) {
        Text("Offline chat", style = MaterialTheme.typography.headlineSmall)
        Text("Messages stay on this phone. Download a compatible GGUF model in Models.")
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { message -> Card { Text("${message.role}: ${message.body}", Modifier.padding(12.dp)) } }
        }
        Row { OutlinedTextField(input, { input = it }, Modifier.weight(1f), label = { Text("Message") }); Spacer(Modifier.width(8.dp)); Button({ vm.send(input); input = "" }) { Text("Send") } }
    }
}

@Composable private fun ImageScreen() {
    var prompt by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Create image", style = MaterialTheme.typography.headlineSmall)
        Text("Text-to-image is processed locally after the ONNX model package is installed.")
        OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), label = { Text("Prompt") }, minLines = 3)
        Button(enabled = false, onClick = {}) { Text("Install an image model to generate") }
        Text("v1: 512×512, optional negative prompt, seed, steps, and guidance. Images are saved to the gallery only when you choose Save.")
    }
}

@Composable private fun ModelsScreen(vm: StudioViewModel) {
    val catalog by vm.catalog.collectAsStateWithLifecycle(); val message by vm.catalogMessage.collectAsStateWithLifecycle()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Models", style = MaterialTheme.typography.headlineSmall)
        Text(message)
        Button({ vm.refreshCatalog() }) { Text("Refresh signed catalog") }
        Text("Starter catalog: Qwen3 4B Instruct (GGUF Q4) and Stable Diffusion 1.5 (ONNX). Downloads are enabled only after a signed catalog is published.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(catalog?.models ?: emptyList()) { model -> ModelCard(model, vm) } }
    }
}

@Composable private fun ModelCard(model: CatalogModel, vm: StudioViewModel) {
    val check = vm.compatible(model)
    var accepted by rememberSaveable(model.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(model.name, style = MaterialTheme.typography.titleMedium); Text("${model.modality} · ${model.requiredRamGb} GB RAM · ${model.licenseName}")
        Row { Checkbox(accepted, { accepted = it }); Text("I accept the model license before downloading.") }
        if (!check.eligible) Text(check.reasons.joinToString(" "), color = MaterialTheme.colorScheme.error)
        Button(enabled = check.eligible && accepted, onClick = { vm.install(model) }) { Text("Download") }
    } }
}

@Composable private fun SettingsScreen(vm: StudioViewModel) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Privacy: TBchat has no account, analytics, cloud inference, or prompt/image upload. Network access is limited to the catalog and model files you request.")
        Button({ vm.clearHistory() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Clear local chat and image history") }
    }
}
