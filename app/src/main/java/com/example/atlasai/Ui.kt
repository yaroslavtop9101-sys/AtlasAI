@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.atlasai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Cyan = Color(0xFF00E5FF)
private val Bg = Color(0xFF05080F)
private val Panel = Color(0xFF0D1826)
private val UserBubble = Color(0xFF12365A)

@Composable
fun AtlasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            onPrimary = Color.Black,
            background = Bg,
            surface = Panel,
            onSurface = Color.White,
            onBackground = Color.White,
            secondary = Color(0xFF2A8CFF)
        ),
        content = content
    )
}

@Composable
fun AtlasApp(prefs: Prefs, voice: Voice) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val brain = remember { Brain(ctx.applicationContext, prefs, voice, scope) }
    var screen by remember {
        mutableStateOf(
            when {
                prefs.lang.isBlank() -> "lang"
                !prefs.onboarded -> "tutorial"
                else -> "chat"
            }
        )
    }
    when (screen) {
        "lang" -> LanguageScreen(prefs) { screen = "tutorial" }
        "tutorial" -> TutorialScreen(prefs) { screen = "setup" }
        "setup" -> SetupScreen(prefs) { screen = "chat" }
        "settings" -> SettingsScreen(
            brain, prefs, voice,
            onBack = { screen = "chat" },
            onTutorial = { screen = "tutorial" },
            onReset = { brain.messages.clear(); prefs.resetAll(); screen = "lang" }
        )
        "history" -> HistoryScreen(brain, onBack = { screen = "chat" }, onOpened = { screen = "chat" })
        else -> ChatScreen(brain, prefs, voice, onSettings = { screen = "settings" }, onHistory = { screen = "history" })
    }
}

// ---------- 1. Выбор языка ----------

@Composable
fun LanguageScreen(prefs: Prefs, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ATLAS", color = Cyan, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
        Spacer(Modifier.height(12.dp))
        Text("Choose your language / Выберите язык", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = { prefs.lang = "en"; onDone() },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("English", fontSize = 18.sp) }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { prefs.lang = "ru"; onDone() },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Русский", fontSize = 18.sp) }
    }
}

// ---------- 2. Обучение: 9 вкладок ----------

@Composable
fun TutorialScreen(prefs: Prefs, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { 9 }
    var key by remember { mutableStateOf(prefs.key) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(24.dp))
                Text("${p + 1} / 9", color = Cyan, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text(Tr.s("t${p + 1}t"), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text(Tr.s("t${p + 1}b"), color = Color(0xFFB8C7D9), fontSize = 17.sp, lineHeight = 26.sp)
                if (p == 4) {
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                        )
                    }) { Text(Tr.s("open_page")) }
                }
                if (p == 8) {
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text(Tr.s("key_hint")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
            repeat(9) { i ->
                Box(
                    Modifier.padding(3.dp).size(if (i == pager.currentPage) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (i == pager.currentPage) Cyan else Color(0xFF3A4A5E))
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (pager.currentPage > 0) {
                OutlinedButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                    Text(Tr.s("back"))
                }
            } else Spacer(Modifier.width(1.dp))
            if (pager.currentPage < 8) {
                Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                    Text(Tr.s("next"))
                }
            } else {
                Button(onClick = { prefs.key = key; onDone() }) { Text(Tr.s("finish")) }
            }
        }
    }
}

// ---------- 3. Имя и режим общения ----------

@Composable
fun SetupScreen(prefs: Prefs, onDone: () -> Unit) {
    var name by remember { mutableStateOf(prefs.name) }
    var voiceMode by remember { mutableStateOf(prefs.voiceInput) }
    Column(
        Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(Tr.s("name_title"), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(Tr.s("name_hint")) },
            supportingText = { Text(Tr.s("name_short")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))
        Text(Tr.s("mode_title"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeButton(Tr.s("mode_voice"), voiceMode, Modifier.weight(1f)) { voiceMode = true }
            ModeButton(Tr.s("mode_text"), !voiceMode, Modifier.weight(1f)) { voiceMode = false }
        }
        Spacer(Modifier.height(8.dp))
        Text(Tr.s("mode_note"), color = Color(0xFF8CA0B5), fontSize = 13.sp)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                prefs.name = name.trim()
                prefs.voiceInput = voiceMode
                prefs.onboarded = true
                onDone()
            },
            enabled = name.trim().length >= 2,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(Tr.s("start"), fontSize = 18.sp) }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.height(52.dp)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp)) { Text(label) }
    }
}

