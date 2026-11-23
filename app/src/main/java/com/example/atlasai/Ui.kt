package com.example.atlasai

import android.Manifest
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import java.io.File
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Тема: тёмная / светлая / системная, размер шрифта ----------

class Palette(
    val bg: Color,
    val panel: Color,
    val user: Color,
    val text: Color,
    val sub: Color,
    val accent: Color,
    val onAccent: Color,
    val line: Color
) {
    fun withAccent(a: Color, onA: Color, mix: Float) =
        Palette(bg, panel, lerp(bg, a, mix), text, sub, a, onA, line)
}

private val DarkP = Palette(
    bg = Color(0xFF05080F), panel = Color(0xFF0D1826), user = Color(0xFF12365A),
    text = Color.White, sub = Color(0xFFB8C7D9), accent = Color(0xFF00E5FF),
    onAccent = Color.Black, line = Color(0xFF2B3E55)
)
private val LightP = Palette(
    bg = Color(0xFFF2F6FB), panel = Color(0xFFFFFFFF), user = Color(0xFFCFE6FF),
    text = Color(0xFF0B1620), sub = Color(0xFF47596B), accent = Color(0xFF006C80),
    onAccent = Color.White, line = Color(0xFFB9C7D6)
)
private val LocalPalette = staticCompositionLocalOf { DarkP }
private val LocalBtnShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(50) }
private val LocalBubbleShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(16.dp) }
private val C: Palette
    @Composable get() = LocalPalette.current

@Composable
fun AtlasTheme(prefs: Prefs, content: @Composable () -> Unit) {
    val dark = when (prefs.theme) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    val base = if (dark) DarkP else LightP
    val ac = Persona.accent(prefs.accent)
    val p = if (prefs.accent == "cyan") {
        base
    } else {
        base.withAccent(
            if (dark) ac.dark else ac.light,
            if (dark) Color.Black else Color.White,
            if (dark) 0.28f else 0.22f
        )
    }
    val btn: Shape = when (prefs.btnShape) {
        "square" -> RoundedCornerShape(4.dp)
        "soft" -> RoundedCornerShape(14.dp)
        else -> RoundedCornerShape(50)
    }
    val bubble: Shape = when (prefs.bubbleShape) {
        "square" -> RoundedCornerShape(4.dp)
        "round" -> RoundedCornerShape(26.dp)
        else -> RoundedCornerShape(16.dp)
    }
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalPalette provides p,
        LocalBtnShape provides btn,
        LocalBubbleShape provides bubble,
        LocalDensity provides Density(d.density, d.fontScale * prefs.fontScale)
    ) {
        MaterialTheme(
            colorScheme = if (dark) {
                darkColorScheme(
                    primary = p.accent, onPrimary = p.onAccent, background = p.bg,
                    surface = p.panel, onSurface = p.text, onBackground = p.text,
                    secondary = Color(0xFF2A8CFF)
                )
            } else {
                lightColorScheme(
                    primary = p.accent, onPrimary = p.onAccent, background = p.bg,
                    surface = p.panel, onSurface = p.text, onBackground = p.text,
                    secondary = Color(0xFF2A8CFF)
                )
            },
            content = content
        )
    }
}

// ---------- Слэш-команды ----------

class Cmd(val name: String, val desc: String)

private val COMMANDS = listOf(
    Cmd("/new", "c_new"), Cmd("/chats", "c_chats"), Cmd("/model", "c_model"),
    Cmd("/limits", "c_limits"), Cmd("/stop", "c_stop"), Cmd("/voice", "c_voice"),
    Cmd("/text", "c_text"), Cmd("/settings", "c_settings"), Cmd("/tutorial", "c_tutorial"),
    Cmd("/clear", "c_clear"), Cmd("/export", "c_export"), Cmd("/help", "c_help"),
    Cmd("/internet", "c_internet"), Cmd("/memory", "c_memory"), Cmd("/hands", "c_hands")
)

