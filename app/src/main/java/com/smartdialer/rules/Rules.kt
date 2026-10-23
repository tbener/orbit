package com.smartdialer.rules

/** A rule written in natural language. Interpreted by the LLM; never enforced by code. */
data class FreeTextRule(
    val id: String,
    val text: String,
)

/** A hard rule enforced deterministically after the LLM answers. */
sealed interface TypedRule {
    val id: String
    val contactName: String

    /** Always put [contactName] in [slot] (1 = top). */
    data class Pin(
        override val id: String,
        override val contactName: String,
        val slot: Int,
    ) : TypedRule

    /** Never show [contactName]. */
    data class Hide(
        override val id: String,
        override val contactName: String,
    ) : TypedRule
}
