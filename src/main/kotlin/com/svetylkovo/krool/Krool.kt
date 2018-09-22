package com.svetylkovo.krool

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Resource pool implementation. Use krool() function to obtain an instance.
 */
class Krool<T>(resources: List<T>) {

    private val pool = resources.map { Resource(it) }

    private val kroolContext = newFixedThreadPoolContext(1, "Krool")

    private var active = true

    /**
     * Delay interval in ms between resource availability checks
     */
    var delayInterval = 10

    /**
     * Use a resource from the pool or suspend until it's available.
     */
    suspend fun <R> use(consume: suspend (T) -> R): R? = findFreeResource()?.let {
        try {
            consume(it.resource)
        } finally {
            synchronized(it.locked) {
                it.locked = false
            }
        }
    }

    private suspend fun findFreeResource() = withContext(kroolContext) {
        while (active) {
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

        null
    }

    /**
     * Use a resource from the pool or block until it's available (use this method if you are using
     * standard threads insted of coroutines).
     */
    fun <R> useBlocking(consume: (T) -> R) = runBlocking {
        use { consume(it) }
    }

    /**
     * Terminate all tasks that are still waiting for a resource.
     */
    fun terminate() {
        active = false
    }

    /**
     * Closes all resources using the close lambda function. This method makes sure this lambda is
     * called on each resource even in case of errors.
     *
     * @throws Throwable if any of the resources failed to close
     */
    fun closeWith(close: (T) -> Unit) {

        terminate()

        var error: Throwable? = null

        pool.forEach {
            try {
                close(it.resource)
            } catch (t: Throwable) {
                error?.addSuppressed(t) ?: run { error = t }
            }
        }

        error?.let { throw it }
    }

    /**
     * The same as closeWith, except it suppresses any thrown Exception.
     */
    fun closeSilentlyWith(close: (T) -> Unit) {
        runCatching { closeWith(close) }
    }
}

/**
 * Creates a new Krool instance using the [resourceBuilder] builder which is executed asynchronously
 * on the [context] for faster initialization performance. Please note that initialization of some
 * resources might fail. In this case the [closeOnError] function is executed to silently close those
 * resources that have succeeded to avoid memory/resource leaks and the exception is thrown.
 */
suspend fun <T> krool(
    resourcesCount: Int,
    closeOnError: (T) -> Unit = {},
    context: CoroutineContext = Dispatchers.Default,
    resourceBuilder: (Int) -> T
): Krool<T> {
    assert(resourcesCount > 0)

    val resources = (1..resourcesCount).map { resourceNum ->
        GlobalScope.async(context) {
            runCatching { resourceBuilder(resourceNum) }
        }
    }.awaitAll()

    val failed = resources.filter { it.isFailure }

    if (failed.isNotEmpty()) {
        //Close succeeded first
        resources.filter { it.isSuccess }.forEach {
            runCatching {
                it.getOrNull()?.let(closeOnError)
            }
        }

        throw failed.first().exceptionOrNull()
                ?: Exception("Oops, an Exception should have been thrown from the first failed resource in the list, but it was null instead!")
    }

    return krool(resources.map { it.getOrThrow() })
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