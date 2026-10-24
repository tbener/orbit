package com.smartdialer.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import com.smartdialer.data.Contact
import com.smartdialer.data.NumberChoice
import com.smartdialer.data.Suggestion
import com.smartdialer.ui.CollectUserMessages
import com.smartdialer.ui.ContactAvatar
import com.smartdialer.ui.UserMessage
import com.smartdialer.ui.components.EmptyState
import com.smartdialer.ui.components.GlassIconButton
import com.smartdialer.ui.components.OrbitTextField
import com.smartdialer.ui.theme.Orbit
import com.smartdialer.ui.theme.OrbitBackground
import com.smartdialer.ui.theme.PillShape
import com.smartdialer.ui.theme.bubbleColorsFor
import com.smartdialer.ui.theme.glass
import kotlinx.coroutines.flow.Flow
import kotlin.math.PI
import kotlin.math.sin

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
    val searching = state.query.isNotBlank()
    val needsAccess = if (searching) !access.contacts else !access.all

    val snackbarHostState = remember { SnackbarHostState() }
    CollectUserMessages(messages, snackbarHostState)

    var quick by remember { mutableStateOf<QuickTarget?>(null) }
    var numberPick by remember { mutableStateOf<NumberPick?>(null) }
    var showGeneralSheet by remember { mutableStateOf(false) }
    var searchPick by remember { mutableStateOf<Contact?>(null) }
    var bottomPanelHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

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

    // The screen softly blurs behind the quick actions (Android 12 and up; older versions only dim).
    val blur by animateDpAsState(if (quick != null) 14.dp else 0.dp, Orbit.motion.move(), label = "blur")

    OrbitBackground {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = {
                    SnackbarHost(snackbarHostState, Modifier.padding(bottom = bottomPanelHeight))
                },
                // While the quick actions are open, screen readers should only see them.
                modifier = (if (blur > 0.dp) Modifier.blur(blur) else Modifier)
                    .then(if (quick != null) Modifier.clearAndSetSemantics { } else Modifier),
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        HomeHeader(
                            isRefreshing = state.isRefreshing,
                            aiProblem = state.aiProblem,
                            onOpenRules = onOpenRules,
                            onOpenAiSettings = onOpenAiSettings,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Orbit.spacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SearchField(state.query, onQueryChange, Modifier.weight(1f))
                            GlassIconButton(
                                icon = Icons.Filled.EditNote,
                                description = "Add general instruction",
                                onClick = { showGeneralSheet = true },
                                tint = Orbit.colors.accent,
                            )
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            if (needsAccess) {
                                PermissionPrompt(access, onRequestAccess, onOpenSettings)
                            } else if (searching) {
                                SearchResults(state.searchResults, state.hiddenIds) { searchPick = it }
                            } else {
                                OrbitField(
                                    suggestions = state.suggestions,
                                    selectedId = state.selectedId,
                                    hiddenId = quick?.suggestion?.contact?.id,
                                    canLoadMore = state.canLoadMore,
                                    onGesture = onBubbleGesture,
                                    onLoadMore = onLoadMore,
                                    bottomInset = bottomPanelHeight + Orbit.spacing.xl,
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
                        state = state,
                        showSuggestionInfo = !searching && !needsAccess,
                        onOpenDialer = onOpenDialer,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .onSizeChanged { bottomPanelHeight = with(density) { it.height.toDp() } },
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
        }
    }

    numberPick?.let { pick ->
        NumberPickerDialog(
            pick = pick,
            onPick = { number -> runWith(pick.purpose, number); numberPick = null },
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

@Composable
private fun HomeHeader(isRefreshing: Boolean, aiProblem: Boolean, onOpenRules: () -> Unit, onOpenAiSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Orbit.spacing.xl, end = Orbit.spacing.lg, top = Orbit.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Who to call",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        UpdatingPulse(active = isRefreshing, modifier = Modifier.padding(start = Orbit.spacing.md))
        if (aiProblem && !isRefreshing) {
            // A quiet note, not an alert: the last good suggestions are still on screen. Tapping opens Settings.
            Box(
                Modifier
                    .size(Orbit.sizing.touchTarget)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpenAiSettings)
                    .semantics { contentDescription = "Suggestions could not be updated. Showing the last good result. Open settings." },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CloudOff, contentDescription = null, tint = Orbit.colors.alertText, modifier = Modifier.size(Orbit.sizing.iconSmall))
            }
        }
        Spacer(Modifier.weight(1f))
        GlassIconButton(
            icon = Icons.Filled.Settings,
            description = "Open settings",
            onClick = onOpenAiSettings,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Orbit.spacing.sm))
        GlassIconButton(
            icon = Icons.Filled.Tune,
            description = "Open rules",
            onClick = onOpenRules,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A small accent dot that pulses while the suggestions refresh. It reserves its space even when idle so
 * the title never shifts, and a screen reader hears "Updating suggestions". With system animations off it
 * is a still dot.
 */
@Composable
private fun UpdatingPulse(active: Boolean, modifier: Modifier = Modifier) {
    val motion = Orbit.motion
    Box(modifier.size(Orbit.spacing.xl), contentAlignment = Alignment.Center) {
        if (active) {
            val pulse = if (motion.reduced) {
                1f
            } else {
                val transition = rememberInfiniteTransition(label = "updating")
                val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "t")
                0.5f + 0.5f * sin(2f * PI.toFloat() * t)
            }
            Box(
                Modifier
                    .size(Orbit.spacing.md)
                    .graphicsLayer {
                        val scale = 0.7f + 0.5f * pulse
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.45f + 0.55f * pulse
                    }
                    .background(Orbit.colors.accent, CircleShape)
                    .semantics {
                        contentDescription = "Updating suggestions"
                        liveRegion = LiveRegionMode.Polite
                    },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager: FocusManager = LocalFocusManager.current
    OrbitTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = "Search all contacts",
        modifier = modifier.padding(vertical = Orbit.spacing.sm),
        shape = PillShape,
        singleLine = true,
        leading = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Orbit.sizing.icon))
        },
        trailing = if (query.isNotEmpty()) {
            {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Clear search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(Orbit.sizing.icon)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onQueryChange("") },
                )
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun SearchResults(results: List<Contact>, hiddenIds: Set<String>, onPick: (Contact) -> Unit) {
    if (results.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Search,
            title = "Nobody by that name",
            body = "Search looks through all of your contacts, by name or phone number.",
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
                    .clickable(role = Role.Button, onClickLabel = "Open actions for ${contact.name}") { onPick(contact) }
                    .heightIn(min = Orbit.sizing.touchTarget + Orbit.spacing.lg)
                    .padding(horizontal = Orbit.spacing.lg, vertical = Orbit.spacing.sm)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContactAvatar(contact, diameter = Orbit.sizing.avatarMedium, initialsSp = 18, colors = colors, glow = 0.2f)
                Spacer(Modifier.width(Orbit.spacing.lg))
                Column {
                    Text(contact.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                    if (contact.id in hiddenIds) HiddenLabel()
                }
            }
        }
    }
}

/**
 * The floating bottom area: a glass caption saying why the selected person is suggested, and the
 * dialer button. The field scrolls under a fade so nothing collides with it.
 */
@Composable
private fun BottomPanel(
    state: HomeUiState,
    showSuggestionInfo: Boolean,
    onOpenDialer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Orbit.colors
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Orbit.spacing.md),
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
                    WhyCaption(title = title, reason = selected.reason)
                }
            }
            DialerPill(onOpenDialer)
        }
    }
}

@Composable
private fun WhyCaption(title: String, reason: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // An opaque base under the glass, so bubbles scrolling underneath never show through the text.
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
            .glass(MaterialTheme.shapes.large, strong = true)
            .padding(horizontal = Orbit.spacing.lg, vertical = Orbit.spacing.md)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Orbit.colors.accent)
        Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
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
