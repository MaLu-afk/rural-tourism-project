package yupay.turismo.data.remote

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import yupay.turismo.data.session.SessionManager
import yupay.turismo.utils.NetworkMonitor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Cliente de **Supabase Realtime** (WebSocket, protocolo Phoenix) usado como **"campana"**:
 * al recibir un evento `postgres_changes` de las tablas del usuario, dispara [onChange]
 * (normalmente `CloudSyncEngine.syncNow()`), que hace el pull incremental. NO aplica el payload
 * del socket directamente; reutiliza toda la lógica de merge ya probada en la capa de nube.
 *
 * Se conecta sólo cuando hay internet + sesión; se reconecta con backoff; re-empuja el
 * `access_token` cuando el JWT se refresca. La URL y la anon key se obtienen de `/auth/config`.
 */
class RealtimeClient(
    private val api: YupayApiService,
    private val session: SessionManager,
    private val networkMonitor: NetworkMonitor,
    private val onChange: suspend () -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // socket persistente: sin timeout de lectura
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val refCounter = AtomicInteger(0)
    private fun nextRef() = refCounter.incrementAndGet().toString()

    @Volatile private var workScope: CoroutineScope? = null
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var connected = false
    @Volatile private var openedOk = false
    @Volatile private var topic: String = ""
    @Volatile private var cachedWsUrl: String? = null
    @Volatile private var changeJob: Job? = null

    /** Arranca el ciclo de vida (conectar/desconectar según red+sesión) en [scope]. */
    fun start(scope: CoroutineScope) {
        if (workScope != null) return
        workScope = scope

        // Conectar cuando online+logueado; desconectar en caso contrario.
        scope.launch {
            combine(networkMonitor.isOnline, session.isLoggedInFlow) { online, logged -> online && logged }
                .distinctUntilChanged()
                .collectLatest { ready ->
                    if (ready) connectLoop() else disconnect()
                }
        }
        // Re-empujar el token al canal cuando se refresque (si no, Realtime cierra al expirar).
        scope.launch {
            session.sessionFlow
                .map { it?.accessToken }
                .distinctUntilChanged()
                .collect { token -> if (connected && !token.isNullOrBlank()) pushAccessToken(token) }
        }
    }

    private suspend fun connectLoop() = coroutineScope {
        var attempt = 0
        try {
            while (isActive) {
                val url = ensureWsUrl()
                val token = session.accessTokenOnce()
                val uid = session.current()?.userId
                if (url == null || token.isNullOrBlank() || uid.isNullOrBlank()) {
                    delay(5_000); continue
                }

                topic = "realtime:yupay:$uid"
                openedOk = false
                val closed = CompletableDeferred<Unit>()
                val ws = client.newWebSocket(
                    Request.Builder().url(url).build(),
                    listener(closed, token, uid)
                )
                webSocket = ws

                val hb = launch {
                    while (isActive) {
                        delay(HEARTBEAT_MS)
                        runCatching { ws.send(heartbeat()) }
                    }
                }
                try {
                    closed.await() // suspende hasta onClosed/onFailure
                } finally {
                    hb.cancel()
                    connected = false
                    runCatching { ws.cancel() }
                    if (webSocket === ws) webSocket = null
                }

                if (openedOk) {
                    attempt = 0
                    delay(1_000) // breve respiro antes de reconectar
                } else {
                    attempt = (attempt + 1).coerceAtMost(6)
                    delay((1_000L shl attempt).coerceAtMost(MAX_BACKOFF_MS))
                }
            }
        } finally {
            connected = false
            runCatching { webSocket?.cancel() }
            webSocket = null
        }
    }

    private fun listener(closed: CompletableDeferred<Unit>, token: String, uid: String) =
        object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                connected = true
                openedOk = true
                runCatching { ws.send(joinMessage(uid, token)) }
            }

            override fun onMessage(ws: WebSocket, text: String) = handleIncoming(text)

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                runCatching { ws.close(1000, null) }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (!closed.isCompleted) closed.complete(Unit)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Realtime socket caído: ${t.message}")
                if (!closed.isCompleted) closed.complete(Unit)
            }
        }

    private fun handleIncoming(text: String) {
        val event = runCatching {
            json.parseToJsonElement(text).jsonObject["event"]?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: return
        if (event == "postgres_changes") debounceChange()
    }

    /** Agrupa ráfagas de eventos en un solo disparo de sync. */
    private fun debounceChange() {
        val s = workScope ?: return
        changeJob?.cancel()
        changeJob = s.launch {
            delay(DEBOUNCE_MS)
            runCatching { onChange() }
        }
    }

    private fun pushAccessToken(token: String) {
        val ws = webSocket ?: return
        runCatching {
            ws.send(phx(topic, "access_token", buildJsonObject { put("access_token", token) }))
        }
    }

    private fun disconnect() {
        connected = false
        runCatching { webSocket?.cancel() }
        webSocket = null
    }

    // ───────────────── Mensajes Phoenix ─────────────────
    private fun joinMessage(uid: String, token: String): String {
        val changes = buildJsonArray {
            add(pgChange("visits", "user_id=eq.$uid"))
            add(pgChange("products", "user_id=eq.$uid"))
            add(pgChange("content", "user_id=eq.$uid"))
            add(pgChange("users", "id=eq.$uid"))
        }
        val payload = buildJsonObject {
            put("config", buildJsonObject { put("postgres_changes", changes) })
            put("access_token", token)
        }
        return phx(topic, "phx_join", payload)
    }

    private fun pgChange(table: String, filter: String): JsonObject = buildJsonObject {
        put("event", "*")
        put("schema", "public")
        put("table", table)
        put("filter", filter)
    }

    private fun heartbeat(): String = phx("phoenix", "heartbeat", buildJsonObject {})

    private fun phx(topic: String, event: String, payload: JsonObject): String =
        buildJsonObject {
            put("topic", topic)
            put("event", event)
            put("payload", payload)
            put("ref", nextRef())
        }.toString()

    // ───────────────── Config (URL del WebSocket) ─────────────────
    /** Deriva `wss://<ref>.supabase.co/realtime/v1/websocket?apikey=...` de /auth/config. */
    private suspend fun ensureWsUrl(): String? {
        cachedWsUrl?.let { return it }
        val cfg = when (val r = api.getConfig()) {
            is ApiResult.Ok -> r.data
            is ApiResult.Fail -> return null
        }
        val base = cfg.supabaseUrl?.trim()?.removeSuffix("/")
        val key = cfg.supabaseAnonKey?.trim()
        if (base.isNullOrBlank() || key.isNullOrBlank()) return null
        val wss = base.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        return ("$wss/realtime/v1/websocket?apikey=$key&vsn=1.0.0").also { cachedWsUrl = it }
    }

    private companion object {
        const val TAG = "RealtimeClient"
        const val HEARTBEAT_MS = 30_000L
        const val DEBOUNCE_MS = 300L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
