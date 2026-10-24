package com.smartdialer.llm

import com.smartdialer.data.Contact
import com.smartdialer.rules.ContactRef
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.TypedRuleParams
import com.smartdialer.rules.TypedRuleType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Locale

/** An instruction waiting to be classified. [aboutName] is the contact it was written for, if any. */
class RuleToClassify(val id: Long, val text: String, val aboutName: String?)

enum class ModelAction { PIN_TO_SLOT, NEVER_SHOW, NONE }

/** What the model said about one rule, before the code decides what to do with it. */
class ModelClassification(
    val ruleId: Long,
    val hard: Boolean,
    val action: ModelAction,
    val slot: Int,
    val personName: String,
    val explanation: String,
)

/**
 * The classification request. **Privacy:** only the instruction text and, for a per-contact instruction, that
 * contact's display name are sent. No contact list, numbers or call data.
 */
object ClassificationPrompt {
    const val PURPOSE = "classification"

    fun request(rules: List<RuleToClassify>, maxSlot: Int): LlmRequest = LlmRequest(
        purpose = PURPOSE,
        system = Prompts.classification,
        user = userMessage(rules, maxSlot),
        schema = schema,
        maxTokens = LlmConfig.CLASSIFY_MAX_TOKENS,
    )

    fun userMessage(rules: List<RuleToClassify>, maxSlot: Int): String = Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("maxSlot", maxSlot)
            put(
                "rules",
                buildJsonArray {
                    rules.forEach { rule ->
                        add(
                            buildJsonObject {
                                put("ruleId", rule.id)
                                put("text", rule.text)
                                put("about", rule.aboutName?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
                            },
                        )
                    }
                },
            )
        },
    )

    private fun stringType() = buildJsonObject { put("type", "string") }
    private fun enumOf(vararg values: String) = buildJsonObject {
        put("type", "string")
        put("enum", JsonArray(values.map(::JsonPrimitive)))
    }

    val schema: JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "classifications",
                    buildJsonObject {
                        put("type", "array")
                        put(
                            "items",
                            buildJsonObject {
                                put("type", "object")
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put("ruleId", buildJsonObject { put("type", "integer") })
                                        put("kind", enumOf("HARD", "SOFT"))
                                        put("action", enumOf("PIN_TO_SLOT", "NEVER_SHOW", "NONE"))
                                        put("slot", buildJsonObject { put("type", "integer") })
                                        put("personName", stringType())
                                        put("explanation", stringType())
                                    },
                                )
                                put(
                                    "required",
                                    JsonArray(listOf("ruleId", "kind", "action", "slot", "personName", "explanation").map(::JsonPrimitive)),
                                )
                                put("additionalProperties", false)
                            },
                        )
                    },
                )
            },
        )
        put("required", JsonArray(listOf(JsonPrimitive("classifications"))))
        put("additionalProperties", false)
    }
}

sealed interface ClassificationParse {
    class Valid(val items: List<ModelClassification>, val issues: List<String>) : ClassificationParse
    class Unusable(val reason: String) : ClassificationParse
}

object ClassificationParser {
    fun parse(text: String, validIds: Set<Long>): ClassificationParse {
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return ClassificationParse.Unusable("The answer was not a JSON object.")
        }
        val array = root["classifications"] as? JsonArray
            ?: return ClassificationParse.Unusable("The answer has no \"classifications\" list.")
        val issues = mutableListOf<String>()
        val seen = HashSet<Long>()
        val items = ArrayList<ModelClassification>()
        array.forEachIndexed { index, element ->
            val item = element as? JsonObject
            val id = (item?.get("ruleId") as? JsonPrimitive)?.doubleOrNull?.takeIf { it % 1.0 == 0.0 }?.toLong()
            when {
                item == null || id == null -> issues += "Entry ${index + 1}: no usable ruleId, dropped."
                id !in validIds -> issues += "Entry ${index + 1}: unknown ruleId $id, dropped."
                !seen.add(id) -> issues += "Entry ${index + 1}: duplicate ruleId $id, dropped."
                else -> {
                    val kind = (item["kind"] as? JsonPrimitive)?.contentOrNull?.trim()
                    val action = ModelAction.entries.firstOrNull {
                        it.name.equals((item["action"] as? JsonPrimitive)?.contentOrNull?.trim(), ignoreCase = true)
                    }
                    if (kind == null || !(kind.equals("HARD", true) || kind.equals("SOFT", true)) || action == null) {
                        seen.remove(id)
                        issues += "Entry ${index + 1} (rule $id): kind or action is not valid, dropped."
                    } else {
                        items += ModelClassification(
                            ruleId = id,
                            hard = kind.equals("HARD", true),
                            action = action,
                            slot = (item["slot"] as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 0,
                            personName = ((item["personName"] as? JsonPrimitive)?.contentOrNull).orEmpty().trim(),
                            explanation = ((item["explanation"] as? JsonPrimitive)?.contentOrNull).orEmpty().trim().replace(Regex("\\s+"), " "),
                        )
                    }
                }
            }
        }
        val missing = validIds - items.map { it.ruleId }.toSet()
        if (missing.isNotEmpty()) issues += "No usable answer for rule(s) ${missing.sorted().joinToString()}; they stay unclassified."
        return ClassificationParse.Valid(items, issues)
    }
}

