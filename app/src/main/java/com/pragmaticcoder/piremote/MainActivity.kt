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
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.clip
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        enableEdgeToEdge()
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

// Crimson royal branding: primary #A4133C / #C9184A, accent #FF4D6D
private val PiGreen = Color(0xFFC9184A)
private val PiGreenDeep = Color(0xFF170609)
private val PiGreenDark = Color(0xFF800F2F)
private val PiGreenSoft = Color(0xFFFFCCD5)
private val PiTeal = Color(0xFFFF4D6D)
private val PiAmber = Color(0xFFF59E0B)

private val PiDarkColors = darkColorScheme(
    primary = PiGreenSoft,
    onPrimary = Color(0xFF3D0715),
    primaryContainer = Color(0xFFA4133C),
    onPrimaryContainer = Color(0xFFFFE5EA),
    secondary = PiTeal,
    secondaryContainer = Color(0xFF2B0812),
    onSecondaryContainer = Color(0xFFFFD6DE),
    tertiary = PiAmber,
    tertiaryContainer = Color(0xFF5B3A09),
    onTertiaryContainer = Color(0xFFFFF7ED),
    background = PiGreenDeep,
    surface = Color(0xFF20090E),
    surfaceVariant = Color(0xFF2B1016),
    onSurface = Color(0xFFFFF0F2),
    outline = Color(0xFFB0949C),
    error = Color(0xFFFCA5A5),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFFE4E6),
)

private val PiLightColors = lightColorScheme(
    primary = Color(0xFFA4133C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E0),
    onPrimaryContainer = Color(0xFF4A0511),
    secondary = Color(0xFFC9184A),
    secondaryContainer = Color(0xFFFFE1E7),
    onSecondaryContainer = Color(0xFF5C0E23),
    tertiary = Color(0xFFD97706),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    background = Color(0xFFFFF5F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFF7E6EA),
    outline = Color(0xFF8A6B73),
)

enum class ChatKind { User, Assistant, Tool, System, Error }

data class ChatItem(
    val id: Long,
    val kind: ChatKind,
    val title: String,
    val text: String,
    val expanded: Boolean = false,
    val ts: Long = System.currentTimeMillis(),
    val model: String? = null,
    val startedAt: Long = 0L,
    val endedAt: Long = 0L,
    val imagePaths: List<String> = emptyList(),
)

