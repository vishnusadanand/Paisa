package com.visa.paisa

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.visa.paisa.data.Repository

class PaisaApp : Application() {

    val repository: Repository by lazy { Repository(this) }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REVIEW,
                "Needs a category",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Quiet nudge when a new expense could not be categorized automatically."
            },
        )
    }

    companion object {
        const val CHANNEL_REVIEW = "review"

        fun repo(context: Context): Repository =
            (context.applicationContext as PaisaApp).repository
    }
}
