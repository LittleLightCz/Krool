package com.svetylkovo.krool

import kotlinx.coroutines.*
import java.util.Collections.synchronizedList
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/**
 * Resource pool implementation. Use krool() function to obtain an instance.
 */
class Krool<T>(resources: List<T>) {

    private val pool = resources.map { Resource(it) }

    private val kroolContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    val initErrors: MutableList<Throwable> = synchronizedList(mutableListOf<Throwable>())

    /**
     * Delay interval in ms between resource availability checks
     */
    var delayInterval = 10L

    /**
     * Use a resource from the pool or suspend until it's available.
     */
    suspend fun <R> use(consume: suspend (T) -> R): R = findFreeResource().let {
        try {
            consume(it.resource)
        } finally {
            synchronized(it.locked) {
                it.locked = false
            }
        }
    }

    private suspend fun findFreeResource() = withContext(kroolContext) {
        while (true) {
            pool.forEach {
                synchronized(it.locked) {
                    if (!it.locked) {
                        it.locked = true
                        return@withContext it
                    }
                }
            }

            delay(delayInterval)
        }

        null // null is however necessary here to workaround the type system
    } ?: throw RuntimeException("Failed to obtain a resource from the pool")

    /**
     * Use a resource from the pool or block until it's available (use this method if you are using
     * standard threads insted of coroutines).
     */
    fun <R> useBlocking(consume: (T) -> R) = runBlocking {
        use { consume(it) }
    }

    /**
     * Closes all resources using the close lambda function. This method makes sure this lambda is
     * called on each resource even in case of errors.
     *
     * @throws Throwable if any of the resources failed to close
     */
    fun closeWith(close: (T) -> Unit) {
        val contextClosingResult = runCatching { kroolContext.close() }

        val resourcesClosingResults = pool.map {
            runCatching { close(it.resource) }
        }

        (resourcesClosingResults + contextClosingResult)
            .mapNotNull { it.exceptionOrNull() }
            .takeIf { it.isNotEmpty() }
            ?.reduce { error, other ->
                error.apply { addSuppressed(other) }
            }
            ?.let { throw it }
    }

    /**
     * The same as [closeWith], except it suppresses any thrown Exception.
     */
    fun closeSilentlyWith(close: (T) -> Unit) {
        runCatching { closeWith(close) }
    }
}

/**
 * Creates a new Krool instance using the resource [builder] function which is executed asynchronously
 * on the [context] for faster initialization performance. If some of the resources fail to initialize,
 * the pool will be created only from the successful ones where the exceptions of the failed ones will
 * be stored in the [Krool.initErrors] property. If all of them fail, an Exception will be thrown.
 */
suspend fun <T : Any> krool(
    count: Int,
    context: CoroutineContext = Dispatchers.IO,
    builder: suspend (Int) -> T
): Krool<T> {
    assert(count > 0) { "Count has to be greater that 0" }

    val resources = withContext(context) {
        (1..count).map { resourceNum ->
            async {
                runCatching { builder(resourceNum) }
            }
        }.awaitAll()
    }

    val failures = resources.mapNotNull { it.exceptionOrNull() }

    if (failures.size == count) throw RuntimeException("Failed to initialize resources.", failures.first())

    val resourcesPool: List<T> = resources.mapNotNull { it.getOrNull() }

    return krool(resourcesPool).apply {
        if (failures.isNotEmpty()) initErrors += failures
    }
}

/**
 * Creates a new Krool instance from a List of resources
 */
fun <T> krool(
    resources: List<T>,
    settings: Krool<T>.() -> Unit = {}
) = Krool(resources).apply(settings)

/**
 * Creates a new Krool instance from varargs
 */
fun <T> kroolOf(
    vararg resources: T,
    settings: Krool<T>.() -> Unit = {}
) = krool(resources.toList(), settings)