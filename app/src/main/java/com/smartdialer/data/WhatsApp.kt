package com.smartdialer.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * One WhatsApp event: who, when, and which way. **Nothing else is ever stored** - no message text, no group,
 * no notification content. [outgoing] is true only for a message started from Orbit's WhatsApp action.
 */
data class WhatsAppEvent(val contactKey: String, val at: Long, val outgoing: Boolean)

interface WhatsAppEventStore {
    val events: Flow<List<WhatsAppEvent>>
    suspend fun all(): List<WhatsAppEvent>

    /** Stores one event and drops events older than [RETENTION_MILLIS]. */
    suspend fun record(event: WhatsAppEvent)
    suspend fun pruneOld(nowMillis: Long)
    suspend fun deleteAll()

    companion object {
        /** Events are kept for 90 days. */
        const val RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1000
    }
}

class InMemoryWhatsAppEventStore : WhatsAppEventStore {
    private val state = MutableStateFlow<List<WhatsAppEvent>>(emptyList())
    override val events: Flow<List<WhatsAppEvent>> = state.asStateFlow()
    override suspend fun all() = state.value
    override suspend fun record(event: WhatsAppEvent) {
        state.update { it + event }
    }

    override suspend fun pruneOld(nowMillis: Long) {
        state.update { list -> list.filter { it.at >= nowMillis - WhatsAppEventStore.RETENTION_MILLIS } }
    }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}

data class WhatsAppSettingsState(
    /** The "WhatsApp signals" switch. On by default, but nothing happens until notification access is granted. */
    val enabled: Boolean = true,
    /** True once the one-time prompt after the update was answered (either button). */
    val promptHandled: Boolean = false,
)

class WhatsAppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("orbit_whatsapp", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<WhatsAppSettingsState> = _state.asStateFlow()

    private fun read() = WhatsAppSettingsState(prefs.getBoolean("enabled", true), prefs.getBoolean("prompt_handled", false))

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
        _state.value = read()
    }

    fun markPromptHandled() {
        prefs.edit().putBoolean("prompt_handled", true).apply()
        _state.value = read()
    }
}

/** What the listener may know about a notification. Never includes its text. */
class NotificationFacts(
    val category: String?,
    val isGroupSummary: Boolean,
    /** The system's group flag (MessagingStyle), when present. */
    val isGroupConversation: Boolean,
    /** A conversation title is set only for group chats. */
    val hasConversationTitle: Boolean,
)

/** Decides which WhatsApp notifications are worth a sender lookup. Pure. */
object WhatsAppNotificationRules {
    fun accept(facts: NotificationFacts): Boolean =
        !facts.isGroupSummary &&
            !facts.isGroupConversation &&
            !facts.hasConversationTitle &&
            (facts.category == null || facts.category == "msg")
}

sealed interface SenderMatch {
    class Found(val contactKey: String) : SenderMatch
    data object Ambiguous : SenderMatch
    data object None : SenderMatch
}

/** Matches the sender shown on a WhatsApp notification to a device contact. It never guesses. */
object WhatsAppSenderMatcher {

    /**
     * 1. A sender that is a phone number matches the contact that has that number (by [CallMatcher.numberKey]).
     * 2. Otherwise an exact match of the normalized name.
     * 3. Otherwise an unambiguous prefix: the sender is the first word(s) of exactly one contact's name.
     * Several candidates at any step mean [SenderMatch.Ambiguous]; nothing found means [SenderMatch.None].
     * Titles like "Family: Mom" (group senders) match nothing and are dropped.
     */
    fun match(sender: String, contacts: List<DeviceContact>): SenderMatch {
        val text = sender.trim()
        if (text.isEmpty()) return SenderMatch.None

        if (looksLikePhoneNumber(text)) {
            val key = CallMatcher.numberKey(text) ?: return SenderMatch.None
            return pick(contacts.filter { c -> c.numbers.any { CallMatcher.numberKey(it) == key } })
        }

        val wanted = normalize(text)
        if (wanted.isEmpty()) return SenderMatch.None
        val exact = contacts.filter { normalize(it.contact.name) == wanted }
        if (exact.isNotEmpty()) return pick(exact)
        return pick(contacts.filter { normalize(it.contact.name).startsWith("$wanted ") })
    }

    private fun pick(found: List<DeviceContact>): SenderMatch {
        val keys = found.map { it.contact.id }.distinct()
        return when {
            keys.isEmpty() -> SenderMatch.None
            keys.size == 1 -> SenderMatch.Found(keys.single())
            else -> SenderMatch.Ambiguous
        }
    }

    private fun looksLikePhoneNumber(text: String): Boolean {
        val digits = text.count { it.isDigit() }
        return digits >= 7 && text.all { it.isDigit() || it in "+-()  ‪‬" }
    }

    private fun normalize(text: String) =
        text.trim().trimStart('~').trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}

/** Folds WhatsApp events into the per-contact signals. Pure. */
object WhatsAppSignals {
    private const val DAY = 24L * 60 * 60 * 1000

    fun merge(base: Map<String, ContactActivity>, events: List<WhatsAppEvent>, nowMillis: Long): Map<String, ContactActivity> {
        if (events.isEmpty()) return base
        val result = HashMap(base)
        events.groupBy { it.contactKey }.forEach { (key, list) ->
            val incoming = list.filter { !it.outgoing }
            val facts = (base[key]?.signals ?: ContactSignals(null, 0, null, 0)).copy(
                lastWhatsAppIncomingAt = incoming.maxOfOrNull { it.at },
                lastWhatsAppOutgoingFromOrbitAt = list.filter { it.outgoing }.maxOfOrNull { it.at },
                whatsAppIncoming7d = incoming.count { it.at >= nowMillis - 7 * DAY },
                whatsAppIncoming30d = incoming.count { it.at >= nowMillis - 30 * DAY },
            )
            result[key] = ContactActivity(facts, base[key]?.lastUsedByNumberKey.orEmpty())
        }
        return result
    }
}
