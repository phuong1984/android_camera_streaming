package com.bapi.ipcamera.stream

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MjpegWriterTest {
    @Test
    fun writesBoundaryAndLength() {
        val out = ByteArrayOutputStream()
        MjpegWriter("frameboundary").writeFrame(out, byteArrayOf(1, 2, 3))
        val s = out.toByteArray().toString(Charsets.ISO_8859_1)
        assertTrue(s.startsWith("--frameboundary\r\n"))
        assertTrue(s.contains("Content-Type: image/jpeg\r\n"))
        assertTrue(s.contains("Content-Length: 3\r\n"))
        assertTrue(s.endsWith("\r\n\r\n\u0001\u0002\u0003\r\n"))
    }
}
