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
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        val dynamicReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                NextUpLog.d("App", "Dynamic system event: ${intent.action}")
                com.nextup.alarmcountdown.data.NextUpPreferences.getInstance(context).syncFromDisk()
                AlarmNotificationManager.updateNotification(context)
                NextUpWidgetProvider.updateAllWidgets(context)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dynamicReceiver, systemFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(dynamicReceiver, systemFilter)
        }
    }
}
