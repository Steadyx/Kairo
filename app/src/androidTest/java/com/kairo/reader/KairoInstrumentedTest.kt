package com.kairo.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

private const val DEBUG_APPLICATION_ID = "com.kairo.reader.debug"
private const val DEBUG_APPLICATION_LABEL = "Kairo Dev"
private const val EXPECTED_TARGET_SDK = 37

@RunWith(AndroidJUnit4::class)
class KairoInstrumentedTest {
    @Test
    fun debugBuildUsesSeparateApplicationIdentity() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val applicationLabel =
            appContext.applicationInfo.loadLabel(appContext.packageManager).toString()

        assertEquals(DEBUG_APPLICATION_ID, appContext.packageName)
        assertEquals(DEBUG_APPLICATION_LABEL, applicationLabel)
        assertEquals(EXPECTED_TARGET_SDK, appContext.applicationInfo.targetSdkVersion)
    }
}
