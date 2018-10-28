package com.svetylkovo.krool

import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Shared coroutine context for all Krool pools
 */
val kroolContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()