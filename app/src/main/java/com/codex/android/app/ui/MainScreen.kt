@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.codex.android.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codex.android.app.BuildConfig
import com.codex.android.app.core.model.ChatRole
import com.codex.android.app.core.model.ConnectionStatus
import com.codex.android.app.core.model.CodexProfile
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

    val showWelcomeGate = state.selectedAccount == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AnimatedLiquidBackground()
        SystemBarScrims()

        if (showWelcomeGate) {
            WelcomeGate(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ConnectedSurface(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }

    if (state.settings.showSettings) {
        SettingsSheet(state = state, viewModel = viewModel)
    }
    if (state.settings.showAccountSheet) {
        AccountSheet(state = state, viewModel = viewModel)
    }
    if (state.settings.showCodexProfileSheet) {
        CodexProfileSheet(state = state, viewModel = viewModel)
    }
}

@Composable
private fun ConnectedSurface(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val wideLayout = maxWidth >= 980.dp
        if (wideLayout) {
            Column(modifier = Modifier.fillMaxSize()) {
                CodexHeader(
                    state = state,
                    onOpenSidebar = null,
                    onOpenProfiles = { viewModel.toggleCodexProfileSheet(true) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    SidebarPanel(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier
                            .width(352.dp)
                            .fillMaxHeight(),
                    )
                    ChatStage(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                    )
                }
            }
        } else {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(344.dp),
                        drawerContainerColor = Color.Transparent,
                    ) {
                        SidebarPanel(
                            state = state,
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 12.dp),
                        )
                    }
                },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    CodexHeader(
                        state = state,
                        onOpenSidebar = { scope.launch { drawerState.open() } },
                        onOpenProfiles = { viewModel.toggleCodexProfileSheet(true) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ChatStage(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CodexHeader(
    state: MainUiState,
    onOpenSidebar: (() -> Unit)?,
    onOpenProfiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        shape = RoundedCornerShape(34.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shadowElevation = 20.dp,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (onOpenSidebar != null) {
                    FilledIconButton(onClick = onOpenSidebar) {
                        Icon(Icons.Rounded.Menu, contentDescription = "Open sidebar")
                    }
                }
            }
            Text(
                text = "Codex",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenProfiles) {
                    Text(
                        text = state.selectedCodexProfile?.email
                            ?: state.selectedCodexProfile?.name
                            ?: state.openAiAccount.email
                            ?: "Codex",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), CircleShape),
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = state.selectedAccount?.username ?: "guest",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarPanel(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 18.dp,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SidebarServerCard(state = state, viewModel = viewModel)
            ThreadSection(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
            FileSection(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SidebarServerCard(state: MainUiState, viewModel: MainViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Server", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "${BuildConfig.DEFAULT_SERVER_HOST}:${BuildConfig.DEFAULT_SERVER_PORT}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutlinedIconButton(onClick = { viewModel.toggleSettings(true) }) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }
            Text(
                text = state.connectionState.message ?: when (state.connectionState.status) {
                    ConnectionStatus.CONNECTED -> "Connected"
                    ConnectionStatus.CONNECTING -> "Connecting"
                    ConnectionStatus.RECONNECTING -> "Reconnecting"
                    ConnectionStatus.FAILED_AUTH -> "Authentication failed"
                    ConnectionStatus.FAILED_SERVER -> "Server unavailable"
                    ConnectionStatus.DISCONNECTED -> "Disconnected"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.connectionState.status == ConnectionStatus.CONNECTING || state.connectionState.status == ConnectionStatus.RECONNECTING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            FilledTonalButton(
                onClick = { viewModel.toggleAccountSheet(true) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add user")
            }
            state.accounts.forEach { account ->
                val selected = account.id == state.selectedAccountId
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = { viewModel.selectAccount(account.id) }),
                    shape = RoundedCornerShape(24.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = account.username,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            )
                            Text(
                                text = account.homeDirectory ?: account.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (selected) {
                            RunningDot(status = ThreadRuntimeStatus.RUNNING)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadSection(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(30.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Dialogs", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = viewModel::createThread) {
                    Text("New")
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.threads, key = { it.threadId }) { thread ->
                    val selected = thread.threadId == state.selectedThreadId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { viewModel.selectThread(thread.threadId) },
                                onLongClick = { viewModel.toggleThreadPinned(thread.threadId) },
                            ),
                        shape = RoundedCornerShape(24.dp),
                        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = thread.title.ifBlank { thread.preview },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                RunningDot(thread.status)
                            }
                            Text(
                                text = thread.preview.ifBlank { "Empty dialog" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = thread.cwd,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
            val transition = rememberInfiniteTransition(label = "thread-running-dot")
            val alpha by transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(animation = tween(720), repeatMode = RepeatMode.Reverse),
                label = "thread-running-alpha",
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.secondary, CircleShape),
            )
        }

        ThreadRuntimeStatus.FAILED -> {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
            )
        }

        else -> Spacer(Modifier.size(10.dp))
    }
}

@Composable
private fun FileSection(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(30.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Files", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = state.sidebar.currentDirectory,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedIconButton(onClick = { viewModel.refreshDirectory() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh files")
                }
            }
            val parentDirectory = remember(state.sidebar.currentDirectory) { parentDirectoryOf(state.sidebar.currentDirectory) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                parentDirectory?.let { parent ->
                    item("parent-directory") {
                        FileNodeCard(
                            node = RemoteFileNode(path = parent, name = "..", isDirectory = true),
                            onOpen = { viewModel.enterDirectory(parent) },
                        )
                    }
                }
                items(state.sidebar.remoteFiles, key = { it.path }) { node ->
                    FileNodeCard(
                        node = node,
                        onOpen = {
                            if (node.isDirectory) {
                                viewModel.enterDirectory(node.path)
                            } else {
                                viewModel.downloadRemotePath(node.path)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FileNodeCard(
    node: RemoteFileNode,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (node.isDirectory) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                contentDescription = null,
                tint = if (node.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = node.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = node.path,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ChatStage(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(38.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 18.dp,
        tonalElevation = 4.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
            ) {
                if (state.connectionState.status == ConnectionStatus.RECONNECTING) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                }
                AnimatedVisibility(visible = state.openAiAccount.requiresOpenAiAuth) {
                    AuthRequiredBanner(state = state, viewModel = viewModel)
                }
                if (state.selectedThreadMessages.isEmpty()) {
                    EmptyChatState(state = state, modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item("top-space") { Spacer(Modifier.height(14.dp)) }
                        items(state.selectedThreadMessages, key = { it.id }) { message ->
                            ChatMessageRow(
                                role = message.role,
                                text = message.text,
                                isStreaming = message.status == com.codex.android.app.core.model.MessageStatus.STREAMING,
                                onPathClick = viewModel::downloadRemotePath,
                                onExternalLinkClick = viewModel::openExternalUrl,
                            )
                        }
                        item("bottom-space") { Spacer(Modifier.height(188.dp)) }
                    }
                }
            }

            FloatingComposer(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun EmptyChatState(
    state: MainUiState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Codex", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                text = state.selectedAccount?.let { "Connected as ${it.username}" } ?: "Login to begin",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = when {
                    state.openAiAccount.requiresOpenAiAuth -> "Sign in to ChatGPT before sending the first message."
                    else -> "Open an existing dialog or start a fresh one from the left panel."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            if (state.connectionState.status == ConnectionStatus.CONNECTING) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun AuthRequiredBanner(
    state: MainUiState,
    viewModel: MainViewModel,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("ChatGPT sign-in required", style = MaterialTheme.typography.titleMedium)
            Text(
                text = when (state.openAiAccount.loginState) {
                    OpenAiLoginState.WAITING_BROWSER_AUTH -> "Finish the browser login flow, then return here."
                    OpenAiLoginState.ERROR -> state.openAiAccount.lastError ?: "Authentication failed."
                    else -> "This Codex session needs a ChatGPT account before it can answer."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = viewModel::startOpenAiLogin) {
                    Text(if (state.openAiAccount.loginState == OpenAiLoginState.WAITING_BROWSER_AUTH) "Open again" else "Sign in")
                }
                OutlinedButton(onClick = { viewModel.toggleCodexProfileSheet(true) }) {
                    Text("Accounts")
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
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth(0.84f),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (role == ChatRole.REASONING) {
                    Text(
                        text = "Thinking",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                MarkdownMessage(
                    text = text,
                    onDownloadLinkClick = onPathClick,
                    onExternalLinkClick = onExternalLinkClick,
                )
                AnimatedVisibility(visible = isStreaming) {
                    Text(
                        text = "Streaming now",
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

@Composable
private fun FloatingComposer(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    var modelExpanded by remember { mutableStateOf(false) }
    var effortExpanded by remember { mutableStateOf(false) }
    val composerEnabled = !state.openAiAccount.requiresOpenAiAuth

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 18.dp,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BasicTextField(
                        value = state.composer.text,
                        onValueChange = viewModel::updateComposerText,
                        enabled = composerEnabled,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                when {
                                    state.composer.isSending -> viewModel.interruptActiveTurn()
                                    composerEnabled -> viewModel.sendMessage()
                                    else -> viewModel.startOpenAiLogin()
                                }
                            },
                        ),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (state.composer.text.isBlank()) {
                                Text(
                                    text = if (composerEnabled) "Напиши Codex сообщение" else "Сначала войдите в ChatGPT",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        },
                    )
                    FilledIconButton(
                        onClick = {
                            when {
                                state.composer.isSending -> viewModel.interruptActiveTurn()
                                composerEnabled -> viewModel.sendMessage()
                                else -> viewModel.startOpenAiLogin()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (state.composer.isSending) Icons.Rounded.Stop else Icons.Rounded.Send,
                            contentDescription = if (state.composer.isSending) "Stop generation" else "Send message",
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    AssistChip(
                        onClick = { modelExpanded = true },
                        label = {
                            Text(
                                text = state.models.firstOrNull { it.model == state.composer.selectedModel }?.displayName
                                    ?: state.composer.selectedModel
                                    ?: "Model",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
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
                }

                Box {
                    AssistChip(
                        onClick = { effortExpanded = true },
                        label = { Text("Think ${state.composer.selectedReasoningEffort.name.lowercase()}") },
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

                Spacer(Modifier.weight(1f))

                if (state.composer.isSending) {
                    Text(
                        text = "Running",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
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

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("GPT Account", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = state.openAiAccount.email ?: "Not signed in",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = buildString {
                            append(
                                when (state.openAiAccount.authMode) {
                                    OpenAiAuthMode.API_KEY -> "Auth mode: API key"
                                    OpenAiAuthMode.CHATGPT -> "Auth mode: ChatGPT"
                                    OpenAiAuthMode.CHATGPT_AUTH_TOKENS -> "Auth mode: tokens"
                                    null -> "Auth mode: unknown"
                                },
                            )
                            state.openAiAccount.planType?.let {
                                append(" • ")
                                append(it)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.openAiAccount.lastError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = viewModel::startOpenAiLogin) {
                            Text(if (state.openAiAccount.authMode == null) "Sign in" else "Reauth")
                        }
                        OutlinedButton(onClick = viewModel::refreshOpenAiAccount) {
                            Text("Refresh")
                        }
                        OutlinedButton(onClick = viewModel::logoutOpenAiAccount) {
                            Text("Logout")
                        }
                    }
                    OutlinedButton(onClick = { viewModel.toggleCodexProfileSheet(true) }) {
                        Text("Codex accounts")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("GitHub", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = state.gitHubSession?.userLogin ?: "Not signed in",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = viewModel::startGitHubLogin) {
                            Text(if (state.gitHubSession == null) "Login" else "Relogin")
                        }
                        OutlinedButton(onClick = viewModel::refreshGitHubRepos) {
                            Text("Refresh repos")
                        }
                        OutlinedButton(onClick = viewModel::scanRemoteRepositories) {
                            Text("Scan server")
                        }
                    }
                    state.deviceFlow?.let { flow ->
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(flow.verificationUri, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                Text(flow.userCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                FilledTonalButton(onClick = { viewModel.openExternalUrl(flow.verificationUri) }) {
                                    Icon(Icons.Rounded.Link, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open GitHub verification")
                                }
                            }
                        }
                    }
                }
            }

            if (state.availableGitHubRepos.isNotEmpty()) {
                Text("Pin Repo To Current Dialog", style = MaterialTheme.typography.titleMedium)
                state.availableGitHubRepos.forEach { repo ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { viewModel.pinRepositoryToCurrentThread(repo) }),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(repo.fullName, fontWeight = FontWeight.Bold)
                            Text(
                                repo.htmlUrl,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("SSH Login", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${BuildConfig.DEFAULT_SERVER_HOST}:${BuildConfig.DEFAULT_SERVER_PORT}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LabeledField(
                label = "Username",
                value = state.accountDraft.username,
                onValueChange = { value -> viewModel.onDraftChanged { it.copy(username = value) } },
            )
            LabeledField(
                label = "Password",
                value = state.accountDraft.password,
                password = true,
                imeAction = ImeAction.Done,
                onValueChange = { value -> viewModel.onDraftChanged { it.copy(password = value) } },
            )
            Button(
                onClick = viewModel::connectDraftAccount,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Войти")
            }
            if (state.accounts.isNotEmpty()) {
                Text("Saved users", style = MaterialTheme.typography.titleMedium)
                state.accounts.forEach { account ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(account.username, fontWeight = FontWeight.Bold)
                                Text(
                                    account.homeDirectory ?: account.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { viewModel.selectAccount(account.id) }) {
                                Text("Use")
                            }
                            TextButton(onClick = { viewModel.deleteAccount(account.id) }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun CodexProfileSheet(state: MainUiState, viewModel: MainViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { viewModel.toggleCodexProfileSheet(false) },
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
            Text("Codex Accounts", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Профили работают через ~/.codex/profiles/*.json и замену ~/.codex/auth.json на сервере.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = viewModel::startOpenAiLogin) {
                    Text("Add account")
                }
                OutlinedButton(onClick = viewModel::refreshOpenAiAccount) {
                    Text("Refresh")
                }
            }
            if (state.codexProfiles.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("No saved Codex profiles yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "После первого успешного входа текущий auth.json будет сохранён сюда автоматически.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                state.codexProfiles.forEach { profile ->
                    CodexProfileCard(
                        profile = profile,
                        selected = profile.isActive,
                        onClick = { viewModel.selectCodexProfile(profile.name) },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun CodexProfileCard(
    profile: CodexProfile,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = profile.email ?: profile.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(profile.name)
                        profile.planType?.let {
                            append(" • ")
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UsageGauge(profile.fiveHourWindow)
                UsageGauge(profile.weeklyWindow)
            }
        }
    }
}

@Composable
private fun UsageGauge(window: com.codex.android.app.core.model.CodexUsageWindow) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val accentColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.size(58.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 9f, cap = StrokeCap.Round),
                size = Size(size.width, size.height),
            )
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * (window.progress ?: 0.18f).coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = 9f, cap = StrokeCap.Round),
                size = Size(size.width, size.height),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(window.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Text(window.valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WelcomeGate(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Codex", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    text = "Remote Codex for Android with live streaming, file access and profile switching.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(34.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 24.dp,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Login", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        text = "Server ${BuildConfig.DEFAULT_SERVER_HOST}:${BuildConfig.DEFAULT_SERVER_PORT}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LabeledField(
                        label = "Username",
                        value = state.accountDraft.username,
                        onValueChange = { value -> viewModel.onDraftChanged { it.copy(username = value) } },
                    )
                    LabeledField(
                        label = "Password",
                        value = state.accountDraft.password,
                        password = true,
                        imeAction = ImeAction.Done,
                        onValueChange = { value -> viewModel.onDraftChanged { it.copy(password = value) } },
                    )
                    Button(
                        onClick = viewModel::connectDraftAccount,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Войти")
                    }
                    if (state.connectionState.status == ConnectionStatus.CONNECTING) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedLiquidBackground() {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFEEF4FF),
                        Color(0xFFF9EEE2),
                        Color(0xFFE5F4EE),
                    ),
                    start = Offset.Zero,
                    end = Offset(1800f, 2400f),
                ),
            ),
    ) {
        val transition = rememberInfiniteTransition(label = "liquid-bg")
        val blobOneX by transition.animateFloat(
            initialValue = 0.08f,
            targetValue = 0.72f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 16000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "blob-one-x",
        )
        val blobOneY by transition.animateFloat(
            initialValue = 0.12f,
            targetValue = 0.68f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 19000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "blob-one-y",
        )
        val blobTwoX by transition.animateFloat(
            initialValue = 0.66f,
            targetValue = 0.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 21000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "blob-two-x",
        )
        val blobTwoY by transition.animateFloat(
            initialValue = 0.18f,
            targetValue = 0.82f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 17000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "blob-two-y",
        )
        val blobThreeX by transition.animateFloat(
            initialValue = 0.22f,
            targetValue = 0.78f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 24000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "blob-three-x",
        )
        val blobThreeY by transition.animateFloat(
            initialValue = 0.62f,
            targetValue = 0.14f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 20000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "blob-three-y",
        )

        LiquidBlob(
            modifier = Modifier
                .size(maxWidth * 0.82f)
                .offset(x = maxWidth * blobOneX - maxWidth * 0.35f, y = maxHeight * blobOneY - maxWidth * 0.28f),
            colors = listOf(Color(0xFF65C6B7), Color(0x332FA48F)),
        )
        LiquidBlob(
            modifier = Modifier
                .size(maxWidth * 0.94f)
                .offset(x = maxWidth * blobTwoX - maxWidth * 0.42f, y = maxHeight * blobTwoY - maxWidth * 0.36f),
            colors = listOf(Color(0xFFEA8A5A), Color(0x33F6C4A2)),
        )
        LiquidBlob(
            modifier = Modifier
                .size(maxWidth * 0.74f)
                .offset(x = maxWidth * blobThreeX - maxWidth * 0.3f, y = maxHeight * blobThreeY - maxWidth * 0.24f),
            colors = listOf(Color(0xFF4B9AE8), Color(0x3398C5FF)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent, Color.White.copy(alpha = 0.08f)),
                    ),
                ),
        )
    }
}

@Composable
private fun LiquidBlob(
    modifier: Modifier,
    colors: List<Color>,
) {
    Box(
        modifier = modifier.background(
            brush = Brush.radialGradient(colors = colors),
            shape = CircleShape,
        ),
    )
}

@Composable
private fun SystemBarScrims() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.24f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(160.dp)
                .navigationBarsPadding()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                        ),
                    ),
                ),
        )
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
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions.Default,
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                decorationBox = { innerTextField ->
                    if (value.isBlank()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
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
