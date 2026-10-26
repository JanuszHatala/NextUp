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

        val systemFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        val dynamicReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                NextUpLog.d("App", "Dynamic system event: ${intent.action}")
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        com.nextup.alarmcountdown.data.NextUpPreferences.getInstance(context).syncFromDisk()
                        AlarmNotificationManager.updateNotification(context)
                        NextUpWidgetProvider.updateAllWidgets(context)
                        com.nextup.alarmcountdown.scheduler.AlarmRefreshScheduler.scheduleNextRefresh(context)
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        // When screen turns off, skip UI re-renders and notification posts.
                        // Only ensure the scheduler maintains the non-wakeup RTC minute refresh.
                        com.nextup.alarmcountdown.scheduler.AlarmRefreshScheduler.scheduleNextRefresh(context)
                    }
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dynamicReceiver, systemFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(dynamicReceiver, systemFilter)
        }

        com.nextup.alarmcountdown.scheduler.AlarmRefreshScheduler.scheduleNextRefresh(this)
    }
}
