package com.tannmenghong.tbchat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tannmenghong.tbchat.download.DownloadStatus.Phase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(type: Class<T>) = StudioViewModel(applicationContext) as T
        })[StudioViewModel::class.java]
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF9CD9C5)) else lightColorScheme(primary = Color(0xFF176B54), background = Color(0xFFF6F8F5), surface = Color(0xFFF6F8F5))
            MaterialTheme(colorScheme = colors) { Studio(vm) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Studio(vm: StudioViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val names = listOf("Chat", "History", "Models", "Settings")
    val icons = listOf(Icons.Default.ChatBubbleOutline, Icons.Default.History, Icons.Default.Download, Icons.Default.Tune)
    Scaffold(topBar = { TopAppBar(title = { Text("TBchat", fontWeight = FontWeight.Bold) }, actions = { IconButton(enabled = !busy, onClick = { vm.newChat(); page = 0 }) { Icon(Icons.Default.Add, "New chat") } }) }, bottomBar = {
        NavigationBar { names.forEachIndexed { index, name -> NavigationBarItem(selected = page == index, onClick = { page = index }, icon = { Icon(icons[index], name) }, label = { Text(name) }) } }
    }) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { when (page) {
        0 -> Chat(vm) { page = 2 }
        1 -> History(vm) { page = 0 }
        2 -> Models(vm)
        else -> Settings(vm)
    } } }
}

@Composable private fun Chat(vm: StudioViewModel, openModels: () -> Unit) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val states by vm.modelStatus.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val response by vm.response.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val ready = states.values.any { it.phase == Phase.INSTALLED }
    var input by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).imePadding()) {
        Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 20.dp)) {
            if (messages.isEmpty()) item {
                Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("A little help.\nEntirely on your phone.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Write, learn, brainstorm and ask questions. Your conversations stay here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!ready) Button(onClick = openModels) { Text("Get your first model") }
                    else listOf("Help me write a thoughtful email", "Explain a topic step by step", "Brainstorm ideas for a project").forEach { suggestion -> OutlinedButton(onClick = { input = suggestion }) { Text(suggestion) } }
                }
            }
            items(messages, key = { it.id }) { message -> Bubble(message.role, message.body) }
            if (busy) item { if (response.isBlank()) LinearProgressIndicator(Modifier.fillMaxWidth()) else Bubble("assistant", response) }
        }
        OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth(), placeholder = { Text(if (ready) "Ask anything…" else "Download a model to begin") }, enabled = ready && !busy, maxLines = 6, trailingIcon = {
            if (busy) IconButton(onClick = vm::stop) { Icon(Icons.Default.Stop, "Stop generation") }
            else IconButton(enabled = ready && input.isNotBlank(), onClick = { vm.send(input.trim()); input = "" }) { Icon(Icons.AutoMirrored.Filled.Send, "Send message") }
        })
        Text("AI can make mistakes. Check important answers.", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun Bubble(role: String, body: String) {
    val clipboard = LocalClipboardManager.current
    Card(colors = CardDefaults.cardColors(containerColor = if (role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (role == "user") "You" else "TBchat", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { clipboard.setText(AnnotatedString(body)) }, Modifier.size(32.dp)) { Icon(Icons.Default.ContentCopy, "Copy message", Modifier.size(16.dp)) }
            }
            SelectionContainer { Text(body) }
        }
    }
}

@Composable private fun History(vm: StudioViewModel, openChat: () -> Unit) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp)) {
        Text("Your conversations", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 12.dp), placeholder = { Text("Search conversations") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (conversations.isEmpty()) item { Text("Start a chat and it will appear here automatically.") }
            items(conversations.filter { it.title.contains(query, true) }, key = { it.id }) { conversation ->
                Card { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    TextButton(enabled = !busy, onClick = { vm.select(conversation.id); openChat() }) { Text(conversation.title) }
                    Row { TextButton(enabled = !busy, onClick = { renameId = conversation.id; title = conversation.title }) { Text("Rename") }; TextButton(enabled = !busy, onClick = { deleteId = conversation.id }) { Text("Delete") } }
                } }
            }
        }
    }
    if (deleteId != null) AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Delete conversation?") }, text = { Text("This removes its messages from this phone.") }, confirmButton = { TextButton(onClick = { vm.delete(deleteId!!); deleteId = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } })
    if (renameId != null) AlertDialog(onDismissRequest = { renameId = null }, title = { Text("Rename conversation") }, text = { OutlinedTextField(title, { title = it }) }, confirmButton = { TextButton(onClick = { vm.rename(renameId!!, title); renameId = null }) { Text("Save") } })
}

