package com.smartdialer.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartdialer.data.Contact
import com.smartdialer.data.NumberChoice
import com.smartdialer.data.Suggestion
import com.smartdialer.ui.CollectUserMessages
import com.smartdialer.ui.ContactAvatar
import com.smartdialer.ui.UserMessage
import com.smartdialer.ui.components.AiIndicator
import com.smartdialer.ui.components.AiIndicatorState
import com.smartdialer.ui.components.EmptyState
import com.smartdialer.ui.components.GlassIconButton
import com.smartdialer.ui.components.OrbitIcons
import com.smartdialer.ui.components.OrbitTextField
import com.smartdialer.ui.theme.Orbit
import com.smartdialer.ui.theme.OrbitBackground
import com.smartdialer.ui.theme.PillShape
import com.smartdialer.ui.theme.bubbleColorsFor
import com.smartdialer.ui.theme.glass
import com.smartdialer.ui.theme.glassCircle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** Everything the home screen can ask the app to do for a contact. */
class HomeActions(
    val chooseNumber: (Contact) -> NumberChoice,
    val onCallNumber: (String) -> Unit,
    val onWhatsAppNumber: (String) -> Unit,
    val onOpenContactCard: (Contact) -> Unit,
    val onOpenDetails: (Contact) -> Unit,
    val onAddContactInstruction: (Contact, String) -> Unit,
    val onAddGeneralInstruction: (String) -> Unit,
    val onWhatsAppUsed: (Contact) -> Unit,
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenDialer: () -> Unit,
    access: AccessState,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    messages: Flow<UserMessage>,
    actions: HomeActions,
    gestureMap: GestureMap = DefaultGestureMap,
) {
    val needsAccess = !access.all
    val snackbarHostState = remember { SnackbarHostState() }
    CollectUserMessages(messages, snackbarHostState)

    var quick by remember { mutableStateOf<QuickTarget?>(null) }
    var numberPick by remember { mutableStateOf<NumberPick?>(null) }
    var showGeneralSheet by remember { mutableStateOf(false) }
    var searchPick by remember { mutableStateOf<Contact?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var panelHeight by remember { mutableStateOf(0.dp) }
    var controlsHeight by remember { mutableStateOf(0.dp) }
    var cardHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    fun runWith(contact: Contact, purpose: NumberPurpose, number: String) {
        when (purpose) {
            NumberPurpose.Call -> actions.onCallNumber(number)
            NumberPurpose.WhatsApp -> {
                actions.onWhatsAppUsed(contact)
                actions.onWhatsAppNumber(number)
            }
        }
    }

    fun useNumber(contact: Contact, purpose: NumberPurpose) {
        when (val choice = actions.chooseNumber(contact)) {
            NumberChoice.None -> Unit
            is NumberChoice.Use -> runWith(contact, purpose, choice.number)
            is NumberChoice.Pick -> numberPick = NumberPick(contact, purpose, choice.candidates)
        }
    }

    fun handlersFor(contact: Contact) = QuickActionHandlers(
        onCall = { useNumber(contact, NumberPurpose.Call) },
        onWhatsApp = { useNumber(contact, NumberPurpose.WhatsApp) },
        onContactCard = { actions.onOpenContactCard(contact) },
        onDetails = { actions.onOpenDetails(contact) },
        onSaveInstruction = { actions.onAddContactInstruction(contact, it) },
    )

    val onBubbleGesture: OrbitGestureHandler = { entry, slot, gesture, bounds ->
        onSelect(entry.value.contact.id)
        when (gestureMap.actionFor(gesture)) {
            BubbleAction.OpenQuickActions -> quick = QuickTarget(entry.value, entry.index + 1, slot, bounds)
            BubbleAction.Call -> useNumber(entry.value.contact, NumberPurpose.Call)
        }
    }

    // The screen softly blurs behind the quick actions and the search (Android 12 and up; older versions only dim).
    val blur by animateDpAsState(if (quick != null || searchOpen) 14.dp else 0.dp, Orbit.motion.move(), label = "blur")

    // The one place for the reason: the top contact, or the contact whose quick actions are open.
    val reasonTarget: Pair<Suggestion, Int>? = quick?.let { it.suggestion to it.rank }
        ?: state.suggestions.firstOrNull()?.let { it to 1 }

    OrbitBackground {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState, Modifier.padding(bottom = panelHeight)) },
                // While the quick actions or the search are open, screen readers should only see those.
                modifier = (if (blur > 0.dp) Modifier.blur(blur) else Modifier)
                    .then(if (quick != null || searchOpen) Modifier.clearAndSetSemantics { } else Modifier),
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        HomeHeader(
                            indicator = when {
                                state.isRefreshing -> AiIndicatorState.Working
                                !state.aiOn || state.aiProblem -> AiIndicatorState.Paused
                                else -> null
                            },
                            onOpenRules = onOpenRules,
                            onOpenAiSettings = onOpenAiSettings,
                            onAddInstruction = { showGeneralSheet = true },
                        )
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            if (needsAccess) {
                                PermissionPrompt(access, onRequestAccess, onOpenSettings)
                            } else {
                                OrbitField(
                                    suggestions = state.suggestions,
                                    selectedId = state.selectedId,
                                    hiddenId = quick?.suggestion?.contact?.id,
                                    canLoadMore = state.canLoadMore,
                                    onGesture = onBubbleGesture,
                                    onLoadMore = onLoadMore,
                                    bottomInset = panelHeight + Orbit.spacing.xl,
                                    empty = {
                                        EmptyState(
                                            icon = Icons.Outlined.Groups,
                                            title = "Quiet skies",
                                            body = "Make or miss a few calls and the people you talk to will drift into view here.",
                                        )
                                    },
                                )
                            }
                        }
                    }
                    BottomPanel(
                        cardSpace = if (reasonTarget != null && !needsAccess) cardHeight else 0.dp,
                        onOpenDialer = onOpenDialer,
                        onOpenSearch = { searchOpen = true },
                        onControlsHeight = { controlsHeight = it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .onSizeChanged { panelHeight = with(density) { it.height.toDp() } },
                    )
                }
            }

            quick?.let { target ->
                val contact = target.suggestion.contact
                val handlers = handlersFor(contact)
                QuickActionsOverlay(
                    target = target,
                    actions = remember(target) { quickActionsFor(contact.name, handlers) },
                    onDismiss = { quick = null },
                )
            }

            // The reason card sits above the dimmed screen, so it stays readable while the quick actions are open.
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            AnimatedVisibility(
                visible = reasonTarget != null && !searchOpen && !needsAccess,
                enter = fadeIn(Orbit.motion.enter()) + slideInVertically(Orbit.motion.enter()) { it / 3 },
                exit = fadeOut(Orbit.motion.exit()) + slideOutVertically(Orbit.motion.exit()) { it / 3 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = Orbit.spacing.lg, end = Orbit.spacing.lg, bottom = navBottom + controlsHeight + Orbit.spacing.lg + Orbit.spacing.sm),
            ) {
                ReasonCard(
                    target = reasonTarget,
                    modifier = Modifier.onSizeChanged { cardHeight = with(density) { it.height.toDp() } + Orbit.spacing.md },
                )
            }

            SearchOverlay(
                open = searchOpen,
                query = state.query,
                onQueryChange = onQueryChange,
                results = state.searchResults,
                suggestions = state.searchSuggestions,
                hiddenIds = state.hiddenIds,
                access = access,
                onPick = { searchPick = it },
                onClose = { searchOpen = false },
            )
        }
    }

    numberPick?.let { pick ->
        NumberPickerDialog(
            pick = pick,
            onPick = { number -> runWith(pick.contact, pick.purpose, number); numberPick = null },
            onDismiss = { numberPick = null },
        )
    }

    searchPick?.let { contact ->
        ContactActionsSheet(
            contact = contact,
            hidden = contact.id in state.hiddenIds,
            actions = remember(contact) { quickActionsFor(contact.name, handlersFor(contact)) },
            onDismiss = { searchPick = null },
        )
    }

    if (showGeneralSheet) {
        GeneralInstructionSheet(
            onDismiss = { showGeneralSheet = false },
            onSave = { actions.onAddGeneralInstruction(it); showGeneralSheet = false },
        )
    }
}

