package com.smartdialer.data

sealed interface NumberChoice {
    /** The contact has no usable phone number. */
    data object None : NumberChoice

    /** One number clearly wins; use it directly. */
    data class Use(val number: String) : NumberChoice

    /** No number clearly wins; ask the user. Candidates are ordered most recently used first. */
    data class Pick(val candidates: List<String>) : NumberChoice
}

/** Chooses which of a contact's phone numbers to call or message. */
object NumberChooser {

    /**
     * - No numbers: [NumberChoice.None]. Numbers that are the same by [CallMatcher.numberKey] count once.
     * - One number: use it.
     * - Several: the number used most recently in the call log wins, but only if it is strictly more
     *   recent than every other number. If no number appears in the call log, or two numbers share the
     *   newest timestamp, nothing clearly wins and the user picks.
     */
    fun choose(numbers: List<String>, lastUsedByNumberKey: Map<String, Long>): NumberChoice {
        val unique = numbers.filter { CallMatcher.numberKey(it) != null }.distinctBy { CallMatcher.numberKey(it) }
        if (unique.isEmpty()) return NumberChoice.None
        if (unique.size == 1) return NumberChoice.Use(unique.single())

        fun lastUsed(number: String): Long? = CallMatcher.numberKey(number)?.let { lastUsedByNumberKey[it] }

        val used = unique.mapNotNull { n -> lastUsed(n)?.let { n to it } }.sortedByDescending { it.second }
        val newest = used.firstOrNull()
        if (newest != null && (used.size == 1 || used[1].second < newest.second)) {
            return NumberChoice.Use(newest.first)
        }
        val unused = unique.filter { lastUsed(it) == null }
        return NumberChoice.Pick(used.map { it.first } + unused)
    }
}
