package com.jariisseo.android.share

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** #22: 링크 → QR 칸 (true = 검은 칸). 오류 정정 M, 둘레 여백 4칸 포함 */
fun qrMatrix(text: String): BitMatrix = QRCodeWriter().encode(
    text, BarcodeFormat.QR_CODE, 0, 0,
    mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4),
)

/** 칸을 정수 배로 키운 흑백 이미지 (한 변 ≤ px). 화면 표시·사진 저장 모두 이것 */
fun BitMatrix.toBitmap(px: Int): Bitmap {
    val cell = maxOf(1, px / width)
    val size = width * cell
    val pixels = IntArray(size * size) { i -> if (get(i % size / cell, i / size / cell)) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

/** 화면에 보일 짧은 링크: http(s):// 와 끝 / 를 뗌 (복사·공유는 원래 주소 그대로) */
fun displayUrl(url: String): String = url.substringAfter("://").trimEnd('/')