/** The "Orbit" wordmark, the AI sparkle status, and three evenly spaced glass icon buttons. */
@Composable
private fun HomeHeader(
    indicator: AiIndicatorState?,
    onOpenRules: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onAddInstruction: () -> Unit,
) {
    val colors = Orbit.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Orbit.spacing.xl, end = Orbit.spacing.lg, top = Orbit.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Orbit",
            style = MaterialTheme.typography.titleMedium.copy(
                brush = Brush.horizontalGradient(listOf(colors.accentStart, colors.accentEnd)),
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.semantics { heading() },
        )
        if (indicator != null) {
            Box(
                Modifier
                    .size(Orbit.sizing.touchTarget)
                    .clickable(role = Role.Button, onClick = onOpenAiSettings)
                    .semantics {
                        contentDescription = if (indicator == AiIndicatorState.Working) {
                            "Updating suggestions"
                        } else {
                            "AI suggestions are paused or unavailable. Showing the last good result. Open settings."
                        }
                        if (indicator == AiIndicatorState.Working) liveRegion = LiveRegionMode.Polite
                    },
                contentAlignment = Alignment.Center,
            ) { AiIndicator(indicator) }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.sm)) {
            GlassIconButton(
                icon = OrbitIcons.Sparkle,
                description = "Add general instruction",
                onClick = onAddInstruction,
                tint = colors.softAccent,
            )
            GlassIconButton(
                icon = Icons.Filled.Tune,
                description = "Open rules",
                onClick = onOpenRules,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GlassIconButton(
                icon = Icons.Filled.Settings,
                description = "Open settings",
                onClick = onOpenAiSettings,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The floating bottom group: a round search button and the "Open dialer" pill, over a soft fade. */
@Composable
private fun BottomPanel(
    cardSpace: androidx.compose.ui.unit.Dp,
    onOpenDialer: () -> Unit,
    onOpenSearch: () -> Unit,
    onControlsHeight: (androidx.compose.ui.unit.Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Orbit.colors
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.16f to colors.backgroundBottom.copy(alpha = 0.96f),
                    0.34f to colors.backgroundBottom,
                    1f to colors.backgroundBottom,
                ),
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = Orbit.spacing.lg, end = Orbit.spacing.lg, top = Orbit.spacing.xl, bottom = Orbit.spacing.lg),
        ) {
            Spacer(Modifier.height(cardSpace))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { onControlsHeight(with(density) { it.height.toDp() }) },
                horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.md, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchButton(onOpenSearch)
                DialerPill(onOpenDialer)
            }
        }
    }
}

