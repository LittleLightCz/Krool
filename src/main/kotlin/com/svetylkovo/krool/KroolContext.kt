package com.svetylkovo.krool

import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Context on which Krool tries to obtain a free resource from the pool. Please note that this
 * context is being shared for all [Krool] instances. Make sure you close this context when you
 * don't need to use Krool anymore (usually at the end/cleanup phase of your application) to avoid
 * resource leaks.
 */
val kroolContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()