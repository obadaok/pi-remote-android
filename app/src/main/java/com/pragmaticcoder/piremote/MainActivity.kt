package com.pragmaticcoder.piremote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private var pendingUri by mutableStateOf<String?>(null)
    private var pendingSharedUris by mutableStateOf<List<String>>(emptyList())
    private var pendingSharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingUri = intent?.data?.toString()
        pendingSharedUris = extractSharedUris(intent)
        pendingSharedText = extractSharedText(intent)
        window.statusBarColor = android.graphics.Color.parseColor("#031F1B")
        window.navigationBarColor = android.graphics.Color.parseColor("#031F1B")
        setContent { PiRemoteApp(connectionUri = pendingUri, sharedUris = pendingSharedUris, sharedText = pendingSharedText) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingUri = intent.data?.toString()
        pendingSharedUris = extractSharedUris(intent)
        pendingSharedText = extractSharedText(intent)
    }

    private fun extractSharedUris(intent: Intent?): List<String> {
        if (intent == null) return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString())
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                ?.map { it.toString() }
                .orEmpty()
            else -> emptyList()
        }
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
    }
}

private val PiGreen = Color(0xFF10B981)
private val PiGreenDeep = Color(0xFF031F1B)
private val PiGreenDark = Color(0xFF064E3B)
private val PiGreenSoft = Color(0xFFD1FAE5)
private val PiTeal = Color(0xFF14B8A6)
private val PiAmber = Color(0xFFF59E0B)

private val PiDarkColors = darkColorScheme(
    primary = PiGreenSoft,
    onPrimary = PiGreenDeep,
    primaryContainer = PiGreenDark,
    onPrimaryContainer = Color(0xFFECFDF5),
    secondary = PiTeal,
    secondaryContainer = Color(0xFF042F2E),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = PiAmber,
    tertiaryContainer = Color(0xFF5B3A09),
    onTertiaryContainer = Color(0xFFFFF7ED),
    background = PiGreenDeep,
    surface = Color(0xFF062A25),
    surfaceVariant = Color(0xFF0B332E),
    onSurface = Color(0xFFF0FDFA),
    outline = Color(0xFF8BB7AD),
    error = Color(0xFFFCA5A5),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFFE4E6),
)

private val PiLightColors = lightColorScheme(
    primary = Color(0xFF047857),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF022C22),
    secondary = Color(0xFF0F766E),
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF042F2E),
    tertiary = Color(0xFFD97706),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    background = Color(0xFFF3FCF8),
    surface = Color.White,
    surfaceVariant = Color(0xFFE6F5F0),
    outline = Color(0xFF5F7F77),
)

enum class ChatKind { User, Assistant, Tool, System, Error }

data class ChatItem(
    val id: Long,
    val kind: ChatKind,
    val title: String,
    val text: String,
    val expanded: Boolean = false,
)

