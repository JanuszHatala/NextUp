package com.nextup.alarmcountdown.data

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.nextup.alarmcountdown.data.AlarmModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AlarmRepository(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Retrieves the current next alarm clock set on the device.
     */
    fun getNextAlarm(): AlarmModel? {
        val nextClock = alarmManager.nextAlarmClock ?: return null
        return AlarmModel(
            triggerTimeMillis = nextClock.triggerTime,
            showIntent = nextClock.showIntent
        )
    }

    /**
     * Observes changes to the next alarm clock reactively.
     */
    fun observeNextAlarm(): Flow<AlarmModel?> = callbackFlow {
        // Send initial state
        trySend(getNextAlarm())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(getNextAlarm())
            }
        }

        val filter = IntentFilter().apply {
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (ignored: Exception) {
            }
        }
    }
}
