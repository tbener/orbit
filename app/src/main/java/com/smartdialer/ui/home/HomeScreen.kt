package com.smartdialer.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartdialer.data.Contact
import com.smartdialer.data.NumberChoice
import com.smartdialer.data.Suggestion
import com.smartdialer.ui.CollectUserMessages
import com.smartdialer.ui.ContactAvatar
import com.smartdialer.ui.UserMessage
import com.smartdialer.ui.components.EmptyState
import com.smartdialer.ui.theme.Orbit
import com.smartdialer.ui.theme.PillShape
import com.smartdialer.ui.theme.bubbleColorsFor
import kotlinx.coroutines.flow.Flow

private const val SMALL_ROW_SIZE = 4

/** Everything the home screen can ask the app to do for a contact. */
class HomeActions(
    val chooseNumber: (Contact) -> NumberChoice,
    val onCallNumber: (String) -> Unit,
    val onWhatsAppNumber: (String) -> Unit,
    val onOpenContactCard: (Contact) -> Unit,
    val onOpenDetails: (Contact) -> Unit,
    val onAddContactInstruction: (Contact, String) -> Unit,
    val onAddGeneralInstruction: (String) -> Unit,
)

private typealias BubbleGestureHandler = (IndexedValue<Suggestion>, BubbleTier, BubbleGesture, Rect) -> Unit

@Composable
fun HomeScreen(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenDialer: () -> Unit,
    access: AccessState,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    messages: Flow<UserMessage>,
    actions: HomeActions,
    gestureMap: GestureMap = DefaultGestureMap,
) {
    val searching = state.query.isNotBlank()
    val needsAccess = if (searching) !access.contacts else !access.all

    val snackbarHostState = remember { SnackbarHostState() }
    CollectUserMessages(messages, snackbarHostState)

    var quick by remember { mutableStateOf<QuickTarget?>(null) }
    var numberPick by remember { mutableStateOf<NumberPick?>(null) }
    var showGeneralSheet by remember { mutableStateOf(false) }

    fun runWith(purpose: NumberPurpose, number: String) {
        when (purpose) {
            NumberPurpose.Call -> actions.onCallNumber(number)
            NumberPurpose.WhatsApp -> actions.onWhatsAppNumber(number)
        }
    }

    fun useNumber(contact: Contact, purpose: NumberPurpose) {
        when (val choice = actions.chooseNumber(contact)) {
            NumberChoice.None -> Unit
            is NumberChoice.Use -> runWith(purpose, choice.number)
            is NumberChoice.Pick -> numberPick = NumberPick(contact, purpose, choice.candidates)
        }
    }

    val onBubbleGesture: BubbleGestureHandler = { entry, tier, gesture, bounds ->
        onSelect(entry.value.contact.id)
        when (gestureMap.actionFor(gesture)) {
            BubbleAction.OpenQuickActions -> quick = QuickTarget(entry.value, entry.index + 1, tier, bounds)
            BubbleAction.Call -> useNumber(entry.value.contact, NumberPurpose.Call)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            // While the card is open, screen readers should only see the card.
            modifier = if (quick != null) Modifier.clearAndSetSemantics { } else Modifier,
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                HomeHeader(onOpenRules)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Orbit.spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchField(state.query, onQueryChange, Modifier.weight(1f))
                    FilledTonalIconButton(
                        onClick = { showGeneralSheet = true },
                        modifier = Modifier.size(SEARCH_HEIGHT),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(Icons.Filled.EditNote, contentDescription = "Add general instruction")
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (needsAccess) {
                        PermissionPrompt(access, onRequestAccess, onOpenSettings)
                    } else if (searching) {
                        SearchResults(state.query, state.searchResults)
                    } else {
                        BubbleList(state, onBubbleGesture, onLoadMore)
                    }
                }
                BottomPanel(state, showSuggestionInfo = !searching && !needsAccess, onOpenDialer = onOpenDialer)
            }
        }

        quick?.let { target ->
            val contact = target.suggestion.contact
            QuickActionsOverlay(
                target = target,
                handlers = QuickActionHandlers(
                    onCall = { useNumber(contact, NumberPurpose.Call) },
                    onWhatsApp = { useNumber(contact, NumberPurpose.WhatsApp) },
                    onContactCard = { actions.onOpenContactCard(contact) },
                    onDetails = { actions.onOpenDetails(contact) },
                    onSaveInstruction = { actions.onAddContactInstruction(contact, it) },
                ),
                onDismiss = { quick = null },
            )
        }
    }

    numberPick?.let { pick ->
        NumberPickerDialog(
            pick = pick,
            onPick = { number -> runWith(pick.purpose, number); numberPick = null },
            onDismiss = { numberPick = null },
        )
    }

    if (showGeneralSheet) {
        GeneralInstructionSheet(
            onDismiss = { showGeneralSheet = false },
            onSave = { actions.onAddGeneralInstruction(it); showGeneralSheet = false },
        )
    }
}

