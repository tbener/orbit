package com.smartdialer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.smartdialer.llm.LlmExchange
import com.smartdialer.ui.rules.GlassPanel
import com.smartdialer.ui.rules.ScreenHeader
import com.smartdialer.ui.theme.Orbit
import com.smartdialer.ui.theme.OrbitBackground
import kotlinx.serialization.json.Json

private val pretty = Json { prettyPrint = true }

/** Pretty-prints JSON for reading; anything that is not JSON is shown as it is. */
internal fun prettyJson(text: String): String =
    runCatching { pretty.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), Json.parseToJsonElement(text)) }
        .getOrDefault(text)

private const val MAX_SHOWN_CHARS = 60_000

/**
 * "What the LLM sees": the last real request body, the raw answer and every validation problem, for each kind
 * of call. These are stored exactly as sent and received, not rebuilt.
 */
@Composable
fun LlmInspectorScreen(state: InspectorUiState, nowMillis: Long, onBack: () -> Unit) {
    OrbitBackground {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                ScreenHeader(title = "What the LLM sees", onBack = onBack)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Orbit.spacing.lg, end = Orbit.spacing.lg, bottom = Orbit.spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Orbit.spacing.md),
                ) {
                    item {
                        Text(
                            "The last real request Orbit sent, exactly as sent, and the raw answer. Phone numbers, emails and photos are never in it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item { ExchangePanel("Ranking", state.ranking, nowMillis) }
                    item { ExchangePanel("Rule classification", state.classification, nowMillis) }
                }
            }
        }
    }
}

@Composable
private fun ExchangePanel(title: String, exchange: LlmExchange?, nowMillis: Long) {
    GlassPanel {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        if (exchange == null) {
            Text("Nothing has been sent yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@GlassPanel
        }
        Text(
            "${relativeTime(nowMillis, exchange.at)} · ${exchange.outcome}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Collapsible("Validation issues and errors (${exchange.errors.size})", startOpen = exchange.errors.isNotEmpty()) {
            if (exchange.errors.isEmpty()) {
                Text("None.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(Orbit.spacing.xs)) {
                        exchange.errors.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall, color = Orbit.colors.alertText)
                        }
                    }
                }
            }
        }
        Collapsible("Request sent", startOpen = false) { Mono(exchange.request?.let(::prettyJson) ?: "Nothing was sent.") }
        Collapsible("Raw response", startOpen = false) { Mono(exchange.response?.let(::prettyJson) ?: "No response.") }
    }
}

@Composable
private fun Collapsible(title: String, startOpen: Boolean, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = (if (open) "▾ " else "▸ ") + title,
            style = MaterialTheme.typography.labelLarge,
            color = Orbit.colors.accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Orbit.sizing.touchTarget)
                .clickable(role = Role.Button) { open = !open }
                .padding(vertical = Orbit.spacing.md),
        )
        if (open) content()
    }
}

@Composable
private fun Mono(text: String) {
    val shown = if (text.length > MAX_SHOWN_CHARS) text.take(MAX_SHOWN_CHARS) + "\n… (${text.length - MAX_SHOWN_CHARS} more characters not shown)" else text
    SelectionContainer {
        Text(
            text = shown,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
