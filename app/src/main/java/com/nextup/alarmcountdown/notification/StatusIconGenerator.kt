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

        val padding = 3f
        val maxTargetW = size - (2f * padding)
        val maxTargetH = size - (2f * padding)

        val digits = text.filter { it.isDigit() }
        val unit = text.filter { !it.isDigit() }

        if (digits.length >= 2 && unit.isNotEmpty()) {
            // For 2+ digits (e.g. "18m", "45m", "12h"), render the digits as hero elements
            // and the unit as a compact superscript top-right so the digits match the large
            // visibility of single-digit variants like "7h".
            val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.LEFT
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            }
            val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.LEFT
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                textScaleX = 0.85f
            }

            val numBounds = Rect()
            val unitBounds = Rect()
            val probeNumSize = 100f
            val probeUnitSize = 40f
            numPaint.textSize = probeNumSize
            unitPaint.textSize = probeUnitSize

            numPaint.getTextBounds(digits, 0, digits.length, numBounds)
            unitPaint.getTextBounds(unit, 0, unit.length, unitBounds)

            val spacing = 2f
            val totalProbeW = numBounds.width() + spacing + unitBounds.width()
            val scaleW = maxTargetW / totalProbeW.coerceAtLeast(1f)
            val scaleH = maxTargetH / numBounds.height().coerceAtLeast(1).toFloat()
            var targetNumSize = probeNumSize * minOf(scaleW, scaleH)
            var targetUnitSize = targetNumSize * 0.40f

            numPaint.textSize = targetNumSize
            unitPaint.textSize = targetUnitSize

            numPaint.getTextBounds(digits, 0, digits.length, numBounds)
            unitPaint.getTextBounds(unit, 0, unit.length, unitBounds)

            while ((numBounds.width() + spacing + unitBounds.width() > maxTargetW ||
                    numBounds.height() > maxTargetH) && numPaint.textSize > 12f) {
                targetNumSize -= 1f
                targetUnitSize = targetNumSize * 0.40f
                numPaint.textSize = targetNumSize
                unitPaint.textSize = targetUnitSize
                numPaint.getTextBounds(digits, 0, digits.length, numBounds)
                unitPaint.getTextBounds(unit, 0, unit.length, unitBounds)
            }

            val totalW = numBounds.width() + spacing + unitBounds.width()
            val startX = (size - totalW) / 2f
            val numX = startX - numBounds.left
            val numY = (size / 2f) - numBounds.exactCenterY()

            val unitX = startX + numBounds.width() + spacing - unitBounds.left
            // Align top of unit with top of digits
            val unitY = (numY + numBounds.top) - unitBounds.top

            canvas.drawText(digits, numX, numY, numPaint)
            canvas.drawText(unit, unitX, unitY, unitPaint)
        } else {
            // For 1-digit variants (e.g. "7h", "5m", "1d"), single line already fits at full height
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.LEFT
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            }

            val bounds = Rect()
            val probeSize = 100f
            paint.textSize = probeSize
            paint.getTextBounds(text, 0, text.length, bounds)

            val scaleX = maxTargetW / bounds.width().coerceAtLeast(1).toFloat()
            val scaleY = maxTargetH / bounds.height().coerceAtLeast(1).toFloat()
            var targetTextSize = probeSize * minOf(scaleX, scaleY)

            paint.textSize = targetTextSize
            paint.getTextBounds(text, 0, text.length, bounds)

            while ((bounds.width() > maxTargetW || bounds.height() > maxTargetH) && paint.textSize > 12f) {
                paint.textSize -= 1f
                paint.getTextBounds(text, 0, text.length, bounds)
            }

            val xPos = (size / 2f) - bounds.exactCenterX()
            val yPos = (size / 2f) - bounds.exactCenterY()
            canvas.drawText(text, xPos, yPos, paint)
        }

        return bitmap
    }
}
