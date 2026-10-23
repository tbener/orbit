package com.smartdialer.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartdialer.data.fake.FakeSuggestionsRepository
import com.smartdialer.rules.fake.FakeRulesRepository
import com.smartdialer.ui.home.HomeScreen
import com.smartdialer.ui.home.HomeViewModel
import com.smartdialer.ui.rules.RulesScreen
import com.smartdialer.ui.rules.RulesViewModel

private enum class Screen { Home, Rules }

@Composable
fun OrbitApp() {
    // Stage 1 wires in-memory fakes here. Stage 2 swaps in real repositories; the UI is unaffected.
    val homeViewModel: HomeViewModel = viewModel(
        factory = viewModelFactory { initializer { HomeViewModel(FakeSuggestionsRepository()) } },
    )
    val rulesViewModel: RulesViewModel = viewModel(
        factory = viewModelFactory { initializer { RulesViewModel(FakeRulesRepository()) } },
    )

    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    BackHandler(enabled = screen == Screen.Rules) { screen = Screen.Home }

    val context = LocalContext.current
    val openDialer = {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No dialer app found", Toast.LENGTH_SHORT).show()
        }
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
