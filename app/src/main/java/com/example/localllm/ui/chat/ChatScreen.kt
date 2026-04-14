package com.example.localllm.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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

    val shouldShowEmptyState by remember(uiState.messages, uiState.streamingText, uiState.isModelLoading) {
        derivedStateOf {
            uiState.messages.isEmpty() &&
                uiState.streamingText.isEmpty() &&
                !uiState.isModelLoading
        }
    }

    LaunchedEffect(conversationId) {
        if (conversationId > 0L) {
            viewModel.loadConversation(conversationId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ChatEvent.ScrollToBottom -> listState.scrollToBottomIfNeeded(force = true)
                is ChatEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LaunchedEffect(
        uiState.messages.size,
        uiState.streamingText,
        uiState.isModelLoading,
        uiState.errorMessage
    ) {
        val shouldAutoScroll =
            listState.isNearBottom() || uiState.isGenerating || uiState.streamingText.isNotEmpty()
        if (shouldAutoScroll) {
            listState.scrollToBottomIfNeeded(force = false)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatTopBar(
                uiState = uiState,
                onNewChat = viewModel::startNewConversation
            )
        },
        bottomBar = {
            ChatInputBar(
                text = uiState.inputText,
                onTextChange = viewModel::onInputChanged,
                isGenerating = uiState.isGenerating,
                onSend = viewModel::sendMessage,
                onStop = viewModel::stopGeneration
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.ime
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                shouldShowEmptyState -> {
                    ChatEmptyState(
                        activeModelId = uiState.activeModelId,
                        onSuggestionClick = viewModel::onSuggestionClicked
                    )
                }

                else -> {
                    ChatMessageList(
                        uiState = uiState,
                        listState = listState
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageList(
    uiState: ChatUiState,
    listState: LazyListState
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = uiState.messages,
            key = { it.id }
        ) { message ->
            AnimatedMessageItem(message = message)
        }

        if (uiState.isModelLoading) {
            item(key = "model_loading") {
                ModelLoadingIndicator()
            }
        }

        uiState.errorMessage?.let { errorMessage ->
            item(key = "error") {
                ChatErrorCard(message = errorMessage)
            }
        }

        if (uiState.streamingText.isNotEmpty()) {
            item(key = "streaming") {
                StreamingBubble(text = uiState.streamingText)
            }
        } else if (uiState.isGenerating) {
            item(key = "typing") {
                TypingIndicator()
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.size(4.dp))
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AnimatedMessageItem(message: Message) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = tween(220)
        ) + fadeIn(animationSpec = tween(180))
    ) {
        MessageBubble(message = message)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    uiState: ChatUiState,
    onNewChat: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (uiState.activeModelId.isNotEmpty()) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                        .semantics {
                            contentDescription = "حالة النموذج"
                            stateDescription = if (uiState.activeModelId.isNotEmpty()) {
                                "نموذج نشط"
                            } else {
                                "لا يوجد نموذج نشط"
                            }
                        }
                )
                Spacer(Modifier.width(8.dp))
                Column(
                    modifier = Modifier.semantics(mergeDescendants = true) { heading() }
                ) {
                    Text(
                        text = "نبض",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = uiState.activeModelId.ifEmpty { "لم يُختر نموذج" },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.activeModelId.isNotEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                }
            }
        },
        actions = {
            uiState.tokensPerSecond?.let { tps ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${"%.1f".format(tps)} t/s",
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

@Composable
private fun ChatEmptyState(
    activeModelId: String,
    onSuggestionClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (activeModelId.isEmpty()) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (activeModelId.isEmpty()) {
                        Icons.Outlined.Warning
                    } else {
                        Icons.Outlined.Memory
                    },
                    contentDescription = if (activeModelId.isEmpty()) {
                        "تحذير: لا يوجد نموذج"
                    } else {
                        "نموذج جاهز"
                    },
                    modifier = Modifier.size(40.dp),
                    tint = if (activeModelId.isEmpty()) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }
        }

        Spacer(Modifier.size(24.dp))

        if (activeModelId.isEmpty()) {
            Text(
                text = "لا يوجد نموذج نشط",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(Modifier.size(8.dp))

            Text(
                text = "الرجاء اختيار نموذج من الإعدادات أو تنزيل نموذج جديد للبدء في استخدام التطبيق.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "نبض جاهز",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(Modifier.size(8.dp))

            Text(
                text = "كل معالجتك تتم محليًا على جهازك\nبدون أي اتصال بالإنترنت",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.size(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionItem("اشرح لي AI", onClick = onSuggestionClick)
                SuggestionItem("اكتب قصيدة", onClick = onSuggestionClick)
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    text: String,
    onClick: (String) -> Unit
) {
    SuggestionChip(
        onClick = { onClick(text) },
        shape = RoundedCornerShape(20.dp),
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconContentColor = MaterialTheme.colorScheme.primary
        ),
        label = {
            Text(
                text = text,
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

@Composable
fun MessageBubble(message: Message) {
    val isUser = message.role == MessageRole.USER
    val formattedTime = rememberMessageTime(message.createdAt)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = buildMessageAccessibilityLabel(
                    message = message,
                    formattedTime = formattedTime
                )
            },
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isUser) {
                MessageAvatar(isUser = false)
                Spacer(Modifier.width(8.dp))
            }

            ChatBubbleSurface(
                isUser = isUser,
                text = message.content
            )

            if (isUser) {
                Spacer(Modifier.width(8.dp))
                MessageAvatar(isUser = true)
            }
        }

        MessageMetaRow(
            isUser = isUser,
            formattedTime = formattedTime,
            tokensUsed = message.tokensUsed
        )
    }
}

@Composable
fun StreamingBubble(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = if (text.isBlank()) {
                    "المساعد يكتب ردًا"
                } else {
                    "رد المساعد قيد التوليد"
                }
            },
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        MessageAvatar(isUser = false)
        Spacer(Modifier.width(8.dp))

        Surface(
            shape = assistantBubbleShape(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(min = 60.dp, max = 320.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "cursor")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "cursorAlpha"
            )

            val cursorChar = if (alpha > 0.5f) " ▌" else ""

            Text(
                text = text + cursorChar,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "المساعد يفكر في الرد"
            },
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MessageAvatar(isUser = false)
        Spacer(Modifier.width(8.dp))

        Surface(
            shape = assistantBubbleShape(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        modifier = Modifier
                            .size(7.dp)
                            .offset(y = offsetY.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelLoadingIndicator() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "جاري تحميل النموذج"
            }
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
                text = "جاري تحميل النموذج...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun ChatErrorCard(message: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "خطأ في التوليد. $message"
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

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
                        text = "اكتب رسالتك...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                },
                modifier = Modifier.weight(1f),
                maxLines = 6,
                shape = RoundedCornerShape(20.dp),
                enabled = true,
                keyboardActions = KeyboardActions(
                    onSend = { if (!isGenerating && text.isNotBlank()) onSend() }
                ),
                singleLine = false,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
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
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = "إيقاف التوليد",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = text.isNotBlank(),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "إرسال الرسالة",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageAvatar(isUser: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isUser) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        modifier = Modifier.size(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isUser) Icons.Filled.Person else Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isUser) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
        }
    }
}

@Composable
private fun ChatBubbleSurface(
    isUser: Boolean,
    text: String
) {
    Surface(
        shape = if (isUser) userBubbleShape() else assistantBubbleShape(),
        color = if (isUser) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.widthIn(min = 60.dp, max = 320.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun MessageMetaRow(
    isUser: Boolean,
    formattedTime: String,
    tokensUsed: Int?
) {
    Row(
        modifier = Modifier.padding(
            start = if (!isUser) 36.dp else 0.dp,
            end = if (isUser) 36.dp else 0.dp,
            top = 3.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formattedTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )

        tokensUsed?.let {
            Text(
                text = "·",
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "$it tokens",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun userBubbleShape() = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomStart = 18.dp,
    bottomEnd = 4.dp
)

private fun assistantBubbleShape() = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomStart = 4.dp,
    bottomEnd = 18.dp
)

@Composable
private fun rememberMessageTime(timestamp: Long): String {
    val formatter = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }
    return remember(timestamp) {
        formatter.format(Date(timestamp))
    }
}

private suspend fun LazyListState.scrollToBottomIfNeeded(force: Boolean) {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return

    if (force || isNearBottom()) {
        animateScrollToItem(lastIndex)
    }
}

private fun LazyListState.isNearBottom(threshold: Int = 2): Boolean {
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return true
    val total = layoutInfo.totalItemsCount
    return lastVisible >= total - 1 - threshold
}

private fun buildMessageAccessibilityLabel(
    message: Message,
    formattedTime: String
): String {
    val sender = if (message.role == MessageRole.USER) "أنت" else "المساعد"
    return buildString {
        append("رسالة من $sender. ")
        append(message.content)
        append(". أُرسلت الساعة $formattedTime")
        message.tokensUsed?.let { append(". عدد الرموز $it") }
        append(".")
    }
}
