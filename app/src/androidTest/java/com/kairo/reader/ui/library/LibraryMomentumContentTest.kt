package com.kairo.reader.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingMomentum
import com.kairo.reader.core.model.ReadingMomentumDay
import com.kairo.reader.core.model.ReadingMomentumWeek
import com.kairo.reader.core.model.ReadingSession
import com.kairo.reader.core.model.ReadingSessionItem
import com.kairo.reader.core.model.ReadingSessionMode
import com.kairo.reader.ui.theme.KairoTheme
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryMomentumContentTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun selectingDayUpdatesTotalAndRecentSessionsStayBounded() {
        val days = momentumDays()
        val sessions = (1L..8L).map(::session)
        composeRule.setContent {
            KairoTheme {
                LibraryMomentumContent(
                    momentum =
                    ReadingMomentum(
                        sessions = sessions,
                        activeDaysInLastSeven = 2,
                        dailyActivity = days,
                    ),
                    weeklyGoalMinutes = 120,
                    onWeeklyGoalChange = {},
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.momentum_total_reading),
        ).performScrollTo()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.momentum_duration_hours, 2),
        ).assertIsDisplayed()

        val firstDayLabel =
            SimpleDateFormat(SHORT_DAY_PATTERN, Locale.getDefault()).format(Date(days.first().startedAt))
        val firstDayDuration =
            composeRule.activity.getString(R.string.momentum_duration_hours_minutes, 1, 30)
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(
                R.string.momentum_day_bar_description,
                firstDayLabel,
                firstDayDuration,
            ),
        ).performClick()
        composeRule.onNodeWithText(firstDayDuration).assertIsDisplayed()

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.momentum_recent_sessions_limit, 5),
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun currentDayIsSelectedInsideTheCalendarWeek() {
        val days = momentumDays()
        composeRule.setContent {
            KairoTheme {
                LibraryMomentumContent(
                    momentum =
                    ReadingMomentum(
                        dailyActivity = days,
                        todayStartedAt = days.first().startedAt,
                    ),
                    weeklyGoalMinutes = 120,
                    onWeeklyGoalChange = {},
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.momentum_today),
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.momentum_duration_hours_minutes, 1, 30),
        ).assertIsDisplayed()
    }

    @Test
    fun previousWeeksAreInitiallyCollapsedAndExpandLazily() {
        val previousWeek =
            ReadingMomentumWeek(
                startedAt = startOfDay(-14),
                endedAt = startOfDay(-7),
                activeDurationMs = 3_600_000L,
                wordsRead = 6_000,
                activeDays = 3,
            )
        composeRule.setContent {
            KairoTheme {
                LibraryMomentumContent(
                    momentum = ReadingMomentum(previousWeeks = listOf(previousWeek)),
                    weeklyGoalMinutes = 120,
                    onWeeklyGoalChange = {},
                )
            }
        }

        val duration = composeRule.activity.getString(R.string.momentum_duration_hours, 1)
        composeRule.onAllNodesWithText(duration).assertCountEquals(0)
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.momentum_previous_weeks),
        ).performScrollTo().performClick()
        composeRule.onNodeWithText(duration).assertIsDisplayed()
    }

    @Test
    fun previousWeekMetricsWrapAtNarrowWidthAndLargeText() {
        val previousWeek =
            ReadingMomentumWeek(
                startedAt = startOfDay(-14),
                endedAt = startOfDay(-7),
                activeDurationMs = 3_600_000L,
                wordsRead = 123_456,
                activeDays = 7,
            )
        composeRule.setContent {
            KairoTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                    Box(modifier = Modifier.width(220.dp)) {
                        MomentumPreviousWeekRow(previousWeek)
                    }
                }
            }
        }

        val duration = composeRule.activity.getString(R.string.momentum_duration_hours, 1)
        val words =
            composeRule.activity.getString(
                R.string.momentum_words_read,
                NumberFormat.getIntegerInstance().format(previousWeek.wordsRead),
            )
        val activeDays =
            composeRule.activity.resources.getQuantityString(
                R.plurals.momentum_active_days_count,
                previousWeek.activeDays,
                previousWeek.activeDays,
            )
        val durationNode = composeRule.onNodeWithText(duration).assertIsDisplayed()
        composeRule.onNodeWithText(words).assertIsDisplayed()
        val activeDaysNode = composeRule.onNodeWithText(activeDays).assertIsDisplayed()

        composeRule.runOnIdle {
            assertTrue(
                activeDaysNode.fetchSemanticsNode().boundsInRoot.top >
                    durationNode.fetchSemanticsNode().boundsInRoot.top,
            )
        }
    }

    @Test
    fun customGoalRejectsInvalidInputAndSavesValidValue() {
        var savedGoal: Int? = null
        composeRule.setContent {
            KairoTheme {
                LibraryMomentumContent(
                    momentum = ReadingMomentum(),
                    weeklyGoalMinutes = 120,
                    onWeeklyGoalChange = { savedGoal = it },
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.momentum_custom_goal),
        ).performScrollTo().performClick()
        val goalField = composeRule.onNode(hasSetTextAction())
        goalField.performTextClearance()
        goalField.performTextInput("29")
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.action_save),
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(
                R.string.momentum_custom_goal_error,
                30,
                1_400,
            ),
        ).assertIsDisplayed()
        assertEquals(null, savedGoal)

        goalField.performTextClearance()
        goalField.performTextInput("245")
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.action_save),
        ).performClick()
        composeRule.runOnIdle { assertEquals(245, savedGoal) }
    }

    @Test
    fun resetRequiresConfirmationAndInvokesCallback() {
        var resetCount = 0
        composeRule.setContent {
            KairoTheme {
                LibraryMomentumContent(
                    momentum = ReadingMomentum(),
                    weeklyGoalMinutes = 120,
                    onWeeklyGoalChange = {},
                    onResetMomentum = { resetCount += 1 },
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.momentum_reset_this_week),
        ).performScrollTo().performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.momentum_reset_message),
        ).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, resetCount) }
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.momentum_reset_confirm),
        ).performClick()
        composeRule.runOnIdle { assertEquals(1, resetCount) }
    }

    private fun momentumDays(): List<ReadingMomentumDay> {
        val start =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -6)
            }
        return List(7) { index ->
            val day = start.clone() as Calendar
            day.add(Calendar.DAY_OF_YEAR, index)
            when (index) {
                0 -> ReadingMomentumDay(day.timeInMillis, 5_400_000L, 9_000, 2)
                6 -> ReadingMomentumDay(day.timeInMillis, 7_200_000L, 12_000, 3)
                else -> ReadingMomentumDay(day.timeInMillis)
            }
        }
    }

    private fun session(id: Long): ReadingSessionItem =
        ReadingSessionItem(
            session =
            ReadingSession(
                id = id.toString(),
                bookId = BOOK.id,
                mode = ReadingSessionMode.READER,
                startedAt = id,
                endedAt = id + SESSION_DURATION_MS,
                activeDurationMs = SESSION_DURATION_MS,
                startChapterIndex = 0,
                startTokenIndex = 0,
                endChapterIndex = 0,
                endTokenIndex = 500,
                wordsRead = 500,
                effectiveWpm = 100,
                isWordCountEstimated = false,
            ),
            book = BOOK,
        )

    private fun startOfDay(dayOffset: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }.timeInMillis

    private companion object {
        const val SHORT_DAY_PATTERN = "EEE"
        const val SESSION_DURATION_MS = 300_000L
        val BOOK = Book(BookId("book"), "Book", emptyList(), chapters = emptyList())
    }
}
