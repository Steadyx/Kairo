package com.kairo.reader.ui.reader

import android.util.Log
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.TestActivity
import com.kairo.reader.ui.theme.KairoTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderFocusNavigationDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<TestActivity>()

    @Test
    fun navigationWithinTheSamePageScrollsToTheNewFocus() {
        val focus = mutableIntStateOf(0)
        lateinit var list: LazyListState
        compose.setContent {
            KairoTheme {
                val holder = rememberReaderListState("same-chapter-and-page", focus.intValue, 20, false)
                list = holder.listState
                LazyColumn(Modifier.height(180.dp), state = list) {
                    items(20) { index -> Text("Paragraph $index", Modifier.height(60.dp)) }
                }
            }
        }
        compose.onNodeWithText("Paragraph 0").assertIsDisplayed()
        compose.runOnIdle { focus.intValue = 12 }
        compose.waitForIdle()
        compose.runOnIdle {
            Log.i("ReaderRegression", "Navigation focus=12, first visible paragraph=${list.firstVisibleItemIndex}")
            assertEquals(12, list.firstVisibleItemIndex)
        }
        compose.onNodeWithText("Paragraph 12").assertIsDisplayed()
    }

    @Test
    fun focusingAnAlreadyVisibleParagraphDoesNotJumpTheViewport() {
        val focus = mutableIntStateOf(0)
        lateinit var list: LazyListState
        compose.setContent {
            KairoTheme {
                list = rememberReaderListState("same-page", focus.intValue, 20, false).listState
                LazyColumn(Modifier.height(180.dp), state = list) {
                    items(20) { index -> Text("Paragraph $index", Modifier.height(60.dp)) }
                }
            }
        }
        compose.runOnIdle { runBlocking { list.scrollBy(12f) } }
        val before = compose.runOnIdle { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
        compose.runOnIdle { focus.intValue = 1 }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(before, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
        }
    }
}