@Composable
private fun SearchButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Orbit.sizing.touchTarget + Orbit.spacing.sm)
            .glassCircle(strong = true, elevation = Orbit.elevation.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Search contacts" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(Orbit.sizing.icon))
    }
}

/** Why the shown contact is where they are: one card, swapped in place when another contact's actions open. */
@Composable
private fun ReasonCard(target: Pair<Suggestion, Int>?, modifier: Modifier = Modifier) {
    val motion = Orbit.motion
    Box(modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = target,
            transitionSpec = {
                (fadeIn(motion.enter(motion.shortMillis)) + slideInVertically(motion.enter(motion.shortMillis)) { it / 5 }) togetherWith
                    (fadeOut(motion.exit(motion.shortMillis)) + slideOutVertically(motion.exit(motion.shortMillis)) { -it / 5 })
            },
            label = "reason",
        ) { shown ->
            if (shown != null) {
                val (suggestion, rank) = shown
                val title = if (rank == 1) "Why ${suggestion.contact.name} is first" else "Why ${suggestion.contact.name} is #$rank"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // An opaque base under the glass, so nothing behind it shows through the text.
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                        .glass(MaterialTheme.shapes.large, strong = true)
                        .padding(horizontal = Orbit.spacing.lg, vertical = Orbit.spacing.md)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = Orbit.colors.accent)
                    Text(suggestion.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}

/**
 * Search: the bottom button grows into a field that rises above the bottom bar and the keyboard, the bubbles dim,
 * and a glass panel above the field lists frequent and recent contacts, or matches while typing. Closed by back,
 * the close icon or a tap outside. With reduced motion everything snaps.
 */
@Composable
private fun SearchOverlay(
    open: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Contact>,
    suggestions: SearchSuggestions,
    hiddenIds: Set<String>,
    access: AccessState,
    onPick: (Contact) -> Unit,
    onClose: () -> Unit,
) {
    val motion = Orbit.motion
    val progress = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(open) {
        if (open) {
            if (motion.reduced) progress.snapTo(1f) else progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
        } else {
            if (motion.reduced) progress.snapTo(0f) else progress.animateTo(0f, motion.exit(200))
        }
    }
    LaunchedEffect(open) {
        if (open) {
            delay(30)
            focus.requestFocus()
            keyboard?.show()
        }
    }

    fun close() {
        focusManager.clearFocus()
        keyboard?.hide()
        onQueryChange("")
        onClose()
    }

    if (!open && progress.value <= 0.001f) return
    BackHandler(enabled = open) { close() }

    val p = progress.value.coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = p }
                .background(Orbit.colors.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close search") { close() },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = Orbit.spacing.lg, vertical = Orbit.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Orbit.spacing.md),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = p; translationY = (1f - p) * 24.dp.toPx() }
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                        .glass(MaterialTheme.shapes.large, strong = true)
                        .padding(vertical = Orbit.spacing.sm),
                ) { SearchPanelContent(query, results, suggestions, hiddenIds, access, onPick) }
            }
            // The field starts as the round button's width and grows to full width as it rises.
            Box(Modifier.fillMaxWidth()) {
                OrbitTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "Search all contacts",
                    modifier = Modifier
                        .fillMaxWidth(0.2f + 0.8f * p)
                        .graphicsLayer { alpha = p; translationY = (1f - p) * 36.dp.toPx() }
                        .focusRequester(focus),
                    shape = PillShape,
                    singleLine = true,
                    leading = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Orbit.sizing.icon))
                    },
                    trailing = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(Orbit.sizing.icon)
                                .clip(CircleShape)
                                .clickable(role = Role.Button) { close() },
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                )
            }
        }
    }
}

