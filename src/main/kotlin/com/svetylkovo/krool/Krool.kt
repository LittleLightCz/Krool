package com.svetylkovo.krool

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.withContext


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
            pool.forEach { it ->
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

    fun <R> useBlocking(consume: suspend (T) -> R) = runBlocking { use(consume) }

    fun terminate() {
        active = false
    }
}

/**
 * Creates a new Krool instance
 */
fun <T> krool(
    resources: List<T>,
    settings: Krool<T>.() -> Unit = {}
) = Krool(resources).apply(settings)