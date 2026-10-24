package com.smartdialer.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/** One validated entry from the model. */
data class ParsedEntry(val contactKey: String, val score: Int, val tier: Tier, val reason: String)

sealed interface RankingParse {
    /** [issues] lists everything that was dropped or repaired, in plain words, for the debug view. */
    class Valid(val entries: List<ParsedEntry>, val issues: List<String>) : RankingParse

    /** Nothing usable; the previous cache is kept. */
    class Unusable(val reason: String, val issues: List<String> = emptyList()) : RankingParse
}

/** Strictly validates the model's ranking answer. Pure. */
object RankingParser {

    fun parse(text: String, validKeys: Set<String>): RankingParse {
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return RankingParse.Unusable("The answer was not a JSON object.")
        }
        val array = root["ranking"] as? JsonArray ?: return RankingParse.Unusable("The answer has no \"ranking\" list.")

        val issues = mutableListOf<String>()
        val seen = HashSet<String>()
        val entries = ArrayList<ParsedEntry>()
        array.forEachIndexed { index, element ->
            val item = element as? JsonObject
            val key = (item?.get("contactKey") as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            when {
                item == null -> issues += "Entry ${index + 1}: not an object, dropped."
                key == null -> issues += "Entry ${index + 1}: no contactKey, dropped."
                key !in validKeys -> issues += "Entry ${index + 1}: unknown contactKey \"$key\", dropped."
                !seen.add(key) -> issues += "Entry ${index + 1}: duplicate contactKey \"$key\", dropped."
                else -> {
                    val rawScore = (item["score"] as? JsonPrimitive)?.doubleOrNull
                    if (rawScore == null || rawScore.isNaN()) {
                        seen.remove(key)
                        issues += "Entry ${index + 1} ($key): score is not a number, dropped."
                    } else {
                        val score = rawScore.coerceIn(0.0, 100.0).let { Math.round(it).toInt() }
                        if (score.toDouble() != rawScore) issues += "Entry ${index + 1} ($key): score $rawScore clamped to $score."
                        val tierText = (item["tier"] as? JsonPrimitive)?.contentOrNull
                        val tier = Tier.parse(tierText) ?: Tier.fromScore(score).also {
                            issues += "Entry ${index + 1} ($key): tier \"$tierText\" is not valid, derived $it from the score."
                        }
                        var reason = ((item["reason"] as? JsonPrimitive)?.contentOrNull).orEmpty().trim().replace(Regex("\\s+"), " ")
                        if (reason.length > LlmConfig.MAX_REASON_LENGTH) {
                            reason = reason.take(LlmConfig.MAX_REASON_LENGTH - 1).trimEnd() + "…"
                            issues += "Entry ${index + 1} ($key): reason shortened."
                        }
                        entries += ParsedEntry(key, score, tier, reason)
                    }
                }
            }
        }
        if (entries.isEmpty()) return RankingParse.Unusable("None of the entries were usable.", issues)

        val missing = validKeys.size - entries.size
        if (missing > 0) issues += "$missing contact(s) were not ranked by the model; they are filled in from the fallback order."
        return RankingParse.Valid(entries, issues)
    }
}

/** Combines the model's entries with the fallback order into the final cached ranking. Pure. */
object RankingMerge {

    /**
     * Model entries come first, highest score first (the model's own order breaks ties). Candidates the
     * model left out follow in [fallbackOrder] (candidates missing from it go last, in candidate order),
     * flagged as [EntrySource.FALLBACK] with the fallback's reason.
     */
    fun merge(
        parsed: List<ParsedEntry>,
        candidateKeys: List<String>,
        fallbackOrder: List<String>,
        fallbackReasons: Map<String, String>,
        generatedAt: Long,
    ): List<CachedEntry> {
        val ranked = parsed.withIndex().sortedWith(compareByDescending<IndexedValue<ParsedEntry>> { it.value.score }.thenBy { it.index }).map { it.value }
        val rankedKeys = ranked.map { it.contactKey }.toSet()
        val omitted = candidateKeys.filter { it !in rankedKeys }
        val fallbackIndex = fallbackOrder.withIndex().associate { it.value to it.index }
        val omittedOrdered = omitted.sortedBy { fallbackIndex[it] ?: Int.MAX_VALUE }

        val entries = ArrayList<CachedEntry>()
        ranked.forEach {
            entries += CachedEntry(it.contactKey, entries.size + 1, it.score, it.tier, it.reason, EntrySource.LLM, generatedAt)
        }
        omittedOrdered.forEach {
            entries += CachedEntry(it, entries.size + 1, 0, Tier.LOW, fallbackReasons[it].orEmpty(), EntrySource.FALLBACK, generatedAt)
        }
        return entries
    }
}
