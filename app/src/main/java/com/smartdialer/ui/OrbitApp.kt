package com.smartdialer.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import com.smartdialer.data.device.DeviceCallLogSource
import com.smartdialer.data.device.DeviceContactsSource
import com.smartdialer.data.device.DeviceSuggestionsRepository
import com.smartdialer.rules.fake.FakeRulesRepository
import com.smartdialer.ui.home.AccessState
import com.smartdialer.ui.home.HomeScreen
import com.smartdialer.ui.home.HomeViewModel
import com.smartdialer.ui.rules.RulesScreen
import com.smartdialer.ui.rules.RulesViewModel

private enum class Screen { Home, Rules }

private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG)

@Composable
fun OrbitApp() {
    val context = LocalContext.current

    val homeViewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val resolver = context.applicationContext.contentResolver
                HomeViewModel(DeviceSuggestionsRepository(DeviceContactsSource(resolver), DeviceCallLogSource(resolver)))
            }
        },
    )
    // Rules are still in-memory fakes until stage 3.
    val rulesViewModel: RulesViewModel = viewModel(
        factory = viewModelFactory { initializer { RulesViewModel(FakeRulesRepository()) } },
    )

    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    BackHandler(enabled = screen == Screen.Rules) { screen = Screen.Home }

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

    val openDialer = {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No dialer app found", Toast.LENGTH_SHORT).show()
        }
    }
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
                onOpenDialer = openDialer,
                access = access,
                onRequestAccess = {
                    permissionLauncher.launch(REQUIRED_PERMISSIONS.filterNot { isGranted(context, it) }.toTypedArray())
                },
                onOpenSettings = openAppSettings,
            )
        }
        Screen.Rules -> {
            val freeText by rulesViewModel.freeTextRules.collectAsStateWithLifecycle()
            val typed by rulesViewModel.typedRules.collectAsStateWithLifecycle()
            RulesScreen(
                freeTextRules = freeText,
                typedRules = typed,
                onUpdateFreeText = rulesViewModel::updateFreeText,
                onDeleteFreeText = rulesViewModel::deleteFreeText,
                onUpdateTyped = rulesViewModel::updateTyped,
                onDeleteTyped = rulesViewModel::deleteTyped,
                onBack = { screen = Screen.Home },
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
