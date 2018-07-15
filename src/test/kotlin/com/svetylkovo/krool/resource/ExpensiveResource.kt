package com.svetylkovo.krool.resource

import kotlinx.coroutines.experimental.delay

class ExpensiveResource(val name: String) {
    suspend fun performExpensiveOperation(number: Int): String {
        delay(500)
        return "$name ($number)"
    }

    fun close() {
        // Closing ...
    }
}