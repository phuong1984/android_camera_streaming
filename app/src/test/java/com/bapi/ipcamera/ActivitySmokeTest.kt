package com.bapi.ipcamera

import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import com.bapi.ipcamera.config.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivitySmokeTest {

    @Test
    fun mainActivity_launchesWithoutCrash() {
        Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    @Test
    fun settingsActivity_launchesWithoutCrash() {
        Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
    }

    @Test
    fun switchCameraButton_showsTargetCamera() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val button = activity.findViewById<Button>(R.id.btnSwitchCamera)

        com.bapi.ipcamera.service.StreamState.frontCamera.value = false
        assertEquals(activity.getString(R.string.switch_to_front), button.text.toString())

        com.bapi.ipcamera.service.StreamState.frontCamera.value = true
        assertEquals(activity.getString(R.string.switch_to_rear), button.text.toString())
    }

    @Test
    fun versionLabel_isShownAsVeePrefixedVersionName() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val tvVersion = activity.findViewById<TextView>(R.id.tvVersion)

        assertEquals(AppVersion.label(BuildConfig.VERSION_NAME), tvVersion.text.toString())
    }

    @Test
    fun languageButton_showsFlagOfTargetLanguage() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        SettingsStore(ctx).saveLanguage(Language.DEFAULT)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val button = activity.findViewById<android.widget.ImageButton>(R.id.btnLanguage)

        val expected = androidx.appcompat.content.res.AppCompatResources.getDrawable(
            activity,
            Language.flagRes(Language.DEFAULT)
        )!!
        assertEquals(expected.intrinsicWidth, button.drawable.intrinsicWidth)
        assertEquals(expected.intrinsicHeight, button.drawable.intrinsicHeight)
    }

    @Test
    fun languageButton_switchesStoredLanguageToEnglish() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        SettingsStore(ctx).saveLanguage(Language.DEFAULT)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<android.widget.ImageButton>(R.id.btnLanguage).performClick()

        assertEquals("en", SettingsStore(ctx).loadLanguage())
        assertEquals("en", AppCompatDelegate.getApplicationLocales()[0]?.language)
    }
}
