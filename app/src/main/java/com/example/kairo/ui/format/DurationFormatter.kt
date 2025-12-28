package com.example.kairo.ui.format

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import com.example.kairo.R
import java.util.Locale

fun formatShortDurationMinutes(context: Context, minutes: Int): String {
    if (minutes <= 0) {
        return context.getString(R.string.time_less_than_one_minute)
    }
    val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    val formatter = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
    return if (minutes < 60) {
        formatter.format(Measure(minutes, MeasureUnit.MINUTE))
    } else {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        if (remainingMinutes == 0) {
            formatter.format(Measure(hours, MeasureUnit.HOUR))
        } else {
            formatter.formatMeasures(
                Measure(hours, MeasureUnit.HOUR),
                Measure(remainingMinutes, MeasureUnit.MINUTE),
            )
        }
    }
}
