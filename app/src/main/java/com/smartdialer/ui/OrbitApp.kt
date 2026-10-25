package com.smartdialer.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartdialer.BuildConfig
import com.smartdialer.OrbitApplication
import com.smartdialer.data.hasNotificationAccess
import com.smartdialer.ui.settings.AiDisclosureDialog
import kotlinx.coroutines.flow.map
import com.smartdialer.ui.settings.WhatsAppPrompt
import com.smartdialer.ui.settings.LlmInspectorScreen
import com.smartdialer.ui.settings.SettingsActions
import com.smartdialer.ui.settings.SettingsScreen
import com.smartdialer.ui.settings.SettingsViewModel
import com.smartdialer.ui.details.DetailsActions
import com.smartdialer.ui.details.DetailsScreen
import com.smartdialer.ui.details.DetailsViewModel
import com.smartdialer.ui.home.AccessState
import com.smartdialer.ui.home.HomeActions
import com.smartdialer.ui.home.HomeScreen
import com.smartdialer.ui.home.HomeViewModel
import com.smartdialer.ui.rules.RulesActions
import com.smartdialer.ui.rules.RulesScreen
import com.smartdialer.ui.rules.RulesViewModel
import com.smartdialer.ui.theme.Orbit

private enum class Screen { Home, Rules, Details, Settings, Inspector }

private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG)

