package com.jariisseo.android.ui.web

import com.jariisseo.android.net.exportViewerUrl
import com.jariisseo.android.net.ownerViewerUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/** #17: 사장님 뷰어 주소, 웹 → 앱 브리지 전달, 앱 → 웹 호출 문자열 */
class OwnerBridgeTest {
    @Test
    fun viewerUrl_ownerModeOrSample_handlesTrailingSlash() {
        assertEquals("http://10.0.2.2:8080/s/k3x9q2mf?mode=owner", ownerViewerUrl("http://10.0.2.2:8080/", "k3x9q2mf", sample = false))
        assertEquals("http://10.0.2.2:8080/s/k3x9q2mf?mode=owner", ownerViewerUrl("http://10.0.2.2:8080", "k3x9q2mf", sample = false))
        assertEquals(
            "http://10.0.2.2:8080/viewer/index.html?mode=owner&sample=snapshot",
            ownerViewerUrl("http://10.0.2.2:8080/", "k3x9q2mf", sample = true),
        )
    }

    /** #21: 출력 모드 주소. floor는 층 id 또는 all, 인코딩 */
    @Test
    fun exportViewerUrl_floorOrAll_encoded() {
        assertEquals("http://x/s/k3x9q2mf?mode=export&floor=f-1nf9", exportViewerUrl("http://x/", "k3x9q2mf", "f-1nf9"))
        assertEquals("http://x/s/k3x9q2mf?mode=export&floor=all", exportViewerUrl("http://x", "k3x9q2mf", "all"))
        assertEquals("http://x/s/k3x9q2mf?mode=export&floor=a%26b+c", exportViewerUrl("http://x/", "k3x9q2mf", "a&b c"))
    }

    @Test
    fun bridge_forwardsThroughPost() {
        val calls = mutableListOf<String>()
        var posted = 0
        val bridge = OwnerBridge(
            onReady = { calls += "ready" },
            onTableTap = { id, occupied -> calls += "tap $id $occupied" },
            onSummary = { free, total -> calls += "summary $free/$total" },
            post = { posted++; it() },
            onExport = { calls += "export $it" },   // #21
        )

        bridge.onReady()
        bridge.onTableTap("t-3f9a", true)
        bridge.onSummary(8, 11)
        bridge.onExport("iVBOR...")

        assertEquals(listOf("ready", "tap t-3f9a true", "summary 8/11", "export iVBOR..."), calls)
        assertEquals(4, posted)
    }

    @Test
    fun revertStatusJs_escapesTableId() {
        assertEquals("window.revertStatus(\"t-3f9a\")", revertStatusJs("t-3f9a"))
        assertEquals("window.revertStatus(\"a'b\\\"c\\\\\")", revertStatusJs("a'b\"c\\"))
    }

    @Test
    fun applyStatusJs_escapesTableId() {
        assertEquals("window.applyStatus(\"t-3f9a\", true)", applyStatusJs("t-3f9a", true))
        assertEquals("window.applyStatus(\"a'b\\\"c\\\\\", false)", applyStatusJs("a'b\"c\\", false))
    }
}
