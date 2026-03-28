package com.codex.android.app.core.util

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class JsonFileStore<T>(
    context: Context,
    private val fileName: String,
    private val serializer: KSerializer<T>,
    private val defaultValue: T,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val file: File = File(context.filesDir, fileName)
    private val state = MutableStateFlow(defaultValue)

    fun state(): StateFlow<T> = state.asStateFlow()

    suspend fun load(): T = withContext(dispatcher) {
        val loaded = if (!file.exists()) {
            defaultValue
        } else {
            runCatching {
                json.decodeFromString(serializer, file.readText())
            }.getOrDefault(defaultValue)
        }
        state.value = loaded
        loaded
    }

    suspend fun update(transform: (T) -> T): T = withContext(dispatcher) {
        val next = transform(state.value)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(serializer, next))
        state.value = next
        next
    }
}

