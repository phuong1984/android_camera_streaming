package com.bapi.ipcamera.stream

import java.io.OutputStream

/**
 * Ghi 1 frame JPEG vào luồng HTTP theo định dạng multipart/x-mixed-replace
 * tương thích ESP32-CAM, đọc được bởi thẻ <img> trong trình duyệt.
 */
class MjpegWriter(val boundary: String) {
    private val partHeader =
        "--$boundary\r\nContent-Type: image/jpeg\r\n".toByteArray(Charsets.ISO_8859_1)
    private val crlf = "\r\n".toByteArray(Charsets.ISO_8859_1)

    fun writeFrame(out: OutputStream, jpeg: ByteArray) {
        out.write(partHeader)
        out.write("Content-Length: ${jpeg.size}\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        out.write(jpeg)
        out.write(crlf)
        out.flush()
    }
}
