package com.svetylkovo.krool

import com.svetylkovo.krool.resource.ExpensiveResource
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
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

    @Test
    fun testCloseWithException() {
        val resources = exceptionThrowingExpensiveResources()

        val pool = krool(resources)

        val throwable = catchThrowable {
            pool.closeWith { it.close() }
        }

        assertThat(throwable).hasMessage("Closing of resource 2 failed!")
    }

    @Test
    fun testCloseSilentlyWith() {
        val resources = exceptionThrowingExpensiveResources()

        val pool = krool(resources)

        val throwable = catchThrowable {
            pool.closeSilentlyWith { it.close() }
        }

        assertThat(throwable).doesNotThrowAnyException()
    }

    @Test
    fun testCloseWithNoException() {
        val resources = (1..5).map { spyk(ExpensiveResource("ExpensiveResource $it")) }

        val pool = krool(resources)

        pool.closeWith { it.close() }

        resources.forEach {
            verify(exactly = 1) { it.close() }
        }
    }

    private fun exceptionThrowingExpensiveResources(): List<ExpensiveResource> {
        return (1..5).map { num ->
            mockk<ExpensiveResource>().also {
                when (num) {
                    2 -> every { it.close() } throws RuntimeException("Closing of resource $num failed!")
                    else -> every { it.close() } returns Unit
                }
            }
        }
    }
}