// ---------- Корень приложения ----------

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
    var memBack by remember { mutableStateOf("chat") }
    if (AppLock.locked && prefs.appLock) {
        LockScreen(prefs)
        return
    }
    when (screen) {
        "lang" -> LanguageScreen(prefs) { screen = "tutorial" }
        "tutorial" -> TutorialScreen(prefs, firstRun = true, onDone = { screen = "setup" }, onExit = {})
        "tutorial_again" -> TutorialScreen(
            prefs, firstRun = false,
            onDone = { screen = "chat" }, onExit = { screen = "chat" }
        )
        "setup" -> SetupScreen(prefs) { screen = "chat" }
        "settings" -> SettingsScreen(
            brain, prefs, voice,
            onBack = { screen = "chat" },
            onAdvanced = { screen = "advanced" },
            onPersona = { screen = "persona" },
            onLook = { screen = "look" },
            onMemory = { memBack = "settings"; screen = "memory" },
            onTutorial = { screen = "tutorial_again" },
            onReset = { brain.wipeAll(); prefs.resetAll(); screen = "lang" }
        )
        "persona" -> PersonaScreen(prefs) { screen = "settings" }
        "look" -> LookScreen(prefs, voice) { screen = "settings" }
        "advanced" -> AdvancedScreen(brain, prefs, voice) { screen = "settings" }
        "memory" -> MemoryScreen(brain.memory) { screen = memBack }
        else -> ChatScreen(
            brain, prefs, voice,
            onSettings = { screen = "settings" },
            onTutorial = { screen = "tutorial_again" },
            onMemory = { memBack = "chat"; screen = "memory" }
        )
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
        Text("ATLAS", color = C.accent, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
        Spacer(Modifier.height(12.dp))
        Text("Choose your language / Выберите язык", color = C.text, fontSize = 16.sp)
        Spacer(Modifier.height(40.dp))
        AButton(
            onClick = { prefs.lang = "en"; onDone() },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("English", fontSize = 18.sp) }
        Spacer(Modifier.height(16.dp))
        AButton(
            onClick = { prefs.lang = "ru"; onDone() },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Русский", fontSize = 18.sp) }
    }
}

// ---------- Поле ключа: показать/скрыть, где взять, проверка ----------

@Composable
fun KeyEditor(value: String, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); status = "" },
            label = { Text(Tr.s("key_hint")) },
            singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { show = !show }) {
                    Icon(
                        if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = C.sub
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(Tr.s("key_safe"), color = C.sub, fontSize = 12.sp)
        if (value.isNotBlank() && !value.trim().startsWith("AIza")) {
            Text(Tr.s("key_format_warn"), color = Color(0xFFFFB300), fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AOutlinedButton(onClick = {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                )
            }) { Text(Tr.s("get_key")) }
            AButton(
                enabled = value.isNotBlank() && !checking,
                onClick = {
                    checking = true
                    ok = false
                    status = Tr.s("key_wait")
                    scope.launch {
                        val err = withContext(Dispatchers.IO) {
                            try {
                                Gemini.check(value.trim())
                            } catch (e: Exception) {
                                e.message ?: "error"
                            }
                        }
                        ok = err == null
                        status = if (err == null) Tr.s("key_ok") else Tr.s("key_bad") + " " + err
                        checking = false
                    }
                }
            ) { Text(Tr.s("key_check")) }
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                status,
                color = if (ok) Color(0xFF43A047) else if (checking) C.sub else Color(0xFFE53935),
                fontSize = 13.sp
            )
        }
    }
}

// ---------- 2. Обучение: 17 шагов, пропустить нельзя ----------

private const val TUT = 20

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TutorialScreen(prefs: Prefs, firstRun: Boolean, onDone: () -> Unit, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { TUT }
    var key by remember { mutableStateOf(prefs.key) }

    BackHandler {
        if (pager.currentPage > 0) {
            scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
        } else if (!firstRun) {
            onExit()
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        if (!firstRun) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onExit) { Text(Tr.s("close")) }
            }
        }
        HorizontalPager(
            state = pager,
            userScrollEnabled = false,
            modifier = Modifier.weight(1f)
        ) { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(16.dp))
                Text("${p + 1} / $TUT", color = C.accent, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text(Tr.s("t${p + 1}t"), color = C.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text(Tr.s("t${p + 1}b"), color = C.sub, fontSize = 17.sp, lineHeight = 26.sp)
                if (p == 4) {
                    Spacer(Modifier.height(24.dp))
                    AButton(onClick = {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                        )
                    }) { Text(Tr.s("open_page")) }
                }
                if (p == 7) {
                    Spacer(Modifier.height(24.dp))
                    KeyEditor(key) { key = it }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
            repeat(TUT) { i ->
                Box(
                    Modifier.padding(2.dp).size(if (i == pager.currentPage) 9.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (i == pager.currentPage) C.accent else C.line)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (pager.currentPage > 0) {
                AOutlinedButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                    Text(Tr.s("back"))
                }
            } else Spacer(Modifier.width(1.dp))
            if (pager.currentPage < TUT - 1) {
                AButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                    Text(Tr.s("next"))
                }
            } else {
                AButton(onClick = { prefs.key = key; onDone() }) { Text(Tr.s("finish")) }
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
        Text(Tr.s("name_title"), color = C.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(Tr.s("name_hint")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))
        Text(Tr.s("mode_title"), color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeButton(Tr.s("mode_voice"), voiceMode, Modifier.weight(1f)) { voiceMode = true }
            ModeButton(Tr.s("mode_text"), !voiceMode, Modifier.weight(1f)) { voiceMode = false }
        }
        Spacer(Modifier.height(8.dp))
        Text(Tr.s("mode_note"), color = C.sub, fontSize = 13.sp)
        Spacer(Modifier.height(32.dp))
        AButton(
            onClick = {
                prefs.name = name.trim()
                prefs.voiceInput = voiceMode
                prefs.onboarded = true
                onDone()
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(Tr.s("start"), fontSize = 18.sp) }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        AButton(onClick = onClick, modifier = modifier.height(52.dp)) { Text(label) }
    } else {
        AOutlinedButton(onClick = onClick, modifier = modifier.height(52.dp)) { Text(label) }
    }
}

// ---------- Таблицы в ответах ----------

sealed interface Block {
    class Txt(val s: String) : Block
    class Tbl(val rows: List<List<String>>) : Block
}

private val sepRe = Regex("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?\\s*$")

private fun splitRow(l: String): List<String> {
    var s = l.trim()
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|")) s = s.dropLast(1)
    return s.split("|").map { it.trim().replace("**", "") }
}

/** Разбивает ответ на обычный текст и markdown-таблицы. */
fun parseBlocks(text: String): List<Block> {
    val lines = text.lines()
    val out = mutableListOf<Block>()
    val buf = StringBuilder()
    fun flush() {
        val s = buf.toString().trim()
        if (s.isNotBlank()) out.add(Block.Txt(s))
        buf.setLength(0)
    }
    var i = 0
    while (i < lines.size) {
        val l = lines[i]
        if (l.contains('|') && i + 1 < lines.size && lines[i + 1].contains('-') && sepRe.matches(lines[i + 1])) {
            flush()
            val rows = mutableListOf(splitRow(l))
            i += 2
            while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) {
                rows.add(splitRow(lines[i]))
                i++
            }
            out.add(Block.Tbl(rows))
        } else {
            buf.append(l).append('\n')
            i++
        }
    }
    flush()
    return out
}

@Composable
private fun TableView(rows: List<List<String>>) {
    val ctx = LocalContext.current
    val cols = rows.maxOf { it.size }
    val shape = RoundedCornerShape(8.dp)
    Column(Modifier.padding(vertical = 4.dp)) {
        Column(Modifier.clip(shape).border(1.dp, C.line, shape).horizontalScroll(rememberScrollState())) {
            rows.forEachIndexed { r, row ->
                Row(
                    Modifier.height(IntrinsicSize.Min)
                        .background(if (r == 0) C.user else Color.Transparent)
                ) {
                    for (c in 0 until cols) {
                        Text(
                            row.getOrElse(c) { "" },
                            color = C.text,
                            fontSize = 14.sp,
                            fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.width(140.dp).fillMaxHeight()
                                .border(0.5.dp, C.line).padding(8.dp)
                        )
                    }
                }
            }
        }
        Row {
            TextButton(onClick = {
                Share.copy(ctx, rows.joinToString("\n") { it.joinToString("\t") })
            }) { Text(Tr.s("copy"), fontSize = 13.sp) }
            TextButton(onClick = {
                val csv = rows.joinToString("\r\n") { r ->
                    r.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }
                }
                Share.file(ctx, "atlas_table.csv", "text/csv", ("\uFEFF" + csv).toByteArray(Charsets.UTF_8))
            }) { Text("CSV", fontSize = 13.sp) }
        }
    }
}

