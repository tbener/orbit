package com.smartdialer.ui.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartdialer.data.Contact
import com.smartdialer.data.NumberChoice
import com.smartdialer.data.Suggestion
import com.smartdialer.ui.CollectUserMessages
import com.smartdialer.ui.ContactAvatar
import com.smartdialer.ui.UserMessage
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
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchField(state.query, onQueryChange, Modifier.weight(1f))
                    IconButton(onClick = { showGeneralSheet = true }, modifier = Modifier.size(48.dp)) {
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

@Composable
private fun HomeHeader(onOpenRules: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Who to call",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        IconButton(onClick = onOpenRules, modifier = Modifier.size(48.dp)) {
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
        modifier = modifier.padding(vertical = 8.dp).heightIn(min = 56.dp),
        placeholder = { Text("Search all contacts") },
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
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
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

    val items = state.suggestions
    val indexed = items.withIndex().toList()
    val hero = indexed.getOrNull(0)
    val flanks = indexed.drop(1).take(2)
    val rest = indexed.drop(3)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (hero != null) {
            item(key = "hero") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    flanks.getOrNull(0)?.let { Bubble(it, BubbleTier.Flank, state, onGesture) }
                    Bubble(hero, BubbleTier.Hero, state, onGesture)
                    flanks.getOrNull(1)?.let { Bubble(it, BubbleTier.Flank, state, onGesture) }
                }
            }
        }
        items(rest.chunked(SMALL_ROW_SIZE), key = { row -> row.first().value.contact.id }) { row ->
            val firstRow = row.first().index < 3 + SMALL_ROW_SIZE
            val tier = if (firstRow) BubbleTier.Medium else BubbleTier.Small
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                row.forEach { entry -> Bubble(entry, tier, state, onGesture, Modifier.weight(1f)) }
                // Keep an incomplete last row aligned to the grid.
                repeat(SMALL_ROW_SIZE - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (items.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = "No suggestions yet. Once you have call history with your contacts, they will show up here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
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
    modifier: Modifier = Modifier,
) {
    ContactBubble(
        suggestion = entry.value,
        rank = entry.index + 1,
        tier = tier,
        selected = state.selectedId == entry.value.contact.id,
        onGesture = { gesture, bounds -> onGesture(entry, tier, gesture, bounds) },
        modifier = modifier,
    )
}

@Composable
private fun SearchResults(query: String, results: List<Contact>) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                text = "No contacts match \"${query.trim()}\"",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(results, key = { it.id }) { contact ->
            val colors = bubbleColorsFor(contact.id)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContactAvatar(contact, diameter = 48.dp, initialsSp = 18, colors = colors)
                Spacer(Modifier.width(16.dp))
                Text(contact.name, style = MaterialTheme.typography.bodyLarge)
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
            Box(Modifier.heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
                if (state.isRefreshing) {
                    Row(
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
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
            modifier = Modifier.heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background,
            ),
        ) {
            Icon(Icons.Filled.Dialpad, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open dialer", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WhyCard(title: String, reason: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(reason, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
