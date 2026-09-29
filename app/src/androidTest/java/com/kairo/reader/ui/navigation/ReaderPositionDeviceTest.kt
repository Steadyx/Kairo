package com.kairo.reader.ui.navigation

import android.net.Uri
import android.util.Log
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.KairoApplication
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.reader.ReaderViewModel
import com.kairo.reader.ui.theme.KairoTheme
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real reader route, using only a uniquely named disposable book. */
@RunWith(AndroidJUnit4::class)
class ReaderPositionDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<TestActivity>()

    @Test
    fun leavingAfterAChapterChangeRestoresTheVisiblePosition() = runBlocking {
        val container = compose.activity.application as KairoApplication
        val source = createBook(container.cacheDir)
        var bookId: BookId? = null
        val visible = mutableStateOf(true)
        try {
            val book = container.libraryRepository.import(Uri.fromFile(source)).book
            bookId = book.id
            assertEquals(2, book.chapters.size)
            lateinit var nav: NavHostController
            compose.setContent {
                KairoTheme {
                    if (visible.value) {
                        nav = rememberNavController()
                        NavHost(navController = nav, startDestination = KairoRoutes.LIBRARY) {
                            composable(KairoRoutes.LIBRARY) {
                                Button(onClick = { nav.navigate(KairoRoutes.reader(book.id.value)) }) {
                                    Text("Open regression book")
                                }
                            }
                            readerDestinations(
                                ReaderDestinationDependencies(
                                    container, nav, UserPreferences(), 250, false, null, {}, {}, {}, {},
                                ),
                            )
                        }
                    }
                }
            }
            compose.onNodeWithText("Open regression book").performClick()
            compose.waitUntil(15_000) { nav.currentBackStackEntry?.destination?.route == KairoRoutes.READER }
            val viewModel = compose.runOnIdle {
                ViewModelProvider(requireNotNull(nav.currentBackStackEntry))[ReaderViewModel::class.java]
            }
            compose.waitUntil(15_000) { viewModel.uiState.value.chapterData != null }
            val next = compose.activity.getString(R.string.content_desc_next_page)
            var pageTurns = 0
            while (viewModel.uiState.value.chapterIndex == 0 && pageTurns < 30) {
                compose.onNodeWithContentDescription(next).performClick()
                compose.waitForIdle()
                pageTurns++
            }
            compose.waitUntil(15_000) {
                viewModel.uiState.value.chapterIndex == 1 && viewModel.uiState.value.chapterData != null
            }
            assertTrue(pageTurns > 1)
            val expected = viewModel.uiState.value.focusIndex
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.content_desc_go_to_library))
                .performClick()
            compose.waitUntil(15_000) { nav.currentBackStackEntry?.destination?.route == KairoRoutes.LIBRARY }
            val saved = requireNotNull(container.readingPositionRepository.getPosition(book.id))
            Log.i("ReaderRegression", "Chapter switch: visible=$expected, saved=${saved.tokenIndex}, chapter=${saved.chapterIndex}")
            assertEquals(1, saved.chapterIndex)
            assertEquals(expected, saved.tokenIndex)
            compose.onNodeWithText("Open regression book").performClick()
            compose.waitUntil(15_000) { nav.currentBackStackEntry?.destination?.route == KairoRoutes.READER }
            val reopened = compose.runOnIdle {
                ViewModelProvider(requireNotNull(nav.currentBackStackEntry))[ReaderViewModel::class.java]
            }
            compose.waitUntil(15_000) { reopened.uiState.value.chapterData != null }
            assertEquals(expected, reopened.uiState.value.focusIndex)
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            bookId?.let { id ->
                container.readingSessionCoordinator.finalizeReader(id)
                container.readingSessionCoordinator.awaitIdle()
                container.libraryRepository.delete(id.value)
            }
            source.delete()
        }
    }

    private fun createBook(directory: File): File {
        val source = File.createTempFile("position-regression-", ".epub", directory)
        ZipOutputStream(source.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write("<container><rootfiles><rootfile full-path=\"content.opf\"/></rootfiles></container>".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("content.opf"))
            zip.write(
                """<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata><title>Position regression ${UUID.randomUUID()}</title><language>en</language></metadata>
                <manifest><item id="one" href="one.xhtml" media-type="application/xhtml+xml"/>
                <item id="two" href="two.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine><itemref idref="one"/><itemref idref="two"/></spine></package>""".toByteArray(),
            )
            zip.closeEntry()
            listOf("one", "two").forEach { chapter ->
                zip.putNextEntry(ZipEntry("$chapter.xhtml"))
                val paragraphs = (1..60).joinToString("") { index ->
                    "<p>Paragraph $index in chapter $chapter. Reading should always resume at the visible word.</p>"
                }
                zip.write("<html><head><title>Chapter $chapter</title></head><body>$paragraphs</body></html>".toByteArray())
                zip.closeEntry()
            }
        }
        return source
    }
}
