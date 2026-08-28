package com.visa.paisa.sms

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.visa.paisa.MainActivity
import com.visa.paisa.PaisaApp
import com.visa.paisa.R
import com.visa.paisa.data.IngestOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires on every incoming SMS. Almost all of them are not transactions; the parser
 * discards those in microseconds. The database write happens on a goAsync() token so
 * the broadcast is not killed halfway through.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // A long alert arrives as several parts of one logical message.
        val body = messages.joinToString("") { it.displayMessageBody.orEmpty() }
        if (body.isBlank()) return
        val sender = messages.first().displayOriginatingAddress
        val timestamp = messages.first().timestampMillis.takeIf { it > 0 }
            ?: System.currentTimeMillis()

        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                val outcome = PaisaApp.repo(appContext).ingest(body, sender, timestamp)
                if (outcome == IngestOutcome.ADDED_NEEDS_REVIEW) {
                    notifyReview(appContext, PaisaApp.repo(appContext).uncategorizedCountNow())
                }
            } catch (e: Exception) {
                // Never let a malformed message crash the SMS pipeline.
            } finally {
                pending.finish()
            }
        }
    }

    private fun notifyReview(context: Context, pendingCount: Int) {
        if (pendingCount <= 0) return

        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_REVIEW, true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = Notification.Builder(context, PaisaApp.CHANNEL_REVIEW)
            .setSmallIcon(R.drawable.ic_stat_paisa)
            .setContentTitle("New expense needs a category")
            .setContentText(
                if (pendingCount == 1) "1 transaction is waiting for you"
                else "$pendingCount transactions are waiting for you",
            )
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