// ---------- 4. Чат ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(brain: Brain, prefs: Prefs, voice: Voice, onSettings: () -> Unit, onHistory: () -> Unit) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<Uri>() }
    val list = rememberLazyListState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val wait: Long? = if (now > 0) Limits.minWait(prefs.model) else null

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { picked.addAll(it) }

    LaunchedEffect(Unit) {
        brain.greet()
        permLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CONTACTS
            )
        )
    }
    LaunchedEffect(brain.messages.size, brain.busy) {
        if (brain.messages.isNotEmpty()) list.animateScrollToItem(brain.messages.size - 1 + if (brain.busy) 1 else 0)
    }

    fun submit(text: String) {
        brain.send(text, picked.toList())
        picked.clear()
        input = ""
    }

    fun toggleMic() {
        if (listening) {
            voice.stopListening()
            return
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        listening = true
        heard = ""
        voice.listen(
            onPartial = { heard = it },
            onResult = { listening = false; heard = ""; submit(it) },
            onFail = { listening = false; heard = it }
        )
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("ATLAS", color = Cyan, fontWeight = FontWeight.Bold, letterSpacing = 4.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
                actions = {
                    IconButton(onClick = { voice.stopSpeaking() }) {
                        Icon(Icons.Default.VolumeOff, contentDescription = Tr.s("stop_voice"), tint = Color.White)
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(Tr.s("new_chat")) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = { menu = false; brain.newChat() }
                        )
                        DropdownMenuItem(
                            text = { Text(Tr.s("history")) },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                            onClick = { menu = false; onHistory() }
                        )
                        DropdownMenuItem(
                            text = { Text(Tr.s("settings")) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = { menu = false; onSettings() }
                        )
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(brain.messages) { Bubble(it) }
                if (brain.busy) {
                    item { Text(Tr.s("thinking"), color = Cyan, fontSize = 14.sp) }
                }
            }
            if (wait == null || wait > 0) {
                Text(
                    if (wait == null) Tr.s(if (prefs.model == "auto") "all_dead" else "model_dead")
                    else String.format(Tr.s("limit_banner"), Limits.fmt(wait)),
                    color = Color(0xFFFFB74D),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (picked.isNotEmpty()) {
                Text(
                    Tr.s("attached") + ": " + picked.size,
                    color = Cyan,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { picker.launch("*/*") }) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = Cyan)
                }
                if (prefs.voiceInput) {
                    Text(
                        if (listening) heard.ifBlank { Tr.s("listening") } else heard.ifBlank { Tr.s("tap_mic") },
                        color = Color(0xFFB8C7D9),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Button(
                        onClick = { toggleMic() },
                        shape = CircleShape,
                        modifier = Modifier.size(60.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (listening) Color(0xFFE53935) else Cyan)
                    ) {
                        Icon(
                            if (listening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.Black
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(Tr.s("type_hint")) },
                        modifier = Modifier.weight(1f),
                        maxLines = 4
                    )
                    IconButton(onClick = { submit(input) }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Cyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: Msg) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (m.fromUser) UserBubble else Panel,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 320.dp).combinedClickable(
                onClick = {},
                onLongClick = {
                    clip.setText(AnnotatedString(m.text))
                    Toast.makeText(ctx, Tr.s("copied"), Toast.LENGTH_SHORT).show()
                }
            )
        ) {
            Column(Modifier.padding(12.dp)) {
                if (m.files.isNotEmpty()) {
                    Text(m.files.joinToString(", "), color = Cyan, fontSize = 12.sp)
                }
                if (m.text.isNotBlank()) Text(m.text, color = Color.White, fontSize = 16.sp)
                m.image?.let {
                    Spacer(Modifier.height(8.dp))
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    )
                }
            }
        }
    }
}

// ---------- 5. Настройки: 4 шестерёнки + 5 кнопок ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    brain: Brain,
    prefs: Prefs,
    voice: Voice,
    onBack: () -> Unit,
    onTutorial: () -> Unit,
    onReset: () -> Unit
) {
    BackHandler(onBack = onBack)
    var keyDialog by remember { mutableStateOf(false) }
    var keyText by remember { mutableStateOf(prefs.key) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text(Tr.s("settings"), color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Шестерёнка 1
            GearRow(Tr.s("s_input")) {
                Switch(checked = prefs.voiceInput, onCheckedChange = { prefs.voiceInput = it })
            }
            // Шестерёнка 2
            GearRow(Tr.s("s_speak")) {
                Switch(checked = prefs.speak, onCheckedChange = { prefs.speak = it })
            }
            // Шестерёнка 3
            GearRow(Tr.s("s_lang")) {
                Row {
                    if (prefs.lang == "en") Button(onClick = {}) { Text("EN") }
                    else OutlinedButton(onClick = { prefs.lang = "en" }) { Text("EN") }
                    Spacer(Modifier.width(8.dp))
                    if (prefs.lang == "ru") Button(onClick = {}) { Text("RU") }
                    else OutlinedButton(onClick = { prefs.lang = "ru" }) { Text("RU") }
                }
            }
            // Шестерёнка 4
            GearRow(Tr.s("s_name")) {
                OutlinedTextField(
                    value = prefs.name,
                    onValueChange = { prefs.name = it },
                    supportingText = { if (prefs.name.trim().length < 2) Text(Tr.s("name_short")) },
                    singleLine = true,
                    modifier = Modifier.width(170.dp)
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(Tr.s("model_title"), color = Cyan, fontWeight = FontWeight.Bold)
            Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    ModelOption(Tr.s("auto"), "", prefs.model == "auto" || Limits.MODELS.none { it.id == prefs.model }) {
                        prefs.model = "auto"
                    }
                    for (m in Limits.MODELS) {
                        ModelOption(m.title, String.format(Tr.s("per_min"), m.rpm), prefs.model == m.id) {
                            prefs.model = m.id
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(Tr.s("s_buttons"), color = Cyan, fontWeight = FontWeight.Bold)

            // Кнопки 1–5
            SettingsButton(Tr.s("b_key")) { keyText = prefs.key; keyDialog = true }
            SettingsButton(Tr.s("b_test")) { voice.speak(Tr.s("test_phrase")) }
            SettingsButton(Tr.s("b_clear")) { brain.clear(); onBack() }
            SettingsButton(Tr.s("b_tut")) { onTutorial() }
            SettingsButton(Tr.s("b_reset")) { onReset() }
        }
    }

    if (keyDialog) {
        AlertDialog(
            onDismissRequest = { keyDialog = false },
            title = { Text(Tr.s("b_key")) },
            text = {
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text(Tr.s("key_hint")) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { prefs.key = keyText; keyDialog = false }) { Text(Tr.s("save")) }
            },
            dismissButton = {
                TextButton(onClick = { keyDialog = false }) { Text(Tr.s("cancel")) }
            }
        )
    }
}

@Composable
private fun GearRow(label: String, control: @Composable () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = Cyan, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = Color.White, modifier = Modifier.weight(1f))
            control()
        }
    }
}

@Composable
private fun SettingsButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(label) }
}