/** What to do with one classified rule. */
sealed interface ClassificationDecision {
    class Soft(val explanation: String) : ClassificationDecision

    /** Create this typed rule and mark the free-text rule as converted. */
    class Hard(val params: TypedRuleParams, val explanation: String) : ClassificationDecision

    /** It looks like a hard rule, but the target is unclear; the user picks. Never guessed. */
    class NeedsReview(val explanation: String, val proposedType: TypedRuleType, val proposedSlot: Int?) : ClassificationDecision
}

sealed interface NameMatch {
    class Found(val contact: Contact) : NameMatch
    class Ambiguous(val contacts: List<Contact>) : NameMatch
    data object NotFound : NameMatch
}

/** Turns what the model said into a decision, resolving who the rule is about. Pure: this is where "never guess" lives. */
object ClassificationResolver {

    fun resolve(rule: FreeTextRule, model: ModelClassification, contacts: List<Contact>, maxSlot: Int): ClassificationDecision {
        val modelExplanation = model.explanation
        if (!model.hard || model.action == ModelAction.NONE) {
            return ClassificationDecision.Soft(
                modelExplanation.ifBlank { "Soft: this guides the ranking but is not guaranteed." },
            )
        }
        val type = if (model.action == ModelAction.PIN_TO_SLOT) TypedRuleType.PIN_TO_SLOT else TypedRuleType.NEVER_SHOW
        val slot = model.slot.takeIf { type == TypedRuleType.PIN_TO_SLOT }

        fun review(why: String) = ClassificationDecision.NeedsReview(
            explanation = "Looks like a hard rule (${describe(type, slot)}), but $why Choose the contact to turn it into a hard rule.",
            proposedType = type,
            proposedSlot = slot,
        )

        val contact: Contact = if (rule.contactKey != null) {
            contacts.firstOrNull { it.id == rule.contactKey }
                ?: return review("the contact it was written for is not on this phone any more.")
        } else {
            val name = model.personName
            if (name.isBlank()) return review("it does not clearly name a person.")
            when (val match = findByName(name, contacts)) {
                is NameMatch.Found -> match.contact
                is NameMatch.Ambiguous -> return review(
                    "\"$name\" matches several contacts (${match.contacts.take(4).joinToString { it.name }}" +
                        (if (match.contacts.size > 4) ", ..." else "") + ").",
                )
                NameMatch.NotFound -> return review("no contact named \"$name\" was found.")
            }
        }

        if (type == TypedRuleType.PIN_TO_SLOT && (slot == null || slot !in 1..maxSlot)) {
            return review("the slot is not clear.")
        }
        val params: TypedRuleParams = when (type) {
            TypedRuleType.PIN_TO_SLOT -> TypedRuleParams.PinToSlot(ContactRef(contact.id, contact.name), slot!!)
            TypedRuleType.NEVER_SHOW -> TypedRuleParams.NeverShow(ContactRef(contact.id, contact.name))
        }
        val explanation = modelExplanation.ifBlank {
            if (type == TypedRuleType.PIN_TO_SLOT) "Hard: always shows ${contact.name} in slot $slot." else "Hard: never suggests ${contact.name}."
        }
        return ClassificationDecision.Hard(params, explanation)
    }

    private fun describe(type: TypedRuleType, slot: Int?) =
        if (type == TypedRuleType.PIN_TO_SLOT) "pin to slot ${slot ?: "?"}" else "never show"

    /**
     * An exact (case-insensitive) match of the whole name wins if it is unique. Without one, a unique contact
     * whose name contains every word of [name] matches. Several matches are [NameMatch.Ambiguous]; nothing is
     * ever matched by similarity.
     */
    fun findByName(name: String, contacts: List<Contact>): NameMatch {
        val wanted = normalize(name)
        if (wanted.isEmpty()) return NameMatch.NotFound
        val exact = contacts.filter { normalize(it.name) == wanted }
        if (exact.size == 1) return NameMatch.Found(exact.single())
        if (exact.size > 1) return NameMatch.Ambiguous(exact)
        val words = wanted.split(' ')
        val partial = contacts.filter { contact -> normalize(contact.name).split(' ').toSet().containsAll(words) }
        return when {
            partial.size == 1 -> NameMatch.Found(partial.single())
            partial.size > 1 -> NameMatch.Ambiguous(partial)
            else -> NameMatch.NotFound
        }
    }

    private fun normalize(text: String) = text.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
