package com.codex.android.app.data.remote.codex

import com.codex.android.app.core.model.CachedThread
import com.codex.android.app.core.model.ChatMessage
import com.codex.android.app.core.model.ChatRole
import com.codex.android.app.core.model.CodexRateLimitSnapshot
import com.codex.android.app.core.model.CodexRateLimitWindow
import com.codex.android.app.core.model.MessageStatus
import com.codex.android.app.core.model.ModelOption
import com.codex.android.app.core.model.OpenAiAuthMode
import com.codex.android.app.core.model.ReasoningEffort
import com.codex.android.app.core.model.ThreadRuntimeStatus
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class CodexAppServerClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val requestId = AtomicLong(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val _events = MutableSharedFlow<CodexEvent>(extraBufferCapacity = 128)
    private var socket: WebSocket? = null

    val events: SharedFlow<CodexEvent> = _events.asSharedFlow()

    suspend fun connect(localPort: Int) = withContext(Dispatchers.IO) {
        close()
        val opened = CompletableDeferred<Unit>()
        val failed = CompletableDeferred<Throwable>()
        val request = Request.Builder()
            .url("ws://127.0.0.1:$localPort")
            .build()
        socket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened.complete(Unit)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    failed.complete(t)
                    _events.tryEmit(CodexEvent.ConnectionProblem(t.message ?: "WebSocket failure"))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _events.tryEmit(CodexEvent.ConnectionClosed(reason))
                }
            },
        )
        opened.await()
        initialize()
        if (failed.isCompleted) throw failed.await()
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        socket?.close(1000, "closing")
        socket = null
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    suspend fun listThreads(): List<CachedThread> {
        val response = request(
            method = "thread/list",
            params = buildJsonObject {
                put("limit", JsonPrimitive(100))
                put("sortKey", JsonPrimitive("updated_at"))
            },
        )
        return response["data"]?.jsonArray.orEmpty().map(::parseThreadSummary)
    }

    suspend fun listModels(): List<ModelOption> {
        val response = request(
            method = "model/list",
            params = buildJsonObject { put("limit", JsonPrimitive(100)) },
        )
        return response["data"]?.jsonArray.orEmpty().map { model ->
            val obj = model.jsonObject
            ModelOption(
                id = obj.string("id"),
                model = obj.string("model"),
                displayName = obj.string("displayName"),
                defaultReasoningEffort = ReasoningEffort.fromWire(obj.stringOrNull("defaultReasoningEffort")),
                supportedReasoningEfforts = obj["supportedReasoningEfforts"]?.jsonArray.orEmpty().map {
                    val effort = it.jsonObject["reasoningEffort"]?.jsonPrimitive?.contentOrNull
                    ReasoningEffort.fromWire(effort)
                }.distinct(),
                isDefault = obj.bool("isDefault"),
            )
        }
    }

    suspend fun getAccount(refreshToken: Boolean = false): CodexAccountStatus {
        val response = request(
            method = "account/read",
            params = buildJsonObject {
                put("refreshToken", JsonPrimitive(refreshToken))
            },
        )
        return parseAccountStatus(response)
    }

    suspend fun getAccountRateLimits(): CodexRateLimitSnapshot {
        val response = request(
            method = "account/rateLimits/read",
            params = JsonNull,
        )
        val snapshot = response["rateLimits"]?.jsonObject ?: buildJsonObject { }
        return parseRateLimitSnapshot(snapshot)
    }

    suspend fun startChatGptLogin(): ChatGptLoginSession {
        val response = request(
            method = "account/login/start",
            params = buildJsonObject {
                put("type", JsonPrimitive("chatgpt"))
            },
        )
        return ChatGptLoginSession(
            loginId = response["loginId"]?.jsonPrimitive?.content.orEmpty(),
            authUrl = response["authUrl"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    suspend fun cancelAccountLogin(loginId: String): CancelAccountLoginResult {
        val response = request(
            method = "account/login/cancel",
            params = buildJsonObject {
                put("loginId", JsonPrimitive(loginId))
            },
        )
        return CancelAccountLoginResult(
            status = response["status"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    suspend fun logoutAccount() {
        request(
            method = "account/logout",
            params = JsonNull,
        )
    }

    suspend fun startThread(cwd: String, model: String?, developerInstructions: String?): ThreadSnapshot {
        val response = request(
            method = "thread/start",
            params = buildJsonObject {
                put("cwd", JsonPrimitive(cwd))
                put("model", model?.let(::JsonPrimitive) ?: JsonNull)
                put("personality", JsonPrimitive("pragmatic"))
                put("approvalPolicy", JsonPrimitive("never"))
                put("developerInstructions", developerInstructions?.let(::JsonPrimitive) ?: JsonNull)
            },
        )
        return parseThread(response["thread"]!!.jsonObject)
    }

    suspend fun resumeThread(threadId: String, cwd: String?, model: String?, developerInstructions: String?): ThreadSnapshot {
        val response = request(
            method = "thread/resume",
            params = buildJsonObject {
                put("threadId", JsonPrimitive(threadId))
                put("cwd", cwd?.let(::JsonPrimitive) ?: JsonNull)
                put("model", model?.let(::JsonPrimitive) ?: JsonNull)
                put("personality", JsonPrimitive("pragmatic"))
                put("approvalPolicy", JsonPrimitive("never"))
                put("developerInstructions", developerInstructions?.let(::JsonPrimitive) ?: JsonNull)
            },
        )
        return parseThread(response["thread"]!!.jsonObject)
    }

    suspend fun readThread(threadId: String): ThreadSnapshot {
        val response = request(
            method = "thread/read",
            params = buildJsonObject {
                put("threadId", JsonPrimitive(threadId))
                put("includeTurns", JsonPrimitive(true))
            },
        )
        return parseThread(response["thread"]!!.jsonObject)
    }

    suspend fun startTurn(
        threadId: String,
        text: String,
        model: String?,
        effort: ReasoningEffort,
        cwd: String?,
    ): String {
        val response = request(
            method = "turn/start",
            params = buildJsonObject {
                put("threadId", JsonPrimitive(threadId))
                put(
                    "input",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", JsonPrimitive("text"))
                                put("text", JsonPrimitive(text))
                            },
                        )
                    },
                )
                put("effort", JsonPrimitive(effort.wireValue))
                put("model", model?.let(::JsonPrimitive) ?: JsonNull)
                put("cwd", cwd?.let(::JsonPrimitive) ?: JsonNull)
                put("summary", JsonPrimitive("concise"))
                put("personality", JsonPrimitive("pragmatic"))
            },
        )
        return response["turn"]?.jsonObject?.string("id").orEmpty()
    }

    suspend fun interruptTurn(threadId: String, turnId: String) {
        request(
            method = "turn/interrupt",
            params = buildJsonObject {
                put("threadId", JsonPrimitive(threadId))
                put("turnId", JsonPrimitive(turnId))
            },
        )
    }

    private suspend fun initialize() {
        request(
            method = "initialize",
            params = buildJsonObject {
                put(
                    "clientInfo",
                    buildJsonObject {
                        put("name", JsonPrimitive("codex-android-client"))
                        put("title", JsonPrimitive("Codex Android Client"))
                        put("version", JsonPrimitive("0.1.0"))
                    },
                )
            },
        )
    }

    private suspend fun request(method: String, params: JsonElement): JsonObject {
        val id = requestId.incrementAndGet().toString()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        val message = buildJsonObject {
            put("id", JsonPrimitive(id))
            put("method", JsonPrimitive(method))
            put("params", params)
        }
        val sent = socket?.send(json.encodeToString(JsonObject.serializer(), message)) ?: false
        if (!sent) {
            pending.remove(id)
            throw IOException("WebSocket is not connected")
        }
        return deferred.await()
    }

    private fun handleMessage(text: String) {
        val payload = json.parseToJsonElement(text).jsonObject
        when {
            payload["id"] != null -> {
                val id = payload["id"]!!.jsonPrimitive.content
                val deferred = pending.remove(id) ?: return
                val error = payload["error"]
                if (error != null && error !is JsonNull) {
                    deferred.completeExceptionally(IOException(error.toString()))
                } else {
                    val result = payload["result"]
                    deferred.complete(result as? JsonObject ?: buildJsonObject { })
                }
            }

            payload["method"] != null -> {
                val method = payload["method"]!!.jsonPrimitive.content
                val params = payload["params"]?.jsonObject ?: buildJsonObject { }
                handleNotification(method, params)
            }
        }
    }

    private fun handleNotification(method: String, params: JsonObject) {
        when (method) {
            "item/agentMessage/delta" -> _events.tryEmit(
                CodexEvent.AgentMessageDelta(
                    threadId = params.string("threadId"),
                    turnId = params.string("turnId"),
                    itemId = params.string("itemId"),
                    delta = params.string("delta"),
                ),
            )

            "turn/started" -> _events.tryEmit(
                CodexEvent.TurnStarted(
                    threadId = params.string("threadId"),
                    turnId = params["turn"]?.jsonObject?.string("id").orEmpty(),
                ),
            )

            "turn/completed" -> _events.tryEmit(
                CodexEvent.TurnCompleted(
                    threadId = params.string("threadId"),
                    turnId = params["turn"]?.jsonObject?.string("id").orEmpty(),
                ),
            )

            "thread/status/changed" -> _events.tryEmit(
                CodexEvent.ThreadStatusChanged(
                    threadId = params.string("threadId"),
                    status = parseThreadStatus(params["status"]?.jsonObject),
                ),
            )

            "thread/name/updated" -> _events.tryEmit(
                CodexEvent.ThreadNameUpdated(
                    threadId = params.string("threadId"),
                    threadName = params.stringOrNull("threadName"),
                ),
            )

            "account/updated" -> _events.tryEmit(
                CodexEvent.AccountUpdated(
                    authMode = parseAuthMode(params.stringOrNull("authMode")),
                    planType = params.stringOrNull("planType"),
                ),
            )

            "account/rateLimits/updated" -> _events.tryEmit(
                CodexEvent.AccountRateLimitsUpdated(
                    snapshot = parseRateLimitSnapshot(params["rateLimits"]?.jsonObject ?: buildJsonObject { }),
                ),
            )

            "account/login/completed" -> _events.tryEmit(
                CodexEvent.AccountLoginCompleted(
                    loginId = params.stringOrNull("loginId"),
                    success = params.bool("success"),
                    error = params.stringOrNull("error"),
                ),
            )

            "thread/realtime/error", "error" -> _events.tryEmit(
                CodexEvent.ConnectionProblem(params.toString()),
            )
        }
    }

    private fun parseThreadSummary(element: JsonElement): CachedThread {
        val obj = element.jsonObject
        return CachedThread(
            threadId = obj.string("id"),
            accountId = "",
            title = obj.stringOrNull("name").orEmpty().ifBlank { obj.string("preview") },
            preview = obj.string("preview"),
            cwd = obj.string("cwd"),
            status = parseThreadStatus(obj["status"]?.jsonObject),
            isArchived = obj.boolOrFalse("archived"),
            updatedAtEpochSeconds = obj.long("updatedAt"),
        )
    }

    private fun parseThread(obj: JsonObject): ThreadSnapshot {
        val messages = obj["turns"]?.jsonArray.orEmpty().flatMap { turnElement ->
            val turn = turnElement.jsonObject
            turn["items"]?.jsonArray.orEmpty().mapNotNull { parseMessageItem(it.jsonObject) }
        }
        return ThreadSnapshot(
            threadId = obj.string("id"),
            title = obj.stringOrNull("name").orEmpty().ifBlank { obj.string("preview") },
            preview = obj.string("preview"),
            cwd = obj.string("cwd"),
            status = parseThreadStatus(obj["status"]?.jsonObject),
            updatedAtEpochSeconds = obj.long("updatedAt"),
            messages = messages,
        )
    }

    private fun parseMessageItem(item: JsonObject): ChatMessage? {
        val type = item.string("type")
        val createdAt = Instant.now().epochSecond
        return when (type) {
            "userMessage" -> {
                val contentText = item["content"]?.jsonArray.orEmpty()
                    .joinToString(separator = "\n") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                    .trim()
                if (contentText.isBlank()) null else ChatMessage(
                    id = item.string("id"),
                    role = ChatRole.USER,
                    text = contentText,
                    createdAtEpochSeconds = createdAt,
                )
            }

            "agentMessage" -> ChatMessage(
                id = item.string("id"),
                role = ChatRole.ASSISTANT,
                text = item.string("text"),
                createdAtEpochSeconds = createdAt,
            )

            "reasoning" -> {
                val summary = item["summary"]?.jsonArray.orEmpty().joinToString("\n") { it.jsonPrimitive.content }
                if (summary.isBlank()) null else ChatMessage(
                    id = item.string("id"),
                    role = ChatRole.REASONING,
                    text = summary,
                    createdAtEpochSeconds = createdAt,
                )
            }

            "commandExecution" -> {
                val output = item.stringOrNull("aggregatedOutput").orEmpty()
                if (output.isBlank()) null else ChatMessage(
                    id = item.string("id"),
                    role = ChatRole.TOOL,
                    text = output,
                    createdAtEpochSeconds = createdAt,
                )
            }

            else -> null
        }
    }

    private fun parseThreadStatus(status: JsonObject?): ThreadRuntimeStatus {
        return when (status?.string("type")) {
            "idle" -> ThreadRuntimeStatus.IDLE
            "active" -> {
                val flags = status["activeFlags"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
                when {
                    "waitingOnApproval" in flags -> ThreadRuntimeStatus.WAITING_ON_APPROVAL
                    "waitingOnUserInput" in flags -> ThreadRuntimeStatus.WAITING_ON_INPUT
                    else -> ThreadRuntimeStatus.RUNNING
                }
            }

            "systemError" -> ThreadRuntimeStatus.FAILED
            else -> ThreadRuntimeStatus.IDLE
        }
    }

    private fun parseAccountStatus(response: JsonObject): CodexAccountStatus {
        val requiresOpenAiAuth = response["requiresOpenaiAuth"]?.jsonPrimitive?.booleanOrNull ?: false
        val accountObject = response["account"]
            ?.takeUnless { it is JsonNull }
            ?.jsonObject
        val account = accountObject?.let { obj ->
            when (obj.string("type")) {
                "apiKey" -> CodexAccountInfo(
                    authMode = OpenAiAuthMode.API_KEY,
                )

                "chatgpt" -> CodexAccountInfo(
                    authMode = OpenAiAuthMode.CHATGPT,
                    email = obj.stringOrNull("email"),
                    planType = obj.stringOrNull("planType"),
                )

                "chatgptAuthTokens" -> CodexAccountInfo(
                    authMode = OpenAiAuthMode.CHATGPT_AUTH_TOKENS,
                    email = obj.stringOrNull("email"),
                    planType = obj.stringOrNull("planType"),
                )

                else -> null
            }
        }
        return CodexAccountStatus(
            requiresOpenAiAuth = requiresOpenAiAuth,
            account = account,
        )
    }

    private fun parseRateLimitSnapshot(response: JsonObject): CodexRateLimitSnapshot {
        return CodexRateLimitSnapshot(
            limitId = response.stringOrNull("limitId"),
            limitName = response.stringOrNull("limitName"),
            primary = response["primary"]
                ?.takeUnless { it is JsonNull }
                ?.jsonObject
                ?.let(::parseRateLimitWindow),
            secondary = response["secondary"]
                ?.takeUnless { it is JsonNull }
                ?.jsonObject
                ?.let(::parseRateLimitWindow),
            planType = response.stringOrNull("planType"),
        )
    }

    private fun parseRateLimitWindow(response: JsonObject): CodexRateLimitWindow {
        return CodexRateLimitWindow(
            usedPercent = response.int("usedPercent"),
            windowDurationMins = response.longOrNull("windowDurationMins"),
            resetsAt = response.longOrNull("resetsAt"),
        )
    }

    private fun parseAuthMode(value: String?): OpenAiAuthMode? {
        return when (value) {
            "apikey", "apiKey" -> OpenAiAuthMode.API_KEY
            "chatgpt" -> OpenAiAuthMode.CHATGPT
            "chatgptAuthTokens" -> OpenAiAuthMode.CHATGPT_AUTH_TOKENS
            else -> null
        }
    }

    private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.content.orEmpty()
    private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String): Int = this[key]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
    private fun JsonObject.bool(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull ?: false
    private fun JsonObject.boolOrFalse(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull ?: false
    private fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
    private fun JsonObject.longOrNull(key: String): Long? = this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
}

data class ThreadSnapshot(
    val threadId: String,
    val title: String,
    val preview: String,
    val cwd: String,
    val status: ThreadRuntimeStatus,
    val updatedAtEpochSeconds: Long,
    val messages: List<ChatMessage>,
)

sealed interface CodexEvent {
    data class AgentMessageDelta(
        val threadId: String,
        val turnId: String,
        val itemId: String,
        val delta: String,
    ) : CodexEvent

    data class TurnStarted(
        val threadId: String,
        val turnId: String,
    ) : CodexEvent

    data class TurnCompleted(
        val threadId: String,
        val turnId: String,
    ) : CodexEvent

    data class ThreadStatusChanged(
        val threadId: String,
        val status: ThreadRuntimeStatus,
    ) : CodexEvent

    data class ThreadNameUpdated(
        val threadId: String,
        val threadName: String?,
    ) : CodexEvent

    data class AccountUpdated(
        val authMode: OpenAiAuthMode?,
        val planType: String?,
    ) : CodexEvent

    data class AccountRateLimitsUpdated(
        val snapshot: CodexRateLimitSnapshot,
    ) : CodexEvent

    data class AccountLoginCompleted(
        val loginId: String?,
        val success: Boolean,
        val error: String?,
    ) : CodexEvent

    data class ConnectionProblem(val message: String) : CodexEvent
    data class ConnectionClosed(val reason: String) : CodexEvent
}

data class CodexAccountStatus(
    val requiresOpenAiAuth: Boolean,
    val account: CodexAccountInfo?,
)

data class CodexAccountInfo(
    val authMode: OpenAiAuthMode,
    val email: String? = null,
    val planType: String? = null,
)

data class ChatGptLoginSession(
    val loginId: String,
    val authUrl: String,
)

data class CancelAccountLoginResult(
    val status: String,
)
