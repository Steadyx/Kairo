package com.example.kairo.data.books

import com.example.kairo.core.dispatchers.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal object TestDispatchers : DispatcherProvider {
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
}

@Suppress("UNCHECKED_CAST")
internal fun <T> Any.callPrivate(name: String, vararg args: Any): T {
    val method =
        javaClass.getDeclaredMethod(
            name,
            *args.map { it.javaClass }.toTypedArray(),
        )
    method.isAccessible = true
    return method.invoke(this, *args) as T
}
