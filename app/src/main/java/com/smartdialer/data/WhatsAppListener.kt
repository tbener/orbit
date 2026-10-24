package com.smartdialer.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.smartdialer.OrbitApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** True when the user has given Orbit notification access in the system settings. */
fun hasNotificationAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/**
 * Listens for WhatsApp message notifications and records who messaged and when, nothing else. It reads only
 * the sender title of notifications from WhatsApp and WhatsApp Business; it never reads, stores, logs or
 * sends message text, and ignores every other app. Group chats, summaries and anything it cannot match to
 * exactly one contact are dropped. It does no work between notifications, so it costs no battery.
 */
class WhatsAppNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onListenerConnected() {
        val container = (application as OrbitApplication).container
        scope.launch { container.whatsAppStore.pruneOld(System.currentTimeMillis()) }
    }

    /** The system may drop the connection (for example after an update); ask to be bound again. */
    override fun onListenerDisconnected() {
        requestRebind(ComponentName(this, WhatsAppNotificationListener::class.java))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in PACKAGES) return
        val container = (application as OrbitApplication).container
        if (!container.whatsAppSettings.state.value.enabled) return

        val notification = sbn.notification
        val extras = notification.extras
        val facts = NotificationFacts(
            category = notification.category,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isGroupConversation = extras.getBoolean("android.isGroupConversation", false),
            hasConversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) != null,
        )
        if (!WhatsAppNotificationRules.accept(facts)) return
        // Only the sender title is read. The message text is never touched.
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val at = sbn.postTime

        scope.launch {
            if (container.contactsRepository.contacts.value == null) container.contactsRepository.reload()
            val contacts = container.contactsRepository.contacts.value ?: return@launch
            val match = WhatsAppSenderMatcher.match(sender, contacts) as? SenderMatch.Found ?: return@launch
            // WhatsApp re-posts a notification as a chat updates; one event per contact per few seconds is enough.
            val last = lastRecorded[match.contactKey]
            if (last != null && at - last < DEDUP_MILLIS) return@launch
            lastRecorded[match.contactKey] = at
            container.whatsAppStore.record(WhatsAppEvent(match.contactKey, at, outgoing = false))
        }
    }

    companion object {
        /** WhatsApp and WhatsApp Business (the Play Store package ids). */
        val PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private const val DEDUP_MILLIS = 3_000L
        private val lastRecorded = java.util.concurrent.ConcurrentHashMap<String, Long>()
    }
}
