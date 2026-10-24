package com.smartdialer.llm

import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.FreeTextStatus

/** Limits that keep requests small and predictable. */
object InstructionLimits {
    /** Longest instruction that can be saved. */
    const val MAX_LENGTH = 300

    /** At most this many instructions (the most recent ones) go into one ranking request. */
    const val MAX_PER_REQUEST = 30

    /** Longest contact name sent. */
    const val MAX_NAME_LENGTH = 60

    /** Largest user message for a ranking request; contacts at the end of the list are left out beyond it. */
    const val MAX_PAYLOAD_CHARS = 120_000

    const val MAX_REASON_LENGTH = 120

    const val OFF_TOPIC_MESSAGE = "This doesn't affect who to call, so Orbit ignores it."

    /** Instructions that may be sent to the ranking model: classified as soft (or waiting for review), not off-topic. */
    fun eligible(rules: List<FreeTextRule>): List<FreeTextRule> =
        rules.filter { it.status == FreeTextStatus.SOFT || it.status == FreeTextStatus.NEEDS_REVIEW }

    /** The most recent [MAX_PER_REQUEST] eligible instructions, oldest first. */
    fun selectForRanking(rules: List<FreeTextRule>): List<FreeTextRule> =
        eligible(rules).sortedWith(compareByDescending<FreeTextRule> { it.createdAt }.thenByDescending { it.id })
            .take(MAX_PER_REQUEST)
            .sortedWith(compareBy({ it.createdAt }, { it.id }))

    /** How many eligible instructions are kept but not sent because of [MAX_PER_REQUEST]. */
    fun unusedCount(rules: List<FreeTextRule>): Int = (eligible(rules).size - MAX_PER_REQUEST).coerceAtLeast(0)
}

/**
 * Cleans text that came from the user or the model. Everything the user wrote (instructions, contact names)
 * is DATA for the model, never instructions; cleaning removes characters that could hide or reshape text.
 */
object Sanitizer {

    /** Drops control, invisible-format (zero-width, bidi override), private-use and unassigned characters; whitespace becomes single spaces. */
    fun plain(text: String): String {
        val out = StringBuilder(text.length)
        var lastSpace = true
        text.codePoints().forEach { cp ->
            val type = Character.getType(cp).toByte()
            val blank = cp == '\n'.code || cp == '\r'.code || cp == '\t'.code || Character.isWhitespace(cp) || Character.isSpaceChar(cp)
            when {
                blank -> if (!lastSpace) { out.append(' '); lastSpace = true }
                type == Character.CONTROL || type == Character.FORMAT || type == Character.PRIVATE_USE ||
                    type == Character.UNASSIGNED || type == Character.SURROGATE -> Unit
                else -> { out.appendCodePoint(cp); lastSpace = false }
            }
        }
        return out.toString().trim()
    }

    fun cleanName(text: String): String = plain(text).take(InstructionLimits.MAX_NAME_LENGTH)

    fun cleanInstruction(text: String): String = plain(text).take(InstructionLimits.MAX_LENGTH)

    private val markdownLink = Regex("\\[([^\\]]*)]\\([^)]*\\)")
    private val url = Regex("(?i)\\b[a-z][a-z0-9+.-]*://\\S+|\\bwww\\.\\S+|\\b[a-z][a-z0-9+.-]*:\\S*//\\S+")
    private val tag = Regex("<[^>]*>?")
    private val markup = Regex("[*_`#<>\\[\\]{}|\\\\~^]")

    /**
     * Turns model text into a plain one-line reason: no markup, no URLs, no control characters, at most
     * [max] characters. Returns null when nothing readable is left, so the caller uses the template reason.
     */
    fun cleanReason(text: String?, max: Int = InstructionLimits.MAX_REASON_LENGTH): String? {
        if (text == null) return null
        var s = markdownLink.replace(text, "$1")
        s = tag.replace(s, " ")
        s = url.replace(s, " ")
        s = markup.replace(s, " ")
        s = plain(s).replace(Regex("\\s+([.,!?;:])"), "$1")
        if (s.none { it.isLetterOrDigit() }) return null
        if (s.length > max) s = s.take(max - 1).trimEnd() + "…"
        return s
    }
}
