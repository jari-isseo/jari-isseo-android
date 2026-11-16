package com.jariisseo.android.share

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class QrTest {
    /** #22 완료 조건 대신: 만든 QR을 다시 읽으면 같은 링크가 나온다 */
    @Test
    fun qrDecodesBackToSameUrl() {
        val url = "http://jariisseo.mingu.cloud/s/k3x9q2mf"
        val m = qrMatrix(url)
        val cell = 4
        val size = m.width * cell
        val pixels = IntArray(size * size) { i -> if (m.get(i % size / cell, i / size / cell)) 0xFF000000.toInt() else -1 }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels)))

        assertEquals(url, QRCodeReader().decode(bitmap, mapOf(DecodeHintType.PURE_BARCODE to true)).text)
    }

    /** 둘레 여백 4칸이 비어 있어야 카메라가 잘 잡음 */
    @Test
    fun qrHasQuietZone() {
        val m = qrMatrix("http://x/s/k3x9q2mf")
        for (i in 0 until m.width) for (j in 0 until 4) {
            assertFalse(m.get(i, j))
            assertFalse(m.get(j, i))
        }
    }

    @Test
    fun displayUrlDropsSchemeAndTrailingSlash() {
        assertEquals("jariisseo.mingu.cloud/s/k3x9q2mf", displayUrl("http://jariisseo.mingu.cloud/s/k3x9q2mf"))
        assertEquals("jariisseo.mingu.cloud/s/k3x9q2mf", displayUrl("https://jariisseo.mingu.cloud/s/k3x9q2mf/"))
        assertEquals("x/s/a", displayUrl("x/s/a"))
    }
}
