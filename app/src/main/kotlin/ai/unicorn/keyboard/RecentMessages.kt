/*
 * Copyright (C) 2026 Unicorn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.unicorn.keyboard

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * The last messages of recent conversations, taken from chat and email notifications, so Ask Unicorn's /reply can
 * answer the conversation the user is in. Keys can't read another app's screen; notifications are the one place
 * Android shares the messages, and only after the user allows it in system settings. Kept in memory only, for a few
 * hours, and only for the apps below. Nothing leaves the phone until the user asks for a reply.
 */
object RecentMessages {
    private const val KEEP_MS = 6 * 60 * 60 * 1000L
    private const val MAX_MESSAGES = 12
    private const val MAX_CONVERSATIONS = 30

    /** Chat and email apps whose message notifications are kept. */
    private val Apps = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.facebook.orca",
        "com.instagram.android", "com.discord", "com.Slack", "com.microsoft.teams", "jp.naver.line.android",
        "com.viber.voip", "com.google.android.apps.dynamite", "com.linkedin.android",
        "com.google.android.gm", "com.microsoft.office.outlook", "com.samsung.android.email.provider",
        "com.yahoo.mobile.client.android.mail", "ch.protonmail.android", "me.proton.android.mail",
    )

    data class Message(val sender: String, val text: String, val at: Long)

    data class Conversation(
        val packageName: String,
        val title: String,
        val messages: List<Message>,
        val isEmail: Boolean,
        val updated: Long,
    ) {
        /** The thread as the AI reads it: "Priya: can we meet Thursday?" per line, or an email. */
        fun asThread(): String = if (isEmail) {
            messages.joinToString("\n\n") { "From: ${it.sender}\n${it.text}" }
        } else {
            messages.joinToString("\n") { "${it.sender}: ${it.text}" }
        }
    }

    private val conversations = LinkedHashMap<String, Conversation>()

    fun isAllowed(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** The system screen where the user allows Unicorn to see notifications. */
    fun settingsIntent(context: Context): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, Listener::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun isMessagingApp(packageName: String?) = packageName in Apps

    /** Recent conversations in [packageName], newest first. */
    @Synchronized
    fun recent(packageName: String?, limit: Int = 4): List<Conversation> {
        prune()
        return conversations.values.filter { it.packageName == packageName }
            .sortedByDescending { it.updated }.take(limit)
    }

    @Synchronized
    fun clear() = conversations.clear()

    @Synchronized
    private fun put(conversation: Conversation) {
        val key = "${conversation.packageName}/${conversation.title}"
        val known = conversations.remove(key)
        // Notifications repeat earlier messages; merge so each message appears once
        val merged = (known?.messages.orEmpty() + conversation.messages)
            .distinctBy { it.sender to it.text }
            .sortedBy { it.at }
            .takeLast(MAX_MESSAGES)
        conversations[key] = conversation.copy(messages = merged)
        while (conversations.size > MAX_CONVERSATIONS) conversations.remove(conversations.keys.first())
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - KEEP_MS
        conversations.entries.removeAll { it.value.updated < cutoff }
    }

    internal fun read(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName !in Apps) return
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val isEmail = packageName.contains("mail") || packageName.contains("outlook") || packageName == "com.google.android.gm"
        val now = System.currentTimeMillis()
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        if (style != null && style.messages.isNotEmpty()) {
            // Apps leave some of these empty rather than null
            val title = style.conversationTitle?.toString()?.takeIf { it.isNotBlank() }
                ?: style.messages.lastOrNull { !it.person?.name.isNullOrBlank() }?.person?.name?.toString()
                ?: notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
                ?: return
            val messages = style.messages.mapNotNull { m ->
                val text = m.text?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                // A message without a person is the user's own
                Message(m.person?.name?.toString()?.takeIf { it.isNotBlank() } ?: "You", text, m.timestamp.takeIf { it > 0 } ?: now)
            }
            if (messages.isNotEmpty()) put(Conversation(packageName, title, messages, isEmail, now))
            return
        }
        val extras = notification.extras
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() } ?: return
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()?.takeIf { it.isNotBlank() } ?: return
        put(Conversation(packageName, sender, listOf(Message(sender, body, sbn.postTime)), isEmail, now))
    }

    /** Receives notifications once the user allows it in system settings. */
    class Listener : NotificationListenerService() {
        override fun onNotificationPosted(sbn: StatusBarNotification) {
            runCatching { read(sbn) }
        }

        override fun onListenerConnected() {
            // Pick up what is already in the shade
            runCatching { activeNotifications?.forEach { read(it) } }
        }
    }
}
