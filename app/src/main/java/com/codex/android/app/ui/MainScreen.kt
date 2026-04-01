@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.codex.android.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
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

    Box(modifier = Modifier.fillMaxSize()) {
        if (showWelcomeGate) {
            AnimatedLiquidBackground()
            SystemBarScrims()
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
        }

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
                        Icon(Icons.Rounded.Menu, contentDescription = "Открыть боковую панель")
                    }
                }
            }
            Text(
                text = "Codex",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
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
                            ?: "Кодекс",
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
                            text = state.selectedAccount?.username ?: "гость",
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
    var showFolderPicker by remember { mutableStateOf(false) }

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
            FolderPickerCard(
                currentDirectory = state.sidebar.currentDirectory,
                onOpenPicker = { showFolderPicker = true },
                onRefresh = { viewModel.refreshDirectory(refreshThreads = false) },
            )
            ThreadSection(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showFolderPicker) {
        FolderPickerSheet(
            state = state,
            onDismiss = { showFolderPicker = false },
            onOpenDirectory = { path -> viewModel.enterDirectory(path) },
            onRefresh = { viewModel.refreshDirectory(refreshThreads = false) },
        )
    }
}

@Composable
private fun FolderPickerCard(
    currentDirectory: String,
    onOpenPicker: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Папка",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = currentDirectory,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedIconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Обновить папку")
            }
            FilledTonalButton(onClick = onOpenPicker) {
                Text("Выбрать папку")
            }
        }
    }
}

