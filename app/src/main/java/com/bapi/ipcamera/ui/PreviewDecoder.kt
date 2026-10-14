package com.bapi.ipcamera.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import android.util.Log
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

/**
 * Giải mã JPEG thành Bitmap cho preview, đã xoay đúng hướng và giảm mẫu theo [maxDim].
 * API 28+ dùng [ImageDecoder] (tự áp EXIF); API cũ dùng [BitmapFactory] + [ExifInterface].
 */
object PreviewDecoder {
    fun decode(jpeg: ByteArray, maxDim: Int): Bitmap? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodeViaImageDecoder(jpeg, maxDim)
            } else {
                decodeViaBitmapFactory(jpeg, maxDim)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not decode preview JPEG", e)
            null
        }

    private fun decodeViaImageDecoder(jpeg: ByteArray, maxDim: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(jpeg))) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val sample = PreviewRotation.sampleSize(
                maxOf(info.size.width, info.size.height), maxDim
            )
            if (sample > 1) decoder.setTargetSampleSize(sample)
        }

    private fun decodeViaBitmapFactory(jpeg: ByteArray, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = PreviewRotation.sampleSize(
                maxOf(bounds.outWidth, bounds.outHeight), maxDim
            )
        }
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts) ?: return null
        return rotate(bmp, exifDegrees(jpeg))
    }

    private fun exifDegrees(jpeg: ByteArray): Int {
        val orientation = try {
            ExifInterface(ByteArrayInputStream(jpeg)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not read preview JPEG orientation", e)
            ExifInterface.ORIENTATION_NORMAL
        }
        return PreviewRotation.degreesForExifOrientation(orientation)
    }

    private fun rotate(bmp: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bmp
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        if (rotated != bmp) bmp.recycle()
        return rotated
    }

    private const val TAG = "PreviewDecoder"
}
