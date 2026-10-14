package com.bapi.ipcamera.http

import com.bapi.ipcamera.config.StreamConfig
import com.bapi.ipcamera.stream.FrameBroadcaster
import fi.iki.elonen.NanoHTTPD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class HttpServerTest {
    private val broadcaster = FrameBroadcaster()
    private val control = object : StreamControlHandler {
        override fun onControl(action: ControlAction) = Unit
        override fun statusJson(): String = "{}"
        override fun currentConfig(): StreamConfig = StreamConfig()
        override fun ensureCameraRunning() = Unit
    }
    private val server = HttpServer(8081, broadcaster, control)

    private fun fakeSession(uri: String, accept: String?): NanoHTTPD.IHTTPSession =
        object : NanoHTTPD.IHTTPSession {
            override fun execute() = Unit
            override fun getCookies(): NanoHTTPD.CookieHandler = throw UnsupportedOperationException()
            override fun getHeaders(): Map<String, String> =
                if (accept == null) emptyMap() else mapOf("accept" to accept)

            override fun getInputStream(): InputStream = InputStream.nullInputStream()
            override fun getMethod() = NanoHTTPD.Method.GET
            override fun getParms(): Map<String, String> = emptyMap()
            override fun getParameters(): Map<String, List<String>> = emptyMap()
            override fun getQueryParameterString(): String? = null
            override fun getUri() = uri
            override fun parseBody(files: MutableMap<String, String>?) = Unit
            override fun getRemoteIpAddress() = "127.0.0.1"
            override fun getRemoteHostName() = "localhost"
        }

    @Test
    fun browserNavigationToRootServesViewerHtml() {
        val r = server.serve(fakeSession("/", "text/html,application/xhtml+xml"))
        assertEquals("text/html; charset=utf-8", r.mimeType)
    }

    @Test
    fun browserNavigationToStreamServesViewerHtml() {
        val r = server.serve(fakeSession("/stream", "text/html"))
        assertEquals("text/html; charset=utf-8", r.mimeType)
    }

    @Test
    fun imageRequestToStreamServesMjpeg() {
        val r = server.serve(fakeSession("/stream", "image/avif,image/webp,image/*,*/*;q=0.8"))
        assertTrue(r.mimeType.startsWith("multipart/x-mixed-replace"))
    }

    @Test
    fun apiRootWithoutHtmlAcceptStaysText() {
        val r = server.serve(fakeSession("/", null))
        assertTrue(r.mimeType.startsWith("text/plain"))
    }
}