@Composable private fun Models(vm: StudioViewModel) {
    val states by vm.modelStatus.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Your offline library", style = MaterialTheme.typography.headlineMedium); Text("Download on Wi-Fi. Chat anywhere.", Modifier.padding(top = 8.dp)) }
        items(vm.models) { model ->
            val state = states[model.id]
            val compatibility = vm.compatibility(model)
            var accept by rememberSaveable(model.id) { mutableStateOf(false) }
            var remove by remember { mutableStateOf(false) }
            Card { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(model.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${"%.2f".format(model.artifacts.sumOf { it.bytes } / 1e9)} GB • Offline text chat")
                Text("Download integrity is checked before installation.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { uri.openUri(model.licenseUrl) }) { Text("Read ${model.licenseName} license") }
                when (state?.phase) {
                    Phase.INSTALLED -> { Text("Ready for offline chat", color = MaterialTheme.colorScheme.primary); OutlinedButton(enabled = !busy, onClick = { remove = true }) { Text("Remove model") } }
                    Phase.QUEUED, Phase.DOWNLOADING -> { LinearProgressIndicator((state.progress / 100f).coerceIn(0f, 1f), Modifier.fillMaxWidth()); Text(if (state.phase == Phase.QUEUED) "Waiting for Wi-Fi / download slot" else "Downloaded ${state.progress}%"); TextButton(onClick = { vm.cancel(model) }) { Text("Pause") } }
                    else -> { if (state?.phase == Phase.FAILED) Text(state.message, color = MaterialTheme.colorScheme.error); if (!compatibility.eligible) Text(compatibility.reasons.joinToString("\n")); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(accept, { accept = it }); Text("I accept the model license") }; Button(enabled = accept && compatibility.eligible, onClick = { vm.download(model) }) { Text(if (state?.phase == Phase.FAILED) "Resume / retry" else "Download") } }
                }
            } }
            if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("Remove downloaded model?") }, text = { Text("Your conversations remain saved. You can download this model again.") }, confirmButton = { TextButton(onClick = { vm.remove(model); remove = false }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("Cancel") } })
        }
    }
}

@Composable private fun Settings(vm: StudioViewModel) {
    val context = LocalContext.current
    val temperature by vm.temperature.collectAsStateWithLifecycle()
    val prompt by vm.systemPrompt.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var clear by remember { mutableStateOf(false) }
    LazyColumn(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Make it yours", style = MaterialTheme.typography.headlineMedium) }
        item { OutlinedTextField(prompt, { vm.settings(it, temperature) }, Modifier.fillMaxWidth(), label = { Text("Assistant instructions") }, minLines = 3) }
        item { Text("Creativity: ${"%.1f".format(temperature)}"); Slider(temperature, { vm.settings(prompt, it) }, valueRange = 0.1f..1.2f); Text("Lower values give more predictable responses.", style = MaterialTheme.typography.bodySmall) }
        item { OutlinedButton(enabled = messages.isNotEmpty() && !busy, onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, messages.joinToString("\n\n") { "${it.role}: ${it.body}" }) }, "Export conversation")) }) { Text("Export current conversation") } }
        item { Text("Stored only on this phone", style = MaterialTheme.typography.titleMedium); Text("No login, analytics or cloud generation. Network access is used to download models. System appearance follows your phone.") }
        item { OutlinedButton(enabled = !busy, onClick = { clear = true }) { Text("Delete all conversations") } }
        item { Text("TBchat ${BuildConfig.VERSION_NAME} • Offline text chat\nImage generation and voice input are not available in this build.", style = MaterialTheme.typography.bodySmall) }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Delete all conversations?") }, text = { Text("This cannot be undone. Downloaded models are kept.") }, confirmButton = { TextButton(onClick = { vm.clearHistory(); clear = false }) { Text("Delete all") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } })
}
