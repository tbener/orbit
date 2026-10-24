package com.smartdialer.ranking

import com.smartdialer.data.Contact
import com.smartdialer.data.Suggestion
import com.smartdialer.rules.TypedRule
import com.smartdialer.rules.TypedRuleParams
import kotlin.math.min

enum class TraceOutcome { APPLIED, IGNORED }

/** One line of the explanation of what a rule did (or why it did nothing). */
data class TraceEntry(val ruleId: Long, val outcome: TraceOutcome, val message: String)

data class EnforcementResult(
    val suggestions: List<Suggestion>,
    val trace: List<TraceEntry>,
)

/**
 * Applies typed (hard) rules to a ranked list. Pure and deterministic: the same inputs always give the
 * same output, and it has no dependency on the LLM layer or on Android. Its output is final.
 *
 * Conflict handling:
 * - Rules are processed oldest first (by `createdAt`, then `id`), so adding a rule never disturbs the
 *   outcome of rules that already exist.
 * - **Contact not found:** a rule whose contact is not in the known contacts is ignored.
 * - **NEVER_SHOW beats PIN_TO_SLOT:** a pinned contact that also has a NEVER_SHOW rule is removed and the
 *   pin is ignored.
 * - **Same slot, two pins:** the older rule wins; the newer pin is ignored.
 * - **Same contact, two pins:** the older rule wins; the newer pin is ignored.
 * - **Slot beyond the list:** the pin is placed at the end of the list (a slot beyond the cap is treated
 *   as the cap). Several such pins keep their relative order.
 * - **Pinned contact missing from the ranked list:** the contact is inserted at its slot.
 * - **Pins shifting each other:** pins are inserted in ascending slot order into the list that remains
 *   after removing hidden and pinned contacts, so every pin lands exactly on its slot when the list is
 *   long enough, and non-pinned contacts keep their relative order around them.
 * - **Cap:** the result is cut to [cap] entries after all pins are placed.
 *
 * NEVER_SHOW applies only to this automatic list; callers must not use it to filter search results.
 */
object RuleEnforcer {

    fun enforce(
        ranked: List<Suggestion>,
        rules: List<TypedRule>,
        contacts: Map<String, Contact>,
        cap: Int,
    ): EnforcementResult {
        if (cap <= 0) return EnforcementResult(emptyList(), emptyList())

        val ordered = rules.sortedWith(compareBy({ it.createdAt }, { it.id }))
        val rankedById = ranked.associateBy { it.contact.id }
        val outcomes = HashMap<Long, TraceEntry>()

        fun ignore(rule: TypedRule, why: String) {
            outcomes[rule.id] = TraceEntry(rule.id, TraceOutcome.IGNORED, why)
        }

        fun apply(rule: TypedRule, what: String) {
            outcomes[rule.id] = TraceEntry(rule.id, TraceOutcome.APPLIED, what)
        }

        // Rules about contacts that no longer exist are skipped entirely.
        val live = ArrayList<Pair<TypedRule, Contact>>()
        for (rule in ordered) {
            val contact = contacts[rule.params.contact.lookupKey] ?: rankedById[rule.params.contact.lookupKey]?.contact
            if (contact == null) {
                ignore(rule, "${rule.params.contact.displayName}: contact not found, rule skipped")
            } else {
                live += rule to contact
            }
        }

        val hidden = HashSet<String>()
        for ((rule, contact) in live) {
            if (rule.params is TypedRuleParams.NeverShow) {
                hidden += contact.id
                apply(
                    rule,
                    if (contact.id in rankedById) "${contact.name} hidden from suggestions"
                    else "${contact.name} is never shown (not in the suggestions anyway)",
                )
            }
        }

        class AcceptedPin(val rule: TypedRule, val contact: Contact, val slot: Int)

        val accepted = ArrayList<AcceptedPin>()
        val slotOwners = HashMap<Int, Contact>()
        val pinnedContacts = HashSet<String>()
        for ((rule, contact) in live) {
            val pin = rule.params as? TypedRuleParams.PinToSlot ?: continue
            when {
                contact.id in hidden ->
                    ignore(rule, "Pin for ${contact.name} ignored: never-show wins")
                pin.slot < 1 ->
                    ignore(rule, "Pin for ${contact.name} ignored: slot ${pin.slot} is not valid")
                contact.id in pinnedContacts ->
                    ignore(rule, "Pin for ${contact.name} ignored: an older rule already pins them")
                pin.slot in slotOwners ->
                    ignore(rule, "Pin for ${contact.name} ignored: slot ${pin.slot} is taken by ${slotOwners.getValue(pin.slot).name} (older rule wins)")
                else -> {
                    accepted += AcceptedPin(rule, contact, pin.slot)
                    slotOwners[pin.slot] = contact
                    pinnedContacts += contact.id
                }
            }
        }

        val result = ranked.filter { it.contact.id !in hidden && it.contact.id !in pinnedContacts }.toMutableList()
        for (pin in accepted.sortedBy { it.slot }) {
            val target = min(pin.slot, cap)
            val index = min(target - 1, result.size)
            val original = rankedById[pin.contact.id]
            result.add(
                index,
                (original ?: Suggestion(pin.contact, subtitle = null, badge = null, reason = ""))
                    .copy(reason = "You pinned ${pin.contact.name} to slot ${pin.slot}.", isPinned = true),
            )
            val notes = buildList {
                if (original == null) add("inserted, not in the ranked list")
                if (index + 1 != pin.slot) add("slot ${pin.slot} is beyond the list, placed at position ${index + 1}")
            }
            apply(
                pin.rule,
                "${pin.contact.name} pinned to slot ${pin.slot}" + if (notes.isEmpty()) "" else " (${notes.joinToString("; ")})",
            )
        }

        return EnforcementResult(
            suggestions = result.take(cap),
            trace = ordered.mapNotNull { outcomes[it.id] },
        )
    }
}
