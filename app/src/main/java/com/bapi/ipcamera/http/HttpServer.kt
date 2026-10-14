package com.bapi.ipcamera.http

import com.bapi.ipcamera.config.StreamConfig
import com.bapi.ipcamera.stream.FrameBroadcaster
import com.bapi.ipcamera.stream.MjpegWriter
import com.bapi.ipcamera.stream.StreamStats
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

interface StreamControlHandler {
    fun onControl(action: ControlAction)
    fun statusJson(): String
    fun currentConfig(): StreamConfig

    /** Đảm bảo camera đang chạy (dùng cho /capture khi chưa có client). */
    fun ensureCameraRunning()
}

class HttpServer(
    port: Int,
    private val broadcaster: FrameBroadcaster,
    private val control: StreamControlHandler,
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) {
            return withCors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""))
        }
        return withCors(route(session))
    }

    private fun route(session: IHTTPSession): Response {
        val wantsHtml = ViewerPage.isHtmlRequest(session.headers["accept"])
        return when (session.uri) {
            // Browser điều hướng (Accept: text/html) nhận trang xem tự khớp tỉ lệ;
            // thẻ <img>/robot (Accept: image/*) vẫn nhận MJPEG thô.
            "/stream" -> if (wantsHtml) serveViewer() else MjpegResponse(broadcaster)
            "/capture" -> serveCapture()
            "/control" -> serveControl(session)
            "/status" -> newFixedLengthResponse(
                Response.Status.OK, "application/json; charset=utf-8", control.statusJson()
            )
            "/" -> if (wantsHtml) {
                serveViewer()
            } else {
                newFixedLengthResponse(
                    Response.Status.OK, "text/plain; charset=utf-8",
                    "IP Camera. Endpoints: /stream /capture /control /status"
                )
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
    }

    private fun serveViewer(): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", ViewerPage.html())

    /** Cho phép trang web ở origin khác (trang robot) đọc /status, /control qua fetch. */
    private fun withCors(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "*")
        return response
    }

    private fun serveCapture(): Response {
        var jpeg = broadcaster.latestFrame()
        if (jpeg == null) {
            control.ensureCameraRunning()
            val deadline = System.currentTimeMillis() + 5000
            while (jpeg == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
                jpeg = broadcaster.latestFrame()
            }
        }
        jpeg ?: return newFixedLengthResponse(
            Response.Status.SERVICE_UNAVAILABLE, "text/plain", "camera not ready"
        )
        return newFixedLengthResponse(
            Response.Status.OK, "image/jpeg", ByteArrayInputStream(jpeg), jpeg.size.toLong()
        )
    }

    private fun serveControl(session: IHTTPSession): Response {
        val params = session.parameters.mapValues { it.value.firstOrNull() ?: "" }
        val action = ControlParams.parse(params)
        if (action != null) control.onControl(action)
        return newFixedLengthResponse(
            Response.Status.OK, "text/plain",
            if (action == null) "unknown control" else "ok"
        )
    }

    /** Response streaming MJPEG, ghi trực tiếp ra socket cho tới khi client ngắt. */
    private class MjpegResponse(private val broadcaster: FrameBroadcaster) : Response(
        Status.OK,
        "multipart/x-mixed-replace; boundary=$BOUNDARY",
        null as InputStream?,
        -1L
    ) {
        public override fun send(outputStream: OutputStream) {
            val writer = MjpegWriter(BOUNDARY)
            val client = broadcaster.newClient()
            val header = (
                "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: multipart/x-mixed-replace; boundary=$BOUNDARY\r\n" +
                    "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                    "Pragma: no-cache\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray(Charsets.ISO_8859_1)
            try {
                outputStream.write(header)
                outputStream.flush()
                var seq = broadcaster.currentSeq()
                while (!Thread.currentThread().isInterrupted) {
                    val pair = broadcaster.awaitFrame(seq, 2000)
                    if (pair == null) {
                        // Không có frame mới: flush để phát hiện client đã ngắt.
                        outputStream.write(EMPTY)
                        outputStream.flush()
                        continue
                    }
                    seq = pair.second
                    val start = System.nanoTime()
                    writer.writeFrame(outputStream, pair.first)
                    val elapsed = System.nanoTime() - start
                    StreamStats.recordWrite(elapsed, elapsed > SLOW_WRITE_NANOS)
                }
            } catch (_: Throwable) {
                // client ngắt hoặc lỗi mạng
            } finally {
                broadcaster.removeClient(client)
                runCatching { outputStream.flush() }
            }
        }

        companion object {
            private const val BOUNDARY = "frameboundary"
            private const val SLOW_WRITE_NANOS = 150_000_000L // 150ms
            private val EMPTY = ByteArray(0)
        }
    }

    companion object {
        const val BOUNDARY = "frameboundary"
    }
}
