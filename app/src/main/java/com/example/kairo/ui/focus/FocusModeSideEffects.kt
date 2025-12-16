package com.example.kairo.ui.focus

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import com.example.kairo.core.model.ReaderTheme

@Composable
fun FocusModeSideEffects(
    enabled: Boolean,
    hideStatusBar: Boolean,
    pauseNotifications: Boolean
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
fun SystemBarsStyleSideEffect(
    readerTheme: ReaderTheme
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() } ?: return
    val window = activity.window
    val controller = remember(window, view) { WindowInsetsControllerCompat(window, view) }

    DisposableEffect(readerTheme) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        val useDarkIcons = readerTheme == ReaderTheme.LIGHT || readerTheme == ReaderTheme.SEPIA
        controller.isAppearanceLightStatusBars = useDarkIcons
        controller.isAppearanceLightNavigationBars = useDarkIcons
        onDispose { }
    }
}

@Composable
private fun FocusDndSideEffect(enabled: Boolean) {
    val context = LocalContext.current
    val notificationManager = remember(context) {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    DisposableEffect(enabled) {
        var previousFilter: Int? = null
        var didChange = false

        if (enabled && notificationManager.isNotificationPolicyAccessGranted) {
            previousFilter = notificationManager.currentInterruptionFilter
            if (previousFilter != NotificationManager.INTERRUPTION_FILTER_NONE) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                didChange = true
            }
        }

        onDispose {
            if (didChange && previousFilter != null && notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(previousFilter)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
