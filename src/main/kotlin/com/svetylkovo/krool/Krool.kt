package com.svetylkovo.krool


class Krool<T>(val resources: List<T>) {

    /**
     * Delay interval in ms between resource availability checks
     */
    var delayInterval = 10

    private val pool = resources.map { Resource(it) }

    fun use(block: T -> R): R {

    }

    fun useBlocking() = use()
}

fun <T> krool(
    resources: List<T>,
    settings: Krool<T>.() -> Unit = {}
) = Krool(resources).apply(settings)