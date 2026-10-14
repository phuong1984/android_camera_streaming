package com.bapi.ipcamera

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
}
