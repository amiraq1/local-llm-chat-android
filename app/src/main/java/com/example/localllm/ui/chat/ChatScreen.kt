package com.example.localllm.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
<<<<<<< HEAD
<<<<<<< HEAD
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
=======
=======
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
<<<<<<< HEAD
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
=======
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.localllm.domain.model.Message
import com.example.localllm.domain.model.MessageRole
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: Long = -1L,
    onOpenConversation: (Long) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(conversationId) {
        if (conversationId > 0L) viewModel.loadConversation(conversationId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ChatEvent.ScrollToBottom -> {
                    val count = listState.layoutInfo.totalItemsCount
                    if (count > 0) listState.animateScrollToItem(count - 1)
                }
                is ChatEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { ChatTopBar(uiState = uiState, onNewChat = viewModel::startNewConversation) },
        bottomBar = {
            ChatInputBar(
                text = uiState.inputText,
                onTextChange = viewModel::onInputChanged,
                isGenerating = uiState.isGenerating,
                onSend = viewModel::sendMessage,
                onStop = viewModel::stopGeneration
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.messages.isEmpty() && uiState.streamingText.isEmpty() && !uiState.isModelLoading) {
                ChatEmptyState(
                    activeModelId = uiState.activeModelId,
<<<<<<< HEAD
<<<<<<< HEAD
                    onSuggestionClick = viewModel::onInputChanged
=======
                    onSuggestionClick = viewModel::onSuggestionClicked
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
=======
                    onSuggestionClick = viewModel::onSuggestionClicked
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        // Animate each message entry
                        AnimatedVisibility(
                            visible = true,
                            enter = slideInVertically(
                                initialOffsetY = { it / 3 },
                                animationSpec = tween(250)
                            ) + fadeIn(tween(200))
                        ) {
                            MessageBubble(message = message)
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    if (uiState.isModelLoading) {
                        item { ModelLoadingIndicator() }
                    }

                    if (uiState.streamingText.isNotEmpty()) {
                        item { StreamingBubble(text = uiState.streamingText) }
                    } else if (uiState.isGenerating && uiState.streamingText.isEmpty()) {
                        item { TypingIndicator() }
                    }
                }
            }
        }
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(uiState: ChatUiState, onNewChat: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status dot
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (uiState.activeModelId.isNotEmpty())
                                MaterialTheme.colorScheme.secondary
                            else
                                MaterialTheme.colorScheme.outline
                        )
                        .semantics {
<<<<<<< HEAD
<<<<<<< HEAD
                            contentDescription = if (uiState.activeModelId.isNotEmpty())
                                "نموذج متصل" else "لا يوجد نموذج"
=======
=======
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
                            contentDescription = "حالة النموذج"
                            stateDescription = if (uiState.activeModelId.isNotEmpty()) {
                                "نموذج نشط"
                            } else {
                                "لا يوجد نموذج نشط"
                            }
<<<<<<< HEAD
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
=======
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
                        }
                )
                Spacer(Modifier.width(8.dp))
                Column(
                    modifier = Modifier.semantics(mergeDescendants = true) { heading() }
                ) {
                    Text(
                        "نبض",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (uiState.activeModelId.isNotEmpty()) {
                        Text(
                            uiState.activeModelId,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "لم يُختر نموذج",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        },
        actions = {
            if (uiState.tokensPerSecond != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        "${"%.1f".format(uiState.tokensPerSecond)} t/s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onNewChat) {
                Icon(
                    Icons.Outlined.EditNote,
                    contentDescription = "محادثة جديدة",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
<<<<<<< HEAD
<<<<<<< HEAD
private fun ChatEmptyState(activeModelId: String, onSuggestionClick: (String) -> Unit = {}) {
=======
private fun ChatEmptyState(activeModelId: String, onSuggestionClick: (String) -> Unit) {
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
=======
private fun ChatEmptyState(activeModelId: String, onSuggestionClick: (String) -> Unit) {
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (activeModelId.isEmpty()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (activeModelId.isEmpty()) Icons.Outlined.Warning else Icons.Outlined.Memory,
                    contentDescription = if (activeModelId.isEmpty()) "تحذير: لا يوجد نموذج" else "نموذج جاهز",
                    modifier = Modifier.size(40.dp),
                    tint = if (activeModelId.isEmpty()) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        if (activeModelId.isEmpty()) {
            Text(
                "لا يوجد نموذج نشط",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "الرجاء اختيار نموذج من الإعدادات أو تنزيل نموذج جديد للبدء في استخدام التطبيق.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        } else {
            Text(
                "نبض جاهز",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "كل معالجتك تتم محليًا على جهازك\nبدون أي اتصال بالإنترنت",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )

            Spacer(Modifier.height(32.dp))

            // Interactive suggestion chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
<<<<<<< HEAD
<<<<<<< HEAD
                SuggestionItem("اشرح لي AI") { onSuggestionClick(it) }
                SuggestionItem("اكتب قصيدة") { onSuggestionClick(it) }
=======
                SuggestionItem("اشرح لي AI", onClick = onSuggestionClick)
                SuggestionItem("اكتب قصيدة", onClick = onSuggestionClick)
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
=======
                SuggestionItem("اشرح لي AI", onClick = onSuggestionClick)
                SuggestionItem("اكتب قصيدة", onClick = onSuggestionClick)
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
            }
        }
    }
}

@Composable
<<<<<<< HEAD
<<<<<<< HEAD
private fun SuggestionItem(text: String, onClick: (String) -> Unit = {}) {
    Surface(
=======
private fun SuggestionItem(text: String, onClick: (String) -> Unit) {
    SuggestionChip(
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
=======
private fun SuggestionItem(text: String, onClick: (String) -> Unit) {
    SuggestionChip(
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
        onClick = { onClick(text) },
        shape = RoundedCornerShape(20.dp),
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconContentColor = MaterialTheme.colorScheme.primary
        ),
        label = {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge
            )
        },
        icon = {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(SuggestionChipDefaults.IconSize)
            )
        }
    )
}

// ─── Message Bubble ───────────────────────────────────────────────────────────

@Composable
fun MessageBubble(message: Message) {
    val isUser = message.role == MessageRole.USER
    val isTool = message.role == MessageRole.TOOL
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            // Assistant / tool avatar
            if (!isUser) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTool)
                        MaterialTheme.colorScheme.tertiaryContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isTool) Icons.Outlined.Build else Icons.Filled.AutoAwesome,
                            contentDescription = if (isTool) "نتيجة أداة" else "المساعد",
                            modifier = Modifier.size(14.dp),
                            tint = if (isTool)
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            // Bubble
            Surface(
                shape = RoundedCornerShape(
                    topStart    = 18.dp,
                    topEnd      = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd   = if (isUser) 4.dp else 18.dp
                ),
                color = when {
                    isUser -> MaterialTheme.colorScheme.primary
                    isTool -> MaterialTheme.colorScheme.tertiaryContainer
                    else   -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.widthIn(min = 60.dp, max = 300.dp)
            ) {
                Text(
                    text  = message.content,
                    style = if (isTool)
                        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    else
                        MaterialTheme.typography.bodyMedium,
                    color = when {
                        isUser -> MaterialTheme.colorScheme.onPrimary
                        isTool -> MaterialTheme.colorScheme.onTertiaryContainer
                        else   -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            // User avatar
            if (isUser) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = "أنت",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // Meta row: time + token count
        Row(
            modifier = Modifier
                .padding(
                    start = if (!isUser) 36.dp else 0.dp,
                    end   = if (isUser) 36.dp else 0.dp,
                    top   = 3.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                timeFormatter.format(Date(message.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            if (message.tokensUsed != null) {
                Text("·", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall)
                Text(
                    "${message.tokensUsed} tokens",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// ─── Streaming Bubble ────────────────────────────────────────────────────────

@Composable
fun StreamingBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = "المساعد يكتب",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .widthIn(min = 60.dp, max = 300.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Blinking cursor
                val infiniteTransition = rememberInfiniteTransition(label = "cursor")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 1f, targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "cursorAlpha"
                )
                
                val cursorChar = if (alpha > 0.5f) " ▌" else "  "
                Text(
                    text = text + cursorChar,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── Typing Indicator ────────────────────────────────────────────────────────

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "المساعد يكتب..." },
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = "جارٍ التفكير",
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Single InfiniteTransition with 3 animations (optimized from 3 separate transitions)
                val infiniteTransition = rememberInfiniteTransition(label = "typing")
                repeat(3) { i ->
                    val offsetY by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = -6f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400, delayMillis = i * 120),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dotOffset$i"
                    )
                    Box(
                        Modifier
                            .size(7.dp)
                            .offset(y = offsetY.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    )
                }
            }
        }
    }
}

// ─── Model Loading ────────────────────────────────────────────────────────────

@Composable
private fun ModelLoadingIndicator() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "جاري تحميل النموذج...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

// ─── Input Bar ────────────────────────────────────────────────────────────────

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("الرسالة") },
                placeholder = {
                    Text(
                        "اكتب رسالتك...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                },
                modifier = Modifier.weight(1f),
                maxLines = 6,
                shape = RoundedCornerShape(20.dp),
                enabled = !isGenerating,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    disabledBorderColor  = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )

            AnimatedContent(
                targetState = isGenerating,
                transitionSpec = {
                    scaleIn(tween(150)) + fadeIn(tween(150)) togetherWith
                    scaleOut(tween(150)) + fadeOut(tween(150))
                },
                label = "sendStopButton"
            ) { generating ->
                if (generating) {
                    FilledIconButton(
                        onClick = onStop,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor   = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "إيقاف التوليد", modifier = Modifier.size(20.dp))
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = text.isNotBlank(),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "إرسال الرسالة", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