// ---------- 4. Чат ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    brain: Brain,
    prefs: Prefs,
    voice: Voice,
    onSettings: () -> Unit,
    onTutorial: () -> Unit,
    onMemory: () -> Unit
) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    var selectText by remember { mutableStateOf<String?>(null) }
    var editIndex by remember { mutableStateOf(-1) }
    var editText by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<Uri>() }
    val list = rememberLazyListState()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { picked.addAll(it) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) photoUri?.let { picked.add(it) }
    }

    fun takePhoto() {
        val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
        val f = File(dir, "photo_" + System.currentTimeMillis() + ".jpg")
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        photoUri = u
        camera.launch(u)
    }

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
    LaunchedEffect(brain.messages.size, brain.busy, brain.streamText.length / 60) {
        if (brain.messages.isNotEmpty()) list.animateScrollToItem(brain.messages.size - 1 + if (brain.busy) 1 else 0)
    }

    // «Поделиться → Атлас»: текст и файлы из других приложений
    LaunchedEffect(Inbox.pending) {
        if (Inbox.pending) {
            Inbox.text?.let { t ->
                input = t
                prefs.voiceInput = false
            }
            if (Inbox.uris.isNotEmpty()) {
                picked.addAll(Inbox.uris)
                Inbox.uris.clear()
            }
            Inbox.text = null
            Inbox.pending = false
        }
    }

    fun exportChat() {
        val sb = StringBuilder()
        val me = prefs.name.ifBlank { "You" }
        for (m in brain.messages) {
            sb.append(if (m.fromUser) "$me: " else "Atlas: ").append(m.text).append("\n\n")
        }
        Share.file(ctx, "atlas_chat.txt", "text/plain", sb.toString().toByteArray(Charsets.UTF_8))
    }

    fun helpText(): String =
        Tr.s("help_title") + "\n" + COMMANDS.joinToString("\n") { it.name + " — " + Tr.s(it.desc) }

    fun runCommand(raw: String) {
        val cmd = raw.trim().lowercase().split(Regex("\\s+")).first()
        when (cmd) {
            "/new" -> brain.newChat()
            "/chats" -> dialog = "chats"
            "/model" -> dialog = "model"
            "/limits" -> brain.note(String.format(Tr.s("limits_msg"), prefs.requestsToday(), prefs.model))
            "/stop" -> voice.stopSpeaking()
            "/voice" -> { prefs.voiceInput = true; brain.note(Tr.s("voice_on")) }
            "/text" -> { prefs.voiceInput = false; brain.note(Tr.s("text_on")) }
            "/settings" -> onSettings()
            "/tutorial" -> onTutorial()
            "/clear" -> brain.clear()
            "/export" -> exportChat()
            "/help" -> brain.note(helpText())
            "/internet" -> {
                prefs.internet = !prefs.internet
                brain.note(Tr.s(if (prefs.internet) "net_on" else "net_off_note"))
            }
            "/hands" -> {
                prefs.handsFree = !prefs.handsFree
                brain.note(Tr.s(if (prefs.handsFree) "hands_on" else "hands_off"))
            }
            "/memory" -> onMemory()
            else -> brain.note(Tr.s("unknown_cmd"))
        }
        input = ""
    }

    fun submit(text: String) {
        if (text.trim().startsWith("/") && picked.isEmpty()) {
            runCommand(text)
            return
        }
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

    // режим без рук: после того как Атлас договорил, снова слушаем
    DisposableEffect(Unit) {
        voice.onSpoken = {
            if (prefs.handsFree && prefs.voiceInput && !listening && !brain.busy) toggleMic()
        }
        onDispose { voice.onSpoken = null }
    }

    Scaffold(
        containerColor = C.bg,
        topBar = {
            TopAppBar(
                title = { Text("ATLAS", color = C.accent, fontWeight = FontWeight.Bold, letterSpacing = 4.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.bg),
                actions = {
                    if (voice.speaking) {
                        IconButton(onClick = { voice.stopSpeaking() }) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = Color(0xFFE53935))
                        }
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = C.text)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(Tr.s("m_new")) },
                            onClick = { menu = false; brain.newChat() }
                        )
                        DropdownMenuItem(
                            text = { Text(Tr.s("m_chats")) },
                            onClick = { menu = false; dialog = "chats" }
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
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(brain.messages) { i, m ->
                    Bubble(
                        m = m,
                        index = i,
                        last = i == brain.messages.lastIndex,
                        brain = brain,
                        voice = voice,
                        onSelect = { selectText = it },
                        onEdit = { idx, t -> editIndex = idx; editText = t }
                    )
                }
                if (brain.busy) {
                    item {
                        if (brain.streamText.isBlank()) {
                            Text(Tr.s("thinking"), color = C.accent, fontSize = 14.sp)
                        } else {
                            Surface(
                                color = C.panel,
                                shape = LocalBubbleShape.current,
                                modifier = Modifier.widthIn(max = 340.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    parseBlocks(brain.streamText).forEach { b ->
                                        when (b) {
                                            is Block.Txt -> Text(b.s, color = C.text, fontSize = 16.sp)
                                            is Block.Tbl -> TableView(b.rows)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (picked.isNotEmpty()) {
                Text(
                    Tr.s("attached") + ": " + picked.size,
                    color = C.accent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            val hints = if (!prefs.voiceInput && input.startsWith("/")) {
                COMMANDS.filter { it.name.startsWith(input.trim().lowercase()) }
            } else emptyList()
            if (hints.isNotEmpty()) {
                Surface(
                    color = C.panel,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        hints.forEach { c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { runCommand(c.name) }.padding(12.dp)
                            ) {
                                Text(c.name, color = C.accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp))
                                Text(Tr.s(c.desc), color = C.sub, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { picker.launch("*/*") }) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = C.accent)
                }
                IconButton(onClick = { takePhoto() }) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = C.accent)
                }
                if (prefs.voiceInput) {
                    TextButton(onClick = { dialog = "cmds" }) {
                        Text("/", color = C.accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (listening) heard.ifBlank { Tr.s("listening") } else heard.ifBlank { Tr.s("tap_mic") },
                        color = C.sub,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    AButton(
                        onClick = { toggleMic() },
                        shape = CircleShape,
                        modifier = Modifier.size(60.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (listening) Color(0xFFE53935) else C.accent)
                    ) {
                        Icon(
                            if (listening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            tint = C.onAccent
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
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = C.accent)
                    }
                }
            }
        }
    }

    // --- окна ---
    if (dialog == "chats") ChatsDialog(brain) { dialog = "" }
    brain.pending?.let { p ->
        AlertDialog(
            onDismissRequest = { brain.cancelPending() },
            title = { Text(p.title) },
            text = { Text(p.body) },
            confirmButton = { TextButton(onClick = { brain.confirmPending() }) { Text(Tr.s("do_it")) } },
            dismissButton = { TextButton(onClick = { brain.cancelPending() }) { Text(Tr.s("cancel")) } }
        )
    }
    if (dialog == "model") ModelDialog(prefs) { dialog = "" }
    if (dialog == "cmds") {
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text(Tr.s("cmds_title")) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    COMMANDS.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { dialog = ""; runCommand(c.name) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(c.name, color = C.accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp))
                            Text(Tr.s(c.desc), color = C.sub, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = "" }) { Text(Tr.s("close")) } }
        )
    }
    selectText?.let { t ->
        AlertDialog(
            onDismissRequest = { selectText = null },
            title = { Text(Tr.s("select_text")) },
            text = {
                SelectionContainer {
                    Text(t, color = C.text, modifier = Modifier.verticalScroll(rememberScrollState()))
                }
            },
            confirmButton = { TextButton(onClick = { selectText = null }) { Text(Tr.s("close")) } }
        )
    }
    if (editIndex >= 0) {
        AlertDialog(
            onDismissRequest = { editIndex = -1 },
            title = { Text(Tr.s("edit_title")) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editText.isNotBlank(),
                    onClick = {
                        val idx = editIndex
                        editIndex = -1
                        brain.editAndSend(idx, editText.trim())
                    }
                ) { Text(Tr.s("send")) }
            },
            dismissButton = { TextButton(onClick = { editIndex = -1 }) { Text(Tr.s("cancel")) } }
        )
    }
}

@Composable
private fun ChatsDialog(brain: Brain, onClose: () -> Unit) {
    var items by remember { mutableStateOf(brain.chats.list()) }
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", if (Tr.ru) Locale("ru") else Locale.US) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(Tr.s("chats_title")) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                if (items.isEmpty()) Text(Tr.s("chats_empty"), color = C.sub)
                items.forEach { ci ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f)
                                .clickable { brain.openChat(ci.id); onClose() }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                ci.title,
                                color = if (ci.id == brain.chatId) C.accent else C.text,
                                maxLines = 2
                            )
                            Text(fmt.format(Date(ci.updated)), color = C.sub, fontSize = 12.sp)
                        }
                        IconButton(onClick = {
                            brain.deleteChat(ci.id)
                            items = brain.chats.list()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = Tr.s("delete"), tint = C.sub)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { brain.newChat(); onClose() }) { Text(Tr.s("m_new")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(Tr.s("close")) } }
    )
}

@Composable
private fun ModelDialog(prefs: Prefs, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(Tr.s("model_title")) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Models.all.forEach { m ->
                    val modifier = Modifier.fillMaxWidth()
                    if (prefs.model == m.id) {
                        AButton(onClick = { prefs.model = m.id; onClose() }, modifier = modifier) {
                            Text(m.name + " — " + Models.desc(m), fontSize = 13.sp)
                        }
                    } else {
                        AOutlinedButton(onClick = { prefs.model = m.id; onClose() }, modifier = modifier) {
                            Text(m.name + " — " + Models.desc(m), fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(Tr.s("close")) } }
    )
}

@Composable
private fun ActIcon(icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = null, tint = C.sub, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    m: Msg,
    index: Int,
    last: Boolean,
    brain: Brain,
    voice: Voice,
    onSelect: (String) -> Unit,
    onEdit: (Int, String) -> Unit
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start
    ) {
        if (m.fromUser) {
            Box {
                val shape = LocalBubbleShape.current
                Surface(
                    color = C.user,
                    shape = shape,
                    modifier = Modifier.widthIn(max = 320.dp).clip(shape)
                        .combinedClickable(onClick = {}, onLongClick = { menu = true })
                ) {
                    Column(Modifier.padding(12.dp)) {
                        if (m.files.isNotEmpty()) {
                            Text(m.files.joinToString(", "), color = C.accent, fontSize = 12.sp)
                        }
                        if (m.text.isNotBlank()) Text(m.text, color = C.text, fontSize = 16.sp)
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (m.text.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text(Tr.s("copy")) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = { menu = false; Share.copy(ctx, m.text) }
                        )
                        DropdownMenuItem(
                            text = { Text(Tr.s("select_text")) },
                            onClick = { menu = false; onSelect(m.text) }
                        )
                    }
                    if (m.files.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(Tr.s("edit")) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menu = false; onEdit(index, m.text) }
                        )
                    }
                }
            }
        } else {
            Column(Modifier.widthIn(max = 340.dp)) {
                Surface(color = C.panel, shape = LocalBubbleShape.current) {
                    Column(Modifier.padding(12.dp)) {
                        if (m.text.isNotBlank()) {
                            val blocks = remember(m.text) { parseBlocks(m.text) }
                            blocks.forEach { b ->
                                when (b) {
                                    is Block.Txt -> Text(b.s, color = C.text, fontSize = 16.sp)
                                    is Block.Tbl -> TableView(b.rows)
                                }
                            }
                        }
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
                if (m.outFiles.isNotEmpty()) {
                    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        m.outFiles.forEach { name ->
                            AOutlinedButton(onClick = {
                                val f = brain.creator.fileOf(name)
                                if (f.exists()) {
                                    Share.file(ctx, name, Share.mimeFor(name), f.readBytes())
                                } else {
                                    Toast.makeText(ctx, Tr.s("file_gone"), Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(name, maxLines = 1)
                            }
                        }
                    }
                }
                if (m.sources.isNotEmpty()) {
                    Column(Modifier.padding(top = 4.dp)) {
                        Text(Tr.s("sources"), color = C.sub, fontSize = 12.sp)
                        m.sources.take(6).forEach { w ->
                            Text(
                                w.title.ifBlank { w.url },
                                color = C.accent,
                                fontSize = 13.sp,
                                maxLines = 1,
                                modifier = Modifier
                                    .clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(w.url))) }
                                    .padding(vertical = 3.dp)
                            )
                        }
                    }
                }
                if (m.model.isNotBlank()) {
                    Text(String.format(Tr.s("model_note"), m.model), color = C.sub, fontSize = 11.sp)
                }
                if (m.text.isNotBlank()) {
                    val lastUser = brain.messages.indexOfLast { it.fromUser }
                    val canRegen = last && !brain.busy && lastUser >= 0 &&
                        brain.messages[lastUser].files.isEmpty()
                    val speakingThis = voice.speaking && voice.speakingText == m.text
                    Row {
                        ActIcon(Icons.Default.ContentCopy) { Share.copy(ctx, m.text) }
                        ActIcon(Icons.Default.Share) { Share.text(ctx, m.text) }
                        ActIcon(if (speakingThis) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp) {
                            if (speakingThis) voice.stopSpeaking() else voice.speak(m.text)
                        }
                        if (canRegen) ActIcon(Icons.Default.Refresh) { brain.regenerate() }
                    }
                }
            }
        }
    }
}

// ---------- Память ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(memory: MemoryStore, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var editIdx by remember { mutableStateOf(-2) }   // -2 = окно закрыто, -1 = новый факт
    var editText by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = C.bg,
        topBar = {
            TopAppBar(
                title = { Text(Tr.s("mem_title"), color = C.text) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.bg),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = C.text)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (memory.items.isEmpty()) Text(Tr.s("mem_empty"), color = C.sub)
            memory.items.forEachIndexed { i, t ->
                Surface(color = C.panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t, color = C.text, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                        IconButton(onClick = { editIdx = i; editText = t }) {
                            Icon(Icons.Default.Edit, contentDescription = Tr.s("edit"), tint = C.sub)
                        }
                        IconButton(onClick = { memory.remove(i) }) {
                            Icon(Icons.Default.Delete, contentDescription = Tr.s("delete"), tint = C.sub)
                        }
                    }
                }
            }
            AButton(onClick = { editIdx = -1; editText = "" }, modifier = Modifier.fillMaxWidth()) {
                Text(Tr.s("mem_add"))
            }
            if (memory.items.isNotEmpty()) {
                AOutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(Tr.s("mem_clear"))
                }
            }
        }
    }

    if (editIdx != -2) {
        AlertDialog(
            onDismissRequest = { editIdx = -2 },
            title = { Text(Tr.s(if (editIdx == -1) "mem_add" else "edit")) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text(Tr.s("mem_hint")) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editText.isNotBlank(),
                    onClick = {
                        if (editIdx == -1) memory.add(editText) else memory.update(editIdx, editText)
                        editIdx = -2
                    }
                ) { Text(Tr.s("save")) }
            },
            dismissButton = { TextButton(onClick = { editIdx = -2 }) { Text(Tr.s("cancel")) } }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(Tr.s("mem_confirm")) },
            confirmButton = {
                TextButton(onClick = { memory.clear(); confirmClear = false }) { Text(Tr.s("delete")) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(Tr.s("cancel")) } }
        )
    }
}

