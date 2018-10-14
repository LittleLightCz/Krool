package com.svetylkovo.krool

import com.svetylkovo.krool.resource.ExpensiveResource
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.*
import org.assertj.core.api.Assertions.*
import org.junit.Test
import java.util.Collections.synchronizedList
import java.util.concurrent.atomic.AtomicInteger

class KroolTest : CoroutineScope {

    override val coroutineContext = Dispatchers.Default

    @Test
    fun testUse() {

        val resources = (1..5).map { ExpensiveResource("ExpensiveResource $it") }

        val pool = krool(resources) {
            delayInterval = 100 //Let's make it slower than default (10)
        }

        runBlocking { testPoolForResult(pool) }
    }

    private suspend fun testPoolForResult(pool: Krool<ExpensiveResource>) {
        val result = (1..10).map { input ->
            Thread.sleep(5)

            async {
                pool.use {
                    it.performExpensiveOperation(input)
                }
            }
        }

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

    @Test
    fun testAsyncInitializer() = runBlocking {
        val pool = krool(5) { spyk(ExpensiveResource("ExpensiveResource $it")) }
        testPoolForResult(pool)
    }

    @Test
    fun testAsyncInitializerWrongCount() {
        assertThatThrownBy {
            runBlocking {
                krool(0) { spyk(ExpensiveResource("ExpensiveResource $it")) }
            }
        }.hasMessage("Count has to be greater that 0")
    }

    @Test
    fun testAsyncInitializerFailure() {
        val resources = synchronizedList(mutableListOf<ExpensiveResource>())

        assertThatThrownBy {
            runBlocking {
                krool(5, closeOnError = { it.close() }) { num ->
                    val resource = ExpensiveResource("ExpensiveResource $num")
                    resource.initialize(num % 2 == 0)
                    spyk(resource).also { resources += it }
                }
            }
        }.hasMessage("Resource ExpensiveResource 2 failed to initialize")

        resources.forEach {
            verify(exactly = 1) {
                it.close()
            }
        }

        assertThat(resources).hasSize(3)
    }

    @Test
    fun testAsyncInitializerCancellability() {
        val counter = AtomicInteger(0)

        val job = GlobalScope.launch {
            krool(5) {
                (1..3).forEach { _ ->
                    delay(100)
                    counter.incrementAndGet()
                }
            }
        }

        Thread.sleep(50)

        job.cancel()

        Thread.sleep(1000)

        assertThat(counter.get()).isEqualTo(0)
    }

    @Test
    fun testUseCancellability() {
        val counter = AtomicInteger(0)

        val krool = runBlocking {
            krool(5) { it }
        }

        val results = (1..3).map { _ ->
            GlobalScope.launch(Dispatchers.IO) {
                krool.use { numberAsResource ->
                    delay(100)
                    counter.addAndGet(numberAsResource)
                }
            }
        }

        Thread.sleep(50)

        results.forEach { it.cancel() }

        Thread.sleep(1000)

        assertThat(counter.get()).isEqualTo(0)
    }

}