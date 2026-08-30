package com.kairo.reader.ui.format

import android.content.res.Resources
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import com.kairo.reader.R
import java.util.Locale

fun formatShortDurationMinutes(resources: Resources, minutes: Int): String {
    if (minutes <= 0) {
        return resources.getString(R.string.time_less_than_one_minute)
    }
    val locale = resources.configuration.locales[0] ?: Locale.getDefault()
    val formatter = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
    return if (minutes < MINUTES_PER_HOUR) {
        formatter.format(Measure(minutes, MeasureUnit.MINUTE))
    } else {
        val hours = minutes / MINUTES_PER_HOUR
        val remainingMinutes = minutes % MINUTES_PER_HOUR
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

private const val MINUTES_PER_HOUR = 60
