package com.smartdialer.llm

import com.smartdialer.data.Contact
import com.smartdialer.ranking.EffectParams
import com.smartdialer.ranking.EffectValidator
import com.smartdialer.ranking.RawEffect
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

/** An instruction waiting to be compiled. [aboutName] is the contact it was written for, if any. */
class RuleToClassify(val id: Long, val text: String, val aboutName: String?)

enum class ModelAction { PIN_TO_SLOT, NEVER_SHOW, NONE }

/** What the model decided for one instruction. */
enum class ModelKind { HARD, COMPILED, NOT_RANKING_RELATED, UNSUPPORTED, SOFT }

/** What the model said about one rule, before the code decides what to do with it. */
class ModelClassification(
    val ruleId: Long,
    val kind: ModelKind,
    val action: ModelAction,
    val slot: Int,
    val personName: String,
    val explanation: String,
    val effects: List<RawEffect> = emptyList(),
)

/**
 * The instruction-compiler request: one call classifies an instruction and, when it is a preference, compiles it into
 * effects for the local scoring engine. **Privacy:** only the instruction text and, for a per-contact instruction, that
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
                                put("text", Sanitizer.cleanInstruction(rule.text))
                                put("about", rule.aboutName?.let { JsonPrimitive(Sanitizer.cleanName(it)) } ?: kotlinx.serialization.json.JsonNull)
                            },
                        )
                    }
                },
            )
        },
    )

    private fun prop(type: String) = buildJsonObject { put("type", type) }
    private fun enumOf(vararg values: String) = buildJsonObject {
        put("type", "string")
        put("enum", JsonArray(values.map(::JsonPrimitive)))
    }

    private fun strings() = buildJsonObject {
        put("type", "array")
        put("items", prop("string"))
    }

    private fun obj(props: Map<String, JsonObject>) = buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(props))
        put("required", JsonArray(props.keys.map(::JsonPrimitive)))
        put("additionalProperties", false)
    }

    private val effectSchema = obj(
        linkedMapOf(
            "type" to enumOf("GLOBAL_WEIGHT", "CONTACT_BOOST", "CADENCE", "TIME_WINDOW"),
            "feature" to prop("string"),
            "multiplier" to prop("number"),
            "personName" to prop("string"),
            "delta" to prop("number"),
            "everyDays" to prop("integer"),
            "strength" to prop("number"),
            "weekday" to prop("string"),
            "part" to prop("string"),
            "weekdays" to strings(),
            "parts" to strings(),
            "boost" to prop("number"),
        ),
    )

    val schema: JsonObject = obj(
        linkedMapOf(
            "classifications" to buildJsonObject {
                put("type", "array")
                put(
                    "items",
                    obj(
                        linkedMapOf(
                            "ruleId" to prop("integer"),
                            "kind" to enumOf("HARD", "COMPILED", "NOT_RANKING_RELATED", "UNSUPPORTED"),
                            "action" to enumOf("PIN_TO_SLOT", "NEVER_SHOW", "NONE"),
                            "slot" to prop("integer"),
                            "personName" to prop("string"),
                            "explanation" to prop("string"),
                            "effects" to buildJsonObject {
                                put("type", "array")
                                put("items", effectSchema)
                            },
                        ),
                    ),
                )
            },
        ),
    )
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
                    val kind = ModelKind.entries.firstOrNull { it.name.equals(str(item, "kind").trim(), ignoreCase = true) }
                    val action = ModelAction.entries.firstOrNull { it.name.equals(str(item, "action").trim(), ignoreCase = true) }
                    if (kind == null || action == null) {
                        seen.remove(id)
                        issues += "Entry ${index + 1} (rule $id): kind or action is not valid, dropped."
                    } else {
                        items += ModelClassification(
                            ruleId = id,
                            kind = kind,
                            action = action,
                            slot = (item["slot"] as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 0,
                            personName = str(item, "personName").trim(),
                            explanation = str(item, "explanation").trim().replace(Regex("\\s+"), " "),
                            effects = (item["effects"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::rawEffect) },
                        )
                    }
                }
            }
        }
        val missing = validIds - items.map { it.ruleId }.toSet()
        if (missing.isNotEmpty()) issues += "No usable answer for rule(s) ${missing.sorted().joinToString()}; they stay unclassified."
        return ClassificationParse.Valid(items, issues)
    }

    private fun str(o: JsonObject, key: String) = ((o[key] as? JsonPrimitive)?.contentOrNull).orEmpty()
    private fun num(o: JsonObject, key: String, default: Double) = (o[key] as? JsonPrimitive)?.doubleOrNull ?: default
    private fun strings(o: JsonObject, key: String) = (o[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    private fun rawEffect(o: JsonObject) = RawEffect(
        type = str(o, "type"),
        feature = str(o, "feature"),
        multiplier = num(o, "multiplier", 1.0),
        personName = str(o, "personName").trim(),
        delta = num(o, "delta", 0.0),
        everyDays = num(o, "everyDays", 0.0).toInt(),
        strength = num(o, "strength", 1.0),
        weekday = str(o, "weekday"),
        part = str(o, "part"),
        weekdays = strings(o, "weekdays"),
        parts = strings(o, "parts"),
        boost = num(o, "boost", 0.0),
    )
}

/** What to do with one classified rule. */
sealed interface ClassificationDecision {
    /** Nothing to do with who to call: kept and shown, never used. */
    data object OffTopic : ClassificationDecision

