package com.nextup.alarmcountdown

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.nextup.alarmcountdown.notification.AlarmNotificationManager
import com.nextup.alarmcountdown.util.NextUpLog
import com.nextup.alarmcountdown.widget.NextUpWidgetProvider

class NextUpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NextUpLog.init(this)
        NextUpLog.i("App", "NextUpApplication initialized")

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                NextUpLog.d("App", "Dynamic screen event: ${intent.action}")
                AlarmNotificationManager.updateNotification(context)
                NextUpWidgetProvider.updateAllWidgets(context)
            }
        }, screenFilter)
    }
}
