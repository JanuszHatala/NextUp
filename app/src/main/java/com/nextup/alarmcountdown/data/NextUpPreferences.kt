package com.nextup.alarmcountdown.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NextUpPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "nextup_preferences"
        private const val KEY_PERMANENT_NOTIFICATION_ENABLED = "permanent_notification_enabled"
        private const val KEY_STATUS_BAR_INFO_ENABLED = "status_bar_info_enabled"
        const val KEY_WIDGET_ALIGNMENT = "widget_alignment"
        const val ALIGNMENT_LEFT = "left"
        const val ALIGNMENT_CENTER = "center"

        @Volatile
        private var INSTANCE: NextUpPreferences? = null

        fun getInstance(context: Context): NextUpPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NextUpPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val _isNotificationEnabledFlow =
        MutableStateFlow(prefs.getBoolean(KEY_PERMANENT_NOTIFICATION_ENABLED, false))
    val isNotificationEnabledFlow: StateFlow<Boolean> = _isNotificationEnabledFlow.asStateFlow()

    private val _isStatusBarInfoEnabledFlow =
        MutableStateFlow(prefs.getBoolean(KEY_STATUS_BAR_INFO_ENABLED, true))
    val isStatusBarInfoEnabledFlow: StateFlow<Boolean> = _isStatusBarInfoEnabledFlow.asStateFlow()

    private val _widgetAlignmentFlow =
        MutableStateFlow(prefs.getString(KEY_WIDGET_ALIGNMENT, ALIGNMENT_LEFT) ?: ALIGNMENT_LEFT)
    val widgetAlignmentFlow: StateFlow<String> = _widgetAlignmentFlow.asStateFlow()

    var isNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERMANENT_NOTIFICATION_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PERMANENT_NOTIFICATION_ENABLED, value).apply()
            _isNotificationEnabledFlow.value = value
        }

    var isStatusBarInfoEnabled: Boolean
        get() = prefs.getBoolean(KEY_STATUS_BAR_INFO_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_STATUS_BAR_INFO_ENABLED, value).apply()
            _isStatusBarInfoEnabledFlow.value = value
        }

    var widgetAlignment: String
        get() = prefs.getString(KEY_WIDGET_ALIGNMENT, ALIGNMENT_LEFT) ?: ALIGNMENT_LEFT
        set(value) {
            com.nextup.alarmcountdown.util.NextUpLog.i("Prefs", "widgetAlignment changed: old=${_widgetAlignmentFlow.value} -> new=$value")
            prefs.edit().putString(KEY_WIDGET_ALIGNMENT, value).commit()
            _widgetAlignmentFlow.value = value
        }

    fun syncFromDisk() {
        _isNotificationEnabledFlow.value = prefs.getBoolean(KEY_PERMANENT_NOTIFICATION_ENABLED, false)
        _isStatusBarInfoEnabledFlow.value = prefs.getBoolean(KEY_STATUS_BAR_INFO_ENABLED, true)
        val alignment = prefs.getString(KEY_WIDGET_ALIGNMENT, ALIGNMENT_LEFT) ?: ALIGNMENT_LEFT
        _widgetAlignmentFlow.value = alignment
        com.nextup.alarmcountdown.util.NextUpLog.d("Prefs", "syncFromDisk: widgetAlignment=$alignment")
    }
}
