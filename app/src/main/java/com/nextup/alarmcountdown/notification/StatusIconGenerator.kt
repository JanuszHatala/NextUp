package com.nextup.alarmcountdown.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat
import com.nextup.alarmcountdown.R
import com.nextup.alarmcountdown.util.AlarmFormatter

object StatusIconGenerator {

    /**
     * Generates a status bar icon.
     * If showStatusBarInfo is true, generates a clean compact badge with the remaining hours or minutes (e.g. "2h", "45m", "1d").
     * If false or not applicable, returns the standard alarm bell icon.
     */
    fun createStatusIcon(
        context: Context,
        triggerTimeMillis: Long?,
        showStatusBarInfo: Boolean
    ): IconCompat {
        if (!showStatusBarInfo || triggerTimeMillis == null || triggerTimeMillis <= 0L) {
            return IconCompat.createWithResource(context, R.drawable.ic_stat_alarm)
        }

        val remainingMillis = triggerTimeMillis - System.currentTimeMillis()
        if (remainingMillis <= 0L) {
            return IconCompat.createWithResource(context, R.drawable.ic_stat_alarm)
        }

        val totalMinutes = remainingMillis / 60_000L
        val textToDraw: String = when {
            totalMinutes < 60 -> "${totalMinutes.coerceAtLeast(1)}m"
            totalMinutes < 24 * 60 -> "${totalMinutes / 60}h"
            else -> "${totalMinutes / (24 * 60)}d"
        }

        return try {
            val bitmap = createBadgeBitmap(textToDraw)
            IconCompat.createWithBitmap(bitmap)
        } catch (e: Exception) {
            IconCompat.createWithResource(context, R.drawable.ic_stat_alarm)
        }
    }

    private fun createBadgeBitmap(text: String): Bitmap {
        // Status bar icons on Android are typically 24x24dp or 48x48px (or 64x64px for density)
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = when (text.length) {
                1 -> 42f
                2 -> 36f
                3 -> 30f
                else -> 26f
            }
        }

        val xPos = canvas.width / 2f
        val yPos = (canvas.height / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(text, xPos, yPos, paint)

        return bitmap
    }
}
