package com.jariisseo.android.net

import java.net.URLEncoder

/**
 * #17: 사장님 뷰 WebView 주소 (API 정의서 6장). 손님 웹과 같은 페이지를 mode=owner로 염.
 * sample = debug 전용 샘플 뷰어 (server#16 손님 웹 페이지 전에 브리지·화면 확인용)
 */
fun ownerViewerUrl(baseUrl: String, publicId: String, sample: Boolean): String {
    val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    return if (sample) "${base}viewer/index.html?mode=owner&sample=snapshot" else "${base}s/$publicId?mode=owner"
}

/** #21: 상태 색 없는 PNG 출력 주소. floor = 층 id 또는 "all"(모든 층을 위아래로 한 장) */
fun exportViewerUrl(baseUrl: String, publicId: String, floor: String): String {
    val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    return "${base}s/$publicId?mode=export&floor=${URLEncoder.encode(floor, "UTF-8")}"
}
