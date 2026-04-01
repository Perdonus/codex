package com.codex.android.app.ui

import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codex.android.app.BuildConfig
import com.codex.android.app.CodexMobileApp
import com.codex.android.app.SessionKeepAliveService
import com.codex.android.app.core.model.AccountDraft
import com.codex.android.app.core.model.CachedThread
import com.codex.android.app.core.model.ChatMessage
import com.codex.android.app.core.model.ChatRole
import com.codex.android.app.core.model.ConnectionState
import com.codex.android.app.core.model.ConnectionStatus
import com.codex.android.app.core.model.ConversationBinding
import com.codex.android.app.core.model.CodexProfile
import com.codex.android.app.core.model.CodexRateLimitSnapshot
import com.codex.android.app.core.model.CodexRateLimitWindow
import com.codex.android.app.core.model.CodexUsageWindow
import com.codex.android.app.core.model.GitHubRepo
import com.codex.android.app.core.model.MessageStatus
import com.codex.android.app.core.model.ModelOption
import com.codex.android.app.core.model.OpenAiAccountState
import com.codex.android.app.core.model.OpenAiAuthMode
import com.codex.android.app.core.model.OpenAiLoginState
import com.codex.android.app.core.model.ReasoningEffort
import com.codex.android.app.core.model.RemoteFileNode
import com.codex.android.app.core.model.RemoteGitRepository
import com.codex.android.app.core.model.SidebarState
import com.codex.android.app.core.model.ThreadRuntimeStatus
import com.codex.android.app.core.util.AgentsFileManager
import com.codex.android.app.core.util.AppDiagnostics
import com.codex.android.app.core.util.SecurityProviderCompat
import com.codex.android.app.data.remote.codex.CodexAppServerClient
import com.codex.android.app.data.remote.codex.CodexAccountStatus
import com.codex.android.app.data.remote.codex.CodexEvent
import com.codex.android.app.data.remote.codex.ThreadSnapshot
import com.codex.android.app.data.remote.ssh.ManagedRemoteSession
import com.codex.android.app.data.remote.ssh.PortForwardHandle
import java.io.File
import java.time.Instant
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as CodexMobileApp).container
    private val localStateRepository = container.localStateRepository
    private val sshKeyManager = container.sshKeyManager
    private val remoteSshGateway = container.remoteSshGateway
    private val gitHubAuthService = container.gitHubAuthService

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    private var remoteSession: ManagedRemoteSession? = null
    private var portForwardHandle: PortForwardHandle? = null
    private var codexClient: CodexAppServerClient? = null
    private var codexEventsJob: Job? = null
    private var reconnectJob: Job? = null
    private var connectionJob: Job? = null
    private var keepAliveJob: Job? = null
    private var openAiPollingJob: Job? = null
    private val diagnosticEntries = ArrayDeque<String>()
    private val connectionMutex = Mutex()
    private var connectionGeneration = 0L
    private var keepAliveHeartbeatCount = 0L
    private var lastKeepAliveSuccessAt: Instant? = null

    init {
        viewModelScope.launch {
            appendDiagnostic("Приложение запущено")
            val persisted = localStateRepository.load()
            syncFromLocalState()
            persisted.selectedAccountId?.let { accountId ->
                if (sshKeyManager.hasKey(accountId)) {
                    launchConnectionTask {
                        connectAccount(accountId)
                    }
                } else {
                    promptForAccountPassword(accountId)
                }
            }
            localStateRepository.state().collectLatest {
                syncFromLocalState()
            }
        }
        viewModelScope.launch {
            uiState
                .map { state ->
                    KeepAliveDescriptor(
                        active = state.connectionState.status in setOf(
                            ConnectionStatus.CONNECTING,
                            ConnectionStatus.CONNECTED,
                            ConnectionStatus.RECONNECTING,
                        ) || state.openAiAccount.loginState == OpenAiLoginState.WAITING_BROWSER_AUTH,
                        title = state.selectedAccount?.displayName ?: "Codex Android",
                        message = keepAliveMessage(state),
                    )
                }
                .distinctUntilChanged()
                .collectLatest { descriptor ->
                    SessionKeepAliveService.sync(
                        context = getApplication(),
                        active = descriptor.active,
                        title = descriptor.title,
                        message = descriptor.message,
                    )
                }
        }
    }

    private fun launchConnectionTask(block: suspend () -> Unit) {
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            runCatching { block() }
                .onFailure { error ->
                    if (!error.isBenignCancellation()) {
                        appendDiagnostic("Ошибка задачи подключения: ${error.message ?: "unknown"}")
                    }
                }
        }
    }

    fun onDraftChanged(transform: (AccountDraft) -> AccountDraft) {
        _uiState.update { it.copy(accountDraft = transform(it.accountDraft)) }
    }

    fun toggleSettings(show: Boolean = !_uiState.value.settings.showSettings) {
        _uiState.update { it.copy(settings = it.settings.copy(showSettings = show)) }
    }

    fun toggleAccountSheet(show: Boolean = !_uiState.value.settings.showAccountSheet) {
        _uiState.update { it.copy(settings = it.settings.copy(showAccountSheet = show)) }
    }

    fun toggleCodexProfileSheet(show: Boolean = !_uiState.value.settings.showCodexProfileSheet) {
        _uiState.update { it.copy(settings = it.settings.copy(showCodexProfileSheet = show)) }
    }

    fun consumeBannerMessage() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    fun openExternalUrl(url: String) {
        _uiState.update { it.copy(pendingExternalUrl = url) }
    }

    fun consumePendingExternalUrl() {
        _uiState.update { it.copy(pendingExternalUrl = null) }
    }

    fun connectDraftAccount() {
        val draft = _uiState.value.accountDraft
        val username = draft.username.trim()
        val password = draft.password
        if (username.isBlank() || password.isBlank()) {
            showMessage("Введите логин и пароль.")
            return
        }
        launchConnectionTask {
            cancelReconnectLoop()
            appendDiagnostic("Старт входа по SSH для $username")
            appendDiagnostic("SSH provider: ${SecurityProviderCompat.currentSshProviderName()}")
            _uiState.update { it.copy(connectionState = ConnectionState(ConnectionStatus.CONNECTING, "Устанавливаю SSH-ключ...")) }
            runCatching<Unit> {
                val account = localStateRepository.upsertAccount(
                    host = BuildConfig.DEFAULT_SERVER_HOST,
                    port = BuildConfig.DEFAULT_SERVER_PORT,
                    username = username,
                    homeDirectory = null,
                    hostFingerprint = null,
                )
                val keyPair = withContext(Dispatchers.Default) {
                    sshKeyManager.generateKeyPair(
                        accountId = account.id,
                        comment = "codex-android@$username",
                    )
                }
                appendDiagnostic("SSH-ключ для аккаунта сгенерирован")
                val bootstrap = remoteSshGateway.bootstrapPasswordAuth(
                    host = BuildConfig.DEFAULT_SERVER_HOST,
                    port = BuildConfig.DEFAULT_SERVER_PORT,
                    username = username,
                    password = password,
                    publicOpenSsh = keyPair.publicOpenSsh,
                )
                localStateRepository.upsertAccount(
                    host = BuildConfig.DEFAULT_SERVER_HOST,
                    port = BuildConfig.DEFAULT_SERVER_PORT,
                    username = username,
                    homeDirectory = bootstrap.homeDirectory,
                    hostFingerprint = bootstrap.hostFingerprint,
                )
                _uiState.update {
                    it.copy(
                        accountDraft = AccountDraft(username = username),
                        settings = it.settings.copy(showAccountSheet = false),
                    )
                }
                appendDiagnostic("SSH-ключ установлен, home=${bootstrap.homeDirectory ?: "unknown"}")
                val connected = connectAccount(
                    accountId = account.id,
                    forceRestartAppServer = false,
                    bootstrapPassword = password,
                )
                check(connected) {
                    _uiState.value.connectionState.message ?: "Не удалось подключиться к серверу"
                }
            }.onFailure { error ->
                if (error.isBenignCancellation()) return@onFailure
                val message = error.message ?: "Ошибка входа"
                val status = when {
                    message.contains("Exhausted available authentication methods", ignoreCase = true) -> ConnectionStatus.FAILED_AUTH
                    else -> ConnectionStatus.FAILED_SERVER
                }
                _uiState.update {
                    it.copy(connectionState = ConnectionState(status, message))
                }
                appendDiagnostic("Ошибка bootstrap SSH: ${error.message ?: "unknown"}")
                showMessage(message)
            }
        }
    }

    fun selectAccount(accountId: String) {
        launchConnectionTask task@{
            cancelReconnectLoop()
            localStateRepository.selectAccount(accountId)
            if (!sshKeyManager.hasKey(accountId)) {
                promptForAccountPassword(accountId)
                return@task
            }
            connectAccount(accountId)
        }
    }

    fun selectCodexProfile(profileName: String) {
        launchConnectionTask task@{
            cancelReconnectLoop()
            val account = _uiState.value.selectedAccount ?: return@task
            val session = remoteSession ?: return@task
            appendDiagnostic("Переключение Codex-профиля: $profileName")
            AppDiagnostics.log("Переключение Codex-профиля: $profileName")
            _uiState.update {
                it.copy(
                    connectionState = ConnectionState(ConnectionStatus.CONNECTING, "Переключаю аккаунт Codex..."),
                    settings = it.settings.copy(showCodexProfileSheet = false),
                )
            }
            runCatching {
                session.activateCodexProfile(profileName)
                connectAccount(account.id, forceRestartAppServer = true, bootstrapPassword = null)
            }.onFailure {
                if (it.isBenignCancellation()) return@onFailure
                showMessage(it.message ?: "Не удалось переключить аккаунт Codex")
            }.onSuccess {
                appendDiagnostic("Codex-профиль активирован: $profileName")
                AppDiagnostics.log("Codex-профиль активирован: $profileName")
            }
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            if (_uiState.value.selectedAccountId == accountId) {
                closeRemoteSession()
            }
            sshKeyManager.delete(accountId)
            localStateRepository.deleteAccount(accountId)
        }
    }

    fun selectThread(threadId: String) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    selectedThreadId = threadId,
                    selectedThreadMessages = state.threads.firstOrNull { it.threadId == threadId }?.messages.orEmpty(),
                )
            }
            runCatching { codexClient?.readThread(threadId) }
                .onSuccess { snapshot ->
                    snapshot?.let {
                        applyThreadSnapshot(it, preserveSelection = true)
                        _uiState.update { state ->
                            state.copy(sidebar = state.sidebar.copy(currentThreadId = threadId, currentDirectory = it.cwd))
                        }
                        refreshDirectory(it.cwd)
                    }
                }
                .onFailure { error ->
                    if (handleConnectionFailure(error)) return@launch
                    showMessage(error.message ?: "Не удалось открыть диалог")
                }
        }
    }

    fun refreshThreads() {
        viewModelScope.launch {
            val account = _uiState.value.selectedAccount ?: return@launch
            runCatching {
                val threads = codexClient?.listThreads().orEmpty()
                    .map { it.copy(accountId = account.id) }
                for (thread in threads) {
                    localStateRepository.upsertThreadCache(thread)
                }
            }.onFailure {
                if (handleConnectionFailure(it)) return@onFailure
                showMessage(it.message ?: "Не удалось обновить диалоги")
            }
        }
    }

    fun createThread() {
        viewModelScope.launch {
            ensureThreadExists()
        }
    }

    fun updateComposerText(text: String) {
        _uiState.update { it.copy(composer = it.composer.copy(text = text)) }
    }

    fun selectModel(modelId: String) {
        _uiState.update { it.copy(composer = it.composer.copy(selectedModel = modelId)) }
    }

    fun selectReasoning(effort: ReasoningEffort) {
        _uiState.update { it.copy(composer = it.composer.copy(selectedReasoningEffort = effort)) }
    }

    fun sendMessage() {
        val content = _uiState.value.composer.text.trim()
        if (content.isBlank()) return
        if (_uiState.value.openAiAccount.requiresOpenAiAuth) {
            _uiState.update {
                it.copy(
                    openAiAccount = it.openAiAccount.copy(
                        loginState = it.openAiAccount.loginState.takeUnless { state -> state == OpenAiLoginState.IDLE }
                            ?: OpenAiLoginState.REQUIRES_LOGIN,
                    ),
                    settings = it.settings.copy(showSettings = true),
                )
            }
            showMessage("Сначала войдите в ChatGPT в настройках.")
            return
        }
        viewModelScope.launch {
            val threadId = ensureThreadExists() ?: return@launch
            val account = _uiState.value.selectedAccount ?: return@launch
            val binding = ensureBinding(threadId, account.id)
            val cwd = binding.pinnedRepoRemotePath ?: binding.cwdOverride ?: _uiState.value.selectedThread?.cwd ?: _uiState.value.sidebar.currentDirectory
            ensureAgents(threadId, binding, cwd)

            appendOrReplaceMessages(
                threadId = threadId,
                accountId = account.id,
                mutate = { current ->
                    current + ChatMessage(
                        id = "local-user-${Instant.now().toEpochMilli()}",
                        role = ChatRole.USER,
                        text = content,
                        createdAtEpochSeconds = Instant.now().epochSecond,
                    )
                },
            )
            _uiState.update {
                it.copy(
                    composer = it.composer.copy(text = "", isSending = true),
                    connectionState = ConnectionState(ConnectionStatus.CONNECTED),
                )
            }
            runCatching {
                val turnId = codexClient?.startTurn(
                    threadId = threadId,
                    text = content,
                    model = _uiState.value.composer.selectedModel,
                    effort = _uiState.value.composer.selectedReasoningEffort,
                    cwd = cwd,
                ).orEmpty()
                localStateRepository.updateBinding(threadId) { current ->
                    (current ?: ConversationBinding(threadId = threadId, accountId = account.id)).copy(
                        accountId = account.id,
                        cwdOverride = cwd,
                        lastKnownTurnId = turnId,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        composer = it.composer.copy(text = content, isSending = false),
                        connectionState = ConnectionState(ConnectionStatus.FAILED_SERVER, error.message),
                    )
                }
                if (handleConnectionFailure(error)) return@onFailure
                showMessage(error.message ?: "Не удалось отправить сообщение")
            }
        }
    }

    fun interruptActiveTurn() {
        viewModelScope.launch {
            val threadId = _uiState.value.selectedThreadId ?: return@launch
            val binding = localStateRepository.state().value.conversationBindings.firstOrNull { it.threadId == threadId } ?: return@launch
            val turnId = binding.lastKnownTurnId ?: return@launch
            runCatching { codexClient?.interruptTurn(threadId, turnId) }
                .onFailure { showMessage(it.message ?: "Не удалось остановить ответ") }
                .onSuccess { _uiState.update { it.copy(composer = it.composer.copy(isSending = false)) } }
        }
    }

    fun toggleThreadPinned(threadId: String) {
        viewModelScope.launch {
            val accountId = _uiState.value.selectedAccountId ?: return@launch
            localStateRepository.updateBinding(threadId) { current ->
                val now = Instant.now().toEpochMilli()
                val existing = current ?: ConversationBinding(threadId = threadId, accountId = accountId)
                existing.copy(
                    isPinned = !existing.isPinned,
                    pinnedOrder = if (!existing.isPinned) now else null,
                )
            }
        }
    }

    fun refreshDirectory(path: String? = null, refreshThreads: Boolean = true) {
        viewModelScope.launch {
            val session = remoteSession ?: return@launch
            val resolved = resolvePath(path ?: _uiState.value.sidebar.currentDirectory)
            runCatching {
                session.listDirectory(resolved).map {
                    RemoteFileNode(
                        path = it.path,
                        name = it.name,
                        isDirectory = it.isDirectory,
                        sizeBytes = it.sizeBytes,
                        modifiedEpochSeconds = it.modifiedEpochSeconds,
                    )
                }
            }.onSuccess { nodes ->
                _uiState.update {
                    it.copy(sidebar = SidebarState(currentDirectory = resolved, remoteFiles = nodes, currentThreadId = it.selectedThreadId))
                }
                syncFromLocalState()
                if (refreshThreads) {
                    refreshThreads()
                }
            }.onFailure {
                showMessage(it.message ?: "Не удалось открыть папку")
            }
        }
    }

    fun enterDirectory(path: String) {
        refreshDirectory(path)
    }

    fun downloadRemotePath(path: String) {
        viewModelScope.launch {
            val session = remoteSession ?: return@launch
            val resolved = resolvePath(path)
            val targetDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: getApplication<Application>().filesDir
            val fileName = resolved.substringAfterLast('/')
            val target = File(targetDir, fileName)
            runCatching { session.downloadToLocal(resolved, target) }
                .onSuccess { showMessage("Сохранено: ${it.absolutePath}") }
                .onFailure { showMessage(it.message ?: "Не удалось скачать файл") }
        }
    }

    fun renameOrMoveRemotePath(oldPath: String, targetInput: String) {
        viewModelScope.launch {
            val session = remoteSession ?: return@launch
            val source = resolvePath(oldPath)
            val target = buildMoveTargetPath(source, targetInput)
            runCatching { session.rename(source, target) }
                .onSuccess {
                    refreshDirectory(parentDirectoryOf(target))
                    showMessage("Перемещено: $target")
                }
                .onFailure { showMessage(it.message ?: "Не удалось переместить файл") }
        }
    }

    fun deleteRemotePath(path: String, isDirectory: Boolean) {
        viewModelScope.launch {
            val session = remoteSession ?: return@launch
            val resolved = resolvePath(path)
            runCatching { session.delete(resolved, isDirectory) }
                .onSuccess {
                    refreshDirectory(parentDirectoryOf(resolved))
                    showMessage("Удалено: ${resolved.substringAfterLast('/')}")
                }
                .onFailure { showMessage(it.message ?: "Не удалось удалить файл") }
        }
    }

    fun createDirectoryInCurrentPath(name: String) {
        viewModelScope.launch {
            val session = remoteSession ?: return@launch
            val target = resolvePath(name)
            runCatching { session.makeDirectory(target) }
                .onSuccess {
                    refreshDirectory(parentDirectoryOf(target))
                    showMessage("Папка создана: ${target.substringAfterLast('/')}")
                }
                .onFailure { showMessage(it.message ?: "Не удалось создать папку") }
        }
    }

    fun refreshOpenAiAccount() {
        viewModelScope.launch {
            refreshOpenAiAccountStatus(refreshToken = true)
        }
    }

    fun startOpenAiLogin() {
        viewModelScope.launch {
            _uiState.value.openAiAccount.pendingAuthUrl?.let { existingUrl ->
                openExternalUrl(existingUrl)
                return@launch
            }
            val client = codexClient ?: run {
                showMessage("Сначала подключитесь к серверу.")
                return@launch
            }
            _uiState.update {
                it.copy(
                    openAiAccount = it.openAiAccount.copy(
                        loginState = OpenAiLoginState.REQUESTING,
                        lastError = null,
                    ),
                )
            }
            runCatching { client.startChatGptLogin() }
                .onSuccess { session ->
                    _uiState.update {
                        it.copy(
                            openAiAccount = it.openAiAccount.copy(
                                requiresOpenAiAuth = true,
                                loginState = OpenAiLoginState.WAITING_BROWSER_AUTH,
                                pendingLoginId = session.loginId,
                                pendingAuthUrl = session.authUrl,
                                lastError = null,
                            ),
                            pendingExternalUrl = session.authUrl,
                        )
                    }
                    startOpenAiPolling(session.loginId)
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(
                            openAiAccount = state.openAiAccount.copy(
                                loginState = OpenAiLoginState.ERROR,
                                lastError = error.message ?: "Не удалось начать вход в ChatGPT",
                            ),
                        )
                    }
                    showMessage(error.message ?: "Не удалось запустить вход в ChatGPT")
                }
        }
    }

    fun cancelOpenAiLogin() {
        viewModelScope.launch {
            val loginId = _uiState.value.openAiAccount.pendingLoginId ?: return@launch
            val client = codexClient ?: run {
                showMessage("Сначала подключитесь к серверу.")
                return@launch
            }
            runCatching { client.cancelAccountLogin(loginId) }
                .onFailure { showMessage(it.message ?: "Не удалось отменить вход в ChatGPT") }
                .onSuccess {
                    openAiPollingJob?.cancel()
                    _uiState.update { state ->
                        state.copy(
                            openAiAccount = state.openAiAccount.copy(
                                loginState = OpenAiLoginState.REQUIRES_LOGIN,
                                pendingLoginId = null,
                                pendingAuthUrl = null,
                                lastError = null,
                            ),
                        )
                    }
                }
        }
    }

    fun logoutOpenAiAccount() {
        viewModelScope.launch {
            val client = codexClient ?: run {
                showMessage("Сначала подключитесь к серверу.")
                return@launch
            }
            runCatching { client.logoutAccount() }
                .onSuccess {
                    openAiPollingJob?.cancel()
                    refreshOpenAiAccountStatus()
                }
                .onFailure { showMessage(it.message ?: "Не удалось выйти из ChatGPT") }
        }
    }

    fun startGitHubLogin() {
        viewModelScope.launch {
            runCatching {
                val session = gitHubAuthService.beginDeviceFlow()
                _uiState.update {
                    it.copy(
                        deviceFlow = DeviceFlowState(
                            userCode = session.userCode,
                            verificationUri = session.verificationUri,
                            expiresInSeconds = session.expiresInSeconds,
                        ),
                        pendingExternalUrl = session.verificationUri,
                    )
                }
                val token = gitHubAuthService.awaitAccessToken(session.deviceCode, session.intervalSeconds)
                val githubSession = gitHubAuthService.fetchSession(token)
                val repos = gitHubAuthService.fetchRepositories(token)
                localStateRepository.updateGitHubSession(githubSession, repos)
                _uiState.update {
                    it.copy(
                        deviceFlow = null,
                        settings = it.settings.copy(availableRepos = repos),
                    )
                }
            }.onFailure {
                showMessage(it.message ?: "Не удалось войти в GitHub")
            }
        }
    }

    fun refreshGitHubRepos() {
        viewModelScope.launch {
            val token = localStateRepository.state().value.gitHubSession?.accessToken ?: return@launch
            runCatching { gitHubAuthService.fetchRepositories(token) }
                .onSuccess {
                    localStateRepository.updateGitHubRepos(it)
                    _uiState.update { state -> state.copy(settings = state.settings.copy(availableRepos = it)) }
                }
                .onFailure { showMessage(it.message ?: "Не удалось обновить репозитории GitHub") }
        }
    }

    fun scanRemoteRepositories() {
        viewModelScope.launch {
            val repos = remoteSession?.listRemoteGitRepos().orEmpty().map {
                RemoteGitRepository(name = it.name, remoteUrl = it.remoteUrl, rootPath = it.rootPath)
            }
            _uiState.update { it.copy(settings = it.settings.copy(remoteGitRepos = repos)) }
        }
    }

    fun pinRepositoryToCurrentThread(repo: GitHubRepo) {
        viewModelScope.launch {
            val threadId = _uiState.value.selectedThreadId ?: return@launch
            val accountId = _uiState.value.selectedAccountId ?: return@launch
            val remoteMatch = _uiState.value.settings.remoteGitRepos.firstOrNull {
                normalizeRepoUrl(it.remoteUrl) == normalizeRepoUrl(repo.htmlUrl)
            }
            localStateRepository.updateBinding(threadId) { current ->
                (current ?: ConversationBinding(threadId = threadId, accountId = accountId)).copy(
                    pinnedRepoUrl = repo.htmlUrl,
                    pinnedRepoName = repo.fullName,
                    pinnedRepoRemotePath = remoteMatch?.rootPath,
                    cwdOverride = remoteMatch?.rootPath ?: current?.cwdOverride,
                )
            }
            val binding = localStateRepository.state().value.conversationBindings.first { it.threadId == threadId }
            ensureAgents(threadId, binding, binding.pinnedRepoRemotePath ?: binding.cwdOverride ?: _uiState.value.sidebar.currentDirectory)
            showMessage(
                if (remoteMatch != null) "Репозиторий ${repo.fullName} закреплён: ${remoteMatch.rootPath}"
                else "Репозиторий ${repo.fullName} закреплён, но папка на сервере не найдена",
            )
        }
    }

    override fun onCleared() {
        connectionJob?.cancel()
        reconnectJob?.cancel()
        keepAliveJob?.cancel()
        codexEventsJob?.cancel()
        openAiPollingJob?.cancel()
        runCatching { portForwardHandle?.close() }
        runCatching { remoteSession?.close() }
        codexClient = null
        SessionKeepAliveService.sync(getApplication(), active = false, title = "Codex Android", message = "Остановлено")
        super.onCleared()
    }

    private suspend fun connectAccount(accountId: String): Boolean {
        return connectAccount(accountId = accountId, forceRestartAppServer = false, bootstrapPassword = null)
    }

    private suspend fun connectAccount(
        accountId: String,
        forceRestartAppServer: Boolean,
        bootstrapPassword: String?,
        fromReconnect: Boolean = false,
    ): Boolean = connectionMutex.withLock {
        if (!fromReconnect) {
            cancelReconnectLoop()
        }
        val account = localStateRepository.state().value.accounts.firstOrNull { it.id == accountId } ?: return false
        val generation = ++connectionGeneration
        closeRemoteSession(
            resetUi = !fromReconnect,
            cancelReconnect = false,
            preserveProfiles = true,
        )
        appendDiagnostic("Подключение к ${account.displayName}, restartAppServer=$forceRestartAppServer")
        _uiState.update {
            it.copy(connectionState = ConnectionState(ConnectionStatus.CONNECTING, "Подключение к ${account.displayName}"))
        }
        try {
            val session = remoteSshGateway.openSession(
                host = account.host,
                port = account.port,
                username = account.username,
                accountId = account.id,
                password = bootstrapPassword,
            )
            remoteSession = session
            val home = session.detectHomeDirectory()
            localStateRepository.upsertAccount(account.host, account.port, account.username, home, account.hostFingerprint)
            val appServerPort = session.appServerPortFor(account.username)
            session.ensureCodexAppServer(appServerPort, forceRestart = forceRestartAppServer)
            val forwarder = session.openLocalPortForward(appServerPort)
            portForwardHandle = forwarder
            val client = container.newCodexClient()
            codexClient = client
            client.connect(forwarder.localPort)
            observeCodexEvents(client, generation)
            val models = client.listModels()
            val threads = client.listThreads().map { it.copy(accountId = account.id) }
            for (thread in threads) {
                localStateRepository.upsertThreadCache(thread)
            }
            val accountStatus = client.getAccount()
            val rateLimits = runCatching { client.getAccountRateLimits() }.getOrNull()
            _uiState.update { state ->
                state.copy(
                    connectionState = ConnectionState(ConnectionStatus.CONNECTED),
                    models = models,
                    composer = state.composer.copy(selectedModel = chooseDefaultModel(models, state.composer.selectedModel)),
                    openAiAccount = mapAccountStatus(accountStatus, rateLimits = rateLimits),
                )
            }
            appendDiagnostic("SSH и Codex app-server подключены для ${account.displayName}")
            AppDiagnostics.log("Подключение установлено для ${account.displayName}")
            startKeepAliveLoop(account.id)
            refreshDirectory(home, refreshThreads = false)
            syncCodexProfiles(
                activeEmail = accountStatus.account?.email,
                activePlanType = accountStatus.account?.planType,
                rateLimits = rateLimits,
            )
            if (_uiState.value.selectedThreadId == null) {
                _uiState.value.threads.firstOrNull()?.threadId?.let(::selectThread)
            }
            true
        } catch (error: Throwable) {
            if (error.isBenignCancellation()) {
                appendDiagnostic("Подключение отменено")
                return false
            }
            appendDiagnostic("Ошибка подключения: ${error.message ?: "unknown"}")
            val authFailed = error.message?.contains("Exhausted available authentication methods", ignoreCase = true) == true
            if (authFailed && bootstrapPassword.isNullOrBlank()) {
                sshKeyManager.delete(account.id)
                promptForAccountPassword(account.id)
                showMessage("Введите пароль для повторной привязки SSH.")
            }
            _uiState.update { state ->
                state.copy(
                    connectionState = ConnectionState(
                        if (authFailed) ConnectionStatus.FAILED_AUTH else ConnectionStatus.FAILED_SERVER,
                        error.message,
                    ),
                )
            }
            false
        }
    }

    private fun observeCodexEvents(client: CodexAppServerClient, generation: Long) {
        codexEventsJob?.cancel()
        codexEventsJob = viewModelScope.launch {
            client.events.collectLatest { event ->
                if (generation != connectionGeneration) return@collectLatest
                when (event) {
                    is CodexEvent.AgentMessageDelta -> handleAgentDelta(event)
                    is CodexEvent.TurnStarted -> {
                        _uiState.update { it.copy(composer = it.composer.copy(isSending = true)) }
                        localStateRepository.updateBinding(event.threadId) { current ->
                            (current ?: ConversationBinding(threadId = event.threadId, accountId = _uiState.value.selectedAccountId.orEmpty())).copy(
                                lastKnownTurnId = event.turnId,
                            )
                        }
                    }

                    is CodexEvent.TurnCompleted -> {
                        _uiState.update { it.copy(composer = it.composer.copy(isSending = false)) }
                        codexClient?.readThread(event.threadId)?.let { snapshot ->
                            applyThreadSnapshot(snapshot, preserveSelection = true)
                        }
                    }

                    is CodexEvent.ThreadStatusChanged -> updateThreadStatus(event.threadId, event.status)
                    is CodexEvent.ThreadNameUpdated -> updateThreadTitle(event.threadId, event.threadName)
                    is CodexEvent.AccountUpdated -> refreshOpenAiAccountStatus(
                        authModeHint = event.authMode,
                        planTypeHint = event.planType,
                    )
                    is CodexEvent.AccountRateLimitsUpdated -> {
                        _uiState.update { state ->
                            state.copy(
                                openAiAccount = state.openAiAccount.copy(rateLimits = event.snapshot),
                            )
                        }
                        viewModelScope.launch {
                            syncCodexProfiles(rateLimits = event.snapshot)
                        }
                    }
                    is CodexEvent.AccountLoginCompleted -> {
                        if (event.success) {
                            refreshOpenAiAccountStatus(refreshToken = true, persistProfileOnSuccess = true)
                        } else {
                            openAiPollingJob?.cancel()
                            _uiState.update { state ->
                                state.copy(
                                    openAiAccount = state.openAiAccount.copy(
                                        loginState = OpenAiLoginState.ERROR,
                                        pendingLoginId = null,
                                        pendingAuthUrl = null,
                                        lastError = event.error ?: "Не удалось войти в ChatGPT",
                                    ),
                                )
                            }
                        }
                    }
                    is CodexEvent.ConnectionProblem -> {
                        handleConnectionProblem(event.message)
                    }

                    is CodexEvent.ConnectionClosed -> {
                        handleConnectionProblem(event.reason.ifBlank { "Соединение Codex закрыто" })
                    }
                }
            }
        }
    }

    private fun handleAgentDelta(event: CodexEvent.AgentMessageDelta) {
        val accountId = _uiState.value.selectedAccountId ?: return
        appendOrReplaceMessages(
            threadId = event.threadId,
            accountId = accountId,
            mutate = { current ->
                val existing = current.lastOrNull()
                if (existing?.id == event.itemId && existing.role == ChatRole.ASSISTANT) {
                    current.dropLast(1) + existing.copy(text = existing.text + event.delta, status = MessageStatus.STREAMING)
                } else {
                    current + ChatMessage(
                        id = event.itemId,
                        role = ChatRole.ASSISTANT,
                        text = event.delta,
                        status = MessageStatus.STREAMING,
                        createdAtEpochSeconds = Instant.now().epochSecond,
                    )
                }
            },
        )
    }

    private suspend fun ensureThreadExists(): String? {
        val existing = _uiState.value.selectedThreadId
        if (existing != null) return existing
        val account = _uiState.value.selectedAccount ?: return null
        val baseDirectory = resolvePath(_uiState.value.sidebar.currentDirectory.takeIf { it.isNotBlank() } ?: account.homeDirectory ?: "~")
        val cwd = remoteSession?.createConversationDirectory(baseDirectory) ?: baseDirectory
        val seedBinding = ConversationBinding(
            threadId = "pending",
            accountId = account.id,
            directoryScope = baseDirectory,
            cwdOverride = cwd,
        )
        val snapshot = codexClient?.startThread(
            cwd = cwd,
            model = _uiState.value.composer.selectedModel,
            developerInstructions = AgentsFileManager.buildManagedBlock(seedBinding),
        ) ?: return null
        val binding = ConversationBinding(
            threadId = snapshot.threadId,
            accountId = account.id,
            directoryScope = baseDirectory,
            cwdOverride = snapshot.cwd,
        )
        localStateRepository.upsertBinding(binding)
        applyThreadSnapshot(snapshot, preserveSelection = false)
        ensureAgents(snapshot.threadId, binding, snapshot.cwd)
        _uiState.update {
            it.copy(
                selectedThreadId = snapshot.threadId,
                selectedThreadMessages = snapshot.messages,
                sidebar = it.sidebar.copy(currentThreadId = snapshot.threadId, currentDirectory = snapshot.cwd),
            )
        }
        return snapshot.threadId
    }

    private suspend fun ensureBinding(threadId: String, accountId: String): ConversationBinding {
        val existing = localStateRepository.state().value.conversationBindings.firstOrNull { it.threadId == threadId }
        if (existing != null) return existing
        val cwd = _uiState.value.selectedThread?.cwd
        val created = ConversationBinding(
            threadId = threadId,
            accountId = accountId,
            directoryScope = cwd?.let(::parentDirectoryOf) ?: _uiState.value.sidebar.currentDirectory,
            cwdOverride = cwd,
        )
        localStateRepository.upsertBinding(created)
        return created
    }

    private suspend fun ensureAgents(threadId: String, binding: ConversationBinding, cwd: String) {
        val session = remoteSession ?: return
        val agentsPath = resolvePath("$cwd/AGENTS.md")
        val mergedBinding = binding.copy(threadId = threadId, cwdOverride = cwd)
        val existing = runCatching { session.readTextFile(agentsPath) }.getOrNull()
        val merged = AgentsFileManager.merge(existing, mergedBinding)
        runCatching { session.writeTextFile(agentsPath, merged) }
    }

    private suspend fun applyThreadSnapshot(snapshot: ThreadSnapshot, preserveSelection: Boolean) {
        val accountId = _uiState.value.selectedAccountId ?: return
        localStateRepository.upsertThreadCache(
            CachedThread(
                threadId = snapshot.threadId,
                accountId = accountId,
                title = snapshot.title,
                preview = snapshot.preview,
                cwd = snapshot.cwd,
                status = snapshot.status,
                updatedAtEpochSeconds = snapshot.updatedAtEpochSeconds,
                messages = snapshot.messages,
            ),
        )
        _uiState.update { state ->
            val shouldSelect = !preserveSelection || state.selectedThreadId == snapshot.threadId
            state.copy(
                selectedThreadId = if (shouldSelect) snapshot.threadId else state.selectedThreadId,
                selectedThreadMessages = if (shouldSelect) snapshot.messages else state.selectedThreadMessages,
            )
        }
    }

    private fun appendOrReplaceMessages(
        threadId: String,
        accountId: String,
        mutate: (List<ChatMessage>) -> List<ChatMessage>,
    ) {
        val currentThread = _uiState.value.threads.firstOrNull { it.threadId == threadId }
        val nextMessages = mutate(currentThread?.messages.orEmpty())
        viewModelScope.launch {
            localStateRepository.setThreadMessages(
                threadId = threadId,
                accountId = accountId,
                title = currentThread?.title ?: "Новый чат",
                preview = nextMessages.firstOrNull()?.text ?: currentThread?.preview.orEmpty(),
                cwd = currentThread?.cwd ?: _uiState.value.sidebar.currentDirectory,
                status = ThreadRuntimeStatus.RUNNING,
                messages = nextMessages,
            )
        }
        _uiState.update { state ->
            state.copy(
                selectedThreadMessages = if (state.selectedThreadId == threadId) nextMessages else state.selectedThreadMessages,
                threads = state.threads.map {
                    if (it.threadId == threadId) it.copy(messages = nextMessages, preview = nextMessages.firstOrNull()?.text ?: it.preview, status = ThreadRuntimeStatus.RUNNING)
                    else it
                },
            )
        }
    }

    private fun updateThreadStatus(threadId: String, status: ThreadRuntimeStatus) {
        _uiState.update { state ->
            state.copy(
                threads = state.threads.map { if (it.threadId == threadId) it.copy(status = status) else it },
            )
        }
    }

    private fun updateThreadTitle(threadId: String, title: String?) {
        if (title.isNullOrBlank()) return
        _uiState.update { state ->
            state.copy(
                threads = state.threads.map { if (it.threadId == threadId) it.copy(title = title) else it },
            )
        }
    }

    private fun scheduleReconnect(accountId: String, message: String) {
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            val delays = listOf(1000L, 2000L, 5000L, 10000L)
            try {
                for (delayMs in delays) {
                    _uiState.update { it.copy(connectionState = ConnectionState(ConnectionStatus.RECONNECTING, message)) }
                    delay(delayMs)
                    if (connectAccount(accountId, forceRestartAppServer = false, bootstrapPassword = null, fromReconnect = true)) {
                        return@launch
                    }
                    if (_uiState.value.connectionState.status == ConnectionStatus.FAILED_AUTH) {
                        return@launch
                    }
                }
                while (isActive) {
                    _uiState.update { it.copy(connectionState = ConnectionState(ConnectionStatus.RECONNECTING, message)) }
                    delay(10000L)
                    if (connectAccount(accountId, forceRestartAppServer = false, bootstrapPassword = null, fromReconnect = true)) {
                        return@launch
                    }
                    if (_uiState.value.connectionState.status == ConnectionStatus.FAILED_AUTH) {
                        return@launch
                    }
                }
            } finally {
                reconnectJob = null
            }
        }
    }

    private suspend fun closeRemoteSession(
        resetUi: Boolean = true,
        cancelReconnect: Boolean = true,
        preserveProfiles: Boolean = false,
    ) {
        if (cancelReconnect) {
            cancelReconnectLoop()
        }
        keepAliveJob?.cancel()
        keepAliveJob = null
        codexEventsJob?.cancel()
        openAiPollingJob?.cancel()
        runCatching { codexClient?.close() }
        runCatching { portForwardHandle?.close() }
        runCatching { remoteSession?.close() }
        codexClient = null
        portForwardHandle = null
        remoteSession = null
        if (resetUi) {
            _uiState.update {
                it.copy(
                    connectionState = ConnectionState(ConnectionStatus.DISCONNECTED),
                    composer = it.composer.copy(isSending = false),
                    openAiAccount = OpenAiAccountState(),
                    codexProfiles = if (preserveProfiles) it.codexProfiles else emptyList(),
                    pendingExternalUrl = null,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    composer = it.composer.copy(isSending = false),
                    pendingExternalUrl = null,
                )
            }
        }
    }

    private fun startOpenAiPolling(loginId: String) {
        openAiPollingJob?.cancel()
        openAiPollingJob = viewModelScope.launch {
            repeat(90) {
                delay(2000L)
                val client = codexClient ?: return@launch
                val status = runCatching { client.getAccount() }.getOrNull() ?: return@repeat
                applyOpenAiAccountStatus(status)
                val pendingId = _uiState.value.openAiAccount.pendingLoginId
                if (status.account != null) {
                    refreshOpenAiAccountStatus(refreshToken = true, persistProfileOnSuccess = true)
                    return@launch
                }
                if (pendingId == null || pendingId != loginId) {
                    return@launch
                }
            }
        }
    }

    private suspend fun refreshOpenAiAccountStatus(
        authModeHint: OpenAiAuthMode? = null,
        planTypeHint: String? = null,
        refreshToken: Boolean = false,
        persistProfileOnSuccess: Boolean = false,
    ) {
        val client = codexClient ?: return
        _uiState.update {
            it.copy(
                openAiAccount = it.openAiAccount.copy(
                    isLoading = true,
                    loginState = it.openAiAccount.loginState.takeUnless { state -> state == OpenAiLoginState.IDLE }
                        ?: OpenAiLoginState.CHECKING,
                    lastError = null,
                ),
            )
        }
        runCatching { client.getAccount(refreshToken = refreshToken) }
            .onSuccess { status ->
                val rateLimits = runCatching { client.getAccountRateLimits() }.getOrNull()
                if (persistProfileOnSuccess) {
                    persistCurrentCodexProfile(status.account?.email)
                }
                applyOpenAiAccountStatus(status, authModeHint, planTypeHint, rateLimits)
                syncCodexProfiles(
                    activeEmail = status.account?.email,
                    activePlanType = status.account?.planType ?: planTypeHint,
                    rateLimits = rateLimits,
                )
                if (status.account != null || !status.requiresOpenAiAuth) {
                    openAiPollingJob?.cancel()
                }
            }
            .onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        openAiAccount = state.openAiAccount.copy(
                            isLoading = false,
                            loginState = OpenAiLoginState.ERROR,
                            lastError = error.message ?: "Не удалось обновить аккаунт GPT",
                        ),
                    )
                }
            }
    }

    private fun markThreadsReconnecting() {
        _uiState.update { state ->
            state.copy(
                threads = state.threads.map { thread ->
                    thread.copy(
                        status = when (thread.status) {
                            ThreadRuntimeStatus.RUNNING,
                            ThreadRuntimeStatus.WAITING_ON_APPROVAL,
                            ThreadRuntimeStatus.WAITING_ON_INPUT,
                            ThreadRuntimeStatus.RECONNECTING -> ThreadRuntimeStatus.RECONNECTING
                            else -> thread.status
                        },
                    )
                },
            )
        }
    }

    private fun applyOpenAiAccountStatus(
        status: CodexAccountStatus,
        authModeHint: OpenAiAuthMode? = null,
        planTypeHint: String? = null,
        rateLimits: CodexRateLimitSnapshot? = null,
    ) {
        _uiState.update { state ->
            state.copy(
                openAiAccount = mapAccountStatus(
                    status = status,
                    previous = state.openAiAccount,
                    authModeHint = authModeHint,
                    planTypeHint = planTypeHint,
                    rateLimits = rateLimits,
                ),
            )
        }
    }

    private fun syncFromLocalState() {
        val persisted = localStateRepository.state().value
        val selectedAccountId = persisted.selectedAccountId
        val currentDirectory = _uiState.value.sidebar.currentDirectory
        val threads = persisted.cachedThreads
            .filter { it.accountId == selectedAccountId }
            .filter { thread -> threadMatchesDirectory(thread, bindingFor(thread.threadId, persisted), currentDirectory) }
            .sortedWith(compareByDescending<CachedThread> { bindingFor(it.threadId, persisted)?.isPinned == true }
                .thenByDescending { bindingFor(it.threadId, persisted)?.pinnedOrder ?: 0L }
                .thenByDescending { it.updatedAtEpochSeconds })
        val selectedThreadId = _uiState.value.selectedThreadId?.takeIf { current -> threads.any { it.threadId == current } }
            ?: threads.firstOrNull()?.threadId
        _uiState.update {
            it.copy(
                isLoaded = true,
                accounts = persisted.accounts.sortedByDescending { account -> account.lastUsedAtEpochSeconds },
                selectedAccountId = selectedAccountId,
                threads = threads,
                selectedThreadId = selectedThreadId,
                selectedThreadMessages = threads.firstOrNull { thread -> thread.threadId == selectedThreadId }?.messages.orEmpty(),
                gitHubSession = persisted.gitHubSession,
                settings = it.settings.copy(
                    availableRepos = persisted.gitHubRepos,
                    showAccountSheet = it.settings.showAccountSheet,
                    showCodexProfileSheet = it.settings.showCodexProfileSheet,
                    showSettings = it.settings.showSettings,
                    remoteGitRepos = it.settings.remoteGitRepos,
                ),
            )
        }
    }

    private suspend fun syncCodexProfiles(
        activeEmail: String? = _uiState.value.openAiAccount.email,
        activePlanType: String? = _uiState.value.openAiAccount.planType,
        rateLimits: CodexRateLimitSnapshot? = _uiState.value.openAiAccount.rateLimits,
    ) {
        val session = remoteSession ?: return
        runCatching {
            session.listCodexProfiles(
                activeEmail = activeEmail,
                activePlanType = activePlanType,
            )
        }.onSuccess { profiles ->
            val fiveHourWindow = selectRateLimitWindow(rateLimits, targetDurationMins = 300L, fallbackLabel = "5Ч")
            val weeklyWindow = selectRateLimitWindow(rateLimits, targetDurationMins = 10080L, fallbackLabel = "7Д")
            _uiState.update {
                it.copy(
                    codexProfiles = profiles
                        .sortedWith(compareByDescending<CodexProfile> { it.isActive }.thenBy { it.name.lowercase() })
                        .map { profile ->
                            if (profile.isActive) {
                                profile.copy(
                                    planType = activePlanType ?: rateLimits?.planType ?: profile.planType,
                                    fiveHourWindow = fiveHourWindow,
                                    weeklyWindow = weeklyWindow,
                                )
                            } else {
                                profile.copy(
                                    fiveHourWindow = profile.fiveHourWindow.copy(valueLabel = "Н/Д", progress = null),
                                    weeklyWindow = profile.weeklyWindow.copy(valueLabel = "Н/Д", progress = null),
                                )
                            }
                        },
                )
            }
            appendDiagnostic(
                "Профили Codex: " +
                    profiles.joinToString { profile ->
                        val marker = if (profile.isActive) "*" else "-"
                        "$marker${profile.email ?: profile.name}"
                    },
            )
        }.onFailure { error ->
            appendDiagnostic("Не удалось синхронизировать профили Codex: ${error.message ?: "unknown"}")
            AppDiagnostics.log("Codex profiles sync failed: ${error.message ?: "unknown"}")
        }
    }

    private suspend fun persistCurrentCodexProfile(preferredName: String?) {
        val session = remoteSession ?: return
        val normalizedName = preferredName?.trim().takeUnless { it.isNullOrBlank() } ?: "profile"
        runCatching { session.saveCurrentCodexProfile(normalizedName) }
            .onSuccess { savedName ->
                appendDiagnostic("Текущий Codex-профиль сохранён: $savedName")
                AppDiagnostics.log("Codex profile saved: $savedName")
            }
            .onFailure { error ->
                appendDiagnostic("Не удалось сохранить текущий Codex-профиль: ${error.message ?: "unknown"}")
                AppDiagnostics.log("Codex profile save failed: ${error.message ?: "unknown"}")
            }
    }

    private fun bindingFor(threadId: String, persisted: com.codex.android.app.core.model.PersistedAppState): ConversationBinding? {
        return persisted.conversationBindings.firstOrNull { it.threadId == threadId }
    }

    private fun threadMatchesDirectory(thread: CachedThread, binding: ConversationBinding?, currentDirectory: String): Boolean {
        if (currentDirectory.isBlank()) return true
        val normalizedCurrent = currentDirectory.removeSuffix("/")
        val scope = binding?.directoryScope?.removeSuffix("/")
        val threadCwd = thread.cwd.removeSuffix("/")
        return scope == normalizedCurrent || threadCwd == normalizedCurrent
    }

    private fun startKeepAliveLoop(accountId: String) {
        keepAliveJob?.cancel()
        keepAliveJob = viewModelScope.launch {
            appendDiagnostic("KeepAlive heartbeat запущен для $accountId")
            AppDiagnostics.log("KeepAlive heartbeat запущен")
            while (isActive) {
                delay(20_000L)
                val client = codexClient ?: break
                if (_uiState.value.connectionState.status !in setOf(ConnectionStatus.CONNECTED, ConnectionStatus.RECONNECTING)) {
                    continue
                }
                runCatching {
                    client.getAccount(refreshToken = false)
                }.onSuccess {
                    keepAliveHeartbeatCount += 1
                    lastKeepAliveSuccessAt = Instant.now()
                    if (keepAliveHeartbeatCount % 3L == 0L) {
                        appendDiagnostic("KeepAlive heartbeat ok #$keepAliveHeartbeatCount")
                    }
                }.onFailure { error ->
                    appendDiagnostic("KeepAlive heartbeat ошибка: ${error.message ?: "unknown"}")
                    AppDiagnostics.log("KeepAlive heartbeat ошибка: ${error.message ?: "unknown"}")
                    handleConnectionFailure(error)
                    return@launch
                }
            }
            appendDiagnostic("KeepAlive heartbeat остановлен")
            AppDiagnostics.log("KeepAlive heartbeat остановлен")
        }
    }

    private fun cancelReconnectLoop() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun handleConnectionProblem(message: String) {
        if (connectionJob?.isActive == true && _uiState.value.connectionState.status == ConnectionStatus.CONNECTING) {
            return
        }
        if (_uiState.value.connectionState.status == ConnectionStatus.RECONNECTING && reconnectJob?.isActive == true) {
            return
        }
        appendDiagnostic("Проблема соединения Codex: $message")
        AppDiagnostics.log("Проблема соединения Codex: $message")
        markThreadsReconnecting()
        _uiState.update {
            it.copy(
                connectionState = ConnectionState(ConnectionStatus.RECONNECTING, message),
                composer = it.composer.copy(isSending = false),
            )
        }
        _uiState.value.selectedAccountId?.let { scheduleReconnect(it, message) }
    }

    private fun handleConnectionFailure(error: Throwable): Boolean {
        if (error.isBenignCancellation()) {
            return true
        }
        val message = error.message ?: return false
        if (
            message.contains("WebSocket", ignoreCase = true) ||
            message.contains("Disconnected", ignoreCase = true) ||
            message.contains("socket", ignoreCase = true)
        ) {
            handleConnectionProblem(message)
            return true
        }
        return false
    }

    private fun showMessage(message: String) {
        appendDiagnostic("Сообщение UI: $message")
        AppDiagnostics.log("UI message: $message")
        _uiState.update { it.copy(bannerMessage = message) }
    }

    fun buildDiagnosticLog(): String {
        val state = _uiState.value
        return buildString {
            appendLine("Codex Android diagnostics")
            appendLine("timestamp=${Instant.now()}")
            appendLine("app_version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android_api=${Build.VERSION.SDK_INT}")
            appendLine("server=${BuildConfig.DEFAULT_SERVER_HOST}:${BuildConfig.DEFAULT_SERVER_PORT}")
            appendLine("ssh_provider=${SecurityProviderCompat.currentSshProviderName()}")
            appendLine("selected_account=${state.selectedAccount?.displayName ?: "none"}")
            appendLine("connection_status=${state.connectionState.status}")
            appendLine("connection_message=${state.connectionState.message ?: ""}")
            appendLine("selected_thread_id=${state.selectedThreadId ?: "none"}")
            appendLine("selected_thread_title=${state.selectedThread?.title ?: "none"}")
            appendLine("cwd=${state.sidebar.currentDirectory}")
            appendLine("codex_profile=${state.selectedCodexProfile?.email ?: state.selectedCodexProfile?.name ?: "none"}")
            appendLine("gpt_auth_mode=${state.openAiAccount.authMode ?: "none"}")
            appendLine("gpt_email=${state.openAiAccount.email ?: "none"}")
            appendLine("gpt_plan=${state.openAiAccount.planType ?: "none"}")
            appendLine("gpt_login_state=${state.openAiAccount.loginState}")
            appendLine("gpt_last_error=${state.openAiAccount.lastError ?: ""}")
            appendLine("github_user=${state.gitHubSession?.userLogin ?: "none"}")
            appendLine("reconnect_active=${reconnectJob?.isActive == true}")
            appendLine("connection_job_active=${connectionJob?.isActive == true}")
            appendLine("keepalive_active=${keepAliveJob?.isActive == true}")
            appendLine("keepalive_heartbeats=$keepAliveHeartbeatCount")
            appendLine("keepalive_last_success=${lastKeepAliveSuccessAt ?: "none"}")
            appendLine("connection_generation=$connectionGeneration")
            appendLine("remote_session_open=${remoteSession != null}")
            appendLine("port_forward_open=${portForwardHandle != null}")
            appendLine("codex_client_open=${codexClient != null}")
            appendLine("accounts_count=${state.accounts.size}")
            appendLine("codex_profiles_count=${state.codexProfiles.size}")
            appendLine(
                "codex_profiles=" + state.codexProfiles.joinToString("|") { profile ->
                    buildString {
                        append(profile.name)
                        append(":")
                        append(profile.email ?: "none")
                        append(":")
                        append(profile.planType ?: "none")
                        append(":")
                        append(if (profile.isActive) "active" else "inactive")
                    }
                },
            )
            appendLine("threads_count=${state.threads.size}")
            appendLine()
            appendLine("recent_events:")
            diagnosticEntries.ifEmpty {
                listOf("[${Instant.now()}] Пусто")
            }.forEach { appendLine(it) }
            appendLine()
            appendLine("app_events:")
            AppDiagnostics.snapshot().ifEmpty {
                listOf("[${Instant.now()}] Пусто")
            }.forEach { appendLine(it) }
        }
    }

    fun notifyLogsCopied() {
        showMessage("Логи скопированы")
    }

    private fun appendDiagnostic(message: String) {
        val entry = "[${Instant.now()}] $message"
        while (diagnosticEntries.size >= 80) {
            diagnosticEntries.removeFirst()
        }
        diagnosticEntries.addLast(entry)
    }

    private fun promptForAccountPassword(accountId: String) {
        val account = localStateRepository.state().value.accounts.firstOrNull { it.id == accountId } ?: return
        appendDiagnostic("Нужен повторный ввод пароля для ${account.displayName}")
        _uiState.update {
            it.copy(
                accountDraft = AccountDraft(username = account.username),
                settings = it.settings.copy(showAccountSheet = true),
            )
        }
    }

    private fun resolvePath(raw: String): String {
        val trimmed = raw.trim()
        val home = _uiState.value.selectedAccount?.homeDirectory ?: "~"
        if (trimmed.isBlank()) return home
        if (trimmed.startsWith("~/")) return home.removeSuffix("/") + "/" + trimmed.removePrefix("~/")
        if (trimmed == "~") return home
        if (trimmed.startsWith("/")) return trimmed
        val base = _uiState.value.sidebar.currentDirectory.ifBlank { _uiState.value.selectedThread?.cwd ?: "~" }
        return if (base.endsWith("/")) "$base$trimmed" else "$base/$trimmed"
    }

    private fun buildMoveTargetPath(sourcePath: String, rawTarget: String): String {
        val trimmed = rawTarget.trim()
        if (trimmed.isBlank()) return sourcePath
        return when {
            trimmed.startsWith("/") || trimmed.startsWith("~/") || trimmed == "~" -> resolvePath(trimmed)
            trimmed.contains("/") -> resolvePath(trimmed)
            else -> {
                val parent = parentDirectoryOf(sourcePath)
                if (parent.endsWith("/")) "$parent$trimmed" else "$parent/$trimmed"
            }
        }
    }

    private fun parentDirectoryOf(path: String): String {
        val normalized = path.trim().trimEnd('/').ifBlank { "/" }
        if (normalized == "/" || normalized == "~") return normalized
        val parent = normalized.substringBeforeLast('/', missingDelimiterValue = normalized)
        return parent.ifBlank { "/" }
    }

    private fun chooseDefaultModel(models: List<ModelOption>, existing: String?): String? {
        return existing?.takeIf { current -> models.any { it.model == current } }
            ?: models.firstOrNull { it.isDefault }?.model
            ?: models.firstOrNull()?.model
    }

    private fun mapAccountStatus(
        status: CodexAccountStatus,
        previous: OpenAiAccountState = OpenAiAccountState(),
        authModeHint: OpenAiAuthMode? = null,
        planTypeHint: String? = null,
        rateLimits: CodexRateLimitSnapshot? = null,
    ): OpenAiAccountState {
        val account = status.account
        val resolvedMode = account?.authMode ?: authModeHint ?: previous.authMode
        val keepPendingLogin = account == null && status.requiresOpenAiAuth && previous.pendingLoginId != null
        return previous.copy(
            isLoading = false,
            requiresOpenAiAuth = status.requiresOpenAiAuth,
            authMode = resolvedMode,
            email = account?.email,
            planType = account?.planType ?: planTypeHint ?: previous.planType,
            rateLimits = rateLimits ?: previous.rateLimits,
            loginState = when {
                account != null -> OpenAiLoginState.SIGNED_IN
                status.requiresOpenAiAuth && previous.pendingLoginId != null -> OpenAiLoginState.WAITING_BROWSER_AUTH
                status.requiresOpenAiAuth -> OpenAiLoginState.REQUIRES_LOGIN
                else -> OpenAiLoginState.IDLE
            },
            pendingLoginId = previous.pendingLoginId.takeIf { keepPendingLogin },
            pendingAuthUrl = previous.pendingAuthUrl.takeIf { keepPendingLogin },
            lastError = null,
        )
    }

    private fun normalizeRepoUrl(url: String): String {
        return url.removeSuffix(".git").replace("git@github.com:", "https://github.com/")
    }

    private fun keepAliveMessage(state: MainUiState): String {
        return when {
            state.openAiAccount.loginState == OpenAiLoginState.WAITING_BROWSER_AUTH ->
                "Жду завершения входа в ChatGPT для ${state.selectedAccount?.username ?: "аккаунта"}"
            state.connectionState.status == ConnectionStatus.RECONNECTING ->
                "Переподключаю ${state.selectedAccount?.displayName ?: "сервер"}"
            state.connectionState.status == ConnectionStatus.CONNECTING ->
                "Подключаю ${state.selectedAccount?.displayName ?: "сервер"}"
            state.connectionState.status == ConnectionStatus.CONNECTED ->
                "Держу активной сессию ${state.selectedAccount?.displayName ?: "Codex"}"
            else -> "Codex Android"
        }
    }

    private fun selectRateLimitWindow(
        snapshot: CodexRateLimitSnapshot?,
        targetDurationMins: Long,
        fallbackLabel: String,
    ): CodexUsageWindow {
        val windows = listOfNotNull(snapshot?.primary, snapshot?.secondary)
        val best = windows.minByOrNull { window ->
            abs(((window.windowDurationMins ?: targetDurationMins) - targetDurationMins).toInt())
        }
        return best?.toUsageWindow(fallbackLabel) ?: CodexUsageWindow(label = fallbackLabel, valueLabel = "Синхр.")
    }

    private fun CodexRateLimitWindow.toUsageWindow(fallbackLabel: String): CodexUsageWindow {
        return CodexUsageWindow(
            label = when (windowDurationMins) {
                300L -> "5Ч"
                10080L -> "7Д"
                else -> fallbackLabel
            },
            valueLabel = "${usedPercent.coerceIn(0, 100)}%",
            progress = usedPercent.coerceIn(0, 100) / 100f,
        )
    }

    private fun Throwable.isBenignCancellation(): Boolean {
        return (this is CancellationException && this !is TimeoutCancellationException) ||
            (message?.contains("cancelled", ignoreCase = true) == true)
    }

    private data class KeepAliveDescriptor(
        val active: Boolean,
        val title: String,
        val message: String,
    )
}
