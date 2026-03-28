package com.codex.android.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PersistedAppState(
    val accounts: List<ServerAccount> = emptyList(),
    val selectedAccountId: String? = null,
    val conversationBindings: List<ConversationBinding> = emptyList(),
    val cachedThreads: List<CachedThread> = emptyList(),
    val gitHubSession: GitHubSession? = null,
    val gitHubRepos: List<GitHubRepo> = emptyList(),
)

@Serializable
data class ServerAccount(
    val id: String,
    val host: String,
    val port: Int,
    val username: String,
    val displayName: String,
    val homeDirectory: String? = null,
    val hostFingerprint: String? = null,
    val lastUsedAtEpochSeconds: Long = 0L,
)

@Serializable
data class GitHubSession(
    val accessToken: String,
    val userLogin: String,
    val userName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class GitHubRepo(
    val id: Long,
    val name: String,
    val fullName: String,
    val htmlUrl: String,
    val sshUrl: String,
    val defaultBranch: String? = null,
    val privateRepo: Boolean = false,
)

@Serializable
data class ConversationBinding(
    val threadId: String,
    val accountId: String,
    val isPinned: Boolean = false,
    val pinnedOrder: Long? = null,
    val pinnedRepoUrl: String? = null,
    val pinnedRepoName: String? = null,
    val pinnedRepoRemotePath: String? = null,
    val cwdOverride: String? = null,
    val lastKnownTurnId: String? = null,
)

@Serializable
data class CachedThread(
    val threadId: String,
    val accountId: String,
    val title: String,
    val preview: String,
    val cwd: String,
    val status: ThreadRuntimeStatus,
    val isArchived: Boolean = false,
    val updatedAtEpochSeconds: Long,
    val messages: List<ChatMessage> = emptyList(),
)

@Serializable
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val status: MessageStatus = MessageStatus.COMPLETE,
    val createdAtEpochSeconds: Long = 0L,
)

@Serializable
enum class ChatRole {
    USER,
    ASSISTANT,
    REASONING,
    TOOL,
}

@Serializable
enum class MessageStatus {
    STREAMING,
    COMPLETE,
    ERROR,
}

@Serializable
enum class ThreadRuntimeStatus {
    IDLE,
    RUNNING,
    WAITING_ON_APPROVAL,
    WAITING_ON_INPUT,
    RECONNECTING,
    FAILED,
}

@Serializable
data class RemoteFileNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long? = null,
    val modifiedEpochSeconds: Long? = null,
)

@Serializable
data class RemoteGitRepository(
    val name: String,
    val remoteUrl: String,
    val rootPath: String,
)

@Serializable
data class ModelOption(
    val id: String,
    val model: String,
    val displayName: String,
    val defaultReasoningEffort: ReasoningEffort,
    val supportedReasoningEfforts: List<ReasoningEffort>,
    val isDefault: Boolean,
)

@Serializable
enum class ReasoningEffort(val wireValue: String) {
    NONE("none"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh");

    companion object {
        fun fromWire(value: String?): ReasoningEffort {
            return entries.firstOrNull { it.wireValue == value } ?: MEDIUM
        }
    }
}

data class ConnectionState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val message: String? = null,
)

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED_AUTH,
    FAILED_SERVER,
}

data class AccountDraft(
    val username: String = "",
    val password: String = "",
)

data class CodexUsageWindow(
    val label: String,
    val valueLabel: String,
    val progress: Float? = null,
)

data class CodexProfile(
    val name: String,
    val isActive: Boolean,
    val email: String? = null,
    val planType: String? = null,
    val fiveHourWindow: CodexUsageWindow = CodexUsageWindow(label = "5H", valueLabel = "Sync"),
    val weeklyWindow: CodexUsageWindow = CodexUsageWindow(label = "7D", valueLabel = "Sync"),
)

data class CodexRateLimitWindow(
    val usedPercent: Int,
    val windowDurationMins: Long? = null,
    val resetsAt: Long? = null,
)

data class CodexRateLimitSnapshot(
    val limitId: String? = null,
    val limitName: String? = null,
    val primary: CodexRateLimitWindow? = null,
    val secondary: CodexRateLimitWindow? = null,
    val planType: String? = null,
)

data class ComposerState(
    val text: String = "",
    val selectedModel: String? = null,
    val selectedReasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
    val isSending: Boolean = false,
)

data class OpenAiAccountState(
    val isLoading: Boolean = false,
    val requiresOpenAiAuth: Boolean = false,
    val authMode: OpenAiAuthMode? = null,
    val email: String? = null,
    val planType: String? = null,
    val rateLimits: CodexRateLimitSnapshot? = null,
    val loginState: OpenAiLoginState = OpenAiLoginState.IDLE,
    val pendingLoginId: String? = null,
    val pendingAuthUrl: String? = null,
    val lastError: String? = null,
)

enum class OpenAiAuthMode {
    API_KEY,
    CHATGPT,
    CHATGPT_AUTH_TOKENS,
}

enum class OpenAiLoginState {
    IDLE,
    CHECKING,
    REQUIRES_LOGIN,
    REQUESTING,
    WAITING_BROWSER_AUTH,
    SIGNED_IN,
    ERROR,
}

data class SidebarState(
    val currentDirectory: String = "~",
    val remoteFiles: List<RemoteFileNode> = emptyList(),
    val currentThreadId: String? = null,
)

data class SettingsState(
    val showSettings: Boolean = false,
    val showAccountSheet: Boolean = false,
    val showCodexProfileSheet: Boolean = false,
    val availableRepos: List<GitHubRepo> = emptyList(),
    val remoteGitRepos: List<RemoteGitRepository> = emptyList(),
)
