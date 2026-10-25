package com.smartdialer.llm

import java.time.LocalDate

/**
 * Why a ranking refresh was asked for. Every trigger counts against the daily cap.
 * - [INITIAL]: first run with no cached ranking.
 * - [SCHEDULED]: the twice-a-day background job.
 * - [INSTRUCTION]: an instruction that affects the LLM input was added, edited or deleted.
 * - [MANUAL]: swipe down on the bubbles, or "Refresh now" in Settings.
 */
enum class RefreshTrigger { INITIAL, SCHEDULED, INSTRUCTION, MANUAL }

/** The daily cap on ranking refreshes, per local calendar day (it resets at local midnight). Pure. */
object RefreshQuota {
    const val DAILY_CAP = 20

    fun dayKey(today: LocalDate): String = today.toString()

    /** How many refreshes were started on [today]. A count kept from another day is 0. */
    fun countToday(state: AiSettingsState, today: LocalDate): Int =
        if (state.refreshDay == dayKey(today)) state.refreshCount else 0

    fun canStart(state: AiSettingsState, today: LocalDate, cap: Int = DAILY_CAP): Boolean = countToday(state, today) < cap
}

/**
 * Makes sure ranking refreshes never run in parallel and no request is lost: while one is running, another
 * trigger queues exactly one follow-up (however many arrive), which runs when the current one finishes. A manual
 * pull while one is running is ignored. Thread-safe and pure of any I/O.
 */
class RefreshGate {
    enum class Start { STARTED, QUEUED, IGNORED }

    private val lock = Any()
    private var running = false
    private var pending = false

    fun tryStart(trigger: RefreshTrigger): Start = synchronized(lock) {
        when {
            !running -> { running = true; Start.STARTED }
            trigger == RefreshTrigger.MANUAL -> Start.IGNORED
            else -> { pending = true; Start.QUEUED }
        }
    }

    /** Call when a run is done: true if a queued follow-up should run now (the gate stays busy), false if idle again. */
    fun nextOrFinish(): Boolean = synchronized(lock) {
        if (pending) {
            pending = false
            true
        } else {
            running = false
            false
        }
    }

    /** Forgets everything (used when a run ends abnormally, so the gate cannot stay stuck). */
    fun reset() = synchronized(lock) {
        running = false
        pending = false
    }

    val isRunning: Boolean get() = synchronized(lock) { running }
}