data class SessionCandidate(
    val host: String,
    val port: Int,
    val label: String,
    val isIdle: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiRemoteApp(connectionUri: String? = null, sharedUris: List<String> = emptyList(), sharedText: String? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val prefs = remember { securePiRemotePreferences(context) }

    var host by remember { mutableStateOf(prefs.getString("host", "192.168.1.") ?: "192.168.1.") }
    var port by remember { mutableStateOf(prefs.getString("port", "37891") ?: "37891") }
    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var input by remember { mutableStateOf("") }
    val attachments = remember { mutableStateListOf<AttachmentItem>() }
    var connected by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    var autoSendShared by remember { mutableStateOf(prefs.getBoolean("autoSendShared", false)) }
    var keepAwake by remember { mutableStateOf(prefs.getBoolean("keepAwake", false)) }
    var pendingAutoSendShared by remember { mutableStateOf(false) }
    var scanningSessions by remember { mutableStateOf(false) }
    var showSessionPicker by remember { mutableStateOf(false) }
    val sessionCandidates = remember { mutableStateListOf<SessionCandidate>() }
    var working by remember { mutableStateOf(false) }
    var suppressNextCloseNotice by remember { mutableStateOf(false) }
    var reconnectAttempts by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Disconnected") }
    var sessionInfo by remember { mutableStateOf("No session") }
    var supportsBinaryFileAttachments by remember { mutableStateOf(false) }
    var activeAssistantId by remember { mutableStateOf<Long?>(null) }
    var scrollVersion by remember { mutableIntStateOf(0) }
    var autoConnectRequest by remember { mutableIntStateOf(0) }
    val messages = remember { mutableStateListOf<ChatItem>() }
    val activeToolMessages = remember { mutableStateMapOf<String, Long>() }
    val pendingUserEchoes = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0

    fun applyConnectionUri(uriText: String): Boolean {
        val parsed = parsePiRemoteUri(uriText, ConnectionSettings(host, port, token)) ?: return false
        host = parsed.host
        port = parsed.port
        token = parsed.token
        prefs.edit()
            .putString("host", host.trim())
            .putString("port", port.trim().ifBlank { "37891" })
            .putString("token", token.trim())
            .apply()
        autoConnectRequest++
        return true
    }

    LaunchedEffect(connectionUri) {
        connectionUri?.let { applyConnectionUri(it) }
    }

    val client = remember {
        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
    var webSocket by remember { mutableStateOf<WebSocket?>(null) }

    fun nextId() = System.nanoTime()

    fun copyText(label: String, value: String) {
        if (value.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    fun latestAssistantText(): String = messages.lastOrNull { it.kind == ChatKind.Assistant }?.text.orEmpty()

    fun addMessage(kind: ChatKind, title: String, text: String) {
        messages.add(ChatItem(nextId(), kind, title, text))
        if (messages.size > 250) messages.removeRange(0, messages.size - 250)
        scrollVersion++
    }

    fun attachmentName(uri: Uri, mimeType: String): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: fallbackAttachmentName(mimeType)
    }

    fun addAttachmentFromUri(uri: Uri) {
        if (attachments.size >= 4) {
            addMessage(ChatKind.Error, "Attachment limit", "Remove an attachment before adding more.")
            return
        }
        runCatching {
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val name = attachmentName(uri, mimeType)
            val attachment = context.contentResolver.openInputStream(uri)?.use { stream ->
                createAttachmentForStream(name, mimeType, stream)
            } ?: return@runCatching
            attachments.add(attachment)
        }.onFailure { error ->
            val message = error.message ?: "Could not read file"
            val title = if (message.contains("larger than")) "Attachment too large" else "Attachment failed"
            addMessage(ChatKind.Error, title, message)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        for (uri in uris.take(4 - attachments.size)) addAttachmentFromUri(uri)
    }
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) return@rememberLauncherForActivityResult
        if (applyConnectionUri(contents)) {
            addMessage(ChatKind.System, "QR", "Connection loaded")
        } else {
            addMessage(ChatKind.Error, "QR", "Not a Pi Remote QR code")
        }
    }

    LaunchedEffect(sharedUris) {
        val parsed = sharedUris.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
        parsed.forEach { addAttachmentFromUri(it) }
        if (parsed.isNotEmpty() && autoSendShared) {
            pendingAutoSendShared = true
            autoConnectRequest++
        }
    }

    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank() && input.isBlank()) input = sharedText
        if (!sharedText.isNullOrBlank() && autoSendShared) {
            pendingAutoSendShared = true
            autoConnectRequest++
        }
    }

    fun appendAssistantDelta(delta: String) {
        if (delta.isEmpty()) return
        val id = activeAssistantId
        val index = id?.let { existingId -> messages.indexOfFirst { it.id == existingId } } ?: -1
        if (index >= 0) {
            val old = messages[index]
            messages[index] = old.copy(text = old.text + delta)
        } else {
            val newId = nextId()
            activeAssistantId = newId
            messages.add(ChatItem(newId, ChatKind.Assistant, "Assistant", delta))
        }
        scrollVersion++
    }

    fun mergeToolText(existing: String, update: String, done: Boolean): String {
        if (existing.isBlank()) return update
        if (update.isBlank() || update == existing) return existing
        if (!done) return update
        return listOf(existing, update).joinToString("\n\n")
    }

    fun upsertToolMessage(toolCallId: String, title: String, text: String, done: Boolean) {
        val existingId = activeToolMessages[toolCallId]
        val index = existingId?.let { id -> messages.indexOfFirst { it.id == id } } ?: -1
        if (index >= 0) {
            val old = messages[index]
            val updatedText = mergeToolText(old.text, text, done)
            messages[index] = old.copy(title = title, text = updatedText)
        } else {
            val newId = nextId()
            activeToolMessages[toolCallId] = newId
            messages.add(ChatItem(newId, ChatKind.Tool, title, text))
        }
        if (done) activeToolMessages.remove(toolCallId)
        scrollVersion++
    }

    fun toggleToolMessage(id: Long) {
        val index = messages.indexOfFirst { it.id == id && it.kind == ChatKind.Tool }
        if (index >= 0) messages[index] = messages[index].copy(expanded = !messages[index].expanded)
    }

    fun saveConnectionSettings() {
        prefs.edit()
            .putString("host", host.trim())
            .putString("port", port.trim().ifBlank { "37891" })
            .putString("token", token.trim())
            .apply()
    }

    fun normalizeUserEcho(text: String): String {
        return text
            .lineSequence()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                    !line.startsWith("Attachments:", ignoreCase = true) &&
                    line != "[image]" &&
                    line != "[file]"
            }
            .joinToString("\n")
            .trim()
    }

    fun isSameUserEcho(pending: String, echoed: String): Boolean {
        if (pending == echoed) return true
        val pendingText = normalizeUserEcho(pending)
        val echoedText = normalizeUserEcho(echoed)
        if (pendingText.isNotBlank() || echoedText.isNotBlank()) return pendingText == echoedText
        return pending.contains("Attachments:", ignoreCase = true) &&
            (echoed.contains("[image]") || echoed.contains("[file]"))
    }

    fun sendJson(type: String, text: String? = null) {
        val ws = webSocket
        if (ws == null || !connected) {
            addMessage(ChatKind.Error, "Not connected", "Connect to Pi before sending commands.")
            return
        }
        if (type in listOf("prompt", "steer", "follow_up") && hasBinaryFileAttachment(attachments) && !supportsBinaryFileAttachments) {
            addMessage(ChatKind.Error, "Unsupported attachment", "This Pi Remote server does not advertise binary file attachment support. Update the Pi extension and reconnect.")
            return
        }
        val payload = runCatching { buildPromptJson(type, text, attachments).toString() }
            .onFailure { addMessage(ChatKind.Error, "Send failed", it.message ?: "Message is too large") }
            .getOrNull() ?: return
        if (!ws.send(payload)) {
            addMessage(ChatKind.Error, "Send failed", "Message is too large to queue; remove an attachment and try again.")
            return
        }

        when (type) {
            "prompt", "steer", "follow_up" -> {
                val displayedText = listOfNotNull(
                    text.orEmpty().takeIf { it.isNotBlank() },
                    attachments.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Attachments: ") { it.name },
                ).joinToString("\n")
                if (displayedText.isNotBlank()) pendingUserEchoes.add(displayedText)
                if (pendingUserEchoes.size > 10) pendingUserEchoes.removeRange(0, pendingUserEchoes.size - 10)
                addMessage(
                ChatKind.User,
                type.replace('_', ' ').replaceFirstChar { it.uppercase() },
                displayedText
                )
            }
            "abort" -> addMessage(ChatKind.System, "Abort", "Abort requested")
            "get_state", "ping" -> addMessage(ChatKind.System, type, "Requested")
        }
        if (type in listOf("prompt", "steer", "follow_up")) attachments.clear()
    }

    LaunchedEffect(pendingAutoSendShared, connected, input, attachments.size) {
        if (pendingAutoSendShared && connected && (input.isNotBlank() || attachments.isNotEmpty())) {
            sendJson("prompt", input)
            input = ""
            pendingAutoSendShared = false
        }
    }

    fun connect(clearMessages: Boolean = true) {
        if (connecting) return
        if (webSocket != null) suppressNextCloseNotice = true
        webSocket?.close(1000, "Reconnect")
        if (clearMessages) {
            messages.clear()
            activeToolMessages.clear()
            activeAssistantId = null
            scrollVersion++
        }
        saveConnectionSettings()
        prefs.edit().putBoolean("autoReconnect", true).apply()
        val cleanHost = host.trim()
        val cleanPort = port.trim().ifBlank { "37891" }
        val url = Uri.Builder()
            .scheme("ws")
            .encodedAuthority("$cleanHost:${cleanPort.toIntOrNull() ?: 37891}")
            .appendQueryParameter("token", token.trim())
            .build()
            .toString()

        connecting = true
        status = "Connecting..."
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                mainHandler.post {
                    connected = true
                    connecting = false
                    showSettings = false
                    working = false
                    supportsBinaryFileAttachments = false
                    status = "Connected"
                    reconnectAttempts = 0
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                mainHandler.post {
                    handleIncoming(
                        text = text,
                        addMessage = ::addMessage,
                        appendAssistantDelta = ::appendAssistantDelta,
                        upsertToolMessage = ::upsertToolMessage,
                        setSessionInfo = { sessionInfo = it },
                        setWorking = { working = it },
                        setSupportsBinaryFileAttachments = { supportsBinaryFileAttachments = it },
                        clearActiveAssistant = { activeAssistantId = null },
                        suppressUserEcho = { echoedText ->
                            val index = pendingUserEchoes.indexOfFirst { pendingText -> isSameUserEcho(pendingText, echoedText) }
                            if (index >= 0) {
                                pendingUserEchoes.removeAt(index)
                                true
                            } else {
                                false
                            }
                        },
                    )
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                mainHandler.post {
                    connected = false
                    connecting = false
                    working = false
                    status = "Disconnected"
                    if (suppressNextCloseNotice) {
                        suppressNextCloseNotice = false
                    } else {
                        addMessage(ChatKind.System, "Disconnected", "$code $reason")
                        scheduleReconnect(
                            mainHandler,
                            reconnectAttempts++,
                            shouldAutoReconnect = { prefs.getBoolean("autoReconnect", false) && host.isNotBlank() && port.isNotBlank() && token.isNotBlank() },
                            connect = { connect(clearMessages = false) },
                            setStatus = { status = it },
                        )
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                mainHandler.post {
                    connected = false
                    connecting = false
                    working = false
                    status = "Error"
                    addMessage(ChatKind.Error, "Connection error", t.message ?: "Unknown error")
                    scheduleReconnect(
                        mainHandler,
                        reconnectAttempts++,
                        shouldAutoReconnect = { prefs.getBoolean("autoReconnect", false) && host.isNotBlank() && port.isNotBlank() && token.isNotBlank() },
                        connect = { connect(clearMessages = false) },
                        setStatus = { status = it },
                    )
                }
            }
        })
    }

    fun disconnect() {
        prefs.edit().putBoolean("autoReconnect", false).apply()
        webSocket?.close(1000, "Android disconnect")
        webSocket = null
        connected = false
        connecting = false
        working = false
        status = "Disconnected"
        reconnectAttempts = 0
    }

    fun shouldAutoReconnect(): Boolean =
        prefs.getBoolean("autoReconnect", false) && host.isNotBlank() && port.isNotBlank() && token.isNotBlank()

    fun scanSessions() {
        val baseHost = host.trim().ifBlank { return }
        val currentPort = port.toIntOrNull() ?: 37891
        val ports = ((currentPort - 2)..(currentPort + 8)).filter { it in 1..65535 }.distinct()
        sessionCandidates.clear()
        scanningSessions = true
        var pending = ports.size
        fun doneOne() = mainHandler.post {
            pending--
            if (pending <= 0) {
                scanningSessions = false
                showSessionPicker = true
            }
        }
        ports.forEach { scanPort ->
            var finished = false
            fun donePort() {
                if (finished) return
                finished = true
                doneOne()
            }
            val url = Uri.Builder().scheme("ws").encodedAuthority("$baseHost:$scanPort").appendQueryParameter("token", token.trim()).build().toString()
            val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (finished) return
                    val obj = runCatching { JSONObject(text) }.getOrNull()
                    if (obj?.optString("type") == "hello") {
                        val state = obj.optJSONObject("state")
                        val idle = state?.optBoolean("isIdle", true) ?: true
                        val model = state?.optJSONObject("model")?.optString("id").orEmpty()
                        val cwd = state?.optString("cwd").orEmpty().substringAfterLast('\\').ifBlank { state?.optString("cwd").orEmpty() }
                        mainHandler.post { sessionCandidates.add(SessionCandidate(baseHost, scanPort, "${if (idle) "Idle" else "Working"} • $model • $cwd", idle)) }
                        webSocket.close(1000, "scan complete")
                        donePort()
                    }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { donePort() }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { donePort() }
            })
            mainHandler.postDelayed({ if (!finished) { ws.cancel(); donePort() } }, 1800)
        }
    }

    LaunchedEffect(connected, keepAwake) {
        if (connected && keepAwake) context.findActivity()?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else context.findActivity()?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    LaunchedEffect(Unit) {
        if (!connected && !connecting && shouldAutoReconnect()) connect()
    }

    DisposableEffect(lifecycleOwner, connected, host, port, token) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !connected && !connecting && shouldAutoReconnect()) {
                connect()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(autoConnectRequest) {
        if (autoConnectRequest > 0 && !connected && !connecting) connect()
    }

    LaunchedEffect(scrollVersion) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) PiDarkColors else PiLightColors) {
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawerContent(
                    connected = connected,
                    connecting = connecting,
                    sessionInfo = sessionInfo,
                    onConnect = { connect() },
                    onDisconnect = ::disconnect,
                    onScanSessions = ::scanSessions,
                    scanningSessions = scanningSessions,
                    onCopyLatest = { copyText("Assistant", latestAssistantText()) },
                    onClear = {
                        messages.clear()
                        activeAssistantId = null
                        activeToolMessages.clear()
                    },
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                )
            },
        ) {
        Scaffold(
            topBar = {
                MinimalTopBar(
                    connected = connected,
                    connecting = connecting,
                    modelLabel = modelLabel(sessionInfo),
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNewChat = {
                        messages.clear()
                        activeAssistantId = null
                        activeToolMessages.clear()
                        input = ""
                    },
                    onOpenSettings = { showSettings = true },
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .imePadding()
                    .navigationBarsPadding()
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (messages.isEmpty()) {
                        WelcomeScreen(
                            working = working,
                            modifier = Modifier.align(Alignment.Center),
                            onSuggest = { suggestion -> input = suggestion },
                        )
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 10.dp),
                    ) {
                        items(messages, key = { it.id }) { item ->
                            ChatCard(
                                item = item,
                                onToggleTool = { toggleToolMessage(item.id) },
                                onCopy = { copyText(item.title, item.text) },
                            )
                        }
                    }
                    OutputScrollbar(
                        listState = listState,
                        itemCount = messages.size,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(6.dp),
                    )
                }

                if (scanningSessions) {
                    ScanningSessionsDialog()
                }

                if (showSessionPicker) {
                    SessionPickerDialog(
                        candidates = sessionCandidates.sortedWith(compareBy<SessionCandidate> { it.isIdle }.thenBy { it.port }),
                        onDismiss = { showSessionPicker = false },
                        onPick = { candidate ->
                            host = candidate.host
                            port = candidate.port.toString()
                            saveConnectionSettings()
                            showSessionPicker = false
                            connect()
                        },
                    )
                }

                ComposerPanel(
                    input = input,
                    onInputChange = { input = it },
                    attachments = attachments,
                    connected = connected,
                    working = working,
                    keyboardVisible = keyboardVisible,
                    onSend = { type ->
                        sendJson(type, input)
                        input = ""
                    },
                    onAttach = { picker.launch("*/*") },
                    onClearAttachments = { attachments.clear() },
                    onRemoveAttachment = { attachment -> attachments.remove(attachment) },
                    onAbort = { sendJson("abort") },
                )
            }
        }
        }

        if (showSettings) {
            SettingsSheet(
                host = host,
                onHostChange = { host = it },
                port = port,
                onPortChange = { port = it },
                token = token,
                onTokenChange = { token = it },
                showToken = showToken,
                onToggleShowToken = { showToken = !showToken },
                autoSendShared = autoSendShared,
                onAutoSendSharedChange = {
                    autoSendShared = it
                    prefs.edit().putBoolean("autoSendShared", it).apply()
                },
                keepAwake = keepAwake,
                onKeepAwakeChange = {
                    keepAwake = it
                    prefs.edit().putBoolean("keepAwake", it).apply()
                },
                connected = connected,
                connecting = connecting,
                status = status,
                onSave = {
                    saveConnectionSettings()
                    addMessage(ChatKind.System, "Saved", "Connection settings saved")
                },
                onScanQr = {
                    qrScanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Scan Pi Remote QR")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false)
                    )
                },
                onConnect = { connect() },
                onDisconnect = ::disconnect,
                onDismiss = { showSettings = false },
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webSocket?.close(1000, "Activity disposed")
            client.dispatcher.executorService.shutdown()
        }
    }
}

