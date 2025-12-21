package com.example.kairo.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.kairo.core.dispatchers.DefaultDispatcherProvider
import com.example.kairo.core.dispatchers.DispatcherProvider

val LocalDispatcherProvider = staticCompositionLocalOf<DispatcherProvider> {
    DefaultDispatcherProvider()
}
