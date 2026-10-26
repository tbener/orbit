package com.smartdialer.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Where a free-text rule stands in classification.
 * - [UNCLASSIFIED]: not classified yet (waiting for the LLM, or the AI is off).
 * - [SOFT]: a preference the LLM reads when ranking; code does not enforce it.
 * - [CONVERTED_TO_TYPED]: it became a typed rule, which code enforces.
 * - [NEEDS_REVIEW]: it looks like a hard rule but the contact is unclear; the user has to pick one.
 * - [COMPILED]: the instruction became one or more effects on the local score (shown and editable on the Rules page).
 * - [UNSUPPORTED]: it cannot be expressed as an effect; kept as a note and used only when the AI ranks.
 * - [REJECTED_OFF_TOPIC]: it has nothing to do with who to call; it is kept, shown, and never sent to the LLM.
 */
enum class FreeTextStatus { UNCLASSIFIED, SOFT, CONVERTED_TO_TYPED, NEEDS_REVIEW, REJECTED_OFF_TOPIC, COMPILED, UNSUPPORTED }

/** A rule written in natural language. Interpreted by the LLM; never enforced by code. */
data class FreeTextRule(
    val id: Long,
    val text: String,
    val createdAt: Long,
    val status: FreeTextStatus,
    /** The typed rule this rule was converted into, if any. */
    val typedRuleId: Long?,
    /** The contact (lookup key) this rule is about. Null means a general instruction for all contacts. */
    val contactKey: String? = null,
    /** One plain sentence saying how the rule was classified and why (shown to the user). Null before classification. */
    val explanation: String? = null,
    /** For [FreeTextStatus.NEEDS_REVIEW]: the kind of typed rule the model proposed, so the form can be pre-filled. */
    val proposedType: TypedRuleType? = null,
    val proposedSlot: Int? = null,
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
