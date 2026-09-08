@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.focus

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.ui.theme.readerThemePalette

@Composable
fun FocusModeSideEffects(
    enabled: Boolean,
    hideStatusBar: Boolean,
    pauseNotifications: Boolean,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() } ?: return
    val window = activity.window
    val insetsController = remember(window, view) { WindowInsetsControllerCompat(window, view) }

    DisposableEffect(enabled, hideStatusBar) {
        val previousBehavior = insetsController.systemBarsBehavior

        if (enabled && hideStatusBar) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.statusBars())
        }

        onDispose {
            insetsController.systemBarsBehavior = previousBehavior
            insetsController.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    FocusDndSideEffect(enabled = enabled && pauseNotifications)
}

@Composable
fun SystemBarsStyleSideEffect(readerTheme: ReaderTheme) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() } ?: return
    val window = activity.window
    val controller = remember(window, view) { WindowInsetsControllerCompat(window, view) }

    DisposableEffect(readerTheme) {
        val useDarkIcons = !readerTheme.readerThemePalette().isDark
        controller.isAppearanceLightStatusBars = useDarkIcons
        controller.isAppearanceLightNavigationBars = useDarkIcons
        onDispose { }
    }
}

@Composable
internal fun FocusDndSideEffect(enabled: Boolean) {
    val context = LocalContext.current
    val owner = remember { Any() }
    val controller = (context.applicationContext as KairoApplication).focusDndController
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(enabled, lifecycle, owner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (enabled) controller.acquire(owner)
                Lifecycle.Event.ON_STOP -> controller.release(owner)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            controller.acquire(owner)
        }
        onDispose {
            lifecycle.removeObserver(observer)
            controller.release(owner)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
