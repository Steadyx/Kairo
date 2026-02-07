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
        javaClass.declaredMethods.firstOrNull { candidate ->
            candidate.name == name &&
                candidate.parameterTypes.size == args.size &&
                candidate.parameterTypes.zip(args).all { (expected, actual) ->
                    isCompatibleParameter(expected, actual)
                }
        } ?: throw NoSuchMethodException(
            "No matching method: ${javaClass.name}.$name(${args.joinToString { it.javaClass.name }})",
        )
    method.isAccessible = true
    return method.invoke(this, *args) as T
}

private fun isCompatibleParameter(
    expectedType: Class<*>,
    actualValue: Any,
): Boolean {
    val actualType = actualValue.javaClass
    if (!expectedType.isPrimitive) {
        return expectedType.isAssignableFrom(actualType)
    }

    return when (expectedType) {
        java.lang.Boolean.TYPE -> actualType == java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> actualType == java.lang.Byte::class.java
        java.lang.Short.TYPE -> actualType == java.lang.Short::class.java
        java.lang.Integer.TYPE -> actualType == java.lang.Integer::class.java
        java.lang.Long.TYPE -> actualType == java.lang.Long::class.java
        java.lang.Float.TYPE -> actualType == java.lang.Float::class.java
        java.lang.Double.TYPE -> actualType == java.lang.Double::class.java
        java.lang.Character.TYPE -> actualType == java.lang.Character::class.java
        else -> false
    }
}
