package com.bapi.ipcamera.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdlePolicyTest {
    @Test
    fun doesNotStopWhenClientPresent() {
        assertFalse(
            IdlePolicy.shouldStopNow(1, 0, 100_000, 15_000, false, true)
        )
    }

    @Test
    fun doesNotStopWhenNoActivityRecordedYet() {
        // /capture vừa khởi động camera, chưa ghi nhận mốc hoạt động → không dừng.
        assertFalse(
            IdlePolicy.shouldStopNow(0, 0, 100_000, 15_000, false, true)
        )
    }

    @Test
    fun doesNotStopWithinGracePeriod() {
        assertFalse(
            IdlePolicy.shouldStopNow(0, 100_000, 110_000, 15_000, false, true)
        )
    }

    @Test
    fun stopsAfterGracePeriodWithNoClients() {
        assertTrue(
            IdlePolicy.shouldStopNow(0, 100_000, 120_000, 15_000, false, true)
        )
    }

    @Test
    fun neverStopsWhenKeepWarm() {
        assertFalse(
            IdlePolicy.shouldStopNow(0, 100_000, 999_000, 15_000, true, true)
        )
    }

    @Test
    fun doesNotStopAlreadyStoppedCamera() {
        assertFalse(
            IdlePolicy.shouldStopNow(0, 100_000, 999_000, 15_000, false, false)
        )
    }
}