private fun scheduleReconnect(
    handler: Handler,
    attempt: Int,
    shouldAutoReconnect: () -> Boolean,
    connect: () -> Unit,
    setStatus: (String) -> Unit,
) {
    if (!shouldAutoReconnect()) return
    val delayMs = min(30_000L, 1_000L * (1L shl attempt.coerceIn(0, 5)))
    setStatus("Reconnecting in ${delayMs / 1000}s…")
    handler.postDelayed({
        if (shouldAutoReconnect()) {
            setStatus("Reconnecting…")
            connect()
        }
    }, delayMs)
}

private fun modelLabel(sessionInfo: String): String {
    val parts = sessionInfo.split("•").map { it.trim() }
    return if (parts.size >= 2) parts[1] else if (sessionInfo.isNotBlank()) sessionInfo else "Not connected"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinimalTopBar(
    connected: Boolean,
    connecting: Boolean,
    modelLabel: String,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu")
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            when {
                                connected -> PiGreen
                                connecting -> PiAmber
                                else -> MaterialTheme.colorScheme.error
                            }
                        ),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        connecting -> "Connecting…"
                        connected -> modelLabel
                        else -> "Offline"
                    },
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            IconButton(onClick = onNewChat) {
                Icon(Icons.Filled.Add, contentDescription = "New chat")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDrawerContent(
    connected: Boolean,
    connecting: Boolean,
    sessionInfo: String,
    scanningSessions: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onScanSessions: () -> Unit,
    onCopyLatest: () -> Unit,
    onClear: () -> Unit,
    onCloseDrawer: () -> Unit,
) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 20.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Brush.linearGradient(listOf(PiGreenDark, PiTeal))),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_pi_remote),
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Column {
                    Text("π Remote", style = MaterialTheme.typography.titleMedium)
                    Text(sessionInfo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Spacer(Modifier.height(8.dp))
            DrawerItem(
                icon = { Icon(if (connected) Icons.Filled.Stop else Icons.Filled.ArrowUpward, contentDescription = null, modifier = Modifier.size(22.dp)) },
                label = if (connected) "Disconnect" else if (connecting) "Connecting…" else "Connect",
                enabled = !connecting,
                onClick = { onCloseDrawer(); if (connected) onDisconnect() else onConnect() },
            )
            DrawerItem(
                icon = { Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(22.dp)) },
                label = if (scanningSessions) "Scanning…" else "Browse sessions",
                enabled = !scanningSessions,
                onClick = { onCloseDrawer(); onScanSessions() },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 6.dp))
            DrawerItem(
                icon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(22.dp)) },
                label = "Copy latest response",
                onClick = { onCloseDrawer(); onCopyLatest() },
            )
            DrawerItem(
                icon = { Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(22.dp)) },
                label = "Clear conversation",
                onClick = { onCloseDrawer(); onClear() },
            )
        }
    }
}

