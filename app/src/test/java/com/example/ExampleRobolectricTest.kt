package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import com.example.media.ImageEnhancerEngine
import com.example.model.AspectRatioOption
import com.example.model.EnhanceMode
import com.example.model.FilterPreset
import com.example.model.StudioEditState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Bugti Editz`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Bugti Editz", appName)
    }

    @Test
    fun `image enhancer processes ultra hd and square crop`() = runTest {
        val source = createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(100, 140, 180))
        }
        val state = StudioEditState(
            enhanceMode = EnhanceMode.ULTRA_HD,
            enhanceStrength = 0.8f,
            filterPreset = FilterPreset.GOLDEN_HOUR,
            aspectRatio = AspectRatioOption.SQUARE_1_1
        )
        val output = ImageEnhancerEngine.processBitmap(source, state, isExport = false)
        assertNotNull(output)
        assertEquals(output.width, output.height)
        assertTrue(output.width == 80)
    }

    @Test
    fun `studio repository persists login state profile name and theme mode`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo1 = com.example.data.StudioRepository(context)
        repo1.setThemeMode(com.example.model.AppThemeMode.WHITE)
        repo1.setAuthSession(
            isLoggedIn = true,
            email = "creator@bugti.app",
            defaultDisplayName = "Bugti Creator"
        )
        repo1.saveProfileName("Bugti Pro")

        val repo2 = com.example.data.StudioRepository(context)
        val loaded = repo2.preferencesFlow.first()
        assertEquals(com.example.model.AppThemeMode.WHITE, loaded.themeMode)
        assertTrue(loaded.isLoggedIn)
        assertEquals("creator@bugti.app", loaded.loggedInEmail)
        assertEquals("Bugti Pro", loaded.profileName)
    }
}
