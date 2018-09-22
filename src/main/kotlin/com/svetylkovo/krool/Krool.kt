package com.svetylkovo.krool

import kotlinx.coroutines.delay
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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
    fun closeSilentlyWith(close: (T) -> Unit) = try {
        closeWith(close)
    } catch (t: Throwable) {
        //supress
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