package com.codex.android.app.ui

import com.codex.android.app.core.model.AccountDraft
import com.codex.android.app.core.model.CachedThread
import com.codex.android.app.core.model.ChatMessage
import com.codex.android.app.core.model.ComposerState
import com.codex.android.app.core.model.ConnectionState
import com.codex.android.app.core.model.GitHubRepo
import com.codex.android.app.core.model.GitHubSession
import com.codex.android.app.core.model.ModelOption
import com.codex.android.app.core.model.OpenAiAccountState
import com.codex.android.app.core.model.ServerAccount
import com.codex.android.app.core.model.SettingsState
import com.codex.android.app.core.model.SidebarState

data class MainUiState(
    val isLoaded: Boolean = false,
    val accounts: List<ServerAccount> = emptyList(),
    val selectedAccountId: String? = null,
    val accountDraft: AccountDraft = AccountDraft(host = "91.233.168.233"),
    val connectionState: ConnectionState = ConnectionState(),
    val threads: List<CachedThread> = emptyList(),
    val selectedThreadId: String? = null,
    val selectedThreadMessages: List<ChatMessage> = emptyList(),
    val composer: ComposerState = ComposerState(),
    val models: List<ModelOption> = emptyList(),
    val sidebar: SidebarState = SidebarState(),
    val settings: SettingsState = SettingsState(),
    val openAiAccount: OpenAiAccountState = OpenAiAccountState(),
    val gitHubSession: GitHubSession? = null,
    val deviceFlow: DeviceFlowState? = null,
    val pendingExternalUrl: String? = null,
    val bannerMessage: String? = null,
)

data class DeviceFlowState(
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Long,
)

val MainUiState.selectedAccount: ServerAccount?
    get() = accounts.firstOrNull { it.id == selectedAccountId }

val MainUiState.selectedThread: CachedThread?
    get() = threads.firstOrNull { it.threadId == selectedThreadId }

val MainUiState.availableGitHubRepos: List<GitHubRepo>
    get() = settings.availableRepos
