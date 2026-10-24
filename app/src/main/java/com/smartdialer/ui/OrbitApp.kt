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
import com.smartdialer.OrbitApplication
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

private enum class Screen { Home, Rules, Details }

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
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    val contactActions = rememberContactActions()

    var askedForAccess by rememberSaveable { mutableStateOf(false) }
    var access by remember { mutableStateOf(readAccess(context, askedForAccess)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        askedForAccess = true
        access = readAccess(context, asked = true)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access = readAccess(context, askedForAccess)
        homeViewModel.onResumed()
    }
    LaunchedEffect(access.all) { homeViewModel.onAccessChanged(access.all) }

    val openAppSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }

    when (screen) {
        Screen.Home -> {
            val state by homeViewModel.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onQueryChange = homeViewModel::onQueryChange,
                onSelect = homeViewModel::select,
                onLoadMore = homeViewModel::loadMore,
                onOpenRules = { screen = Screen.Rules },
                onOpenDialer = contactActions::openDialer,
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
                ),
            )
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
                            DetailsViewModel(key, container.rulesRepository, container.contactsRepository, container.suggestionsRepository)
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
                    onBack = { screen = Screen.Home },
                ),
            )
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