private val SEARCH_HEIGHT = Orbit.sizing.touchTarget + Orbit.spacing.sm

/** Space above the top bubble so the outer orbit ring is not cut off by the top of the list. */
private val RING_ROOM = Orbit.spacing.xxl + Orbit.spacing.md

@Composable
private fun HomeHeader(onOpenRules: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Orbit.spacing.xl, end = Orbit.spacing.lg, top = Orbit.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Who to call",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        FilledTonalIconButton(
            onClick = onOpenRules,
            modifier = Modifier.size(Orbit.sizing.touchTarget),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(Icons.Filled.Tune, contentDescription = "Open rules")
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager: FocusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.padding(vertical = Orbit.spacing.sm).heightIn(min = SEARCH_HEIGHT),
        placeholder = { Text("Search all contacts", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = PillShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun BubbleList(
    state: HomeUiState,
    onGesture: BubbleGestureHandler,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 2
        }
    }
    // Keyed on the loaded count so a short first page keeps loading until the viewport is filled.
    LaunchedEffect(nearEnd, state.suggestions.size, state.canLoadMore) {
        if (nearEnd && state.canLoadMore) onLoadMore()
    }

    // Bubbles animate in once; scrolling back or reordering does not replay it.
    val seen = remember { mutableSetOf<String>() }

    val items = state.suggestions
    val indexed = items.withIndex().toList()
    val hero = indexed.getOrNull(0)
    val flanks = indexed.drop(1).take(2)
    val rest = indexed.drop(3)
    val ringColor = Orbit.colors.orbitRing
    val heroRadius = BubbleTier.Hero.diameter / 2

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Orbit.spacing.lg, vertical = Orbit.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Orbit.spacing.md),
    ) {
        if (hero != null) {
            item(key = "hero") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Two faint rings around the top bubble: the "orbit" the others sit on.
                        .drawBehind {
                            val center = Offset(size.width / 2f, (RING_ROOM + Orbit.spacing.xs + heroRadius).toPx())
                            listOf(heroRadius + Orbit.spacing.xl, heroRadius + Orbit.spacing.xl + Orbit.spacing.xxl).forEach { radius ->
                                drawCircle(ringColor, radius = radius.toPx(), center = center, style = Stroke(width = 1.dp.toPx()))
                            }
                        }
                        .padding(top = RING_ROOM),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.xs, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        flanks.getOrNull(0)?.let { Bubble(it, BubbleTier.Flank, state, onGesture, seen) }
                        Bubble(hero, BubbleTier.Hero, state, onGesture, seen)
                        flanks.getOrNull(1)?.let { Bubble(it, BubbleTier.Flank, state, onGesture, seen) }
                    }
                }
            }
        }
        items(rest.chunked(SMALL_ROW_SIZE), key = { row -> row.first().value.contact.id }) { row ->
            val firstRow = row.first().index < 3 + SMALL_ROW_SIZE
            val tier = if (firstRow) BubbleTier.Medium else BubbleTier.Small
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                row.forEach { entry -> Bubble(entry, tier, state, onGesture, seen, Modifier.weight(1f)) }
                // Keep an incomplete last row aligned to the grid.
                repeat(SMALL_ROW_SIZE - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (items.isEmpty()) {
            item(key = "empty") {
                if (state.isRefreshing) {
                    Box(Modifier.fillMaxWidth().padding(Orbit.spacing.xl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(Orbit.sizing.icon), strokeWidth = 2.dp)
                    }
                } else {
                    EmptyState(
                        icon = Icons.Outlined.Groups,
                        title = "No suggestions yet",
                        body = "Orbit suggests people from your call history. Once you have made or missed some calls with your contacts, they will show up here.",
                    )
                }
            }
        }
    }
}

