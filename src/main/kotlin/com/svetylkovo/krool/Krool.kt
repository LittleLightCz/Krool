package com.svetylkovo.krool

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking


class Krool<T>(resources: List<T>) {

    /**
     * Delay interval in ms between resource availability checks
     */
    var delayInterval = 10

    private val pool = resources.map { Resource(it) }

    private var active = true

    suspend fun <R> use(consume: (T) -> R): R? {

        var freeResource : Resource<T>? = null

        while (freeResource == null || active) {
            pool.forEach { it ->
                synchronized(it.locked) {
                    if (!it.locked) {
                        it.locked = true
                        freeResource = it
                        return@forEach
                    }
                }
            }

            if (freeResource == null) {
                delay(delayInterval)
            }
        }

        return freeResource?.let {
            try {
                consume(it.resource)
            } finally {
                synchronized(it.locked) {
                    it.locked = false
                }
            }
        }
    }

    fun <R> useBlocking(consume: (T) -> R) = runBlocking { use(consume) }

    fun terminate() {
        active = false
    }
}

fun <T> krool(
    resources: List<T>,
    settings: Krool<T>.() -> Unit = {}
) = Krool(resources).apply(settings)