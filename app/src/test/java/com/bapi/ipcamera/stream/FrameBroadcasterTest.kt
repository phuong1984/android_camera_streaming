package com.bapi.ipcamera.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameBroadcasterTest {
    @Test
    fun latestFrame_isNullBeforeFirstPublish() {
        val b = FrameBroadcaster()
        assertNull(b.latestFrame())
    }

    @Test
    fun publish_storesLatest() {
        val b = FrameBroadcaster()
        b.publish(byteArrayOf(9, 8))
        assertArrayEquals(byteArrayOf(9, 8), b.latestFrame())
    }

    @Test
    fun clientCount_tracksSubscribers() {
        val b = FrameBroadcaster()
        val c = b.newClient()
        assertEquals(1, b.clientCount())
        b.removeClient(c)
        assertEquals(0, b.clientCount())
    }

    @Test
    fun awaitFrame_wakesOnPublish() {
        val b = FrameBroadcaster()
        val seqBefore = b.currentSeq()
        val t = Thread { Thread.sleep(50); b.publish(byteArrayOf(1)) }
        t.start()
        val pair = b.awaitFrame(seqBefore, 2000)
        assertArrayEquals(byteArrayOf(1), pair?.first)
        t.join()
    }
}
