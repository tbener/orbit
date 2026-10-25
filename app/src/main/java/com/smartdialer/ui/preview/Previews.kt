package com.smartdialer.ui.preview

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.smartdialer.data.Contact
import com.smartdialer.data.ContactSignals
import com.smartdialer.data.NumberChoice
import com.smartdialer.data.Suggestion
import com.smartdialer.data.SuggestionBadge
import com.smartdialer.ranking.TraceEntry
import com.smartdialer.ranking.TraceOutcome
import com.smartdialer.rules.ContactRef
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.FreeTextStatus
import com.smartdialer.rules.TypedRule
import com.smartdialer.rules.TypedRuleParams
import com.smartdialer.ui.details.DetailsActions
import com.smartdialer.ui.details.DetailsScreen
import com.smartdialer.ui.details.DetailsUiState
import com.smartdialer.ui.home.AccessState
import com.smartdialer.ui.home.HomeActions
import com.smartdialer.ui.home.HomeScreen
import com.smartdialer.ui.home.HomeUiState
import com.smartdialer.ui.rules.RulesActions
import com.smartdialer.ui.rules.RulesScreen
import com.smartdialer.ui.rules.RulesUiState
import com.smartdialer.ui.theme.OrbitTheme
import kotlinx.coroutines.flow.emptyFlow

/** Dark (the primary design), light, and two large font sizes: the ways every screen has to work. */
@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES, showBackground = true, widthDp = 360, heightDp = 780)
@Preview(name = "Light", uiMode = UI_MODE_NIGHT_NO, showBackground = true, widthDp = 360, heightDp = 780)
@Preview(name = "Dark font 130%", uiMode = UI_MODE_NIGHT_YES, fontScale = 1.3f, showBackground = true, widthDp = 360, heightDp = 780)
@Preview(name = "Dark font 200%", uiMode = UI_MODE_NIGHT_YES, fontScale = 2f, showBackground = true, widthDp = 360, heightDp = 780)
annotation class ThemePreviews

private val names = listOf(
    "Mom", "Dana", "Ron", "Yael", "Avi", "Nir", "Tamar", "Noam", "Shira", "Omer",
    "Lior", "Maya", "Itay", "Roni", "Gil", "Tal", "Ofir", "Hila", "Eli (Dentist)", "Alexandra Papadopoulos",
)

private fun suggestion(index: Int): Suggestion {
    val name = names[index]
    return Suggestion(
        contact = Contact(id = "key-$index", name = name),
        subtitle = when (index) {
            0 -> "3 days"
            1 -> "Missed"
            2 -> "Sunday"
            7 -> "2 weeks"
            else -> null
        },
        badge = if (index == 1) SuggestionBadge.MissedCall else null,
        reason = when (index) {
            0 -> "You wanted to talk every 3 days, and it's been 3."
            1 -> "Dana called yesterday and you haven't called back."
            else -> "You usually talk about every 2 weeks."
        },
        isPinned = index == 0,
    )
}

private fun homeState(count: Int, refreshing: Boolean = false) = HomeUiState(
    suggestions = List(count) { suggestion(it) },
    selectedId = if (count > 0) "key-0" else null,
    isRefreshing = refreshing,
)

private val noHomeActions = HomeActions(
    chooseNumber = { NumberChoice.None },
    onCallNumber = {},
    onWhatsAppNumber = {},
    onOpenContactCard = {},
    onOpenDetails = {},
    onAddContactInstruction = { _, _ -> },
    onAddGeneralInstruction = {},
    onWhatsAppUsed = {},
    onTogglePin = { _, _ -> },
)

@Composable
private fun PreviewHome(state: HomeUiState, access: AccessState = AccessState(contacts = true, callLog = true)) {
    OrbitTheme {
        HomeScreen(
            state = state,
            onQueryChange = {},
            onSelect = {},
            onLoadMore = {},
            onOpenRules = {},
            onOpenAiSettings = {},
            onOpenDialer = {},
            onPullRefresh = {},
            access = access,
            onRequestAccess = {},
            onOpenSettings = {},
            messages = emptyFlow(),
            actions = noHomeActions,
        )
    }
}

