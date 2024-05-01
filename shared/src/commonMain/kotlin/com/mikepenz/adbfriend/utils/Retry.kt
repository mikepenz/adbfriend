package com.mikepenz.adbfriend.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalContracts::class)
suspend inline fun <T> retry(retryCount: Int, delayAfterRetry: Long = 0L, noinline preRetry: ((Int) -> Unit)? = null, block: (Int) -> T): T {
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
    }

    var attempt = 0
    while (true) {
        try {
            return block(attempt)
        } catch (failure: Throwable) {
            /* avoid swallowing CancellationExceptions */
            if (failure is CancellationException) {
                throw failure
            } else if (attempt < retryCount) {
                attempt++
                val before = System.currentTimeMillis()
                withTimeout(delayAfterRetry) {
                    preRetry?.invoke(attempt)
                }
                delay((System.currentTimeMillis() - before).coerceAtMost(delayAfterRetry))
            } else {
                throw failure
            }
        }
    }
}