internal data class RecentSession(
    val host: String,
    val port: Int,
    val label: String,
    val lastOpened: Long,
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
    // Active backend + per-backend saved settings. Top-level host/port/token
    // always mirror the ACTIVE backend so the existing connect/UI flow works.
    var backend by remember {
        mutableStateOf(
            runCatching { BackendKind.valueOf(prefs.getString("backend", BackendKind.Pi.name) ?: BackendKind.Pi.name) }
                .getOrDefault(BackendKind.Pi)
        )
    }
    // One-time migration: seed Pi settings from legacy top-level keys.
    LaunchedEffect(Unit) {
        if (prefs.getString("pi.host", null) == null && prefs.getString("host", null) != null) {
            prefs.edit()
                .putString("pi.host", prefs.getString("host", "") ?: "")
                .putString("pi.port", prefs.getString("port", "") ?: "")
                .putString("pi.token", prefs.getString("token", "") ?: "")
                .apply()
        }
        if (prefs.getString("oc.port", null) == null) prefs.edit().putString("oc.port", "4096").apply()
    }
    fun hostKey(kind: BackendKind) = if (kind == BackendKind.Pi) "pi.host" else "oc.host"
    fun portKey(kind: BackendKind) = if (kind == BackendKind.Pi) "pi.port" else "oc.port"
    fun tokenKey(kind: BackendKind) = if (kind == BackendKind.Pi) "pi.token" else "oc.password"

    fun saveConnectionSettings() {
        prefs.edit()
            .putString(hostKey(backend), host.trim())
            .putString(portKey(backend), port.trim().ifBlank { if (backend == BackendKind.Pi) "37891" else "4096" })
            .putString(tokenKey(backend), token.trim())
            .apply()
    }

    fun loadBackendSettings(kind: BackendKind) {
        host = prefs.getString(hostKey(kind), "") ?: ""
        port = prefs.getString(portKey(kind), if (kind == BackendKind.Pi) "37891" else "4096") ?: ""
        token = prefs.getString(tokenKey(kind), "") ?: ""
    }

    var input by remember { mutableStateOf("") }
    val attachments = remember { mutableStateListOf<AttachmentItem>() }
    var connected by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    var autoSendShared by remember { mutableStateOf(prefs.getBoolean("autoSendShared", false)) }
    var keepAwake by remember { mutableStateOf(prefs.getBoolean("keepAwake", false)) }
    var pendingAutoSendShared by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var suppressNextCloseNotice by remember { mutableStateOf(false) }
    var reconnectAttempts by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Disconnected") }
    var sessionInfo by remember { mutableStateOf("No session") }
    var supportsBinaryFileAttachments by remember { mutableStateOf(false) }
    var activeAssistantId by remember { mutableStateOf<Long?>(null) }
    var scrollVersion by remember { mutableIntStateOf(0) }
    var autoConnectRequest by remember { mutableIntStateOf(0) }
    val messages = remember {
        mutableStateListOf<ChatItem>().apply {
            val historyFile = File(context.filesDir, "chat_history.json")
            if (historyFile.isFile) {
                runCatching { deserializeMessages(historyFile.readText()) }.getOrNull()?.let { addAll(it) }
            }
        }
    }
    val recentSessions = remember {
        mutableStateListOf<RecentSession>().apply {
            val raw = prefs.getString("recentSessions", null) ?: return@apply
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(RecentSession(o.optString("host"), o.optInt("port"), o.optString("label"), o.optLong("lastOpened")))
                }
            }
        }
    }
    val activeToolMessages = remember { mutableStateMapOf<String, Long>() }
    val pendingUserEchoes = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0

    val client = remember {
        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
    var webSocket by remember { mutableStateOf<WebSocket?>(null) }
    var ocEventCall by remember { mutableStateOf<okhttp3.Call?>(null) }
    var ocSessionId by remember { mutableStateOf(prefs.getString("oc.sessionId", "") ?: "") }

    // Late-bound reference so backend code can trigger a reconnect without a
    // forward declaration cycle (assigned right after connect() is defined).
    var connectRef: (Boolean) -> Unit by remember { mutableStateOf<(Boolean) -> Unit>({}) }

    fun nextId() = System.nanoTime()

    fun disconnect() {
        prefs.edit().putBoolean("autoReconnect", false).apply()
        webSocket?.close(1000, "Android disconnect")
        webSocket = null
        ocEventCall?.cancel()
        ocEventCall = null
        connected = false
        connecting = false
        working = false
        status = "Disconnected"
        reconnectAttempts = 0
    }

    /** Switches backend without losing either side's saved settings. */
    fun switchBackend(kind: BackendKind) {
        if (kind == backend) return
        saveConnectionSettings()
        disconnect()
        backend = kind
        prefs.edit().putString("backend", kind.name).apply()
        loadBackendSettings(kind)
        messages.clear()
        activeToolMessages.clear()
        activeAssistantId = null
        sessionInfo = "No session"
        working = false
        scrollVersion++
        autoConnectRequest++
    }

    fun applyConnectionUri(uriText: String): Boolean {
        val trimmed = uriText.trimStart()
        val targetBackend = when {
            trimmed.startsWith("oc-remote://") -> BackendKind.OpenCode
            trimmed.startsWith("pi-remote://") -> BackendKind.Pi
            else -> return false
        }
        val fallback = ConnectionSettings("", if (targetBackend == BackendKind.Pi) "37891" else "4096", "")
        val parsed = (
            if (targetBackend == BackendKind.OpenCode) parseOpenCodeUri(uriText, fallback)
            else parsePiRemoteUri(uriText, fallback)
            ) ?: return false
        // Loading a QR for the other backend switches to it; the current
        // backend's settings stay saved and untouched.
        if (targetBackend != backend) switchBackend(targetBackend)
        host = parsed.host
        port = parsed.port
        token = parsed.token
        prefs.edit()
            .putString(hostKey(backend), host.trim())
            .putString(portKey(backend), port.trim().ifBlank { if (backend == BackendKind.Pi) "37891" else "4096" })
            .putString(tokenKey(backend), token.trim())
            .apply()
        autoConnectRequest++
        return true
    }

    LaunchedEffect(connectionUri) {
        connectionUri?.let { applyConnectionUri(it) }
    }

    fun persistMessages() {
        runCatching {
            File(context.filesDir, "chat_history.json").writeText(serializeMessages(messages))
        }
    }

    fun persistRecentSessions() {
        runCatching {
            val arr = JSONArray()
            recentSessions.forEach { s ->
                arr.put(JSONObject().put("host", s.host).put("port", s.port).put("label", s.label).put("lastOpened", s.lastOpened))
            }
            prefs.edit().putString("recentSessions", arr.toString()).apply()
        }
    }

    fun recordRecentSession(host: String, port: Int, label: String) {
        if (host.isBlank() || port !in 1..65535) return
        val now = System.currentTimeMillis()
        val existing = recentSessions.firstOrNull { it.host == host && it.port == port }
        recentSessions.removeAll { it.host == host && it.port == port }
        recentSessions.add(0, RecentSession(host, port, label.ifBlank { existing?.label.orEmpty() }, now))
        while (recentSessions.size > 20) recentSessions.removeAt(recentSessions.lastIndex)
        persistRecentSessions()
    }

    fun refreshCurrentRecentLabel(info: String) {
        val port = port.toIntOrNull() ?: return
        val name = sessionName(info, connected = true)
        if (name.isBlank()) return
        val idx = recentSessions.indexOfFirst { it.host == host.trim() && it.port == port }
        if (idx >= 0 && recentSessions[idx].label != name) {
            recentSessions[idx] = recentSessions[idx].copy(label = name)
            persistRecentSessions()
        }
    }

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

    /** Seals the open assistant bubble with its real duration and closes it. */
    fun finalizeActiveAssistant() {
        val id = activeAssistantId ?: return
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            val old = messages[index]
            if (old.endedAt == 0L) {
                messages[index] = old.copy(endedAt = System.currentTimeMillis())
            }
        }
        activeAssistantId = null
    }

    fun appendAssistantDelta(delta: String) {
        if (delta.isEmpty()) return
        val id = activeAssistantId
        val index = id?.let { existingId -> messages.indexOfFirst { it.id == existingId } } ?: -1
        if (index >= 0) {
            val old = messages[index]
            messages[index] = old.copy(text = old.text + delta)
        } else {
            // Finalize any stale bubble so the new bubble lands AFTER previous events,
            // preserving stream order: Message A -> Tool A -> Message B ...
            finalizeActiveAssistant()
            val newId = nextId()
            activeAssistantId = newId
            val now = System.currentTimeMillis()
            messages.add(
                ChatItem(
                    id = newId,
                    kind = ChatKind.Assistant,
                    title = "Assistant",
                    text = delta,
                    ts = now,
                    startedAt = now,
                    model = modelLabel(sessionInfo).takeIf { connected },
                )
            )
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
            // A new tool card must close the open assistant bubble, otherwise
            // subsequent assistant deltas would keep appending ABOVE this card
            // and break stream ordering (A → CodeA → B → CodeB).
            finalizeActiveAssistant()
            val newId = nextId()
            activeToolMessages[toolCallId] = newId
            val now = System.currentTimeMillis()
            messages.add(ChatItem(newId, ChatKind.Tool, title, text, ts = now, startedAt = now))
        }
        if (done) {
            val doneId = activeToolMessages[toolCallId]
            val doneIdx = doneId?.let { id -> messages.indexOfFirst { it.id == id } } ?: -1
            if (doneIdx >= 0 && messages[doneIdx].endedAt == 0L) {
                messages[doneIdx] = messages[doneIdx].copy(endedAt = System.currentTimeMillis())
            }
            activeToolMessages.remove(toolCallId)
        }
        scrollVersion++
    }

    fun toggleToolMessage(id: Long) {
        val index = messages.indexOfFirst { it.id == id && it.kind == ChatKind.Tool }
        if (index >= 0) messages[index] = messages[index].copy(expanded = !messages[index].expanded)
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

    // ---- OpenCode backend (official HTTP + SSE API) ---------------------------
    val ocSessions = remember { mutableStateListOf<OpenCodeSessionEntry>() }

    fun openCodeBaseUrl(): String =
        "http://${host.trim().ifBlank { "127.0.0.1" }}:${port.toIntOrNull() ?: 4096}"

    /** Applies protocol effects to the chat — shared by both backends' event paths. */
    fun applyEffects(effects: IncomingEffects) {
        effects.messages.forEach { addMessage(it.kind, it.title, it.text) }
        effects.assistantDeltas.forEach(::appendAssistantDelta)
        effects.toolUpdates.forEach { upsertToolMessage(it.toolCallId, it.title, it.text, it.done) }
        effects.sessionInfo?.let { info ->
            sessionInfo = info
            refreshCurrentRecentLabel(info)
        }
        effects.working?.let { working = it }
        effects.supportsBinaryFileAttachments?.let { supportsBinaryFileAttachments = it }
        if (effects.clearActiveAssistant) finalizeActiveAssistant()
    }

    fun openCodeFail(message: String) {
        mainHandler.post {
            connected = false
            connecting = false
            working = false
            status = "Error"
            addMessage(ChatKind.Error, "OpenCode", message)
            scheduleReconnect(
                mainHandler,
                reconnectAttempts++,
                shouldAutoReconnect = { prefs.getBoolean("autoReconnect", false) && host.isNotBlank() && port.isNotBlank() },
                connect = { connectRef(false) },
                setStatus = { status = it },
            )
        }
    }

    fun startOpenCodeStream(base: String, auth: String) {
        val request = Request.Builder()
            .url("$base/event")
            .header("Authorization", auth)
            .header("Accept", "text/event-stream")
            .build()
        val call = client.newCall(request)
        ocEventCall = call
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (call.isCanceled()) return
                openCodeFail(e.message ?: "Event stream failed")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { r ->
                    if (!r.isSuccessful) {
                        openCodeFail("Event stream HTTP ${r.code}")
                        return
                    }
                    val source = r.body?.source() ?: run { openCodeFail("Empty stream"); return }
                    try {
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            val data = extractSseData(line) ?: continue
                            val effects = runCatching {
                                parseOpenCodeEvent(JSONObject(data), ocSessionId.ifBlank { null })
                            }.getOrNull() ?: continue
                            mainHandler.post { applyEffects(effects) }
                        }
                        // Server closed the stream cleanly.
                        if (!call.isCanceled()) openCodeFail("Event stream closed")
                    } catch (_: java.io.IOException) {
                        if (!call.isCanceled()) openCodeFail("Event stream interrupted")
                    }
                }
            }
        })
    }

    fun renderOpenCodeHistory(effects: IncomingEffects, rawBody: String) {
        mainHandler.post {
            messages.clear()
            activeToolMessages.clear()
            activeAssistantId = null
            scrollVersion++
            applyEffects(effects)
            if (effects.messages.isEmpty()) {
                addMessage(ChatKind.System, "OpenCode", "جلسة فارغة — أرسل أول رسالة")
            }
            // Derive a status line: state • model • backend label
            val model = runCatching {
                val root = runCatching { JSONObject(rawBody) }.getOrNull()
                val arr = root?.optJSONArray("data") ?: runCatching { JSONArray(rawBody) }.getOrNull()
                var found = ""
                val n = arr?.length() ?: 0
                for (i in n - 1 downTo 0) {
                    val m = arr?.optJSONObject(i) ?: continue
                    val mo = m.optJSONObject("model") ?: m.optJSONObject("info")?.optJSONObject("model")
                    if (mo != null && mo.optString("id").isNotBlank()) {
                        found = "${mo.optString("providerID")}/${mo.optString("id")}"
                        break
                    }
                }
                found
            }.getOrDefault("").ifBlank { "opencode" }
            sessionInfo = "Idle • $model • OpenCode"
        }
    }

    fun loadOpenCodeLegacyHistory(id: String, auth: String) {
        val request = Request.Builder()
            .url("${openCodeBaseUrl()}/session/$id/message?limit=100")
            .header("Authorization", auth)
            .get()
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string().orEmpty()
                response.close()
                renderOpenCodeHistory(parseOpenCodeLegacyHistory(body), body)
            }
        })
    }

    fun loadOpenCodeHistory() {
        val id = ocSessionId
        if (id.isBlank()) return
        val auth = openCodeAuthHeader(token.trim())
        val v2Request = Request.Builder()
            .url("${openCodeBaseUrl()}/api/session/$id/message?limit=100")
            .header("Authorization", auth)
            .get()
            .build()
        client.newCall(v2Request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string().orEmpty()
                response.close()
                // v2 storage may be empty for sessions created by older server
                // instances — fall back to the legacy endpoint in that case.
                val v2Count = runCatching {
                    JSONObject(body).optJSONArray("data")?.length() ?: 0
                }.getOrDefault(0)
                if (v2Count > 0) {
                    renderOpenCodeHistory(parseOpenCodeHistory(body), body)
                } else {
                    loadOpenCodeLegacyHistory(id, auth)
                }
            }
        })
    }

    fun refreshOcSessions() {
        if (backend != BackendKind.OpenCode) return
        val request = Request.Builder()
            .url("${openCodeBaseUrl()}/api/session")
            .header("Authorization", openCodeAuthHeader(token.trim()))
            .get()
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string().orEmpty()
                response.close()
                val parsed = parseOpenCodeSessions(body)
                mainHandler.post {
                    ocSessions.clear()
                    ocSessions.addAll(parsed.take(20))
                    if (ocSessionId.isBlank() && parsed.isNotEmpty()) {
                        ocSessionId = parsed.first().id
                        prefs.edit().putString("oc.sessionId", ocSessionId).apply()
                        if (connected) loadOpenCodeHistory()
                    }
                }
            }
        })
    }

    fun connectOpenCode(clearMessages: Boolean = false) {
        if (connecting) return
        ocEventCall?.cancel()
        ocEventCall = null
        val cleanHost = host.trim()
        val cleanPort = port.trim().ifBlank { "4096" }
        val sessionKey = "oc:$cleanHost:$cleanPort:${ocSessionId.take(12)}"
        if (clearMessages) {
            messages.clear()
            activeToolMessages.clear()
            activeAssistantId = null
            scrollVersion++
        } else {
            val previousKey = prefs.getString("lastSessionKey", null)
            if (previousKey != null && previousKey != sessionKey && messages.isNotEmpty()) {
                persistMessages()
                messages.clear()
                activeToolMessages.clear()
                activeAssistantId = null
                scrollVersion++
            }
            prefs.edit().putString("lastSessionKey", sessionKey).apply()
        }
        saveConnectionSettings()
        prefs.edit().putBoolean("autoReconnect", true).apply()
        connecting = true
        status = "Connecting..."
        val base = openCodeBaseUrl()
        val auth = openCodeAuthHeader(token.trim())
        val health = Request.Builder().url("$base/global/health").header("Authorization", auth).build()
        client.newCall(health).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                openCodeFail("Cannot reach OpenCode server (${e.message ?: "network error"})")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val ok = response.isSuccessful
                val code = response.code
                response.close()
                when {
                    code == 401 -> openCodeFail("Authentication failed — check the server password in settings.")
                    !ok -> openCodeFail("OpenCode server HTTP $code")
                    else -> mainHandler.post {
                        connected = true
                        connecting = false
                        showSettings = false
                        working = false
                        supportsBinaryFileAttachments = false
                        status = "Connected"
                        reconnectAttempts = 0
                        recordRecentSession(cleanHost, cleanPort.toIntOrNull() ?: 4096, "")
                        loadOpenCodeHistory()
                        refreshOcSessions()
                        startOpenCodeStream(base, auth)
                    }
                }
            }
        })
    }

    fun sendOpenCode(type: String, text: String?) {
        if (!connected) {
            addMessage(ChatKind.Error, "Not connected", "Connect to OpenCode first.")
            return
        }
        if (type == "abort") {
            val request = Request.Builder()
                .url("${openCodeBaseUrl()}/api/session/$ocSessionId/interrupt")
                .header("Authorization", openCodeAuthHeader(token.trim()))
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    mainHandler.post { addMessage(ChatKind.Error, "Abort failed", e.message ?: "") }
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
            })
            addMessage(ChatKind.System, "Abort", "Abort requested")
            return
        }
        if (type !in listOf("prompt", "steer", "follow_up")) return
        if (attachments.isNotEmpty()) {
            addMessage(ChatKind.System, "Attachments", "File attachments are not supported on the OpenCode backend yet — sending text only.")
        }
        val promptText = text.orEmpty()
        if (promptText.isBlank()) return

        fun postPrompt(sessionId: String) {
            val delivery = when (type) {
                "steer" -> "steer"
                "follow_up" -> "queue"
                else -> null
            }
            val payload = JSONObject().put("prompt", JSONObject().put("text", promptText))
            if (delivery != null && working) payload.put("delivery", delivery)
            val request = Request.Builder()
                .url("${openCodeBaseUrl()}/api/session/$sessionId/prompt")
                .header("Authorization", openCodeAuthHeader(token.trim()))
                .post(okhttp3.RequestBody.create("application/json".toMediaType(), payload.toString()))
                .build()
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    mainHandler.post { addMessage(ChatKind.Error, "Send failed", e.message ?: "Request failed") }
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val ok = response.isSuccessful
                    val errBody = if (!ok) response.body?.string().orEmpty().take(200) else ""
                    response.close()
                    mainHandler.post {
                        if (!ok) {
                            addMessage(ChatKind.Error, "Send failed", errBody.ifBlank { "HTTP error" })
                        } else {
                            messages.add(ChatItem(nextId(), ChatKind.User, type.replace('_', ' ').replaceFirstChar { it.uppercase() }, promptText, ts = System.currentTimeMillis()))
                            scrollVersion++
                            attachments.clear()
                        }
                    }
                }
            })
        }

        if (ocSessionId.isBlank()) {
            val request = Request.Builder()
                .url("${openCodeBaseUrl()}/api/session")
                .header("Authorization", openCodeAuthHeader(token.trim()))
                .post(okhttp3.RequestBody.create("application/json".toMediaType(), JSONObject().put("title", "Phone session").toString()))
                .build()
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    mainHandler.post { addMessage(ChatKind.Error, "New session failed", e.message ?: "") }
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val body = response.body?.string().orEmpty()
                    response.close()
                    val id = runCatching {
                        JSONObject(body).optJSONObject("data")?.optString("id").orEmpty()
                    }.getOrDefault("")
                    mainHandler.post {
                        if (id.startsWith("ses")) {
                            ocSessionId = id
                            prefs.edit().putString("oc.sessionId", id).apply()
                            postPrompt(id)
                        } else {
                            addMessage(ChatKind.Error, "New session failed", body.take(200))
                        }
                    }
                }
            })
        } else {
            postPrompt(ocSessionId)
        }
    }

    fun sendJson(type: String, text: String? = null) {
        if (backend == BackendKind.OpenCode) {
            sendOpenCode(type, text)
            return
        }
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
                // Persist image attachments locally so the echo can render real previews.
                val previewPaths = mutableListOf<String>()
                for (attachment in attachments) {
                    val b64 = attachment.base64 ?: continue
                    runCatching {
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                        val file = java.io.File(context.cacheDir, "preview-${System.nanoTime()}-${attachment.name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
                        file.writeBytes(bytes)
                        previewPaths.add(file.absolutePath)
                    }
                }
                val fileNames = attachments.filter { it.base64 == null }.map { it.name }
                val displayedText = listOfNotNull(
                    text.orEmpty().takeIf { it.isNotBlank() },
                    fileNames.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Attachments: ") { it },
                ).joinToString("\n")
                if (displayedText.isNotBlank()) pendingUserEchoes.add(displayedText)
                if (pendingUserEchoes.size > 10) pendingUserEchoes.removeRange(0, pendingUserEchoes.size - 10)
                messages.add(ChatItem(nextId(), ChatKind.User, type.replace('_', ' ').replaceFirstChar { it.uppercase() }, displayedText, ts = System.currentTimeMillis(), imagePaths = previewPaths))
                scrollVersion++
                if (messages.size > 250) messages.removeRange(0, messages.size - 250)
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

    fun connect(clearMessages: Boolean = false) {
        if (backend == BackendKind.OpenCode) {
            connectOpenCode(clearMessages)
            return
        }
        if (connecting) return
        if (webSocket != null) suppressNextCloseNotice = true
        webSocket?.close(1000, "Reconnect")
        val cleanHost = host.trim()
        val cleanPort = port.trim().ifBlank { "37891" }
        val sessionKey = "$cleanHost:$cleanPort"
        if (clearMessages) {
            messages.clear()
            activeToolMessages.clear()
            activeAssistantId = null
            scrollVersion++
        } else {
            // Switching to a DIFFERENT session starts a fresh transcript.
            // Reconnecting to the SAME session keeps the locally persisted history.
            val previousKey = prefs.getString("lastSessionKey", null)
            if (previousKey != null && previousKey != sessionKey && messages.isNotEmpty()) {
                persistMessages()
                messages.clear()
                activeToolMessages.clear()
                activeAssistantId = null
                scrollVersion++
            }
            prefs.edit().putString("lastSessionKey", sessionKey).apply()
        }
        saveConnectionSettings()
        prefs.edit().putBoolean("autoReconnect", true).apply()
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
                    recordRecentSession(cleanHost, cleanPort.toIntOrNull() ?: 37891, "")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                mainHandler.post {
                    handleIncoming(
                        text = text,
                        addMessage = ::addMessage,
                        appendAssistantDelta = ::appendAssistantDelta,
                        upsertToolMessage = ::upsertToolMessage,
                        setSessionInfo = { info ->
                            sessionInfo = info
                            refreshCurrentRecentLabel(info)
                        },
                        setWorking = { working = it },
                        setSupportsBinaryFileAttachments = { supportsBinaryFileAttachments = it },
                        clearActiveAssistant = { finalizeActiveAssistant() },
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
                        if (!prefs.getBoolean("autoReconnect", false)) addMessage(ChatKind.System, "Disconnected", "$code $reason")
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
                    if (!prefs.getBoolean("autoReconnect", false)) addMessage(ChatKind.Error, "Connection error", t.message ?: "Unknown error")
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
        connectRef = { clear -> connect(clear) }
    }

    val liveSessions = remember { mutableStateListOf<org.json.JSONObject>() }

    fun fetchLiveSessions() {
        if (backend == BackendKind.OpenCode) {
            refreshOcSessions()
            return
        }
        if (host.isBlank() || port.isBlank()) return
        val url = Uri.Builder().scheme("http").encodedAuthority("${host.trim()}:${port.toIntOrNull() ?: 37890}").appendPath("admin").appendPath("sessions").build().toString()
        val request = Request.Builder().url(url).header("Authorization", "Bearer ${token.trim()}").get().build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string().orEmpty()
                mainHandler.post {
                    liveSessions.clear()
                    runCatching {
                        val arr = org.json.JSONObject(body).optJSONArray("live")
                        for (i in 0 until (arr?.length() ?: 0)) arr?.optJSONObject(i)?.let { liveSessions.add(it) }
                    }
                }
            }
        })
    }

    fun spawnSession() {
        if (backend == BackendKind.OpenCode) {
            if (!connected && host.isBlank()) {
                addMessage(ChatKind.Error, "New session", "Configure the OpenCode server first.")
                return
            }
            val request = Request.Builder()
                .url("${openCodeBaseUrl()}/api/session")
                .header("Authorization", openCodeAuthHeader(token.trim()))
                .post(okhttp3.RequestBody.create("application/json".toMediaType(), JSONObject().put("title", "Phone session").toString()))
                .build()
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    mainHandler.post { addMessage(ChatKind.Error, "New session", e.message ?: "Request failed") }
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val body = response.body?.string().orEmpty()
                    response.close()
                    val id = runCatching { JSONObject(body).optJSONObject("data")?.optString("id").orEmpty() }.getOrDefault("")
                    mainHandler.post {
                        if (id.startsWith("ses")) {
                            ocSessionId = id
                            prefs.edit().putString("oc.sessionId", id).apply()
                            addMessage(ChatKind.System, "New session", "OpenCode session created. Connecting…")
                            connect(clearMessages = true)
                        } else {
                            addMessage(ChatKind.Error, "New session", body.take(200))
                        }
                    }
                }
            })
            return
        }
        if (host.isBlank() || port.isBlank()) {
            addMessage(ChatKind.Error, "Spawn failed", "Set host and port in settings first.")
            return
        }
        val url = Uri.Builder().scheme("http").encodedAuthority("${host.trim()}:${port.toIntOrNull() ?: 37890}").appendPath("admin").appendPath("sessions").appendPath("spawn").build().toString()
        val request = Request.Builder().url(url).header("Authorization", "Bearer ${token.trim()}").post(okhttp3.RequestBody.create(null, ByteArray(0))).build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                mainHandler.post { addMessage(ChatKind.Error, "New session", e.message ?: "Request failed") }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string().orEmpty()
                mainHandler.post {
                    if (response.isSuccessful) {
                        val pid = runCatching { org.json.JSONObject(body).optInt("pid") }.getOrDefault(0)
                        addMessage(ChatKind.System, "New session", if (pid > 0) "Pi session created (pid $pid). Connecting…" else "Pi session created. Connecting…")
                        connect(clearMessages = true)
                    } else {
                        addMessage(ChatKind.Error, "New session", "HTTP ${response.code}")
                    }
                }
            }
        })
    }

    fun shouldAutoReconnect(): Boolean =
        prefs.getBoolean("autoReconnect", false) && host.isNotBlank() && port.isNotBlank() && token.isNotBlank()

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

    // ---- Scroll architecture (chat-grade) -------------------------------------
    // stickToBottom is TRUE only while the user is effectively at the latest
    // message. Opening the keyboard NEVER scrolls; new content only snaps when
    // stuck. Explicit scroll-to-bottom happens solely via the FAB or sending.
    var stickToBottom by remember { mutableStateOf(true) }

    val isAtBottom by remember {
        derivedStateOf {
            val li = listState.layoutInfo
            val last = li.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= li.totalItemsCount - 1 &&
                (li.viewportEndOffset - (last.offset + last.size)) > -180
        }
    }

    // Re-evaluate stickiness whenever the user finishes a scroll gesture.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) stickToBottom = isAtBottom
        }
    }

    // New content: pin ONLY when already stuck. Instant snap (no animation)
    // keeps the last messages glued under the keyboard edge.
    LaunchedEffect(scrollVersion) {
        if (messages.isNotEmpty() && stickToBottom) {
            withFrameNanos { }
            listState.scrollToItem(messages.lastIndex)
        }
    }

    // Keyboard inset animation: re-pin EVERY frame for the duration of the IME
    // open/close animation, so the last message stays glued just above the
    // input bar (single-shot pinning races the animation and leaves the chat
    // stranded at the top with a blank gap).
    LaunchedEffect(keyboardVisible) {
        if (messages.isNotEmpty() && stickToBottom) {
            val deadline = System.nanoTime() + 500_000_000L
            while (System.nanoTime() < deadline) {
                withFrameNanos { }
                if (!stickToBottom) break
                listState.scrollToItem(messages.lastIndex)
            }
        }
    }

    // First composition (restored session): jump straight to the latest message
    // without animation and without waiting for network.
    LaunchedEffect(Unit) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    // Persist transcript (debounced) on any content change, incl. streamed deltas.
    LaunchedEffect(Unit) {
        snapshotFlow {
            messages.size to (messages.lastOrNull()?.text?.length ?: 0)
        }.collect {
            delay(600)
            persistMessages()
        }
    }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) PiDarkColors else PiLightColors) {
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        LaunchedEffect(drawerState) {
            snapshotFlow { drawerState.isOpen }.collect { if (it) fetchLiveSessions() }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    AppDrawerContent(
                    connected = connected,
                    connecting = connecting,
                    sessionInfo = sessionInfo,
                    backend = backend,
                    onSwitchBackend = { kind -> switchBackend(kind) },
                    ocSessions = ocSessions.toList(),
                    activeOcSessionId = ocSessionId,
                    onSelectOcSession = { entry ->
                        val changed = entry.id != ocSessionId
                        ocSessionId = entry.id
                        prefs.edit().putString("oc.sessionId", entry.id).apply()
                        if (changed || !connected) connect(clearMessages = true)
                        else loadOpenCodeHistory()
                    },
                    sessions = recentSessions.toList(),
                    currentSessionKey = "$host.trim():${port.toIntOrNull() ?: -1}",
                    onConnect = { connect() },
                    onDisconnect = ::disconnect,
                    onSelectSession = { s ->
                        host = s.host
                        port = s.port.toString()
                        saveConnectionSettings()
                        connect()
                    },
                    onCopyLatest = { copyText("Assistant", latestAssistantText()) },
                    onNewSession = { spawnSession() },
                    liveSessions = liveSessions.toList(),
                    onClear = {
                        messages.clear()
                        activeAssistantId = null
                        activeToolMessages.clear()
                        persistMessages()
                    },
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                )
                }
            },
        ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Scaffold(
            topBar = {
                MinimalTopBar(
                    connected = connected,
                    connecting = connecting,
                    sessionName = sessionName(sessionInfo, connected),
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
                    .consumeWindowInsets(padding)
                    .imePadding()
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

                    // Scroll-to-latest FAB (ChatGPT style): visible only when the
                    // user has scrolled away from the bottom.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = messages.isNotEmpty() && !isAtBottom,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    stickToBottom = true
                                    listState.animateScrollToItem(messages.lastIndex)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Scroll to latest")
                        }
                    }
                }

                // Real working state derived from actual Pi events only.
                if (connected && working) {
                    val runningToolTitle = messages.lastOrNull { m -> activeToolMessages.values.contains(m.id) }?.title.orEmpty()
                    val subStatus = if (runningToolTitle.contains("running", ignoreCase = true)) "ينفذ أمرًا…" else "يفكر الآن…"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(PiTeal),
                        )
                        Text(subStatus, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }

                ComposerPanel(
                    input = input,
                    onInputChange = { input = it },
                    attachments = attachments,
                    connected = connected,
                    working = working,
                    keyboardVisible = keyboardVisible,
                    backendLabel = backend.label,
                    onSend = { type ->
                        sendJson(type, input)
                        input = ""
                        stickToBottom = true
                    },
                    onAttach = { picker.launch("*/*") },
                    onClearAttachments = { attachments.clear() },
                    onRemoveAttachment = { attachment -> attachments.remove(attachment) },
                onAbort = { sendJson("abort") },
                )
            }
        }
        }
        }
        }

        if (showSettings) {
            SettingsSheet(
                backend = backend,
                onBackendChange = { kind -> switchBackend(kind) },
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
                            .setPrompt("Scan Pi or OpenCode Remote QR")
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

private fun sessionName(sessionInfo: String, connected: Boolean): String {
    val cwd = sessionInfo.substringAfterLast('•', "").trim()
    val name = cwd.substringAfterLast('/').substringAfterLast('\\')
    return when {
        name.isNotBlank() -> name
        connected -> "Pi session"
        else -> "No active session"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinimalTopBar(
    connected: Boolean,
    connecting: Boolean,
    sessionName: String,
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
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
                            connected -> sessionName
                            else -> "Offline — $sessionName"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    modelLabel,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.outline,
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
    backend: BackendKind,
    onSwitchBackend: (BackendKind) -> Unit,
    ocSessions: List<OpenCodeSessionEntry>,
    activeOcSessionId: String,
    onSelectOcSession: (OpenCodeSessionEntry) -> Unit,
    sessions: List<RecentSession>,
    currentSessionKey: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSelectSession: (RecentSession) -> Unit,
    onCopyLatest: () -> Unit,
    onClear: () -> Unit,
    onNewSession: () -> Unit,
    liveSessions: List<org.json.JSONObject>,
    onCloseDrawer: () -> Unit,
) {
    var showAllSessions by remember { mutableStateOf(false) }
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        // Backend selector — Pi / OpenCode
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Backend", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        BackendKind.entries.forEach { kind ->
                            val selected = backend == kind
                            Surface(
                                onClick = { if (!selected && !connecting) onSwitchBackend(kind) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (selected && connected) PiGreen else Color.Transparent),
                                     )
                                     Spacer(Modifier.width(6.dp))
                                     Text(
                                         kind.label,
                                         style = MaterialTheme.typography.labelLarge,
                                         fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                         color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                     )
                                 }
                             }
                         }
                     }
                 }
             }

         Column(
             modifier = Modifier
                 .padding(horizontal = 14.dp, vertical = 20.dp)
                 .fillMaxHeight()
                 .verticalScroll(rememberScrollState()),
             verticalArrangement = Arrangement.spacedBy(8.dp),
         ) {
            // Header with explicit close button (works in RTL too)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text("πm Remote", style = MaterialTheme.typography.titleMedium)
                    Text(sessionInfo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onCloseDrawer) {
                    Icon(Icons.Filled.Close, contentDescription = "Close drawer")
                }
            }

            // Fixed horizontal actions card (RTL-compatible)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    DrawerActionCell(
                        icon = { Icon(if (connected) Icons.Filled.LinkOff else Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(22.dp)) },
                        label = if (connected) "Disconnect" else if (connecting) "Connecting…" else "Connect",
                        tint = if (connected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        enabled = !connecting,
                        onClick = { onCloseDrawer(); if (connected) onDisconnect() else onConnect() },
                        modifier = Modifier.weight(1f),
                    )
                    DrawerActionCell(
                        icon = { Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(22.dp)) },
                        label = "Clear chat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onCloseDrawer(); onClear() },
                        modifier = Modifier.weight(1f),
                    )
                    DrawerActionCell(
                        icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(22.dp)) },
                        label = "Copy last",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onCloseDrawer(); onCopyLatest() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            DrawerActionCell(
                icon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(22.dp)) },
                label = "New session",
                tint = MaterialTheme.colorScheme.primary,
                onClick = { onCloseDrawer(); onNewSession() },
                modifier = Modifier.fillMaxWidth(),
            )

            // Inline sessions — latest first, current highlighted
            Text("Sessions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (backend == BackendKind.OpenCode) {
                ocSessions.forEach { entry ->
                    val isCurrent = entry.id == activeOcSessionId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                            .clickable { onCloseDrawer(); onSelectOcSession(entry) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isCurrent) PiGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${entry.id.take(14)} · ${entry.directory.substringAfterLast('/')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isCurrent) {
                            Surface(color = PiGreen, shape = RoundedCornerShape(999.dp)) {
                                Text("ACTIVE", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
                if (ocSessions.isEmpty()) {
                    Text(
                        if (connected) "لا توجد جلسات — أرسل رسالة لإنشاء جلسة" else "اتصل بالسيرفر لعرض الجلسات",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
            liveSessions.forEach { entry ->
                val live = entry.optBoolean("live")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (live) PiGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    )
                    Column {
                        Text(
                            if (live) "Current session" else "Closed session",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (live) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        )
                        Text(
                            "#" + entry.optString("processId").take(8),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            if (liveSessions.none { it.optBoolean("live") }) {
                Text(
                    "لا توجد جلسات حية — أنشئ جلسة أو أعد تشغيل جلسة Terminal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            }
            if (sessions.isEmpty()) {
                Text(
                    "No recent sessions yet. Connect to a Pi session and it will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                val visible = if (showAllSessions) sessions else sessions.take(5)
                visible.forEach { session ->
                    val key = "${session.host}:${session.port}"
                    val isCurrent = key == currentSessionKey
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                            .clickable { onCloseDrawer(); onSelectSession(session) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isCurrent) PiGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isCurrent) "Current Session" else session.label.ifBlank { key },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isCurrent && session.label.isNotBlank()) {
                                Text(session.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } else if (!isCurrent) {
                                Text(key, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        if (isCurrent) {
                            Surface(color = PiGreen, shape = RoundedCornerShape(999.dp)) {
                                Text("ACTIVE", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
                if (sessions.size > 5) {
                    TextButton(onClick = { showAllSessions = !showAllSessions }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showAllSessions) "عرض أقل" else "عرض المزيد (${sessions.size - 5})")
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerActionCell(
    icon: @Composable () -> Unit,
    label: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides tint) { icon() }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    backend: BackendKind,
    onBackendChange: (BackendKind) -> Unit,
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
            // Backend selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                BackendKind.entries.forEach { kind ->
                    val selected = backend == kind
                    Surface(
                        onClick = { if (!selected) onBackendChange(kind) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            kind.label,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(host, onHostChange, label = { Text("Host") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(port, onPortChange, label = { Text("Port") }, modifier = Modifier.width(110.dp), singleLine = true, shape = RoundedCornerShape(14.dp))
            }
            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                label = { Text(if (backend == BackendKind.OpenCode) "Server password" else "Token") },
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
            ) { Text(if (connected) "Disconnect" else if (connecting) "Connecting…" else "Connect to ${backend.label}") }
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
    backendLabel: String,
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
                placeholder = { Text(if (connected) "Message $backendLabel…" else "Connect to send…") },
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
            text = { Text("This stops the active response/tool run.") },
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
                val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                // Header label: real identity (أنت / actual model name)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (item.kind) {
                            ChatKind.User -> "أنت"
                            ChatKind.Assistant -> item.model ?: "Pi"
                            else -> toolTitle(item)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = when (item.kind) {
                            ChatKind.User -> MaterialTheme.colorScheme.onSecondaryContainer
                            ChatKind.Assistant -> MaterialTheme.colorScheme.primary
                            ChatKind.Tool -> MaterialTheme.colorScheme.onTertiaryContainer
                            ChatKind.Error -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (item.kind == ChatKind.Tool) {
                        Icon(
                            if (item.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (item.expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (item.imagePaths.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.imagePaths.forEach { path ->
                            val bitmap = remember(path) { runCatching { android.graphics.BitmapFactory.decodeFile(path) }.getOrNull() }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Attachment preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
                if (item.kind == ChatKind.Assistant) {
                    MarkdownText(text = item.text.ifBlank { "…" })
                } else if (item.text.isNotBlank()) {
                    Text(
                        item.text.ifBlank { "…" },
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                        fontFamily = if (item.kind == ChatKind.Tool) FontFamily.Monospace else FontFamily.Default,
                        maxLines = if (item.kind == ChatKind.Tool && !item.expanded) 3 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Metadata line — only real values, never invented.
                val meta = when (item.kind) {
                    ChatKind.User -> "أنت · ${timeFmt.format(Date(item.ts))}"
                    ChatKind.Assistant -> buildString {
                        append(item.model ?: "Pi")
                        append(" · ")
                        append(timeFmt.format(Date(item.ts)))
                        if (item.endedAt > item.startedAt) {
                            append(" · ")
                            append(String.format(Locale.US, "%.1fs", (item.endedAt - item.startedAt) / 1000.0))
                        }
                    }
                    else -> null
                }
                if (meta != null) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
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
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)
    var i = 0
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        while (i < lines.size) {
            val trimmed = lines[i].trimStart()
            when {
                // Fenced code blocks ```
                trimmed.startsWith("```") -> {
                    val info = trimmed.removePrefix("```").trim()
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    i++ // skip closing fence
                    val label = when (info.lowercase()) {
                        "bash", "sh", "shell", "zsh" -> "Bash Code"
                        "output", "terminal", "log" -> "Terminal Output"
                        "" -> when {
                            codeLines.any { it.trimStart().startsWith("$ ") } -> "Terminal Output"
                            codeLines.any { it.contains("BUILD SUCCESSFUL") || it.contains("BUILD FAILED") || it.contains("Task :") } -> "Build Output"
                            codeLines.any { it.contains("Exception") || it.contains("error:", ignoreCase = true) } -> "Error"
                            else -> "Code Block"
                        }
                        else -> info.replaceFirstChar { it.uppercase() } + " Code"
                    }
                    MarkdownCodeBlock(codeLines.joinToString("\n"), label)
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
                            style = bodyStyle,
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
                    style = bodyStyle,
                )
            }
            i++
        }
    }
}

@Composable
private fun MarkdownCodeBlock(code: String, label: String) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${code.lines().size} lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(label, code))
                        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy code",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Text(
                    code,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            } else if (code.isNotBlank()) {
                Text(
                    code.lines().take(1).firstOrNull().orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MarkdownHeading(text: String, level: Int) {
    val style = when (level) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(PiTeal),
        )
        Text(
            parseInlineMarkdown(text, Color.Transparent),
            style = style.copy(textDirection = TextDirection.Content),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
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

// ---- Local transcript persistence -------------------------------------------
// Keeps the FULL ordered event stream (user/model/tool/code) across session
// switches and full app restarts. Capped to the last 400 events.

private fun serializeMessages(list: List<ChatItem>): String {
    val arr = JSONArray()
    list.takeLast(400).forEach { m ->
        arr.put(
            JSONObject()
                .put("id", m.id)
                .put("kind", m.kind.name)
                .put("title", m.title)
                .put("text", m.text)
                .put("ts", m.ts)
                .put("model", m.model ?: "")
                .put("startedAt", m.startedAt)
                .put("endedAt", m.endedAt)
        )
    }
    return arr.toString()
}

private fun deserializeMessages(json: String): List<ChatItem> {
    val arr = JSONArray(json)
    val out = mutableListOf<ChatItem>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val kind = runCatching { ChatKind.valueOf(o.optString("kind")) }.getOrDefault(ChatKind.System)
        out += ChatItem(
            id = o.optLong("id", System.nanoTime()),
            kind = kind,
            title = o.optString("title"),
            text = o.optString("text"),
            expanded = false,
            ts = o.optLong("ts", System.currentTimeMillis()),
            model = o.optString("model").takeIf { it.isNotBlank() },
            startedAt = o.optLong("startedAt", 0L),
            endedAt = o.optLong("endedAt", 0L),
        )
    }
    return out.sortedBy { it.id }
}