@Composable
private fun SearchPanelContent(
    query: String,
    results: List<Contact>,
    suggestions: SearchSuggestions,
    hiddenIds: Set<String>,
    access: AccessState,
    onPick: (Contact) -> Unit,
) {
    when {
        !access.contacts -> PanelHint("Allow contacts access on the home screen to search your contacts.")
        query.isBlank() && suggestions.isEmpty ->
            PanelHint(
                if (access.callLog) "Type a name or number to search all of your contacts. Frequent and recent contacts show up here once you have call history."
                else "Type a name or number to search. Allow call log access to also see frequent and recent contacts here.",
            )
        query.isBlank() -> LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = PaddingValues(vertical = Orbit.spacing.xs)) {
            if (suggestions.frequent.isNotEmpty()) {
                item(key = "h-frequent") { GroupHeader("Frequent") }
                items(suggestions.frequent, key = { "f-${it.id}" }) { ContactRow(it, it.id in hiddenIds, onPick) }
            }
            if (suggestions.recent.isNotEmpty()) {
                item(key = "h-recent") { GroupHeader("Recent") }
                items(suggestions.recent, key = { "r-${it.id}" }) { ContactRow(it, it.id in hiddenIds, onPick) }
            }
        }
        results.isEmpty() -> PanelHint("Nobody by that name. Search looks through all of your contacts, by name or phone number.")
        else -> LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = PaddingValues(vertical = Orbit.spacing.xs)) {
            items(results, key = { it.id }) { ContactRow(it, it.id in hiddenIds, onPick) }
        }
    }
}

@Composable
private fun PanelHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Orbit.spacing.lg, vertical = Orbit.spacing.md),
    )
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = Orbit.colors.accent,
        modifier = Modifier.padding(horizontal = Orbit.spacing.lg, vertical = Orbit.spacing.sm).semantics { heading() },
    )
}

/** One contact in the search panel: photo or initials, name, and the "Hidden from suggestions" label when it applies. */
@Composable
private fun ContactRow(contact: Contact, hidden: Boolean, onPick: (Contact) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Open actions for ${contact.name}") { onPick(contact) }
            .heightIn(min = Orbit.sizing.touchTarget + Orbit.spacing.md)
            .padding(horizontal = Orbit.spacing.lg, vertical = Orbit.spacing.xs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(contact, diameter = Orbit.sizing.avatarMedium - Orbit.spacing.xs, initialsSp = 16, colors = bubbleColorsFor(contact.id), glow = 0.2f)
        Spacer(Modifier.width(Orbit.spacing.lg))
        Column {
            Text(contact.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            if (hidden) HiddenLabel()
        }
    }
}

/** The floating "Open dialer" pill: accent gradient in dark, deep navy in light, with a soft glow underneath. */
@Composable
private fun DialerPill(onClick: () -> Unit) {
    val colors = Orbit.colors
    val fill = if (colors.isDark) Brush.horizontalGradient(listOf(colors.accentStart, colors.accentEnd)) else Brush.linearGradient(listOf(Color(0xFF10172E), Color(0xFF1B2A55)))
    val content = if (colors.isDark) colors.onAccent else Color.White
    Row(
        modifier = Modifier
            .shadow(Orbit.elevation.high, PillShape, ambientColor = colors.accentEnd.copy(alpha = 0.5f), spotColor = colors.accentEnd.copy(alpha = 0.7f))
            .clip(PillShape)
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = Orbit.sizing.touchTarget + Orbit.spacing.sm)
            .padding(horizontal = Orbit.spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.sm),
    ) {
        Icon(Icons.Filled.Dialpad, contentDescription = null, tint = content, modifier = Modifier.size(Orbit.sizing.iconSmall + 2.dp))
        Text("Open dialer", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = content)
    }
}
