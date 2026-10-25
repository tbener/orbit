package com.smartdialer.ranking

import com.smartdialer.data.Suggestion

/**
 * Pure logic for moving one bubble to another slot. Slots are rank positions 1..N, the same in every layout.
 * Pinned contacts occupy fixed slots, and no drop can target a pinned slot.
 */
object Reorder {

    sealed interface Result {
        /** Dropped on its own slot: nothing happens. */
        data object NoOp : Result

        /** The target slot belongs to a pinned contact. */
        data object Blocked : Result

        /** An unpinned contact moved: [order] is the whole list afterwards (pinned contacts at their fixed slots). */
        class Moved(val order: List<String>) : Result

        /** A pinned contact moved: its pin is now at [newSlot]; [order] is the list afterwards. */
        class PinMoved(val contactKey: String, val newSlot: Int, val order: List<String>) : Result
    }

    /**
     * Moves the contact at slot [from] to slot [to] in [order] (slot = index + 1). [pinned] maps pinned contacts
     * to their slots.
     *
     * - An unpinned contact: take the sequence of unpinned contacts in order, move the dragged one to the target's
     *   position in that sequence (those in between shift by one, skipping pinned slots), then put the pinned
     *   contacts back at their fixed slots.
     * - A pinned contact dropped on an unpinned slot moves its pin; the unpinned contacts keep their order and
     *   fill the remaining slots.
     */
    fun move(order: List<String>, pinned: Map<String, Int>, from: Int, to: Int): Result {
        val n = order.size
        if (from !in 1..n || to !in 1..n) return Result.NoOp
        if (from == to) return Result.NoOp
        val pinnedSlots = order.indices.filter { order[it] in pinned }.map { it + 1 }.toSet()
        if (to in pinnedSlots) return Result.Blocked

        val dragged = order[from - 1]
        if (dragged in pinned) {
            val newPins = pinnedSlots.associateWith { slot -> order[slot - 1] }.toMutableMap()
            newPins.remove(from)
            newPins[to] = dragged
            val sequence = order.filter { it !in pinned }
            return Result.PinMoved(dragged, to, build(n, newPins, sequence))
        }

        val unpinnedSlots = (1..n).filter { it !in pinnedSlots }
        val sequence = unpinnedSlots.map { order[it - 1] }.toMutableList()
        val fromIndex = unpinnedSlots.indexOf(from)
        val toIndex = unpinnedSlots.indexOf(to)
        sequence.removeAt(fromIndex)
        sequence.add(toIndex, dragged)
        return Result.Moved(build(n, pinnedSlots.associateWith { order[it - 1] }, sequence))
    }

    /** Pinned contacts at their slots, the [sequence] filling every other slot in order. */
    private fun build(n: Int, pinnedAt: Map<Int, String>, sequence: List<String>): List<String> {
        val rest = sequence.iterator()
        return (1..n).map { slot -> pinnedAt[slot] ?: rest.next() }
    }
}

/** A temporary manual arrangement, applied to the ranked list before the hard rules. */
object ManualOrder {
    /**
     * Contacts named in [order] come first in that order; contacts it does not mention follow in their ranked
     * order. Names that are not in [ranked] are ignored. A null or empty [order] changes nothing.
     */
    fun apply(ranked: List<Suggestion>, order: List<String>?): List<Suggestion> {
        if (order.isNullOrEmpty()) return ranked
        val byId = ranked.associateBy { it.contact.id }
        val first = order.distinct().mapNotNull(byId::get)
        val named = first.map { it.contact.id }.toSet()
        return first + ranked.filter { it.contact.id !in named }
    }
}