@Composable
private fun DrawerItem(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon()
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    showToken: Boolean,
    onToggleShowToken: () -> Unit,
    autoSendShared: Boolean,
    onAutoSendSharedChange: (Boolean) -> Unit,
    keepAwake: Boolean,
    onKeepAwakeChange: (Boolean) -> Unit,
    connected: Boolean,
    connecting: Boolean,
    status: String,
    onSave: () -> Unit,
    onScanQr: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Connection", style = MaterialTheme.typography.titleLarge)
            Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(host, onHostChange, label = { Text("Host") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(port, onPortChange, label = { Text("Port") }, modifier = Modifier.width(110.dp), singleLine = true, shape = RoundedCornerShape(14.dp))
            }
            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                label = { Text("Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = onToggleShowToken) { Text(if (showToken) "Hide" else "Show") }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, shape = RoundedCornerShape(999.dp)) { Text("Save") }
                OutlinedButton(onClick = onScanQr, shape = RoundedCornerShape(999.dp)) { Text("Scan QR") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            SettingsSwitchRow(
                title = "Auto-send shared content",
                subtitle = "When opened from Android Share",
                checked = autoSendShared,
                onCheckedChange = onAutoSendSharedChange,
            )
            SettingsSwitchRow(
                title = "Keep screen awake",
                subtitle = "While connected",
                checked = keepAwake,
                onCheckedChange = onKeepAwakeChange,
            )
            Button(
                onClick = { if (connected) onDisconnect() else onConnect() },
                enabled = !connecting,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(999.dp),
                colors = if (connected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(),
            ) { Text(if (connected) "Disconnect" else if (connecting) "Connecting…" else "Connect to Pi") }
        }
    }
}
@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ScanningSessionsDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Scanning sessions") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                Text("Checking nearby Pi Remote ports…")
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun SessionPickerDialog(
    candidates: List<SessionCandidate>,
    onDismiss: () -> Unit,
    onPick: (SessionCandidate) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pi sessions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (candidates.isEmpty()) {
                    Text("No sessions found nearby. Check host/token and try again.")
                } else {
                    candidates.forEach { candidate ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth().clickable { onPick(candidate) },
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (candidate.isIdle) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${candidate.host}:${candidate.port}", style = MaterialTheme.typography.titleSmall)
                                Text(candidate.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WelcomeScreen(
    working: Boolean,
    modifier: Modifier = Modifier,
    onSuggest: (String) -> Unit,
) {
    val suggestions = listOf(
        "Explain this project structure",
        "Write a bash backup script",
        "Summarize recent changes",
        "Help me fix a bug",
    )
    Column(
        modifier = modifier.padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(PiGreenDark, PiTeal))),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_pi_remote),
                contentDescription = null,
                modifier = Modifier.size(70.dp),
            )
        }
        Text(
            if (working) "Pi is working…" else "How can I help you today?",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { suggestion ->
                Surface(
                    onClick = { onSuggest(suggestion) },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                ) {
                    Text(
                        suggestion,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OutputScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    if (itemCount <= 1) return

    val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    if (visibleCount >= itemCount) return

    val firstVisible = listState.firstVisibleItemIndex
    val thumbFraction = (visibleCount.toFloat() / itemCount.toFloat()).coerceIn(0.08f, 1f)
    val maxFirst = (itemCount - visibleCount).coerceAtLeast(1)
    val offsetFraction = (firstVisible.toFloat() / maxFirst.toFloat()).coerceIn(0f, 1f)
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)

    Canvas(modifier = modifier) {
        val trackWidth = size.width
        drawRoundRect(
            color = trackColor,
            size = androidx.compose.ui.geometry.Size(trackWidth, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2, trackWidth / 2),
        )
        val thumbHeight = size.height * thumbFraction
        val thumbTop = (size.height - thumbHeight) * offsetFraction
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(0f, thumbTop),
            size = androidx.compose.ui.geometry.Size(trackWidth, thumbHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2, trackWidth / 2),
        )
    }
}

@Composable
private fun ComposerPanel(
    input: String,
    onInputChange: (String) -> Unit,
    attachments: List<AttachmentItem>,
    connected: Boolean,
    working: Boolean,
    keyboardVisible: Boolean,
    onSend: (String) -> Unit,
    onAttach: () -> Unit,
    onClearAttachments: () -> Unit,
    onRemoveAttachment: (AttachmentItem) -> Unit,
    onAbort: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val modes = listOf(
        Triple("prompt", "Ask — send a new prompt", "ask"),
        Triple("steer", "Steer — redirect the current run", "steer"),
        Triple("follow_up", "Follow up — continue the topic", "follow"),
    )
    var selectedMode by remember { mutableStateOf("prompt") }
    var confirmAbort by remember { mutableStateOf(false) }
    val canSend = connected && (input.isNotBlank() || attachments.isNotEmpty())
    var sentPulse by remember { mutableStateOf(false) }
    val sendEnabled = canSend && !sentPulse
    fun sendWithPulse(type: String = selectedMode) {
        if (!sendEnabled) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        sentPulse = true
        onSend(type)
    }
    LaunchedEffect(sentPulse) {
        if (sentPulse) {
            delay(350)
            sentPulse = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (attachments.isNotEmpty()) {
            AttachmentChips(attachments = attachments, onRemove = onRemoveAttachment)
            Spacer(Modifier.height(6.dp))
        }

        // Mode icon chips row (compact icons with long-press tooltips)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
        ) {
            IconChipButton(
                icon = { Icon(Icons.Outlined.AttachFile, contentDescription = "Attach file", modifier = Modifier.size(20.dp)) },
                tooltip = "Attach file",
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onAttach() },
                enabled = attachments.size < 4,
                context = context,
            )
            modes.forEach { (value, tooltip, _) ->
                IconChipButton(
                    icon = {
                        when (value) {
                            "prompt" -> Icon(Icons.AutoMirrored.Outlined.Help, contentDescription = null, modifier = Modifier.size(20.dp))
                            "steer" -> Icon(Icons.Outlined.Explore, contentDescription = null, modifier = Modifier.size(20.dp))
                            else -> Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    },
                    tooltip = tooltip.substringBefore(" —"),
                    selected = selectedMode == value,
                    onClick = { selectedMode = value; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    context = context,
                )
            }
            Spacer(Modifier.weight(1f))
            IconChipButton(
                icon = { Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp)) },
                tooltip = "Abort run",
                danger = true,
                enabled = connected && working,
                onClick = { confirmAbort = true },
                context = context,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Pill input + circular send button
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text(if (connected) "Message Pi…" else "Connect to send…") },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp),
                minLines = 1,
                maxLines = if (keyboardVisible) 4 else 5,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (sendEnabled) sendWithPulse() }),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { sendWithPulse() },
                enabled = sendEnabled,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(999.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = if (sentPulse) "Sent" else "Send")
            }
        }
    }

    if (confirmAbort) {
        AlertDialog(
            onDismissRequest = { confirmAbort = false },
            title = { Text("Abort current run?") },
            text = { Text("This stops the active Pi response/tool run.") },
            dismissButton = { TextButton(onClick = { confirmAbort = false }) { Text("Cancel") } },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        confirmAbort = false
                        onAbort()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text("Abort") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun IconChipButton(
    icon: @Composable () -> Unit,
    tooltip: String,
    selected: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    context: android.content.Context,
) {
    val bg = when {
        danger && enabled -> MaterialTheme.colorScheme.errorContainer
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val fg = when {
        danger && enabled -> MaterialTheme.colorScheme.onErrorContainer
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .combinedClickable(
                onClick = { if (enabled) onClick() },
                onLongClick = {
                    Toast.makeText(context, tooltip, Toast.LENGTH_SHORT).show()
                },
                enabled = enabled,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) { icon() }
    }
}

@Composable
private fun AttachmentChips(
    attachments: List<AttachmentItem>,
    onRemove: (AttachmentItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        attachments.forEach { attachment ->
            AssistChip(
                onClick = { onRemove(attachment) },
                label = {
                    Text(
                        attachment.chipLabel,
                        maxLines = 1,
                    )
                },
                trailingIcon = { Text("×") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ChatCard(item: ChatItem, onToggleTool: () -> Unit, onCopy: () -> Unit) {
    val colors = when (item.kind) {
        ChatKind.User -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ChatKind.Assistant -> CardDefaults.cardColors(containerColor = Color.Transparent)
        ChatKind.Tool -> CardDefaults.cardColors(containerColor = toolContainerColor(item))
        ChatKind.System -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
        ChatKind.Error -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    }
    val isUser = item.kind == ChatKind.User
    val align = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    // ChatGPT-style bubbles: user bubble 18dp right-aligned; Pi messages full-width flat.
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(14.dp)
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
        ElevatedCard(
            modifier = Modifier
                .then(if (isUser) Modifier.fillMaxWidth(0.85f) else Modifier.fillMaxWidth())
                .combinedClickable(
                    onClick = { if (item.kind == ChatKind.Tool) onToggleTool() },
                    onLongClick = onCopy,
                ),
            colors = colors,
            shape = shape,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isUser) 3.dp else 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = if (item.kind == ChatKind.Assistant) 6.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (item.kind != ChatKind.Assistant) {
                    Text(
                        toolTitle(item),
                        style = MaterialTheme.typography.labelLarge,
                        color = when (item.kind) {
                            ChatKind.User -> MaterialTheme.colorScheme.onSecondaryContainer
                            ChatKind.Tool -> MaterialTheme.colorScheme.onTertiaryContainer
                            ChatKind.Error -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                }
                if (item.kind == ChatKind.Assistant) {
                    MarkdownText(text = item.text.ifBlank { "…" })
                } else {
                    Text(
                        item.text.ifBlank { "…" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = if (item.kind == ChatKind.Tool) FontFamily.Monospace else FontFamily.Default,
                        maxLines = if (item.kind == ChatKind.Tool && !item.expanded) 3 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun toolContainerColor(item: ChatItem): Color {
    return when {
        item.title.contains("failed", ignoreCase = true) -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
        item.title.contains("running", ignoreCase = true) -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
    }
}

@Composable
private fun MarkdownText(text: String) {
    val lines = text.lines()
    var i = 0
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        while (i < lines.size) {
            val trimmed = lines[i].trimStart()
            when {
                // Fenced code blocks ```
                trimmed.startsWith("```") -> {
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    i++ // skip closing fence
                    MarkdownCodeBlock(codeLines.joinToString("\n"))
                }
                trimmed.startsWith("# ") -> MarkdownHeading(trimmed.substring(2), level = 1)
                trimmed.startsWith("## ") -> MarkdownHeading(trimmed.substring(3), level = 2)
                trimmed.startsWith("### ") || trimmed.startsWith("#### ") ->
                    MarkdownHeading(trimmed.trimStart('#').trim(), level = 3)
                // Bullet lists
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            parseInlineMarkdown(trimmed.substring(2), MaterialTheme.colorScheme.surfaceVariant),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                // Numbered lists
                Regex("^\\d+[.)] ").containsMatchIn(trimmed) -> {
                    Text(
                        parseInlineMarkdown(trimmed, MaterialTheme.colorScheme.surfaceVariant),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
                trimmed.isBlank() -> Spacer(Modifier.height(6.dp))
                else -> Text(
                    parseInlineMarkdown(trimmed, MaterialTheme.colorScheme.surfaceVariant),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            i++
        }
    }
}

@Composable
private fun MarkdownCodeBlock(code: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            code,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
private fun MarkdownHeading(text: String, level: Int) {
    val style = when (level) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    Text(
        parseInlineMarkdown(text, Color.Transparent),
        style = style,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Renders **bold**, `inline code` and strips raw markers instead of printing them. */
private fun parseInlineMarkdown(line: String, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < line.length) {
        when {
            line.startsWith("**", i) -> {
                val end = line.indexOf("**", i + 2)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append(line[i]); i++
                }
            }
            line[i] == '`' -> {
                val end = line.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
                    ) { append(line.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(line[i]); i++
                }
            }
            else -> {
                append(line[i]); i++
            }
        }
    }
}

private fun sessionNickname(sessionInfo: String): String {
    val candidate = sessionInfo.substringAfterLast('•', "").trim()
    return candidate.substringAfterLast('\\').substringAfterLast('/').take(18)
}

private fun toolTitle(item: ChatItem): String {
    if (item.kind != ChatKind.Tool) return when (item.kind) {
        ChatKind.User -> "You"
        ChatKind.Assistant -> "Assistant"
        ChatKind.System -> "System"
        ChatKind.Error -> "Error"
        ChatKind.Tool -> item.title
    }
    val icon = when {
        item.title.contains("running", ignoreCase = true) -> "●"
        item.title.contains("failed", ignoreCase = true) -> "⚠"
        else -> "✓"
    }
    return if (item.expanded) "$icon ${item.title}" else "$icon ${item.title}  · tap to expand"
}

private fun handleIncoming(
    text: String,
    addMessage: (ChatKind, String, String) -> Unit,
    appendAssistantDelta: (String) -> Unit,
    upsertToolMessage: (String, String, String, Boolean) -> Unit,
    setSessionInfo: (String) -> Unit,
    setWorking: (Boolean) -> Unit,
    setSupportsBinaryFileAttachments: (Boolean) -> Unit = {},
    clearActiveAssistant: () -> Unit,
    suppressUserEcho: (String) -> Boolean,
) {
    val effects = parseIncoming(text, suppressUserEcho)
    effects.messages.forEach { addMessage(it.kind, it.title, it.text) }
    effects.assistantDeltas.forEach(appendAssistantDelta)
    effects.toolUpdates.forEach { upsertToolMessage(it.toolCallId, it.title, it.text, it.done) }
    effects.sessionInfo?.let(setSessionInfo)
    effects.working?.let(setWorking)
    effects.supportsBinaryFileAttachments?.let(setSupportsBinaryFileAttachments)
    if (effects.clearActiveAssistant) clearActiveAssistant()
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