// ---------- 5. Настройки ----------

@Composable
private fun Choice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val modifier = Modifier.weight(1f)
            if (value == selected) {
                AButton(
                    onClick = { onSelect(value) },
                    modifier = modifier,
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text(label, fontSize = 13.sp, maxLines = 1) }
            } else {
                AOutlinedButton(
                    onClick = { onSelect(value) },
                    modifier = modifier,
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text(label, fontSize = 13.sp, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun CardColumn(label: String, content: @Composable () -> Unit) {
    Surface(color = C.panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, color = C.text)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    brain: Brain,
    prefs: Prefs,
    voice: Voice,
    onBack: () -> Unit,
    onAdvanced: () -> Unit,
    onPersona: () -> Unit,
    onLook: () -> Unit,
    onMemory: () -> Unit,
    onTutorial: () -> Unit,
    onReset: () -> Unit
) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    var keyDialog by remember { mutableStateOf(false) }

    fun setLock(on: Boolean) {
        if (!on) {
            prefs.appLock = false
        } else if (BiometricManager.from(ctx).canAuthenticate(LOCK_AUTH) == BiometricManager.BIOMETRIC_SUCCESS) {
            prefs.appLock = true
        } else {
            Toast.makeText(ctx, Tr.s("lock_unavailable"), Toast.LENGTH_LONG).show()
        }
    }
    var keyText by remember { mutableStateOf(prefs.key) }

    Scaffold(
        containerColor = C.bg,
        topBar = {
            TopAppBar(
                title = { Text(Tr.s("settings"), color = C.text) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.bg),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = C.text)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GearRow(Tr.s("s_input")) {
                Switch(checked = prefs.voiceInput, onCheckedChange = { prefs.voiceInput = it })
            }
            GearRow(Tr.s("s_speak")) {
                Switch(checked = prefs.speak, onCheckedChange = { prefs.speak = it })
            }
            GearRow(Tr.s("s_internet")) {
                Switch(checked = prefs.internet, onCheckedChange = { prefs.internet = it })
            }
            CardColumn(Tr.s("s_search")) {
                Choice(
                    listOf("auto" to Tr.s("sm_auto"), "always" to Tr.s("sm_always"), "off" to Tr.s("sm_off")),
                    prefs.searchMode
                ) { prefs.searchMode = it }
            }
            GearRow(Tr.s("s_hands")) {
                Switch(checked = prefs.handsFree, onCheckedChange = { prefs.handsFree = it })
            }
            GearRow(Tr.s("s_lock")) {
                Switch(checked = prefs.appLock, onCheckedChange = { setLock(it) })
            }
            GearRow(Tr.s("s_lang")) {
                Row {
                    if (prefs.lang == "en") AButton(onClick = {}) { Text("EN") }
                    else AOutlinedButton(onClick = { prefs.lang = "en" }) { Text("EN") }
                    Spacer(Modifier.width(8.dp))
                    if (prefs.lang == "ru") AButton(onClick = {}) { Text("RU") }
                    else AOutlinedButton(onClick = { prefs.lang = "ru" }) { Text("RU") }
                }
            }
            GearRow(Tr.s("s_name")) {
                OutlinedTextField(
                    value = prefs.name,
                    onValueChange = { prefs.name = it },
                    singleLine = true,
                    modifier = Modifier.width(170.dp)
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(Tr.s("s_buttons"), color = C.accent, fontWeight = FontWeight.Bold)

            SettingsButton(Tr.s("b_persona")) { onPersona() }
            SettingsButton(Tr.s("b_look")) { onLook() }
            SettingsButton(Tr.s("b_adv")) { onAdvanced() }
            SettingsButton(Tr.s("b_mem")) { onMemory() }
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
            text = { KeyEditor(keyText) { keyText = it } },
            confirmButton = {
                TextButton(onClick = { prefs.key = keyText; keyDialog = false }) { Text(Tr.s("save")) }
            },
            dismissButton = {
                TextButton(onClick = { keyDialog = false }) { Text(Tr.s("cancel")) }
            }
        )
    }
}

// ---------- 6. Дополнительно ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreen(brain: Brain, prefs: Prefs, voice: Voice, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var modelDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = C.bg,
        topBar = {
            TopAppBar(
                title = { Text(Tr.s("adv_title"), color = C.text) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.bg),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = C.text)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GearRow(Tr.s("adv_stream")) {
                Switch(checked = prefs.streaming, onCheckedChange = { prefs.streaming = it })
            }
            GearRow(Tr.s("adv_encrypt")) {
                Switch(
                    checked = prefs.encryptChats,
                    onCheckedChange = {
                        prefs.encryptChats = it
                        brain.chats.convertAll()
                    }
                )
            }
            CardColumn(Tr.s("adv_len")) {
                Choice(
                    listOf("short" to Tr.s("len_short"), "normal" to Tr.s("len_normal"), "long" to Tr.s("len_long")),
                    prefs.answerLen
                ) { prefs.answerLen = it }
            }
            CardColumn(Tr.s("adv_model")) {
                val cur = Models.all.firstOrNull { it.id == prefs.model }
                Text(
                    if (cur != null) cur.name + " — " + Models.desc(cur) else prefs.model,
                    color = C.sub,
                    fontSize = 13.sp
                )
                AOutlinedButton(onClick = { modelDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(Tr.s("model_title"))
                }
            }
            CardColumn(Tr.s("adv_key")) {
                KeyEditor(prefs.key) { prefs.key = it }
            }
            VoiceCards(prefs, voice)
            CardColumn(Tr.s("adv_theme")) {
                Choice(
                    listOf("dark" to Tr.s("th_dark"), "light" to Tr.s("th_light"), "system" to Tr.s("th_system")),
                    prefs.theme
                ) { prefs.theme = it }
            }
            CardColumn(Tr.s("adv_font")) {
                Choice(
                    listOf("0.85" to "S", "1.0" to "M", "1.15" to "L", "1.3" to "XL"),
                    prefs.fontScale.toString()
                ) { prefs.fontScale = it.toFloat() }
            }
            CardColumn(Tr.s("adv_auto")) {
                Choice(
                    listOf("0" to Tr.s("ad_off"), "7" to Tr.s("ad_7"), "30" to Tr.s("ad_30"), "90" to Tr.s("ad_90")),
                    prefs.autoDeleteDays.toString()
                ) { prefs.autoDeleteDays = it.toInt() }
            }
        }
    }
    if (modelDialog) ModelDialog(prefs) { modelDialog = false }
}

@Composable
private fun GearRow(label: String, control: @Composable () -> Unit) {
    Surface(color = C.panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = C.accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = C.text, modifier = Modifier.weight(1f))
            control()
        }
    }
}

@Composable
private fun SettingsButton(label: String, onClick: () -> Unit) {
    AOutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(label) }
}

