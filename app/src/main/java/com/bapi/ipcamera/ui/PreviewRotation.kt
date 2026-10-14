package com.bapi.ipcamera.ui

/**
 * Toán xoay ảnh thuần cho preview trong app (không phụ thuộc Android) để unit-test.
 * EXIF orientation: 1 = bình thường, 3 = 180°, 6 = 90° CW, 8 = 270° CW.
 */
object PreviewRotation {
    /** Số độ cần xoay bitmap theo chiều kim đồng hồ để ảnh thẳng đứng. */
    fun degreesForExifOrientation(exif: Int): Int = when (exif) {
        6 -> 90
        3 -> 180
        8 -> 270
        else -> 0
    }

    /** Kích thước (rộng x cao) sau khi xoay; đổi chỗ khi xoay 90/270. */
    fun rotatedSize(width: Int, height: Int, degrees: Int): Pair<Int, Int> =
        if (degrees % 180 == 90) height to width else width to height

    /** Hệ số giảm mẫu (lũy thừa 2) để cạnh dài không vượt quá [target]; target<=0 → 1. */
    fun sampleSize(maxSide: Int, target: Int): Int {
        if (target <= 0) return 1
        var s = 1
        while (maxSide / (s * 2) >= target) s *= 2
        return s
    }
}
