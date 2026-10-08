package com.nextup.alarmcountdown.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NextUpLog {
    private const val TAG = "NextUp"
    private const val MAX_LOG_SIZE_BYTES = 1024 * 1024 // 1 MB
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        if (logFile == null) {
            synchronized(this) {
                if (logFile == null) {
                    try {
                        val dir = context.applicationContext.filesDir
                        if (!dir.exists()) dir.mkdirs()
                        logFile = File(dir, "nextup_debug.log")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize NextUpLog file", e)
                    }
                }
            }
        }
    }

    fun d(tagSuffix: String, message: String) {
        val fullTag = "$TAG-$tagSuffix"
        Log.d(fullTag, message)
        writeDisk("DEBUG", fullTag, message)
    }

    fun i(tagSuffix: String, message: String) {
        val fullTag = "$TAG-$tagSuffix"
        Log.i(fullTag, message)
        writeDisk("INFO", fullTag, message)
    }

    fun w(tagSuffix: String, message: String, throwable: Throwable? = null) {
        val fullTag = "$TAG-$tagSuffix"
        Log.w(fullTag, message, throwable)
        writeDisk("WARN", fullTag, if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message)
    }

    fun e(tagSuffix: String, message: String, throwable: Throwable? = null) {
        val fullTag = "$TAG-$tagSuffix"
        Log.e(fullTag, message, throwable)
        writeDisk("ERROR", fullTag, if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message)
    }

    private fun writeDisk(level: String, tag: String, message: String) {
        val file = logFile ?: return
        try {
            synchronized(this) {
                if (file.exists() && file.length() > MAX_LOG_SIZE_BYTES) {
                    val backup = File(file.parentFile, "nextup_debug.log.old")
                    if (backup.exists()) backup.delete()
                    file.renameTo(backup)
                }
                FileWriter(file, true).use { writer ->
                    val timestamp = dateFormat.format(Date())
                    writer.write("[$timestamp] [$level] [$tag] $message\n")
                }
            }
        } catch (_: Exception) {
        }
    }
}
