package com.smartdialer.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where a free-text rule stands in the classification process (classification itself arrives in stage 4). */
enum class FreeTextStatus { UNCLASSIFIED, SOFT, CONVERTED_TO_TYPED }

/** A rule written in natural language. Interpreted by the LLM; never enforced by code. */
data class FreeTextRule(
    val id: Long,
    val text: String,
    val createdAt: Long,
    val status: FreeTextStatus,
    /** The typed rule this rule was converted into, if any. */
    val typedRuleId: Long?,
)

/**
 * A reference to a contact by its stable contacts lookup key. Matching only ever uses [lookupKey];
 * [displayName] is a snapshot kept so the rule can still be shown after the contact is deleted.
 */
@Serializable
data class ContactRef(
    val lookupKey: String,
    val displayName: String,
)

enum class TypedRuleType { PIN_TO_SLOT, NEVER_SHOW }

/**
 * What a typed rule does. To add a rule type: add a [TypedRuleType] value, add a subclass here with a
 * unique [SerialName], handle it in `RuleEnforcer`, and add it to the Rules page form. Parameters are
 * stored as JSON, so no database migration is needed.
 */
@Serializable
sealed interface TypedRuleParams {
    val type: TypedRuleType
    val contact: ContactRef

    /** Always put [contact] in [slot] (1 = top). */
    @Serializable
    @SerialName("PIN_TO_SLOT")
    data class PinToSlot(override val contact: ContactRef, val slot: Int) : TypedRuleParams {
        override val type: TypedRuleType get() = TypedRuleType.PIN_TO_SLOT
    }

    /** Never include [contact] in automatic suggestions. Search results are not affected. */
    @Serializable
    @SerialName("NEVER_SHOW")
    data class NeverShow(override val contact: ContactRef) : TypedRuleParams {
        override val type: TypedRuleType get() = TypedRuleType.NEVER_SHOW
    }
}

/** A hard rule enforced deterministically after the ranking is produced. */
data class TypedRule(
    val id: Long,
    val createdAt: Long,
    val params: TypedRuleParams,
    /** The free-text rule this rule was converted from, if any. */
    val sourceFreeTextRuleId: Long?,
)

/** Converts [TypedRuleParams] to and from the JSON stored in the database. */
object TypedRuleCodec {
    private val json = Json { classDiscriminator = "kind" }

    fun encode(params: TypedRuleParams): String =
        json.encodeToString(TypedRuleParams.serializer(), params)

    fun decode(text: String): TypedRuleParams =
        json.decodeFromString(TypedRuleParams.serializer(), text)
}