    class Soft(val explanation: String) : ClassificationDecision

    /** Create this typed rule and mark the free-text rule as converted. */
    class Hard(val params: TypedRuleParams, val explanation: String) : ClassificationDecision

    /** It looks like a hard rule, or needs a contact, but the target is unclear; the user picks. Never guessed. */
    class NeedsReview(val explanation: String, val proposedType: TypedRuleType?, val proposedSlot: Int?) : ClassificationDecision

    /** The instruction became these validated effects. [dropped] says why other proposed effects were left out. */
    class Compiled(val effects: List<EffectParams>, val explanation: String, val dropped: List<String>) : ClassificationDecision

    /** It cannot be expressed as an effect; kept as a note for the AI ranking mode. */
    class Unsupported(val explanation: String) : ClassificationDecision
}

sealed interface NameMatch {
    class Found(val contact: Contact) : NameMatch
    class Ambiguous(val contacts: List<Contact>) : NameMatch
    data object NotFound : NameMatch
}

/** Turns what the model said into a decision, resolving who the rule is about. Pure: this is where "never guess" lives. */
object ClassificationResolver {

    fun resolve(rule: FreeTextRule, model: ModelClassification, contacts: List<Contact>, maxSlot: Int): ClassificationDecision {
        if (model.kind == ModelKind.NOT_RANKING_RELATED) return ClassificationDecision.OffTopic
        val modelExplanation = Sanitizer.cleanReason(model.explanation, 200).orEmpty()
        return when (model.kind) {
            ModelKind.SOFT -> ClassificationDecision.Soft(modelExplanation.ifBlank { "Soft: this guides the ranking but is not guaranteed." })
            ModelKind.UNSUPPORTED ->
                ClassificationDecision.Unsupported(modelExplanation.ifBlank { "This can't be turned into a ranking effect, so it is kept as a note." })
            ModelKind.COMPILED -> compile(rule, model, contacts, modelExplanation)
            else -> hard(rule, model, contacts, maxSlot, modelExplanation)
        }
    }

    private fun compile(rule: FreeTextRule, model: ModelClassification, contacts: List<Contact>, explanation: String): ClassificationDecision {
        val known = contacts.map { it.id }.toSet()
        // A per-contact instruction is about that contact; a general one names people, resolved by name (never guessed).
        val keyOf = { name: String ->
            if (rule.contactKey != null) rule.contactKey else (findByName(name, contacts) as? NameMatch.Found)?.contact?.id
        }
        val effects = ArrayList<EffectParams>()
        val dropped = ArrayList<String>()
        var unresolvedContact = false
        model.effects.forEach { raw ->
            val v = EffectValidator.validate(raw, keyOf, known)
            if (v.params != null) effects += v.params else {
                dropped += v.issue.orEmpty()
                if (v.issue?.startsWith("no contact") == true || v.issue == "unknown contact") unresolvedContact = true
            }
        }
        return when {
            effects.isNotEmpty() -> ClassificationDecision.Compiled(effects, explanation.ifBlank { "Compiled into ranking effects." }, dropped)
            unresolvedContact -> ClassificationDecision.NeedsReview(
                "I could not tell which contact this is about (${dropped.firstOrNull().orEmpty()}). Edit the instruction to name them.",
                null,
                null,
            )
            else -> ClassificationDecision.Unsupported(explanation.ifBlank { "This can't be turned into a ranking effect, so it is kept as a note." })
        }
    }

    private fun hard(rule: FreeTextRule, model: ModelClassification, contacts: List<Contact>, maxSlot: Int, modelExplanation: String): ClassificationDecision {
        if (model.action == ModelAction.NONE) {
            return ClassificationDecision.Unsupported(modelExplanation.ifBlank { "This can't be turned into a ranking effect, so it is kept as a note." })
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

        if (type == TypedRuleType.PIN_TO_SLOT && (slot == null || slot !in 1..maxSlot)) return review("the slot is not clear.")
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
