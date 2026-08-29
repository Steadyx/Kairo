package com.kairo.reader.ui.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kairo.reader.R
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.RsvpConfigConstraints

private const val READER_FONT_SIZE_MIN_SP = 14f
private const val READER_FONT_SIZE_MAX_SP = 32f

@Composable
fun ReaderSettingsContent(
    fontSizeSp: Float,
    readerTheme: ReaderTheme,
    textBrightness: Float,
    invertedScroll: Boolean,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onTextBrightnessChange: (Float) -> Unit,
    onInvertedScrollChange: (Boolean) -> Unit,
) {
    val resources = LocalResources.current

    DeferredSliderRow(
        title = stringResource(R.string.reader_font_size_title),
        valueLabel = { resources.getString(R.string.format_sp, it.toInt()) },
        rawValue = fontSizeSp,
        onCommit = { onFontSizeChange(it.coerceIn(READER_FONT_SIZE_MIN_SP, READER_FONT_SIZE_MAX_SP)) },
        valueRange = READER_FONT_SIZE_MIN_SP..READER_FONT_SIZE_MAX_SP,
    )

    ThemeSelector(selected = readerTheme, onThemeChange = onThemeChange)

    SettingsSliderRow(
        title = stringResource(R.string.reader_text_brightness_title),
        subtitle = stringResource(R.string.reader_text_brightness_subtitle),
        valueLabel =
        stringResource(
            R.string.format_percent,
            (
                textBrightness.coerceIn(
                    RsvpConfigConstraints.MIN_TEXT_BRIGHTNESS.toFloat(),
                    RsvpConfigConstraints.MAX_TEXT_BRIGHTNESS.toFloat(),
                ) * RsvpConfigConstraints.PERCENT_SCALE
                ).toInt(),
        ),
        value =
        textBrightness.coerceIn(
            RsvpConfigConstraints.MIN_TEXT_BRIGHTNESS.toFloat(),
            RsvpConfigConstraints.MAX_TEXT_BRIGHTNESS.toFloat(),
        ),
        onValueChange = {
            onTextBrightnessChange(
                it.coerceIn(
                    RsvpConfigConstraints.MIN_TEXT_BRIGHTNESS.toFloat(),
                    RsvpConfigConstraints.MAX_TEXT_BRIGHTNESS.toFloat(),
                ),
            )
        },
        valueRange =
        RsvpConfigConstraints.MIN_TEXT_BRIGHTNESS.toFloat()..RsvpConfigConstraints.MAX_TEXT_BRIGHTNESS.toFloat(),
    )

    Text(stringResource(R.string.reader_scrolling_title), style = MaterialTheme.typography.titleMedium)
    SettingsSwitchRow(
        title = stringResource(R.string.reader_invert_swipe_title),
        subtitle = stringResource(R.string.reader_invert_swipe_subtitle),
        checked = invertedScroll,
        onCheckedChange = onInvertedScrollChange,
    )
}

@Composable
fun FocusSettingsContent(
    focusModeEnabled: Boolean,
    focusHideStatusBar: Boolean,
    focusPauseNotifications: Boolean,
    focusApplyInReader: Boolean,
    focusApplyInRsvp: Boolean,
    onFocusModeEnabledChange: (Boolean) -> Unit,
    onFocusHideStatusBarChange: (Boolean) -> Unit,
    onFocusPauseNotificationsChange: (Boolean) -> Unit,
    onFocusApplyInReaderChange: (Boolean) -> Unit,
    onFocusApplyInRsvpChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasDndAccess by remember {
        mutableStateOf(
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .isNotificationPolicyAccessGranted
        )
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasDndAccess =
                        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                            .isNotificationPolicyAccessGranted
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    SettingsSwitchRow(
        title = stringResource(R.string.focus_enable_title),
        subtitle = stringResource(R.string.focus_mode_subtitle),
        checked = focusModeEnabled,
        onCheckedChange = onFocusModeEnabledChange,
    )

    SettingsSwitchRow(
        title = stringResource(R.string.focus_hide_status_bar_title),
        subtitle = stringResource(R.string.focus_hide_status_bar_subtitle),
        checked = focusHideStatusBar,
        onCheckedChange = onFocusHideStatusBarChange,
        enabled = focusModeEnabled,
    )

    SettingsSwitchRow(
        title = stringResource(R.string.focus_pause_notifications_title),
        subtitle = stringResource(R.string.focus_pause_notifications_subtitle),
        checked = focusPauseNotifications,
        onCheckedChange = onFocusPauseNotificationsChange,
        enabled = focusModeEnabled,
    )

    if (focusModeEnabled && focusPauseNotifications && !hasDndAccess) {
        Text(
            stringResource(R.string.focus_dnd_permission_message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        ) {
            Text(stringResource(R.string.focus_dnd_permission_action))
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(stringResource(R.string.focus_apply_title), style = MaterialTheme.typography.titleMedium)

    SettingsSwitchRow(
        title = stringResource(R.string.focus_apply_reader_title),
        subtitle = stringResource(R.string.focus_apply_reader_subtitle),
        checked = focusApplyInReader,
        onCheckedChange = onFocusApplyInReaderChange,
        enabled = focusModeEnabled,
    )
    SettingsSwitchRow(
        title = stringResource(R.string.focus_apply_rsvp_title),
        subtitle = stringResource(R.string.focus_apply_rsvp_subtitle),
        checked = focusApplyInRsvp,
        onCheckedChange = onFocusApplyInRsvpChange,
        enabled = focusModeEnabled,
    )
}
