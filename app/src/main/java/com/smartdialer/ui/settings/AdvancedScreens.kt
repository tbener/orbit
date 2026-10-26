package com.smartdialer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.smartdialer.ranking.EffectText
import com.smartdialer.ranking.FeatureIds
import com.smartdialer.ranking.ScoredContact
import com.smartdialer.ranking.ScoringConfig
import com.smartdialer.ui.rules.GlassPanel
import com.smartdialer.ui.rules.ScreenHeader
import com.smartdialer.ui.rules.SolidButton
import com.smartdialer.ui.theme.Orbit
import com.smartdialer.ui.theme.OrbitBackground

/** The score of one contact, feature by feature, in plain lines. Used by "Compare engines" and the Details screen. */
@Composable
fun BreakdownLines(scored: ScoredContact, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Orbit.spacing.xxs)) {
        scored.contributions.forEach { c ->
            val multiplier = if (c.multiplier != 1.0) " × ${"%.2f".format(c.multiplier)}" else ""
            Mono("${EffectText.featureLabel(c.featureId)}: ${"%.2f".format(c.value)} × ${"%.1f".format(c.weight)}$multiplier = ${"%.1f".format(c.points)}")
        }
        if (scored.contributions.isEmpty()) Mono("No feature applies.")
        scored.applied.forEach { Mono("note: ${it.text} = ${"%+.1f".format(it.points)}") }
        Mono("score ${"%.1f".format(scored.score)} (${scored.tier.name.lowercase()})")
    }
}

@Composable
private fun Mono(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onBackground)
}

/** Both orders side by side, with rank differences, and the local score breakdown per contact. */
@Composable
fun CompareScreen(state: CompareUiState, onRunAi: () -> Unit, onBack: () -> Unit) {
    var open by remember { mutableStateOf<String?>(null) }
    OrbitBackground {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                ScreenHeader(title = "Compare engines", onBack = onBack)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Orbit.spacing.lg, end = Orbit.spacing.lg, bottom = Orbit.spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Orbit.spacing.md),
                ) {
                    item {
                        Text(
                            "The local engine's order and the AI's order on the same data, before pins and hides. The AI run is manual, " +
                                "counts toward today's limit and is not saved.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        SolidButton(
                            label = if (state.running) "Asking the AI…" else "Run AI ranking",
                            icon = Icons.Filled.Refresh,
                            onClick = { if (!state.running) onRunAi() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    state.status?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = Orbit.colors.accent) } }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.md)) {
                            Text("Local", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                            Text("AI", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                        }
                    }
                    val localIds = state.local.map { it.contactKey }
                    val rows = maxOf(state.local.size, state.ai?.size ?: 0).coerceAtMost(25)
                    items((0 until rows).toList()) { i ->
                        val local = state.local.getOrNull(i)
                        val aiId = state.ai?.getOrNull(i)
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.md)) {
                                Cell(
                                    text = local?.let { "${i + 1}. ${it.name}" } ?: "",
                                    diff = local?.let { l -> state.ai?.indexOf(l.contactKey)?.takeIf { it >= 0 }?.let { it - i } },
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(role = Role.Button) { open = if (open == local?.contactKey) null else local?.contactKey },
                                )
                                Cell(
                                    text = aiId?.let { "${i + 1}. ${state.names[it] ?: it}" } ?: if (state.ai == null) "–" else "",
                                    diff = aiId?.let { id -> localIds.indexOf(id).takeIf { it >= 0 }?.let { it - i } },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (local != null && open == local.contactKey) GlassPanel { BreakdownLines(local) }
                        }
                    }
                }
            }
        }
    }
}

/** A rank in one of the two lists. [diff] is where the other engine puts the contact, minus this rank: highlighted when not 0. */
@Composable
private fun Cell(text: String, diff: Int?, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = Orbit.spacing.xs), horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.xs)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f, fill = false))
        if (diff != null && diff != 0) {
            Text(
                text = if (diff < 0) "▲${-diff}" else "▼$diff",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (diff < 0) Orbit.colors.accent else Orbit.colors.alertText,
            )
        }
    }
}

/** Sliders for the feature weights and key caps, with a live preview of the top 15. */
@Composable
fun TuningScreen(
    state: TuningUiState,
    onWeight: (String, Double) -> Unit,
    onChange: ((ScoringConfig) -> ScoringConfig) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    OrbitBackground {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                ScreenHeader(title = "Tune weights", onBack = onBack)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Orbit.spacing.lg, end = Orbit.spacing.lg, bottom = Orbit.spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Orbit.spacing.sm),
                ) {
                    item {
                        GlassPanel {
                            Text("Top 15 with these values", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                            state.preview.forEachIndexed { i, s ->
                                Text("${i + 1}. ${s.name}  ·  ${"%.0f".format(s.score)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                            }
                            if (state.preview.isEmpty()) Text("Nothing to rank yet.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    items(FeatureIds.all) { id ->
                        SliderRow("${EffectText.featureLabel(id)} weight", state.config.weight(id).toFloat(), 0f..60f) { onWeight(id, it.toDouble()) }
                    }
                    item { SliderRow("missed call, day after (share)", state.config.missedDayAfterFactor.toFloat(), 0f..1f) { v -> onChange { it.copy(missedDayAfterFactor = v.toDouble()) } } }
                    item { SliderRow("calls in 30 days for full frequency", state.config.frequencyFull30d.toFloat(), 1f..20f) { v -> onChange { it.copy(frequencyFull30d = v.toInt()) } } }
                    item { SliderRow("calls in 90 days for full frequency", state.config.frequencyFull90d.toFloat(), 5f..60f) { v -> onChange { it.copy(frequencyFull90d = v.toInt()) } } }
                    item { SliderRow("WhatsApp recency (days)", state.config.whatsAppRecentDays.toFloat(), 1f..21f) { v -> onChange { it.copy(whatsAppRecentDays = v.toInt()) } } }
                    item { SliderRow("base score needed for #1", state.config.minBaseForTop.toFloat(), 0f..30f) { v -> onChange { it.copy(minBaseForTop = v.toDouble()) } } }
                    item {
                        SolidButton("Copy weights as text", Icons.Filled.ContentCopy, { clipboard.setText(AnnotatedString(state.config.asText())) }, Modifier.fillMaxWidth())
                    }
                    item { SolidButton("Reset to defaults", Icons.Filled.Refresh, onReset, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            Text("%.1f".format(value), style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.accent)
        }
        Slider(value = value.coerceIn(range), onValueChange = onValue, valueRange = range)
    }
}
