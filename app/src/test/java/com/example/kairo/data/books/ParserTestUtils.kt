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
    val parameterTypes = args.map { arg ->
        when (arg) {
            is Long -> java.lang.Long.TYPE
            is Int -> java.lang.Integer.TYPE
            is Boolean -> java.lang.Boolean.TYPE
            is Double -> java.lang.Double.TYPE
            is Float -> java.lang.Float.TYPE
            is Byte -> java.lang.Byte.TYPE
            is Short -> java.lang.Short.TYPE
            is Char -> java.lang.Character.TYPE
            else -> arg.javaClass
        }
    }.toTypedArray()
    val method = javaClass.getDeclaredMethod(name, *parameterTypes)
    method.isAccessible = true
    return method.invoke(this, *args) as T
}
