package com.svetylkovo.krool.resource

import kotlinx.coroutines.delay

class ExpensiveResource(val name: String) {
    suspend fun performExpensiveOperation(number: Int): String {
        delay(500)
        return "$name ($number)"
    }

    fun close() {
        // Closing ...
    }
}