package com.my.kizzy.gateway

import com.kaspercito.logger.Logger
import com.my.kizzy.gateway.entities.Heartbeat
import com.my.kizzy.gateway.entities.Identify.Companion.toIdentifyPayload
import com.my.kizzy.gateway.entities.Payload
import com.my.kizzy.gateway.entities.Ready
import com.my.kizzy.gateway.entities.Resume
import com.my.kizzy.gateway.entities.op.OpCode
import com.my.kizzy.gateway.entities.op.OpCode.DISPATCH
import com.my.kizzy.gateway.entities.op.OpCode.HEARTBEAT
import com.my.kizzy.gateway.entities.op.OpCode.HELLO
import com.my.kizzy.gateway.entities.op.OpCode.IDENTIFY
import com.my.kizzy.gateway.entities.op.OpCode.INVALID_SESSION
import com.my.kizzy.gateway.entities.op.OpCode.PRESENCE_UPDATE
import com.my.kizzy.gateway.entities.op.OpCode.RECONNECT
import com.my.kizzy.gateway.entities.op.OpCode.RESUME
import com.my.kizzy.gateway.entities.presence.Presence
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Modified by Zion Huang
 */
private const val TAG = "DiscordWebSocket"

open class DiscordWebSocket(
    private val token: String,
    private val os: String = "Android",
    private val browser: String = "Discord Android",
    private val device: String = "Generic Android Device",
) : CoroutineScope {
    private val gatewayUrl = "wss://gateway.discord.gg/?v=9&encoding=json"
    private var websocket: DefaultClientWebSocketSession? = null
    private var sequence = 0
    private var sessionId: String? = null
    private var heartbeatInterval = 0L
    private var resumeGatewayUrl: String? = null
    private var heartbeatJob: Job? = null
    @Volatile
    private var connected = false

    // True from the moment connect() is called until the socket is open (or the attempt failed).
    // Closes the window where two callers could both open a gateway session.
    @Volatile
    private var connecting = false

    // False once a heartbeat is sent and until Discord ACKs it: two beats in a row without an ACK means
    // the connection is a zombie (e.g. wifi -> mobile data switch) and must be rebuilt.
    @Volatile
    private var awaitingAck = false
    private var client: HttpClient =
        HttpClient {
            install(WebSockets)
        }
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    // The live gateway session (collect loop). Separate from [retryJob] so that scheduling a retry
    // from INSIDE a failed session no longer sees its own job as "already retrying" and gives up.
    private var connectionJob: Job? = null
    private var retryJob: Job? = null
    private var currentReconnectDelay = INITIAL_RECONNECT_DELAY

    override val coroutineContext: CoroutineContext
        get() = SupervisorJob() + Dispatchers.Default

    fun connect() {
        if (connected || connecting || websocket?.isActive == true) {
            // `connecting` / `websocket?.isActive` guard the mid-handshake window: the socket is open
            // but not yet READY, so `connected` is still false — without them a second session would open.
            Logger.i(TAG, "Gateway already connected.")
            return
        }
        connecting = true
        connectionJob?.cancel()
        connectionJob =
            launch {
                try {
                    val url = resumeGatewayUrl ?: gatewayUrl
                    Logger.i(TAG, "Connecting to Discord Gateway at $url")
                    websocket =
                        client.webSocketSession(url) {
                            header("User-Agent", "Discord-Android/314013;RNA")
                            header("Accept-Language", "en-US")
                            header("Cache-Control", "no-cache")
                            header("Pragma", "no-cache")
                        }
                    // `connected` intentionally stays false here — it only flips true on READY/RESUMED.
                    // sendActivity spin-waits on `connected`, and a presence sent before IDENTIFY→READY
                    // completes is rejected by the gateway and tears down the fresh connection (#2236).
                    Logger.i(TAG, "Successfully connected to Discord Gateway.")
                    awaitingAck = false
                    connecting = false
                    currentReconnectDelay = INITIAL_RECONNECT_DELAY
                    // start receiving messages
                    websocket!!
                        .incoming
                        .receiveAsFlow()
                        .collect {
                            when (it) {
                                is Frame.Text -> {
                                    val jsonString = it.readText()
                                    onMessage(json.decodeFromString(jsonString))
                                }

                                else -> {}
                            }
                        }
                    handleClose()
                } catch (e: CancellationException) {
                    // close() (or a newer connect()) cancelled this session on purpose: do NOT schedule a
                    // reconnection, otherwise every closeRPC() would resurrect a ghost connection.
                    // `connecting` is left alone: a newer connect() may already own it.
                    throw e
                } catch (e: Exception) {
                    connecting = false
                    Logger.e(TAG, "Gateway connection error: ${e.stackTraceToString()}")
                    scheduleReconnection()
                }
            }
    }

    private fun scheduleReconnection() {
        if (retryJob?.isActive == true) {
            return
        }
        heartbeatJob?.cancel()
        connected = false
        retryJob =
            launch {
                delay(currentReconnectDelay)
                Logger.i(TAG, "Attempting to reconnect...")
                currentReconnectDelay = (currentReconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY)
                connect()
            }
    }

    /** Throws the current socket away without waiting for a close handshake that may never come. */
    private fun dropConnection() {
        connected = false
        val ws = websocket
        websocket = null
        runCatching { ws?.cancel() }
    }

    private suspend fun handleClose() {
        heartbeatJob?.cancel()
        connected = false
        val close = websocket?.closeReason?.await()
        val code = close?.code?.toInt() ?: -1
        websocket = null
        Logger.w(TAG, "Gateway closed with code: $code, reason: ${close?.message}")
        when (code) {
            // Generic/expected close → reconnect immediately.
            4000 -> {
                delay(200.milliseconds)
                connect()
            }
            // Authentication / identify failures (blank or invalid token, disallowed intents, …) are
            // NOT recoverable: reconnecting just loops forever and drains the battery (issue #2157).
            in NON_RECOVERABLE_CLOSE_CODES -> {
                // Logged under "Auth", not TAG: TAG is muted in Logger, so this line never reached any log.
                Logger.e(
                    "Auth",
                    if (code == 4004) {
                        "Discord: the gateway rejected the token (4004), not reconnecting — the user must log in to Discord again"
                    } else {
                        "Discord: gateway closed with non-recoverable code $code, not reconnecting"
                    },
                )
            }
            else -> scheduleReconnection()
        }
    }

    private suspend fun onMessage(payload: Payload) {
        Logger.i(TAG, "Gateway received: op=${payload.op}, seq=${payload.s}, event=${payload.t}")
        payload.s?.let {
            sequence = it
        }
        when (payload.op) {
            DISPATCH -> {
                payload.handleDispatch()
            }
            HEARTBEAT -> {
                sendHeartBeat()
            }
            OpCode.HEARTBEAT_ACK -> {
                awaitingAck = false
            }
            RECONNECT -> {
                reconnectWebSocket()
            }
            INVALID_SESSION -> {
                handleInvalidSession()
            }
            HELLO -> {
                payload.handleHello()
            }
            else -> {}
        }
    }

    open fun Payload.handleDispatch() {
        when (this.t.toString()) {
            "READY" -> {
                val ready = json.decodeFromJsonElement<Ready>(this.d!!)
                sessionId = ready.sessionId
                resumeGatewayUrl = ready.resumeGatewayUrl + "/?v=9&encoding=json"
                Logger.i(TAG, "Gateway READY: resume_gateway_url updated to $resumeGatewayUrl, session_id updated to $sessionId")
                connected = true
                return
            }

            "RESUMED" -> {
                // A resumed session never receives a fresh READY, so flip `connected` here too;
                // otherwise sendActivity spin-waiters would block forever.
                connected = true
                Logger.i(TAG, "Gateway: Session Resumed")
            }

            else -> {}
        }
    }

    private suspend inline fun handleInvalidSession() {
        Logger.w(TAG, "Gateway: Handling Invalid Session. Sending Identify after 150ms")
        delay(150)
        sendIdentify()
    }

    private suspend inline fun Payload.handleHello() {
        if (sequence > 0 && !sessionId.isNullOrBlank()) {
            sendResume()
        } else {
            sendIdentify()
        }
        heartbeatInterval = json.decodeFromJsonElement<Heartbeat>(this.d!!).heartbeatInterval
        Logger.i(TAG, "Gateway: Setting heartbeatInterval=$heartbeatInterval")
        startHeartbeatJob(heartbeatInterval)
    }

    private suspend fun sendHeartBeat() {
        Logger.i(TAG, "Gateway: Sending $HEARTBEAT with seq: $sequence")
        send(
            op = HEARTBEAT,
            d = if (sequence == 0) "null" else sequence.toString(),
        )
    }

    private suspend inline fun reconnectWebSocket() {
        websocket?.close(
            CloseReason(
                code = 4000,
                message = "Attempting to reconnect",
            ),
        )
    }

    private suspend fun sendIdentify() {
        Logger.i(TAG, "Gateway: Sending $IDENTIFY")
        send(
            op = IDENTIFY,
            d =
                token.toIdentifyPayload(
                    os = os,
                    browser = browser,
                    device = device,
                ),
        )
    }

    private suspend fun sendResume() {
        Logger.i(TAG, "Gateway: Sending $RESUME")
        send(
            op = RESUME,
            d =
                Resume(
                    seq = sequence,
                    sessionId = sessionId,
                    token = token,
                ),
        )
    }

    private fun startHeartbeatJob(interval: Long) {
        heartbeatJob?.cancel()
        heartbeatJob =
            launch {
                while (isActive) {
                    if (awaitingAck) {
                        Logger.w(TAG, "Gateway: no heartbeat ACK, the connection looks dead — rebuilding it")
                        dropConnection()
                        scheduleReconnection()
                        return@launch
                    }
                    awaitingAck = true
                    runCatching { sendHeartBeat() }
                        .onFailure { if (it is CancellationException) throw it }
                    delay(interval)
                }
            }
    }

    private fun isSocketConnectedToAccount(): Boolean = connected && websocket?.isActive == true

    @OptIn(DelicateCoroutinesApi::class)
    fun isWebSocketConnected(): Boolean = websocket?.incoming != null && websocket?.outgoing?.isClosedForSend == false

    private suspend inline fun <reified T> send(
        op: OpCode,
        d: T?,
    ) {
        if (websocket?.isActive == true) {
            val payload =
                json.encodeToString(
                    Payload(
                        op = op,
                        d = json.encodeToJsonElement(d),
                    ),
                )
            if (op == IDENTIFY) {
                Logger.i(TAG, "Gateway sending payload: [REDACTED IDENTIFY PAYLOAD]")
            } else {
                Logger.i(TAG, "Gateway sending payload: $payload")
            }
            websocket?.send(Frame.Text(payload))
        }
    }

    fun close() {
        retryJob?.cancel()
        retryJob = null
        connectionJob?.cancel()
        connectionJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        resumeGatewayUrl = null
        sessionId = null
        sequence = 0
        connected = false
        connecting = false
        awaitingAck = false
        // Forget the socket right away so an immediate connect() (pause → play) opens a fresh one
        // instead of being refused because the old session job is still winding down.
        val ws = websocket
        websocket = null
        if (ws != null) {
            launch(Dispatchers.IO) {
                runCatching { withTimeoutOrNull(2.seconds) { ws.close() } }
                runCatching { ws.cancel() }
                Logger.i(TAG, "Gateway: Connection to gateway closed")
            }
        }
    }

    suspend fun sendActivity(presence: Presence): Boolean {
        // Bounded wait for IDENTIFY→READY (or RESUME→RESUMED) to complete. A non-blank but invalid
        // token closes with a non-recoverable code and is never retried (see NON_RECOVERABLE_CLOSE_CODES),
        // so an unbounded spin-wait here would hang this coroutine forever.
        val ready =
            withTimeoutOrNull(30.seconds) {
                while (!isSocketConnectedToAccount()) {
                    delay(100.milliseconds)
                }
                true
            } ?: false
        if (!ready) {
            Logger.w(TAG, "Gateway not connected within 30s — presence update not delivered")
            return false
        }
        Logger.i(TAG, "Gateway: Sending $PRESENCE_UPDATE")
        send(
            op = PRESENCE_UPDATE,
            d = presence,
        )
        return true
    }

    companion object {
        private val INITIAL_RECONNECT_DELAY = 1.seconds
        private val MAX_RECONNECT_DELAY = 60.seconds

        // Discord gateway close codes signalling an unrecoverable auth/identify error. Reconnecting on
        // these would loop indefinitely with the same bad credentials (see issue #2157):
        // 4004 auth failed, 4010 invalid shard, 4011 sharding required, 4012 invalid API version,
        // 4013 invalid intent(s), 4014 disallowed intent(s).
        private val NON_RECOVERABLE_CLOSE_CODES = setOf(4004, 4010, 4011, 4012, 4013, 4014)
    }
}