@Composable
private fun Bubble(
    entry: IndexedValue<Suggestion>,
    tier: BubbleTier,
    state: HomeUiState,
    onGesture: BubbleGestureHandler,
    seen: MutableSet<String>,
    modifier: Modifier = Modifier,
) {
    val id = entry.value.contact.id
    val firstTime = remember(id) { seen.add(id) }
    ContactBubble(
        suggestion = entry.value,
        rank = entry.index + 1,
        tier = tier,
        selected = state.selectedId == id,
        onGesture = { gesture, bounds -> onGesture(entry, tier, gesture, bounds) },
        modifier = modifier.bubbleEntrance(play = firstTime, index = entry.index),
    )
}

@Composable
private fun SearchResults(query: String, results: List<Contact>) {
    if (results.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Search,
            title = "No contacts match \"${query.trim()}\"",
            body = "Search looks through all of your contacts by name or phone number.",
            modifier = Modifier.padding(top = Orbit.spacing.xl),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = Orbit.spacing.xs)) {
        items(results, key = { it.id }) { contact ->
            val colors = bubbleColorsFor(contact.id)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Orbit.sizing.touchTarget + Orbit.spacing.lg)
                    .padding(horizontal = Orbit.spacing.lg, vertical = Orbit.spacing.sm)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContactAvatar(contact, diameter = Orbit.sizing.avatarMedium, initialsSp = 18, colors = colors)
                Spacer(Modifier.width(Orbit.spacing.lg))
                Text(contact.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable
private fun BottomPanel(
    state: HomeUiState,
    showSuggestionInfo: Boolean,
    onOpenDialer: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Orbit.spacing.lg, vertical = Orbit.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSuggestionInfo) {
            val selectedIndex = state.suggestions.indexOfFirst { it.contact.id == state.selectedId }
            val selected = state.suggestions.getOrNull(selectedIndex)
            if (selected != null) {
                val title = if (selectedIndex == 0) {
                    "Why ${selected.contact.name} is first"
                } else {
                    "Why ${selected.contact.name} is #${selectedIndex + 1}"
                }
                WhyCard(title = title, reason = selected.reason)
            }
            // Fixed height so the indicator appearing does not shift the layout.
            val motion = Orbit.motion
            val indicatorAlpha by animateFloatAsState(
                targetValue = if (state.isRefreshing) 1f else 0f,
                animationSpec = if (state.isRefreshing) motion.enter() else motion.exit(),
                label = "refreshIndicator",
            )
            Box(Modifier.heightIn(min = Orbit.sizing.touchTarget - Orbit.spacing.md), contentAlignment = Alignment.Center) {
                if (indicatorAlpha > 0f) {
                    Row(
                        modifier = Modifier
                            .graphicsLayer { alpha = indicatorAlpha }
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(Orbit.spacing.lg), strokeWidth = 2.dp)
                        Spacer(Modifier.width(Orbit.spacing.sm))
                        Text(
                            text = "Updating suggestions…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Button(
            onClick = onOpenDialer,
            modifier = Modifier.heightIn(min = SEARCH_HEIGHT),
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = Orbit.elevation.medium),
        ) {
            Icon(Icons.Filled.Dialpad, contentDescription = null, modifier = Modifier.size(Orbit.sizing.iconSmall + 2.dp))
            Spacer(Modifier.width(Orbit.spacing.sm))
            Text("Open dialer", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WhyCard(title: String, reason: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Orbit.sizing.avatarLarge - Orbit.spacing.md)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(Orbit.spacing.lg)) {
            Box(
                modifier = Modifier
                    .size(Orbit.sizing.icon + Orbit.spacing.sm)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(Orbit.sizing.iconSmall + 2.dp))
            }
            Spacer(Modifier.width(Orbit.spacing.md))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(reason, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