// ---------- История чатов ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(brain: Brain, onBack: () -> Unit, onOpened: () -> Unit) {
    BackHandler(onBack = onBack)
    var renaming by remember { mutableStateOf<Chat?>(null) }
    var newTitle by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", if (Tr.ru) Locale("ru") else Locale.US) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text(Tr.s("history"), color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { brain.newChat(); onOpened() }) {
                        Icon(Icons.Default.Add, contentDescription = Tr.s("new_chat"), tint = Cyan)
                    }
                }
            )
        }
    ) { pad ->
        if (brain.chats.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(Tr.s("history_empty"), color = Color(0xFF8CA0B5))
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(brain.chats.toList()) { c ->
                    Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier.weight(1f).clickable { brain.open(c.id); onOpened() }.padding(vertical = 14.dp)
                            ) {
                                Text(c.title, color = Color.White, fontSize = 16.sp, maxLines = 1)
                                Text(fmt.format(Date(c.updated)), color = Color(0xFF8CA0B5), fontSize = 12.sp)
                            }
                            IconButton(onClick = { renaming = c; newTitle = c.title }) {
                                Icon(Icons.Default.Edit, contentDescription = Tr.s("rename"), tint = Cyan)
                            }
                            IconButton(onClick = { brain.delete(c.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = Tr.s("delete"), tint = Color(0xFFE57373))
                            }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { c ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(Tr.s("rename")) },
            text = { OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { brain.rename(c.id, newTitle); renaming = null }) { Text(Tr.s("save")) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(Tr.s("cancel")) } }
        )
    }
}

@Composable
private fun ModelOption(title: String, note: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White)
            if (note.isNotBlank()) Text(note, color = Color(0xFF8CA0B5), fontSize = 12.sp)
        }
    }
}