// ---------- Блокировка приложения ----------

private val LOCK_AUTH =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

@Composable
fun LockScreen(prefs: Prefs) {
    val ctx = LocalContext.current

    fun prompt() {
        val act = ctx as? FragmentActivity
        if (act == null) {
            AppLock.locked = false
            return
        }
        val bp = BiometricPrompt(
            act,
            ContextCompat.getMainExecutor(ctx),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    AppLock.locked = false
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(Tr.s("lock_title"))
            .setAllowedAuthenticators(LOCK_AUTH)
            .build()
        bp.authenticate(info)
    }

    LaunchedEffect(Unit) {
        if (BiometricManager.from(ctx).canAuthenticate(LOCK_AUTH) != BiometricManager.BIOMETRIC_SUCCESS) {
            // на телефоне нет ни отпечатка, ни блокировки экрана: снимаем блокировку, чтобы не запереть вас
            prefs.appLock = false
            AppLock.locked = false
        } else {
            prompt()
        }
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ATLAS", color = C.accent, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
        Spacer(Modifier.height(12.dp))
        Text(Tr.s("lock_text"), color = C.sub)
        Spacer(Modifier.height(32.dp))
        AButton(onClick = { prompt() }) { Text(Tr.s("unlock")) }
    }
}

// ---------- Кнопки с настраиваемой формой ----------

@Composable
private fun AButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape ?: LocalBtnShape.current,
        contentPadding = contentPadding,
        colors = colors,
        content = content
    )
}