@ThemePreviews
@Composable
private fun HomeManyContacts() = PreviewHome(homeState(count = 20))

@ThemePreviews
@Composable
private fun HomeFewContacts() = PreviewHome(homeState(count = 4, refreshing = true))

@ThemePreviews
@Composable
private fun HomeEmpty() = PreviewHome(homeState(count = 0))

@ThemePreviews
@Composable
private fun HomeNeedsPermission() = PreviewHome(homeState(count = 0), AccessState(contacts = false, callLog = false))

@ThemePreviews
@Composable
private fun HomeSearchNoResults() = PreviewHome(HomeUiState(query = "zzz"))

private val mom = ContactRef("key-0", "Mom")

private fun detailsState(empty: Boolean) = DetailsUiState(
    contactKey = "key-0",
    contact = Contact("key-0", "Mom"),
    contactsLoaded = true,
    signals = ContactSignals(
        lastContactMillis = 0L,
        unreturnedMissedCalls = 1,
        lastMissedMillis = 0L,
        callCount = 6,
    ),
    instructions = if (empty) {
        emptyList()
    } else {
        listOf(FreeTextRule(1, "Call after 6pm, she is usually busy before.", 1, FreeTextStatus.UNCLASSIFIED, null, "key-0"))
    },
    typedRules = if (empty) emptyList() else listOf(TypedRule(2, 2, TypedRuleParams.PinToSlot(mom, 1), null)),
)

private val noDetailsActions = DetailsActions({}, { _, _ -> }, {}, {}, { _, _ -> }, {}, {})

@ThemePreviews
@Composable
private fun DetailsWithRules() {
    OrbitTheme { DetailsScreen(detailsState(empty = false), emptyFlow(), nowMillis = 3L * 86_400_000L, actions = noDetailsActions) }
}

@ThemePreviews
@Composable
private fun DetailsEmpty() {
    OrbitTheme { DetailsScreen(detailsState(empty = true), emptyFlow(), nowMillis = 3L * 86_400_000L, actions = noDetailsActions) }
}

private val noRulesActions = RulesActions({}, { _, _ -> }, {}, {}, { _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {}, {})

@ThemePreviews
@Composable
private fun RulesWithRules() {
    OrbitTheme {
        RulesScreen(
            state = RulesUiState(
                freeText = listOf(
                    FreeTextRule(1, "Ignore missed calls.", 1, FreeTextStatus.SOFT, null, null, "Soft: a preference the ranking weighs, not a guaranteed rule."),
                    FreeTextRule(2, "Call after 6pm.", 2, FreeTextStatus.SOFT, null, "key-0", "Soft: a time-of-day preference."),
                    FreeTextRule(5, "Always put Mom first.", 5, FreeTextStatus.CONVERTED_TO_TYPED, 3, null, "Hard: always shows Mom in slot 1."),
                    FreeTextRule(
                        6, "Never show Ron.", 6, FreeTextStatus.NEEDS_REVIEW, null, null,
                        "Looks like a hard rule (never show), but \"Ron\" matches several contacts (Ron Cohen, Ron Levi). Choose the contact to turn it into a hard rule.",
                        com.smartdialer.rules.TypedRuleType.NEVER_SHOW,
                    ),
                ),
                typed = listOf(
                    TypedRule(3, 3, TypedRuleParams.PinToSlot(mom, 1), 5),
                    TypedRule(4, 4, TypedRuleParams.NeverShow(ContactRef("gone", "Old Boss")), null),
                ),
                contacts = List(3) { Contact("key-$it", names[it]) },
                trace = listOf(
                    TraceEntry(3, TraceOutcome.APPLIED, "Mom pinned to slot 1"),
                    TraceEntry(4, TraceOutcome.IGNORED, "Old Boss: contact not found, rule skipped"),
                ),
            ),
            actions = noRulesActions,
        )
    }
}

@ThemePreviews
@Composable
private fun RulesEmpty() {
    OrbitTheme { RulesScreen(state = RulesUiState(contacts = emptyList()), actions = noRulesActions) }
}
