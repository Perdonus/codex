@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.codex.android.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codex.android.app.core.model.ChatRole
import com.codex.android.app.core.model.ConnectionStatus
import com.codex.android.app.core.model.OpenAiAuthMode
import com.codex.android.app.core.model.OpenAiLoginState
import com.codex.android.app.core.model.ReasoningEffort
import com.codex.android.app.core.model.RemoteFileNode
import com.codex.android.app.core.model.ThreadRuntimeStatus
import com.codex.android.app.ui.components.MarkdownMessage
import kotlinx.coroutines.launch

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(state.bannerMessage) {
        state.bannerMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeBannerMessage()
        }
    }

    LaunchedEffect(state.pendingExternalUrl) {
        state.pendingExternalUrl?.let { url ->
            runCatching { uriHandler.openUri(url) }
            viewModel.consumePendingExternalUrl()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 920.dp
        if (wideLayout) {
            MainScreenWide(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
        } else {
            MainScreenCompact(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
        }
    }

    if (state.settings.showSettings) {
        SettingsSheet(state = state, viewModel = viewModel)
    }
    if (state.settings.showAccountSheet) {
        AccountSheet(state = state, viewModel = viewModel)
    }
}

@Composable
private fun MainScreenWide(
    state: MainUiState,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Sidebar(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.width(338.dp).fillMaxHeight(),
            )
            ChatPane(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun MainScreenCompact(
    state: MainUiState,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
                Sidebar(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .combinedClickable(onClick = { scope.launch { drawerState.open() } }),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Menu", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Codex Android Client", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = state.selectedAccount?.displayName ?: "No connected account",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
            ChatPane(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@Composable
private fun Sidebar(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AccountHeader(state, viewModel)
        ThreadSection(state, viewModel, Modifier.weight(1f))
        FileSection(state, viewModel, Modifier.weight(1f))
    }
}

@Composable
private fun AccountHeader(state: MainUiState, viewModel: MainViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Server", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                state.selectedAccount?.displayName ?: "No account selected",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                state.connectionState.message ?: when (state.connectionState.status) {
                    ConnectionStatus.CONNECTED -> "Connected"
                    ConnectionStatus.CONNECTING -> "Connecting"
                    ConnectionStatus.RECONNECTING -> "Reconnecting"
                    ConnectionStatus.FAILED_AUTH -> "Auth failed"
                    ConnectionStatus.FAILED_SERVER -> "Server failed"
                    ConnectionStatus.DISCONNECTED -> "Disconnected"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.connectionState.status == ConnectionStatus.CONNECTING || state.connectionState.status == ConnectionStatus.RECONNECTING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { viewModel.toggleAccountSheet(true) }) {
                    Text("Add user")
                }
                OutlinedButton(onClick = { viewModel.toggleSettings(true) }) {
                    Text("Settings")
                }
            }
            state.accounts.forEach { account ->
                val selected = account.id == state.selectedAccountId
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { viewModel.selectAccount(account.id) },
                            onLongClick = { viewModel.deleteAccount(account.id) },
                        ),
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(account.displayName, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        Text(
                            account.homeDirectory ?: "${account.host}:${account.port}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadSection(state: MainUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Dialogs", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { viewModel.createThread() }) { Text("New") }
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.threads, key = { it.threadId }) { thread ->
                val selected = thread.threadId == state.selectedThreadId
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { viewModel.selectThread(thread.threadId) },
                            onLongClick = { viewModel.toggleThreadPinned(thread.threadId) },
                        ),
                    shape = RoundedCornerShape(22.dp),
                    color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = thread.title.ifBlank { thread.preview },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            RunningDot(thread.status)
                        }
                        Text(
                            text = thread.preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                        Text(
                            text = thread.cwd,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningDot(status: ThreadRuntimeStatus) {
    when (status) {
        ThreadRuntimeStatus.RUNNING,
        ThreadRuntimeStatus.WAITING_ON_APPROVAL,
        ThreadRuntimeStatus.WAITING_ON_INPUT,
        ThreadRuntimeStatus.RECONNECTING -> {
            val transition = rememberInfiniteTransition(label = "thread-dot")
            val alpha by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
                label = "thread-dot-alpha",
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.secondary, CircleShape),
            )
        }

        ThreadRuntimeStatus.FAILED -> Box(
            modifier = Modifier
                .size(10.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape),
        )

        else -> Spacer(Modifier.size(10.dp))
    }
}

@Composable
private fun FileSection(state: MainUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Files", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { viewModel.refreshDirectory() }) { Text("Refresh") }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.sidebar.currentDirectory,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val parentDirectory = remember(state.sidebar.currentDirectory) { parentDirectoryOf(state.sidebar.currentDirectory) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            parentDirectory?.let { parent ->
                item(key = "parent-directory") {
                    FileRow(
                        node = RemoteFileNode(
                            path = parent,
                            name = "..",
                            isDirectory = true,
                        ),
                        onOpen = { viewModel.enterDirectory(parent) },
                    )
                }
            }
            items(state.sidebar.remoteFiles, key = { it.path }) { node ->
                FileRow(node = node, onOpen = {
                    if (node.isDirectory) viewModel.enterDirectory(node.path) else viewModel.downloadRemotePath(node.path)
                })
            }
        }
    }
}

@Composable
private fun FileRow(node: RemoteFileNode, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = (if (node.isDirectory) "[DIR] " else "[FILE] ") + node.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!node.isDirectory) {
                Text(
                    text = node.path,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChatPane(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { Composer(state, viewModel) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            if (state.connectionState.status == ConnectionStatus.RECONNECTING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            }
            AnimatedVisibility(visible = state.openAiAccount.requiresOpenAiAuth) {
                AuthRequiredBanner(
                    state = state,
                    onLoginClick = viewModel::startOpenAiLogin,
                    onSettingsClick = { viewModel.toggleSettings(true) },
                )
            }
            if (state.selectedThreadMessages.isEmpty()) {
                Box(modifier = Modifier.weight(1f)) {
                    EmptyChat(state = state)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Spacer(Modifier.height(10.dp)) }
                    items(state.selectedThreadMessages, key = { it.id }) { message ->
                        ChatMessageRow(
                            role = message.role,
                            text = message.text,
                            isStreaming = message.status == com.codex.android.app.core.model.MessageStatus.STREAMING,
                            onPathClick = viewModel::downloadRemotePath,
                            onExternalLinkClick = viewModel::openExternalUrl,
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyChat(state: MainUiState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Codex on Android", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = state.selectedAccount?.let { "Connected as ${it.displayName}" } ?: "Add an SSH account to begin",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.openAiAccount.requiresOpenAiAuth) {
                Text(
                    text = "ChatGPT login is required before Codex can answer.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.connectionState.status == ConnectionStatus.CONNECTING) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun AuthRequiredBanner(
    state: MainUiState,
    onLoginClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("ChatGPT login required", style = MaterialTheme.typography.titleMedium)
            Text(
                text = when (state.openAiAccount.loginState) {
                    OpenAiLoginState.WAITING_BROWSER_AUTH -> "Complete the ChatGPT sign-in flow in your browser, then come back here."
                    OpenAiLoginState.ERROR -> state.openAiAccount.lastError ?: "Authentication failed. Try again."
                    else -> "This remote Codex session requires an authenticated GPT account before messages can be sent."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onLoginClick) {
                    Text(if (state.openAiAccount.loginState == OpenAiLoginState.WAITING_BROWSER_AUTH) "Open again" else "Sign in")
                }
                OutlinedButton(onClick = onSettingsClick) {
                    Text("Settings")
                }
            }
        }
    }
}

@Composable
private fun ChatMessageRow(
    role: ChatRole,
    text: String,
    isStreaming: Boolean,
    onPathClick: (String) -> Unit,
    onExternalLinkClick: (String) -> Unit,
) {
    val isUser = role == ChatRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (isUser) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(0.82f),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MarkdownMessage(
                    text = text,
                    onDownloadLinkClick = onPathClick,
                    onExternalLinkClick = onExternalLinkClick,
                )
                AnimatedVisibility(visible = isStreaming) {
                    Text(
                        text = "Streaming...",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (role == ChatRole.REASONING) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Composer(state: MainUiState, viewModel: MainViewModel) {
    var modelExpanded by remember { mutableStateOf(false) }
    var effortExpanded by remember { mutableStateOf(false) }
    val composerEnabled = !state.openAiAccount.requiresOpenAiAuth
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { modelExpanded = true },
                    label = { Text(state.composer.selectedModel ?: "Model") },
                )
                DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                    state.models.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.displayName) },
                            onClick = {
                                viewModel.selectModel(model.model)
                                modelExpanded = false
                            },
                        )
                    }
                }
                AssistChip(
                    onClick = { effortExpanded = true },
                    label = { Text("Think: ${state.composer.selectedReasoningEffort.name.lowercase()}") },
                )
                DropdownMenu(expanded = effortExpanded, onDismissRequest = { effortExpanded = false }) {
                    ReasoningEffort.entries.forEach { effort ->
                        DropdownMenuItem(
                            text = { Text(effort.name.lowercase()) },
                            onClick = {
                                viewModel.selectReasoning(effort)
                                effortExpanded = false
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    BasicTextField(
                        value = state.composer.text,
                        onValueChange = viewModel::updateComposerText,
                        enabled = composerEnabled,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (composerEnabled) viewModel.sendMessage() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        decorationBox = { inner ->
                            if (state.composer.text.isBlank()) {
                                Text(
                                    if (composerEnabled) "Message Codex..." else "Sign in to ChatGPT to chat...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                }
                Surface(
                    modifier = Modifier.size(52.dp).combinedClickable(
                        onClick = {
                            when {
                                state.composer.isSending -> viewModel.interruptActiveTurn()
                                composerEnabled -> viewModel.sendMessage()
                                else -> viewModel.toggleSettings(true)
                            }
                        },
                    ),
                    shape = CircleShape,
                    color = when {
                        state.composer.isSending -> MaterialTheme.colorScheme.secondary
                        composerEnabled -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = when {
                                state.composer.isSending -> "[]"
                                composerEnabled -> ">"
                                else -> "!"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = when {
                                state.composer.isSending -> Color.Black
                                composerEnabled -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSheet(state: MainUiState, viewModel: MainViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { viewModel.toggleSettings(false) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("GPT Account", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when {
                            state.openAiAccount.email != null -> state.openAiAccount.email
                            state.openAiAccount.requiresOpenAiAuth -> "Sign in required"
                            else -> "Waiting for remote Codex status"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = buildString {
                            val modeLabel = when (state.openAiAccount.authMode) {
                                OpenAiAuthMode.API_KEY -> "Auth mode: API key"
                                OpenAiAuthMode.CHATGPT -> "Auth mode: ChatGPT OAuth"
                                OpenAiAuthMode.CHATGPT_AUTH_TOKENS -> "Auth mode: external ChatGPT tokens"
                                null -> "Auth mode: unknown"
                            }
                            append(modeLabel)
                            state.openAiAccount.planType?.let {
                                append(" • Plan: ")
                                append(it)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.openAiAccount.lastError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = viewModel::startOpenAiLogin,
                            enabled = !state.openAiAccount.isLoading,
                        ) {
                            Text(
                                when (state.openAiAccount.loginState) {
                                    OpenAiLoginState.WAITING_BROWSER_AUTH -> "Open browser again"
                                    OpenAiLoginState.SIGNED_IN -> "Re-auth"
                                    else -> "Sign in to ChatGPT"
                                },
                            )
                        }
                        OutlinedButton(
                            onClick = viewModel::refreshOpenAiAccount,
                            enabled = !state.openAiAccount.isLoading,
                        ) {
                            Text("Refresh")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = viewModel::cancelOpenAiLogin,
                            enabled = state.openAiAccount.pendingLoginId != null,
                        ) {
                            Text("Cancel login")
                        }
                        OutlinedButton(
                            onClick = viewModel::logoutOpenAiAccount,
                            enabled = state.openAiAccount.authMode != null,
                        ) {
                            Text("Logout")
                        }
                    }
                    if (state.openAiAccount.isLoading || state.openAiAccount.loginState == OpenAiLoginState.REQUESTING) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    state.openAiAccount.pendingAuthUrl?.let { authUrl ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Browser flow", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Codex stores and refreshes ChatGPT auth under the hood. This app only opens the browser flow and tracks the result.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(authUrl, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                FilledTonalButton(onClick = { viewModel.openExternalUrl(authUrl) }) {
                                    Text("Open auth page")
                                }
                            }
                        }
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("GitHub", style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.gitHubSession?.userLogin ?: "Not signed in",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = viewModel::startGitHubLogin) {
                            Text(if (state.gitHubSession == null) "Login" else "Relogin")
                        }
                        OutlinedButton(onClick = viewModel::refreshGitHubRepos) {
                            Text("Refresh repos")
                        }
                        OutlinedButton(onClick = viewModel::scanRemoteRepositories) {
                            Text("Scan server repos")
                        }
                    }
                    state.deviceFlow?.let { flow ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Open ${flow.verificationUri}", fontWeight = FontWeight.SemiBold)
                                Text("Code: ${flow.userCode}", style = MaterialTheme.typography.titleMedium)
                                FilledTonalButton(onClick = { viewModel.openExternalUrl(flow.verificationUri) }) {
                                    Text("Open GitHub verification")
                                }
                            }
                        }
                    }
                }
            }
            Text("Repositories", style = MaterialTheme.typography.titleMedium)
            state.availableGitHubRepos.forEach { repo ->
                Surface(
                    modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { viewModel.pinRepositoryToCurrentThread(repo) }),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(repo.fullName, fontWeight = FontWeight.SemiBold)
                        Text(repo.htmlUrl, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AccountSheet(state: MainUiState, viewModel: MainViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { viewModel.toggleAccountSheet(false) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add SSH user", style = MaterialTheme.typography.titleLarge)
            LabeledField("Host", state.accountDraft.host) { value ->
                viewModel.onDraftChanged { it.copy(host = value) }
            }
            LabeledField("Port", state.accountDraft.port, imeAction = ImeAction.Next) { value ->
                viewModel.onDraftChanged { it.copy(port = value) }
            }
            LabeledField("Username", state.accountDraft.username) { value ->
                viewModel.onDraftChanged { it.copy(username = value) }
            }
            LabeledField("Password", state.accountDraft.password, password = true, imeAction = ImeAction.Done) { value ->
                viewModel.onDraftChanged { it.copy(password = value) }
            }
            Button(
                onClick = viewModel::connectDraftAccount,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save and connect")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

private fun parentDirectoryOf(path: String): String? {
    val normalized = path.trim().removeSuffix("/")
    if (normalized.isBlank() || normalized == "~" || normalized == "/") return null
    val parent = normalized.substringBeforeLast('/', missingDelimiterValue = "")
    return when {
        parent.isBlank() -> "/"
        else -> parent
    }
}
