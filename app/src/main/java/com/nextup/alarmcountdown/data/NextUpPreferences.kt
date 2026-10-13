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
        private const val KEY_LEARNING_START_EPOCH = "learning_start_epoch"
        private const val KEY_IS_LEARNING_STOPPED = "is_learning_stopped"
        const val LEARNING_PERIOD_MILLIS = 7 * 24 * 60 * 60 * 1000L

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

    private val _isLearningPeriodFlow =
        MutableStateFlow(computeIsLearningPeriod())
    val isLearningPeriodFlow: StateFlow<Boolean> = _isLearningPeriodFlow.asStateFlow()

    private val _learningDayNumberFlow =
        MutableStateFlow(computeLearningDayNumber())
    val learningDayNumberFlow: StateFlow<Int> = _learningDayNumberFlow.asStateFlow()

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

    var learningStartEpoch: Long
        get() {
            var start = prefs.getLong(KEY_LEARNING_START_EPOCH, 0L)
            if (start <= 0L) {
                start = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LEARNING_START_EPOCH, start).apply()
            }
            return start
        }
        set(value) {
            prefs.edit().putLong(KEY_LEARNING_START_EPOCH, value).apply()
            updateLearningFlows()
        }

    var isLearningStopped: Boolean
        get() = prefs.getBoolean(KEY_IS_LEARNING_STOPPED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_IS_LEARNING_STOPPED, value).apply()
            updateLearningFlows()
        }

    val isLearningPeriod: Boolean
        get() = computeIsLearningPeriod()

    val learningDayNumber: Int
        get() = computeLearningDayNumber()

    private fun computeIsLearningPeriod(): Boolean {
        if (prefs.getBoolean(KEY_IS_LEARNING_STOPPED, false)) return false
        val start = prefs.getLong(KEY_LEARNING_START_EPOCH, 0L)
        if (start <= 0L) return true
        val elapsed = System.currentTimeMillis() - start
        return elapsed in 0 until LEARNING_PERIOD_MILLIS
    }

    private fun computeLearningDayNumber(): Int {
        val start = prefs.getLong(KEY_LEARNING_START_EPOCH, 0L)
        if (start <= 0L) return 1
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(0L)
        val day = (elapsed / (24 * 60 * 60 * 1000L)) + 1
        return day.coerceIn(1L, 7L).toInt()
    }

    private fun updateLearningFlows() {
        _isLearningPeriodFlow.value = computeIsLearningPeriod()
        _learningDayNumberFlow.value = computeLearningDayNumber()
    }

    fun restartLearningPeriod() {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong(KEY_LEARNING_START_EPOCH, now)
            .putBoolean(KEY_IS_LEARNING_STOPPED, false)
            .apply()
        updateLearningFlows()
    }

    fun stopLearningPeriod() {
        prefs.edit()
            .putBoolean(KEY_IS_LEARNING_STOPPED, true)
            .apply()
        updateLearningFlows()
    }

    fun resetLearningPeriod() {
        restartLearningPeriod()
    }

    fun syncFromDisk() {
        _isNotificationEnabledFlow.value = prefs.getBoolean(KEY_PERMANENT_NOTIFICATION_ENABLED, false)
        _isStatusBarInfoEnabledFlow.value = prefs.getBoolean(KEY_STATUS_BAR_INFO_ENABLED, true)
        val alignment = prefs.getString(KEY_WIDGET_ALIGNMENT, ALIGNMENT_LEFT) ?: ALIGNMENT_LEFT
        _widgetAlignmentFlow.value = alignment
        updateLearningFlows()
        com.nextup.alarmcountdown.util.NextUpLog.d("Prefs", "syncFromDisk: widgetAlignment=$alignment")
    }
}
