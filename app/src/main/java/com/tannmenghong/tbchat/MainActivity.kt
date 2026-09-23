package com.tannmenghong.tbchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tannmenghong.tbchat.catalog.StarterCatalog
import com.tannmenghong.tbchat.data.*
import com.tannmenghong.tbchat.device.DeviceCompatibility
import com.tannmenghong.tbchat.domain.CatalogModel
import com.tannmenghong.tbchat.download.DownloadStatus
import com.tannmenghong.tbchat.download.ModelDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(this, Factory(applicationContext))[StudioViewModel::class.java]
        setContent { TBchatTheme { StudioApp(model) } }
    }
}
private class Factory(private val context: android.content.Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(type: Class<T>) = StudioViewModel(context) as T
}

class StudioViewModel(private val context: android.content.Context) : ViewModel() {
    private val dao = LocalDatabase.open(context).dao(); private val downloads = ModelDownloadManager(context)
    val models = StarterCatalog.catalog.models
    val modelStatus = MutableStateFlow(models.associate { it.id to DownloadStatus(DownloadStatus.Phase.NOT_INSTALLED) })
    val selectedConversation = MutableStateFlow<String?>(null)
    val messages = selectedConversation.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.messages(id) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    init { refreshModelStatus() }
    fun refreshModelStatus() = viewModelScope.launch(Dispatchers.IO) { modelStatus.value = models.associate { it.id to downloads.status(it) } }
    fun download(model: CatalogModel) { downloads.enqueue(model); watch(model) }
    fun cancel(model: CatalogModel) { downloads.cancel(model); refreshModelStatus() }
    fun remove(model: CatalogModel) { downloads.remove(model); refreshModelStatus() }
    fun compatibility(model: CatalogModel) = DeviceCompatibility.check(context, model)
    private fun watch(model: CatalogModel) = viewModelScope.launch { repeat(7200) { val state = withContext(Dispatchers.IO) { downloads.status(model) }; modelStatus.update { it + (model.id to state) }; if (state.phase !in setOf(DownloadStatus.Phase.QUEUED, DownloadStatus.Phase.DOWNLOADING)) return@launch; delay(1_000) } }
    fun send(text: String) = viewModelScope.launch { if (text.isBlank()) return@launch; val id = selectedConversation.value ?: UUID.randomUUID().toString().also { selectedConversation.value = it }; val now = System.currentTimeMillis(); dao.putConversation(ConversationEntity(id, text.take(40), now, now)); dao.putMessage(MessageEntity(UUID.randomUUID().toString(), id, "user", text, now)) }
    fun clearHistory() = viewModelScope.launch { dao.clearConversations(); dao.clearImages() }
}

@Composable private fun TBchatTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF155EEF), secondary = Color(0xFF475467), surface = Color(0xFFF9FAFB)), content = content)
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun StudioApp(vm: StudioViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }; val tabs = listOf("Chat", "Models", "Create", "Settings")
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("TBchat", fontWeight = FontWeight.Bold) }) }, bottomBar = { NavigationBar { tabs.forEachIndexed { i, name -> NavigationBarItem(i == tab, { tab = i }, icon = { Text(if (i == tab) "●" else "○") }, label = { Text(name) }) } } }) { inset -> Box(Modifier.padding(inset).fillMaxSize()) { when(tab) { 0 -> ChatScreen(vm); 1 -> ModelsScreen(vm); 2 -> ImageScreen(); else -> SettingsScreen(vm) } } }
}

@Composable private fun ChatScreen(vm: StudioViewModel) {
    val messages by vm.messages.collectAsStateWithLifecycle(); val states by vm.modelStatus.collectAsStateWithLifecycle(); val ready = vm.models.any { states[it.id]?.phase == DownloadStatus.Phase.INSTALLED }; var input by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(20.dp).fillMaxSize()) { Text("Private chat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(if (ready) "Your local model is ready to load." else "Set up a model first—nothing is sent to a server.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp)); if (!ready) SetupBanner("Download Qwen3 in Models to start offline chat."); LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(messages) { Card { Text(it.body, Modifier.padding(14.dp)) } } }; OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Message") }, enabled = ready, trailingIcon = { TextButton(enabled = ready, onClick = { vm.send(input); input = "" }) { Text("Send") } }) }
}

@Composable private fun ModelsScreen(vm: StudioViewModel) {
    val states by vm.modelStatus.collectAsStateWithLifecycle(); LazyColumn(Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Text("Model library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Download once, then use offline.", color = MaterialTheme.colorScheme.onSurfaceVariant) }; items(vm.models) { model -> ModelCard(model, states[model.id] ?: DownloadStatus(DownloadStatus.Phase.NOT_INSTALLED), vm) }; item { Text("Only models with tested mobile settings appear here.", style = MaterialTheme.typography.bodySmall) } }
}

@Composable private fun ModelCard(model: CatalogModel, status: DownloadStatus, vm: StudioViewModel) {
    val compatibility = vm.compatibility(model); var accepted by rememberSaveable(model.id) { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(model.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Offline chat · 2.50 GB download · 8 GB RAM recommended"); Text("Apache-2.0 · Verified before installation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); when(status.phase) { DownloadStatus.Phase.DOWNLOADING -> { LinearProgressIndicator(progress = status.progress / 100f, modifier = Modifier.fillMaxWidth()); Text("Downloading ${status.progress}%") }; DownloadStatus.Phase.QUEUED -> Text(status.message, color = MaterialTheme.colorScheme.primary); DownloadStatus.Phase.INSTALLED -> Text("Installed and ready", color = Color(0xFF067647), fontWeight = FontWeight.Bold); DownloadStatus.Phase.FAILED -> Text(status.message, color = MaterialTheme.colorScheme.error); else -> Unit }; if (!compatibility.eligible) Text(compatibility.reasons.joinToString(" "), color = MaterialTheme.colorScheme.error); when(status.phase) { DownloadStatus.Phase.NOT_INSTALLED, DownloadStatus.Phase.FAILED -> { Row { Checkbox(accepted, { accepted = it }); Text("I accept the ${model.licenseName} license.") }; Button(enabled = accepted && compatibility.eligible, onClick = { vm.download(model) }, modifier = Modifier.fillMaxWidth()) { Text("Download model") } }; DownloadStatus.Phase.QUEUED, DownloadStatus.Phase.DOWNLOADING -> OutlinedButton(onClick = { vm.cancel(model) }, modifier = Modifier.fillMaxWidth()) { Text("Cancel download") }; DownloadStatus.Phase.INSTALLED -> OutlinedButton(onClick = { vm.remove(model) }, modifier = Modifier.fillMaxWidth()) { Text("Remove from device") } } } }
}

@Composable private fun ImageScreen() = Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Image maker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); SetupBanner("Text-to-image is not enabled in this release. It will appear only after its offline model and Android runtime are fully tested.") }
@Composable private fun SettingsScreen(vm: StudioViewModel) = Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text("Your data", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Chats and model files stay on this phone. No accounts, analytics, cloud inference, or prompt uploads."); OutlinedButton(onClick = { vm.clearHistory() }) { Text("Clear local history") } }
@Composable private fun SetupBanner(text: String) = Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF4FF))) { Text(text, Modifier.padding(14.dp), color = Color(0xFF1849A9)) }
