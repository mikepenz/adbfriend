package com.mikepenz.adbfriend

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Scope interface to indicate activity to the timeout
 */
interface TimerScope : CoroutineScope {
    var timeoutCallback: (() -> Unit)?

    fun reset()
}

/**
 * A version of [withTimeoutOrNull] that supports [TimerScope] and can be reset.
 */
suspend fun <T> withResettableTimeoutOrNull(timeMillis: Long, block: suspend TimerScope.() -> T): T? {
    // Create a timer instance in advance, so that we can check if ResettableTimeoutCancellationException coming from it
    val timer = TimerScopeImpl(timeMillis)
    try {
        // Run the code in a separate scope that we can cancel on timeout
        return coroutineScope {
            timer.context = coroutineContext
            timer.start()
            val result = timer.block()
            timer.complete()
            result
        }
    } catch (e: ResettableTimeoutCancellationException) {
        if (e.timer === timer) return null
        throw e
    }
}

// -------- private implementation details --------
class ResettableTimeoutCancellationException(val timer: TimerScope) : CancellationException()

private class TimerScopeImpl(
    private var timeout: Long,
) : TimerScope {
    lateinit var context: CoroutineContext
    private var previousTime = System.currentTimeMillis()
    private var cancellationJob: Job? = null // != null when running
    override var timeoutCallback: (() -> Unit)? = null

    override val coroutineContext: CoroutineContext
        get() = context

    override fun reset() {
        previousTime = System.currentTimeMillis()
    }

    fun start() {
        @Suppress("OPT_IN_USAGE")
        cancellationJob = GlobalScope.launch(coroutineContext) {
            while (previousTime + timeout > System.currentTimeMillis()) {
                delay((previousTime + timeout - System.currentTimeMillis()).coerceAtLeast(0))
            }
            // cancel top-level context on timeout
            timeoutCallback?.invoke()
            context.cancel(ResettableTimeoutCancellationException(this@TimerScopeImpl))
        }
    }

    fun complete() {
        cancellationJob?.apply { // do only when running
            cancel(CancellationException()) // cancel cancellation job
            cancellationJob = null
        }
        timeoutCallback = null
    }
}