@Composable
private fun AOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = LocalBtnShape.current,
        contentPadding = contentPadding,
        content = content
    )
}

// ---------- Голос (общий блок для «Внешний вид и голос» и «Дополнительно») ----------

@Composable
private fun VoiceCards(prefs: Prefs, voice: Voice) {
    var voiceMenu by remember { mutableStateOf(false) }
    val names = remember(Tr.lang) { voice.voiceNames() }
    CardColumn(Tr.s("adv_rate") + ": " + String.format(Locale.US, "%.1f", prefs.ttsRate) + "x") {
        Slider(
            value = prefs.ttsRate,
            onValueChange = { prefs.ttsRate = Math.round(it * 10f) / 10f },
            valueRange = 0.5f..2f
        )
    }
    CardColumn(Tr.s("adv_voice")) {
        Box {
            AOutlinedButton(onClick = { voiceMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (prefs.ttsVoice.isBlank()) Tr.s("voice_default") else prefs.ttsVoice, maxLines = 1)
            }
            DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                DropdownMenuItem(
                    text = { Text(Tr.s("voice_default")) },
                    onClick = { prefs.ttsVoice = ""; voiceMenu = false }
                )
                names.forEach { n ->
                    DropdownMenuItem(
                        text = { Text(n) },
                        onClick = { prefs.ttsVoice = n; voiceMenu = false }
                    )
                }
            }
        }
        AOutlinedButton(onClick = { voice.speak(Tr.s("test_phrase")) }, modifier = Modifier.fillMaxWidth()) {
            Text(Tr.s("b_test"))
        }
    }
}

