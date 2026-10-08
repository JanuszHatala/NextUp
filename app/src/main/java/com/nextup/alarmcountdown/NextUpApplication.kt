package com.nextup.alarmcountdown

import android.app.Application
import com.nextup.alarmcountdown.util.NextUpLog

class NextUpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NextUpLog.init(this)
        NextUpLog.i("App", "NextUpApplication initialized")
    }
}
