package com.svetylkovo.krool.resource

import kotlinx.coroutines.delay

class ExpensiveResource(val name: String) {

    suspend fun initialize(fail: Boolean = false) {
        delay(500)
        if (fail) throw Exception("Resource $name failed to initialize")
    }

    suspend fun performExpensiveOperation(number: Int): String {
        delay(500)
        return "$name ($number)"
    }

    fun close() {
        // Closing ...
    }
}