// ---------- Характер и стиль речи ----------

@Composable
private fun ChipGrid(options: List<Opt>, selected: List<String>, max: Int, onChange: (List<String>) -> Unit) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { o ->
                    val on = o.id in selected
                    val toggle = {
                        if (on) {
                            onChange(selected - o.id)
                        } else if (selected.size < max) {
                            onChange(selected + o.id)
                        } else {
                            Toast.makeText(ctx, String.format(Tr.s("persona_max"), max), Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (on) {
                        AButton(
                            onClick = toggle,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) { Text(o.label(), fontSize = 13.sp, maxLines = 1) }
                    } else {
                        AOutlinedButton(
                            onClick = toggle,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) { Text(o.label(), fontSize = 13.sp, maxLines = 1) }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaScreen(prefs: Prefs, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val traits = Persona.ids(prefs.traits)
    val styles = Persona.ids(prefs.speech)
    Scaffold(
        containerColor = C.bg,
        topBar = {
            TopAppBar(
                title = { Text(Tr.s("b_persona"), color = C.text) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.bg),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = C.text)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CardColumn(Tr.s("persona_traits") + " (" + traits.size + "/" + Persona.MAX_TRAITS + ")") {
                ChipGrid(Persona.traits, traits, Persona.MAX_TRAITS) { prefs.traits = it.joinToString(",") }
            }
            CardColumn(Tr.s("persona_styles") + " (" + styles.size + "/" + Persona.MAX_STYLES + ")") {
                ChipGrid(Persona.styles, styles, Persona.MAX_STYLES) { prefs.speech = it.joinToString(",") }
            }
            AOutlinedButton(
                onClick = { prefs.traits = ""; prefs.speech = "" },
                modifier = Modifier.fillMaxWidth()
            ) { Text(Tr.s("persona_reset")) }
        }
    }
}

// ---------- Внешний вид и голос ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookScreen(prefs: Prefs, voice: Voice, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val shapes = listOf("round" to Tr.s("sh_round"), "soft" to Tr.s("sh_soft"), "square" to Tr.s("sh_square"))
    Scaffold(
        containerColor = C.bg,
        topBar = {
            TopAppBar(
                title = { Text(Tr.s("b_look"), color = C.text) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.bg),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = C.text)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GearRow(Tr.s("s_lang")) {
                Row {
                    if (prefs.lang == "en") AButton(onClick = {}) { Text("EN") }
                    else AOutlinedButton(onClick = { prefs.lang = "en" }) { Text("EN") }
                    Spacer(Modifier.width(8.dp))
                    if (prefs.lang == "ru") AButton(onClick = {}) { Text("RU") }
                    else AOutlinedButton(onClick = { prefs.lang = "ru" }) { Text("RU") }
                }
            }
            CardColumn(Tr.s("adv_theme")) {
                Choice(
                    listOf("dark" to Tr.s("th_dark"), "light" to Tr.s("th_light"), "system" to Tr.s("th_system")),
                    prefs.theme
                ) { prefs.theme = it }
            }
            CardColumn(Tr.s("look_accent") + ": " + Persona.accent(prefs.accent).label()) {
                Persona.accents.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { ac ->
                            Box(
                                Modifier.size(44.dp)
                                    .clip(CircleShape)
                                    .background(ac.dark)
                                    .then(
                                        if (prefs.accent == ac.id) Modifier.border(3.dp, C.text, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { prefs.accent = ac.id }
                            )
                        }
                    }
                }
            }
            CardColumn(Tr.s("look_buttons")) {
                Choice(shapes, prefs.btnShape) { prefs.btnShape = it }
            }
            CardColumn(Tr.s("look_bubbles")) {
                Choice(shapes, prefs.bubbleShape) { prefs.bubbleShape = it }
            }
            CardColumn(Tr.s("adv_font")) {
                Choice(
                    listOf("0.85" to "S", "1.0" to "M", "1.15" to "L", "1.3" to "XL", "1.5" to "XXL"),
                    prefs.fontScale.toString()
                ) { prefs.fontScale = it.toFloat() }
                Text(Tr.s("look_sample"), color = C.text)
            }
            VoiceCards(prefs, voice)
        }
    }
}