@Composable
fun OrbitApp() {
    val context = LocalContext.current

    val container = (context.applicationContext as OrbitApplication).container
    val homeViewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    suggestions = container.suggestionsRepository,
                    pipeline = container.suggestionPipeline,
                    rules = container.rulesRepository,
                    contacts = container.contactsRepository,
                    aiSettings = container.aiSettings,
                    whatsAppStore = container.whatsAppStore,
                    whatsAppSettings = container.whatsAppSettings,
                    uiSettings = container.uiSettings,
                    ai = container.aiSuggestions,
                    manualArrangement = container.manualArrangement,
                )
            }
        },
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settings = container.aiSettings,
                    suggestions = container.aiSuggestions,
                    cache = container.rankingCache,
                    service = container.rankingService,
                    exchanges = container.llmExchanges,
                    apiKeyMissing = BuildConfig.LLM_API_KEY.isBlank(),
                    whatsAppSettings = container.whatsAppSettings,
                    whatsAppStore = container.whatsAppStore,
                    uiSettings = container.uiSettings,
                )
            }
        },
    )
    val rulesViewModel: RulesViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                RulesViewModel(container.rulesRepository, container.contactsRepository, container.suggestionPipeline)
            }
        },
    )

    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    var detailsKey by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = screen != Screen.Home) { screen = if (screen == Screen.Inspector) Screen.Settings else Screen.Home }

    val contactActions = rememberContactActions()

    var whatsAppGranted by remember { mutableStateOf(hasNotificationAccess(context)) }
    val whatsAppState by container.whatsAppSettings.state.collectAsStateWithLifecycle()

    var askedForAccess by rememberSaveable { mutableStateOf(false) }
    var access by remember { mutableStateOf(readAccess(context, askedForAccess)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        askedForAccess = true
        access = readAccess(context, asked = true)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access = readAccess(context, askedForAccess)
        homeViewModel.onResumed()
        val granted = hasNotificationAccess(context)
        whatsAppGranted = granted
        settingsViewModel.onAccessChecked(granted)
    }
    LaunchedEffect(access.all) { homeViewModel.onAccessChanged(access.all) }

    val openNotificationAccess = {
        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    val openAppSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }

    // A short fade with a small slide, only to show that the screen changed. Snaps when animations are off.
    val motion = Orbit.motion
    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (fadeIn(motion.enter(delayMillis = motion.shortMillis / 2)) +
                slideInHorizontally(motion.enter()) { if (targetState == Screen.Home) -it / 12 else it / 12 }) togetherWith
                fadeOut(motion.exit())
        },
        label = "screen",
    ) { current ->
    when (current) {
        Screen.Home -> {
            val state by homeViewModel.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onQueryChange = homeViewModel::onQueryChange,
                onSelect = homeViewModel::select,
                onLoadMore = homeViewModel::loadMore,
                onOpenRules = { screen = Screen.Rules },
                onOpenAiSettings = { screen = Screen.Settings },
                onOpenDialer = contactActions::openDialer,
                onPullRefresh = homeViewModel::pullRefresh,
                onDragActive = homeViewModel::setDragging,
                onMoveBubble = homeViewModel::moveBubble,
                onDismissToast = homeViewModel::dismissToast,
                access = access,
                onRequestAccess = {
                    permissionLauncher.launch(REQUIRED_PERMISSIONS.filterNot { isGranted(context, it) }.toTypedArray())
                },
                onOpenSettings = openAppSettings,
                messages = homeViewModel.messages,
                actions = HomeActions(
                    chooseNumber = homeViewModel::chooseNumber,
                    onCallNumber = contactActions::call,
                    onWhatsAppNumber = contactActions::whatsApp,
                    onOpenContactCard = { contactActions.openContactCard(it.id) },
                    onOpenDetails = { detailsKey = it.id; screen = Screen.Details },
                    onAddContactInstruction = homeViewModel::addContactInstruction,
                    onAddGeneralInstruction = homeViewModel::addGeneralInstruction,
                    onWhatsAppUsed = homeViewModel::onWhatsAppUsed,
                    onTogglePin = homeViewModel::togglePin,
                ),
            )
            // Already granted (for example by hand): nothing to ask.
            LaunchedEffect(whatsAppGranted) { if (whatsAppGranted) container.whatsAppSettings.markPromptHandled() }
            // One-time prompt, after the AI disclosure so two dialogs never stack.
            if (!state.needsDisclosure && access.all && whatsAppState.enabled && !whatsAppState.promptHandled && !whatsAppGranted) {
                WhatsAppPrompt(
                    onEnable = { container.whatsAppSettings.markPromptHandled(); openNotificationAccess() },
                    onNotNow = { container.whatsAppSettings.markPromptHandled() },
                )
            }
            // Nothing is sent to the API until this has been accepted. Shown once access is granted.
            if (state.needsDisclosure && access.all) {
                AiDisclosureDialog(onAccept = homeViewModel::acceptDisclosure, onDecline = homeViewModel::declineDisclosure)
            }
        }
        Screen.Settings -> {
            val state by settingsViewModel.state.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                nowMillis = System.currentTimeMillis(),
                actions = SettingsActions(
                    onSetEnabled = settingsViewModel::setEnabled,
                    onAcceptDisclosure = settingsViewModel::acceptDisclosure,
                    onRefreshNow = settingsViewModel::refreshNow,
                    onOpenInspector = { screen = Screen.Inspector },
                    onSetLayout = settingsViewModel::setLayoutMode,
                    onSetWhatsApp = settingsViewModel::setWhatsAppEnabled,
                    onOpenNotificationAccess = { openNotificationAccess() },
                    onDeleteWhatsApp = settingsViewModel::deleteWhatsAppData,
                    onBack = { screen = Screen.Home },
                ),
            )
        }
        Screen.Inspector -> {
            val state by settingsViewModel.inspector.collectAsStateWithLifecycle()
            LlmInspectorScreen(state = state, nowMillis = System.currentTimeMillis(), onBack = { screen = Screen.Settings })
        }
        Screen.Details -> {
            val key = detailsKey
            if (key == null) {
                screen = Screen.Home
            } else {
                val detailsViewModel: DetailsViewModel = viewModel(
                    key = "details-$key",
                    factory = viewModelFactory {
                        initializer {
                            DetailsViewModel(
                                key,
                                container.rulesRepository,
                                container.contactsRepository,
                                container.suggestionsRepository,
                                container.whatsAppEvents,
                                container.suggestionPipeline.result.map { it.suggestions },
                            )
                        }
                    },
                )
                val state by detailsViewModel.state.collectAsStateWithLifecycle()
                DetailsScreen(
                    state = state,
                    messages = detailsViewModel.messages,
                    nowMillis = System.currentTimeMillis(),
                    actions = DetailsActions(
                        addInstruction = detailsViewModel::addInstruction,
                        updateInstruction = detailsViewModel::updateInstruction,
                        deleteInstruction = detailsViewModel::deleteInstruction,
                        addTyped = detailsViewModel::addTyped,
                        updateTyped = detailsViewModel::updateTyped,
                        deleteTyped = detailsViewModel::deleteTyped,
                        onBack = { screen = Screen.Home },
                    ),
                )
            }
        }
        Screen.Rules -> {
            val state by rulesViewModel.state.collectAsStateWithLifecycle()
            RulesScreen(
                state = state,
                actions = RulesActions(
                    addFreeText = rulesViewModel::addFreeText,
                    updateFreeText = rulesViewModel::updateFreeText,
                    deleteFreeText = rulesViewModel::deleteFreeText,
                    addTyped = rulesViewModel::addTyped,
                    updateTyped = rulesViewModel::updateTyped,
                    deleteTyped = rulesViewModel::deleteTyped,
                    makeHard = rulesViewModel::makeHard,
                    makeSoft = rulesViewModel::makeSoft,
                    keepSoft = rulesViewModel::keepSoft,
                    onBack = { screen = Screen.Home },
                ),
            )
        }
    }
    }
}

private fun isGranted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/**
 * Reads the current permission state. After the user has been asked at least once, a missing
 * permission that no longer shows a rationale was denied for good, so only settings can grant it.
 */
private fun readAccess(context: Context, asked: Boolean): AccessState {
    val missing = REQUIRED_PERMISSIONS.filterNot { isGranted(context, it) }
    val activity = context.findActivity()
    val permanentlyDenied = asked && activity != null && missing.isNotEmpty() &&
        missing.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    return AccessState(
        contacts = isGranted(context, Manifest.permission.READ_CONTACTS),
        callLog = isGranted(context, Manifest.permission.READ_CALL_LOG),
        needsSettings = permanentlyDenied,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
