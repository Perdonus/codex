package com.codex.android.app.core.util

import java.time.Instant

object AppDiagnostics {
    private const val MAX_ENTRIES = 160
    private val entries = ArrayDeque<String>()

    @Synchronized
    fun log(message: String) {
        val entry = "[${Instant.now()}] $message"
        while (entries.size >= MAX_ENTRIES) {
            entries.removeFirst()
        }
        entries.addLast(entry)
    }

    @Synchronized
    fun snapshot(): List<String> = entries.toList()
}