@Composable
private fun FolderPickerSheet(
    state: MainUiState,
    onDismiss: () -> Unit,
    onOpenDirectory: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val directories = state.sidebar.remoteFiles.filter { it.isDirectory }
    val parentDirectory = remember(state.sidebar.currentDirectory) { parentDirectoryOf(state.sidebar.currentDirectory) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Выбрать папку", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = state.sidebar.currentDirectory,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedIconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Обновить список папок")
                }
            }
            parentDirectory?.let { parent ->
                FolderPickerRow(
                    name = "..",
                    selected = false,
                    onClick = { onOpenDirectory(parent) },
                )
            }
            if (directories.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Text(
                        text = "В этой папке нет вложенных директорий.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                directories.forEach { node ->
                    FolderPickerRow(
                        name = node.name,
                        selected = false,
                        onClick = { onOpenDirectory(node.path) },
                    )
                }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Готово")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun FolderPickerRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
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
                    Text("Сервер", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "${BuildConfig.DEFAULT_SERVER_HOST}:${BuildConfig.DEFAULT_SERVER_PORT}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutlinedIconButton(onClick = { viewModel.toggleSettings(true) }) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Настройки")
                }
            }
            Text(
                text = state.connectionState.message ?: when (state.connectionState.status) {
                    ConnectionStatus.CONNECTED -> "Подключено"
                    ConnectionStatus.CONNECTING -> "Подключение"
                    ConnectionStatus.RECONNECTING -> "Переподключение"
                    ConnectionStatus.FAILED_AUTH -> "Ошибка входа"
                    ConnectionStatus.FAILED_SERVER -> "Сервер недоступен"
                    ConnectionStatus.DISCONNECTED -> "Нет подключения"
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
                Text("Добавить пользователя")
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
                Text("Диалоги", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = viewModel::createThread) {
                    Text("Новый")
                }
            }
            if (state.threads.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("В этой папке пока нет диалогов", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Выберите другую папку или создайте новый чат.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
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
                                    text = thread.preview.ifBlank { "Пустой диалог" },
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
    var actionNode by remember { mutableStateOf<RemoteFileNode?>(null) }
    var moveNode by remember { mutableStateOf<RemoteFileNode?>(null) }
    var moveTarget by remember { mutableStateOf("") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

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
                    Text("Файлы", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = state.sidebar.currentDirectory,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedIconButton(
                        onClick = {
                            newFolderName = ""
                            showCreateFolderDialog = true
                        },
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Создать папку")
                    }
                    OutlinedIconButton(onClick = { viewModel.refreshDirectory() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Обновить файлы")
                    }
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
                            onLongPress = null,
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
                        onLongPress = {
                            actionNode = node
                        },
                    )
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        PathPromptDialog(
            title = "Новая папка",
            value = newFolderName,
            onValueChange = { newFolderName = it },
            confirmLabel = "Создать",
            supportingText = "Папка будет создана в текущей директории.",
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = {
                viewModel.createDirectoryInCurrentPath(newFolderName)
                showCreateFolderDialog = false
            },
        )
    }

    actionNode?.let { node ->
        AlertDialog(
            onDismissRequest = { actionNode = null },
            title = { Text(node.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.downloadRemotePath(node.path)
                            actionNode = null
                        },
                    ) {
                        Text("Скачать")
                    }
                    TextButton(
                        onClick = {
                            moveNode = node
                            moveTarget = node.path
                            actionNode = null
                        },
                    ) {
                        Text("Переместить / переименовать")
                    }
                    TextButton(
                        onClick = {
                            viewModel.deleteRemotePath(node.path, node.isDirectory)
                            actionNode = null
                        },
                    ) {
                        Text("Удалить")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { actionNode = null }) {
                    Text("Закрыть")
                }
            },
        )
    }

    moveNode?.let { node ->
        PathPromptDialog(
            title = "Переместить / переименовать",
            value = moveTarget,
            onValueChange = { moveTarget = it },
            confirmLabel = "Применить",
            supportingText = "Можно ввести новое имя или полный путь.",
            onDismiss = { moveNode = null },
            onConfirm = {
                viewModel.renameOrMoveRemotePath(node.path, moveTarget)
                moveNode = null
            },
        )
    }
}

@Composable
private fun FileNodeCard(
    node: RemoteFileNode,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongPress,
            ),
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
private fun PathPromptDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmLabel: String,
    supportingText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = value.trim().isNotBlank(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
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
            Text(
                "Codex",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = state.selectedAccount?.let { "Подключено как ${it.username}" } ?: "Войдите, чтобы начать",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = when {
                    state.openAiAccount.requiresOpenAiAuth -> "Войдите в ChatGPT перед первым сообщением."
                    else -> "Откройте диалог слева или создайте новый."
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
            Text("Нужен вход в ChatGPT", style = MaterialTheme.typography.titleMedium)
            Text(
                text = when (state.openAiAccount.loginState) {
                    OpenAiLoginState.WAITING_BROWSER_AUTH -> "Завершите вход в браузере и вернитесь сюда."
                    OpenAiLoginState.ERROR -> state.openAiAccount.lastError ?: "Не удалось выполнить вход."
                    else -> "Этой сессии Codex нужен аккаунт ChatGPT, чтобы отвечать."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = viewModel::startOpenAiLogin) {
                    Text(if (state.openAiAccount.loginState == OpenAiLoginState.WAITING_BROWSER_AUTH) "Открыть снова" else "Войти")
                }
                OutlinedButton(onClick = { viewModel.toggleCodexProfileSheet(true) }) {
                    Text("Аккаунты")
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
                        text = "Размышления",
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
                        text = "Ответ печатается",
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
                                    text = if (composerEnabled) "Напишите сообщение для Codex" else "Сначала войдите в ChatGPT",
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
                            contentDescription = if (state.composer.isSending) "Остановить ответ" else "Отправить сообщение",
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
                                    ?: "Модель",
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
                        label = { Text("Раздумья: ${reasoningEffortLabel(state.composer.selectedReasoningEffort)}") },
                    )
                    DropdownMenu(expanded = effortExpanded, onDismissRequest = { effortExpanded = false }) {
                        ReasoningEffort.entries.forEach { effort ->
                            DropdownMenuItem(
                                text = { Text(reasoningEffortLabel(effort)) },
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
                        text = "Идёт ответ",
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
    val clipboardManager = LocalClipboardManager.current
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
            Text("Настройки", style = MaterialTheme.typography.titleLarge)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Аккаунт GPT", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = state.openAiAccount.email ?: "Вход не выполнен",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = buildString {
                            append(
                                when (state.openAiAccount.authMode) {
                                    OpenAiAuthMode.API_KEY -> "Режим входа: API key"
                                    OpenAiAuthMode.CHATGPT -> "Режим входа: ChatGPT"
                                    OpenAiAuthMode.CHATGPT_AUTH_TOKENS -> "Режим входа: токены"
                                    null -> "Режим входа: неизвестно"
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
                            Text(if (state.openAiAccount.authMode == null) "Войти" else "Войти заново")
                        }
                        OutlinedButton(onClick = viewModel::refreshOpenAiAccount) {
                            Text("Обновить")
                        }
                        OutlinedButton(onClick = viewModel::logoutOpenAiAccount) {
                            Text("Выйти")
                        }
                    }
                    OutlinedButton(onClick = { viewModel.toggleCodexProfileSheet(true) }) {
                        Text("Аккаунты Codex")
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
                        text = state.gitHubSession?.userLogin ?: "Вход не выполнен",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = viewModel::startGitHubLogin) {
                            Text(if (state.gitHubSession == null) "Войти" else "Войти заново")
                        }
                        OutlinedButton(onClick = viewModel::refreshGitHubRepos) {
                            Text("Обновить репо")
                        }
                        OutlinedButton(onClick = viewModel::scanRemoteRepositories) {
                            Text("Сканировать сервер")
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
                                    Text("Открыть подтверждение GitHub")
                                }
                            }
                        }
                    }
                }
            }

            if (state.availableGitHubRepos.isNotEmpty()) {
                Text("Закрепить репозиторий за текущим диалогом", style = MaterialTheme.typography.titleMedium)
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

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Диагностика", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Скопировать текущие логи подключения и состояния приложения.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(viewModel.buildDiagnosticLog()))
                            viewModel.notifyLogsCopied()
                        },
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Скопировать логи")
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
            Text("Вход по SSH", style = MaterialTheme.typography.titleLarge)
            LabeledField(
                label = "Логин",
                value = state.accountDraft.username,
                onValueChange = { value -> viewModel.onDraftChanged { it.copy(username = value) } },
            )
            LabeledField(
                label = "Пароль",
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
                Text("Сохранённые пользователи", style = MaterialTheme.typography.titleMedium)
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
                                Text("Выбрать")
                            }
                            TextButton(onClick = { viewModel.deleteAccount(account.id) }) {
                                Text("Удалить", color = MaterialTheme.colorScheme.error)
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
            Text("Аккаунты Codex", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Профили работают через ~/.codex/profiles/*.json и замену ~/.codex/auth.json на сервере.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = viewModel::startOpenAiLogin) {
                    Text("Добавить аккаунт")
                }
                OutlinedButton(onClick = viewModel::refreshOpenAiAccount) {
                    Text("Обновить")
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
                        Text("Сохранённых профилей Codex пока нет", style = MaterialTheme.typography.titleMedium)
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
                        onClick = {
                            if (!profile.isActive) {
                                viewModel.selectCodexProfile(profile.name)
                            }
                        },
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
                        append(if (profile.name == "current") "Текущий auth.json" else profile.name)
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
                if (selected) {
                    Text(
                        text = "Активен",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
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
    val clipboardManager = LocalClipboardManager.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
    ) {
        Text(
            text = "Codex",
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-116).dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .animateContentSize()
                .padding(bottom = 4.dp),
            shape = RoundedCornerShape(34.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shadowElevation = 24.dp,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Вход",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                LabeledField(
                    label = "Логин",
                    value = state.accountDraft.username,
                    labelColor = Color.White,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.86f),
                    textColor = Color.White,
                    placeholderColor = Color.White.copy(alpha = 0.72f),
                    onValueChange = { value -> viewModel.onDraftChanged { it.copy(username = value) } },
                )
                LabeledField(
                    label = "Пароль",
                    value = state.accountDraft.password,
                    password = true,
                    imeAction = ImeAction.Done,
                    labelColor = Color.White,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.86f),
                    textColor = Color.White,
                    placeholderColor = Color.White.copy(alpha = 0.72f),
                    onValueChange = { value -> viewModel.onDraftChanged { it.copy(password = value) } },
                )
                Button(
                    onClick = viewModel::connectDraftAccount,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Войти")
                }
                if (
                    state.connectionState.status == ConnectionStatus.FAILED_AUTH ||
                    state.connectionState.status == ConnectionStatus.FAILED_SERVER
                ) {
                    FilledTonalButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(viewModel.buildDiagnosticLog()))
                            viewModel.notifyLogsCopied()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Скопировать лог")
                    }
                }
                if (state.connectionState.status == ConnectionStatus.CONNECTING) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun AnimatedLiquidBackground() {
    val colorScheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colorScheme.background,
                        colorScheme.surfaceContainer,
                        colorScheme.surface,
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
            colors = listOf(colorScheme.primary.copy(alpha = 0.42f), colorScheme.primary.copy(alpha = 0.04f)),
        )
        LiquidBlob(
            modifier = Modifier
                .size(maxWidth * 0.94f)
                .offset(x = maxWidth * blobTwoX - maxWidth * 0.42f, y = maxHeight * blobTwoY - maxWidth * 0.36f),
            colors = listOf(colorScheme.secondary.copy(alpha = 0.34f), colorScheme.secondary.copy(alpha = 0.04f)),
        )
        LiquidBlob(
            modifier = Modifier
                .size(maxWidth * 0.74f)
                .offset(x = maxWidth * blobThreeX - maxWidth * 0.3f, y = maxHeight * blobThreeY - maxWidth * 0.24f),
            colors = listOf(colorScheme.tertiary.copy(alpha = 0.3f), colorScheme.tertiary.copy(alpha = 0.03f)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colorScheme.surface.copy(alpha = 0.18f),
                            Color.Transparent,
                            colorScheme.background.copy(alpha = 0.34f),
                        ),
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
                            MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.42f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(272.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
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
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    placeholderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = labelColor)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = containerColor,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                cursorBrush = SolidColor(textColor),
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
                            color = placeholderColor,
                        )
                    }
                    innerTextField()
                },
            )
        }
    }
}

private fun reasoningEffortLabel(effort: ReasoningEffort): String {
    return when (effort) {
        ReasoningEffort.NONE -> "выкл"
        ReasoningEffort.MINIMAL -> "минимум"
        ReasoningEffort.LOW -> "низко"
        ReasoningEffort.MEDIUM -> "средне"
        ReasoningEffort.HIGH -> "высоко"
        ReasoningEffort.XHIGH -> "максимум"
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
