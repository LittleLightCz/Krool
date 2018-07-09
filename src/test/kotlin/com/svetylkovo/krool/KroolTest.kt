package com.svetylkovo.krool

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.awaitAll
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class KroolTest {

    @Test
    fun testUse() {

        val resources = (1..5).map { ExpensiveResource("ExpensiveResource $it") }

        val pool = krool(resources) {
            delayInterval = 100 //Let's make it slower than default (10)
        }

        val result = (1..10).map { input ->
            Thread.sleep(5)

            async {
                pool.use {
                    it.performExpensiveOperation(input)
                }
            }
        }

        runBlocking {
            val stringResult = result.awaitAll().joinToString("\n")

            assertThat(stringResult).contains(
                "ExpensiveResource 1",
                "ExpensiveResource 2",
                "ExpensiveResource 3",
                "ExpensiveResource 4",
                "ExpensiveResource 5",
                "(1)",
                "(2)",
                "(3)",
                "(4)",
                "(5)",
                "(6)",
                "(7)",
                "(8)",
                "(9)",
                "(10)"
            )
        }
    }

}

class ExpensiveResource(val name: String) {
    suspend fun performExpensiveOperation(input: Int): String {
        delay(500)
        return "$name ($input)"
    }
}