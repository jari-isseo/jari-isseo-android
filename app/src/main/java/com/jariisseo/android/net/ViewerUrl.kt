package com.jariisseo.android.net

/**
 * #17: 사장님 뷰 WebView 주소 (API 정의서 6장). 손님 웹과 같은 페이지를 mode=owner로 염.
 * sample = debug 전용 샘플 뷰어 (server#16 손님 웹 페이지 전에 브리지·화면 확인용)
 */
fun ownerViewerUrl(baseUrl: String, publicId: String, sample: Boolean): String {
    val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    return if (sample) "${base}viewer/index.html?mode=owner&sample=snapshot" else "${base}s/$publicId?mode=owner"
}
