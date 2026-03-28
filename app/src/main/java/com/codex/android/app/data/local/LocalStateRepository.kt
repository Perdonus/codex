package com.codex.android.app.data.local

import android.content.Context
import com.codex.android.app.BuildConfig
import com.codex.android.app.core.model.CachedThread
import com.codex.android.app.core.model.ChatMessage
import com.codex.android.app.core.model.ConversationBinding
import com.codex.android.app.core.model.GitHubRepo
import com.codex.android.app.core.model.GitHubSession
import com.codex.android.app.core.model.PersistedAppState
import com.codex.android.app.core.model.ServerAccount
import com.codex.android.app.core.model.ThreadRuntimeStatus
import com.codex.android.app.core.util.JsonFileStore
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

class LocalStateRepository(context: Context) {
    private val store = JsonFileStore(
        context = context,
        fileName = "app-state.json",
        serializer = PersistedAppState.serializer(),
        defaultValue = PersistedAppState(),
    )

    fun state(): StateFlow<PersistedAppState> = store.state()

    suspend fun load() = store.load()

    suspend fun upsertAccount(
        host: String,
        port: Int,
        username: String,
        homeDirectory: String?,
        hostFingerprint: String?,
    ): ServerAccount {
        val now = Instant.now().epochSecond
        var created: ServerAccount? = null
        store.update { current ->
            val existing = current.accounts.firstOrNull {
                it.host == host && it.port == port && it.username == username
            }
            val account = (existing ?: ServerAccount(
                id = UUID.randomUUID().toString(),
                host = host,
                port = port,
                username = username,
                displayName = "$username@$host",
            )).copy(
                homeDirectory = homeDirectory ?: existing?.homeDirectory,
                hostFingerprint = hostFingerprint ?: existing?.hostFingerprint,
                lastUsedAtEpochSeconds = now,
            )
            created = account
            current.copy(
                accounts = current.accounts.filterNot { it.id == account.id } + account,
                selectedAccountId = account.id,
            )
        }
        return checkNotNull(created)
    }

    suspend fun selectAccount(accountId: String) {
        store.update { it.copy(selectedAccountId = accountId) }
    }

    suspend fun deleteAccount(accountId: String) {
        store.update { current ->
            current.copy(
                accounts = current.accounts.filterNot { it.id == accountId },
                selectedAccountId = current.selectedAccountId.takeUnless { it == accountId }
                    ?: current.accounts.firstOrNull { it.id != accountId }?.id,
                conversationBindings = current.conversationBindings.filterNot { it.accountId == accountId },
                cachedThreads = current.cachedThreads.filterNot { it.accountId == accountId },
            )
        }
    }

    suspend fun updateGitHubSession(session: GitHubSession?, repos: List<GitHubRepo> = emptyList()) {
        store.update { it.copy(gitHubSession = session, gitHubRepos = repos.ifEmpty { it.gitHubRepos }) }
    }

    suspend fun updateGitHubRepos(repos: List<GitHubRepo>) {
        store.update { it.copy(gitHubRepos = repos) }
    }

    suspend fun upsertThreadCache(thread: CachedThread) {
        store.update { current ->
            current.copy(
                cachedThreads = current.cachedThreads.filterNot { it.threadId == thread.threadId } + thread,
            )
        }
    }

    suspend fun setThreadMessages(threadId: String, accountId: String, title: String, preview: String, cwd: String, status: ThreadRuntimeStatus, messages: List<ChatMessage>) {
        upsertThreadCache(
            CachedThread(
                threadId = threadId,
                accountId = accountId,
                title = title,
                preview = preview,
                cwd = cwd,
                status = status,
                updatedAtEpochSeconds = Instant.now().epochSecond,
                messages = messages,
            ),
        )
    }

    suspend fun upsertBinding(binding: ConversationBinding) {
        store.update { current ->
            current.copy(
                conversationBindings = current.conversationBindings.filterNot { it.threadId == binding.threadId } + binding,
            )
        }
    }

    suspend fun updateBinding(threadId: String, transform: (ConversationBinding?) -> ConversationBinding) {
        store.update { current ->
            val currentBinding = current.conversationBindings.firstOrNull { it.threadId == threadId }
            val updated = transform(currentBinding)
            current.copy(
                conversationBindings = current.conversationBindings.filterNot { it.threadId == threadId } + updated,
            )
        }
    }

    companion object {
        fun defaultDraftHost(): String = BuildConfig.DEFAULT_SERVER_HOST
    }
}

