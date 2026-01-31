package com.six2dez.burp.aiagent.util

import burp.api.montoya.MontoyaApi
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object AuditLogger {
    private val lock = ReentrantLock()
    private val logDir = File("logs")
    private val logFile = File(logDir, "api_analysis_audit.log")

    init {
        try {
            if (!logDir.exists()) logDir.mkdirs()
            if (!logFile.exists()) logFile.createNewFile()
        } catch (_: Exception) {
        }
    }

    fun logEvent(api: MontoyaApi?, requesterId: String?, action: String, details: String, level: String = "INFO") {
        val ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val entry = buildString {
            append(ts)
            append(" | ")
            append(level)
            append(" | ")
            append(action)
            append(" | requester=")
            append(requesterId ?: "unknown")
            append(" | ")
            append(details.replace("\n", " "))
        }

        // Write to file (best-effort)
        try {
            lock.withLock {
                FileWriter(logFile, true).use { it.appendLine(entry) }
            }
        } catch (e: Exception) {
            // ignore file errors
        }

        // Also log to Montoya if available
        try {
            api?.logging()?.logToOutput(entry)
        } catch (_: Exception) {
        }
    }
}
