package com.nextup.alarmcountdown.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
        // Status bar icons on Android are rendered in a 24x24dp slot.
        // Render to 96x96px (4x density for xxxhdpi screens) to maximize sharpness and prevent blur.
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            if (text.length >= 3) {
                // Slight horizontal compression to maximize vertical height for 3+ chars (e.g. "45m")
                textScaleX = 0.90f
            }
        }

        val padding = 3f
        val maxTargetW = size - (2f * padding)
        val maxTargetH = size - (2f * padding)

        val bounds = Rect()
        val probeSize = 100f
        paint.textSize = probeSize
        paint.getTextBounds(text, 0, text.length, bounds)

        val scaleX = maxTargetW / bounds.width().coerceAtLeast(1).toFloat()
        val scaleY = maxTargetH / bounds.height().coerceAtLeast(1).toFloat()
        var targetTextSize = probeSize * minOf(scaleX, scaleY)

        paint.textSize = targetTextSize
        paint.getTextBounds(text, 0, text.length, bounds)

        // Safety adjustment in case hinting slightly exceeds bounds
        while ((bounds.width() > maxTargetW || bounds.height() > maxTargetH) && paint.textSize > 12f) {
            paint.textSize -= 1f
            paint.getTextBounds(text, 0, text.length, bounds)
        }

        // Center exact ink glyph bounds within the square canvas
        val xPos = (size / 2f) - bounds.exactCenterX()
        val yPos = (size / 2f) - bounds.exactCenterY()
        canvas.drawText(text, xPos, yPos, paint)

        return bitmap
    }
}
